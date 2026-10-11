import { createHmac, randomInt, timingSafeEqual } from "node:crypto";
import { SignJWT, jwtVerify } from "jose";

export function createJwtVerifier(config) {
  const key = new TextEncoder().encode(config.sessionSigningKey);
  return async (token) => {
    const { payload } = await jwtVerify(token, key, {
      algorithms: ["HS256"],
      issuer: "swiftpay-merchant-backend",
      audience: "swiftpay-merchant-api"
    });
    if (typeof payload.sub !== "string" || payload.sub.length === 0) {
      throw new Error("JWT subject is required");
    }
    return payload;
  };
}

export function createOtpAuth(config, { sendEmail, now = Date.now, codeGenerator = () => String(randomInt(0, 1_000_000)).padStart(6, "0") }) {
  const allowedEmails = new Set(config.allowedEmails.map((email) => email.toLowerCase()));
  const challenges = new Map();
  const lastSentAt = new Map();
  const key = new TextEncoder().encode(config.sessionSigningKey);

  async function requestOtp(emailValue) {
    const email = emailValue.trim().toLowerCase();
    if (!allowedEmails.has(email)) return;

    const previousSentAt = lastSentAt.get(email) || 0;
    if (now() - previousSentAt < 60_000) return;

    const code = codeGenerator();
    const codeHash = createHmac("sha256", key).update(`${email}:${code}`).digest();
    const sentAt = now();
    const challenge = { codeHash, expiresAt: sentAt + 10 * 60_000, attempts: 0 };
    challenges.set(email, challenge);
    lastSentAt.set(email, sentAt);
    try {
      await sendEmail(email, code);
    } catch (error) {
      if (challenges.get(email) === challenge) {
        challenges.delete(email);
        lastSentAt.delete(email);
      }
      throw error;
    }
  }

  async function verifyOtp(emailValue, code) {
    const email = emailValue.trim().toLowerCase();
    const challenge = challenges.get(email);
    if (!allowedEmails.has(email) || !challenge || challenge.expiresAt <= now() || challenge.attempts >= 5) {
      challenges.delete(email);
      return null;
    }

    challenge.attempts += 1;
    const submittedHash = createHmac("sha256", key).update(`${email}:${code}`).digest();
    if (!timingSafeEqual(challenge.codeHash, submittedHash)) return null;
    challenges.delete(email);

    const accessToken = await new SignJWT({
      scope: "swiftpay:payments:read swiftpay:payments:write swiftpay:disbursements:read swiftpay:disbursements:write"
    })
      .setProtectedHeader({ alg: "HS256", typ: "JWT" })
      .setSubject(email)
      .setIssuer("swiftpay-merchant-backend")
      .setAudience("swiftpay-merchant-api")
      .setIssuedAt()
      .setExpirationTime("8h")
      .sign(key);

    return { accessToken, expiresInSeconds: 8 * 60 * 60, email };
  }

  return { requestOtp, verifyOtp };
}

export async function sendOtpEmail(config, email, code, fetchImpl = fetch) {
  const response = await fetchImpl("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${config.resendApiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      from: config.otpFromEmail,
      to: [email],
      subject: "Your SwiftPay sign-in code",
      text: `Your SwiftPay sign-in code is ${code}. It expires in 10 minutes.`
    }),
    signal: AbortSignal.timeout(10000)
  });
  if (!response.ok) {
    const responseBody = await response.text();
    const providerError = (() => {
      try {
        return JSON.parse(responseBody);
      } catch {
        return null;
      }
    })();
    const providerCode = [providerError?.name, providerError?.code]
      .find((value) => typeof value === "string" && /^[a-zA-Z0-9_.-]{1,64}$/.test(value));
    const codeDetail = providerCode ? `, ${providerCode}` : "";
    throw new Error(`OTP email provider rejected request (HTTP ${response.status}${codeDetail})`);
  }
}

export function principalHasScope(principal, requiredScope) {
  const scopes = [
    ...(typeof principal.scope === "string" ? principal.scope.split(/\s+/) : []),
    ...(Array.isArray(principal.scopes) ? principal.scopes : []),
    ...(Array.isArray(principal.permissions) ? principal.permissions : [])
  ];
  return scopes.includes(requiredScope);
}