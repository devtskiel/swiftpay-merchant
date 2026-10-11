import assert from "node:assert/strict";
import { test } from "node:test";
import { createJwtVerifier, createOtpAuth } from "../src/auth.js";

test("OTP is allowlisted, one-time, and issues a scoped verifiable session", async () => {
  let now = 1_000_000;
  const sentMessages = [];
  const config = {
    allowedEmails: ["merchant@example.com"],
    sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes"
  };
  const auth = createOtpAuth(config, {
    now: () => now,
    codeGenerator: () => "204681",
    sendEmail: async (email, code) => sentMessages.push({ email, code })
  });

  await auth.requestOtp("other@example.com");
  assert.equal(sentMessages.length, 0);

  await auth.requestOtp("MERCHANT@example.com");
  assert.deepEqual(sentMessages, [{ email: "merchant@example.com", code: "204681" }]);
  assert.equal(await auth.verifyOtp("merchant@example.com", "000000"), null);

  const session = await auth.verifyOtp("merchant@example.com", "204681");
  assert.equal(session.email, "merchant@example.com");
  assert.equal(session.expiresInSeconds, 8 * 60 * 60);

  const verifyToken = createJwtVerifier(config);
  const principal = await verifyToken(session.accessToken);
  assert.equal(principal.sub, "merchant@example.com");
  assert.match(principal.scope, /swiftpay:disbursements:write/);
  assert.equal(await auth.verifyOtp("merchant@example.com", "204681"), null);

  await auth.requestOtp("merchant@example.com");
  assert.equal(sentMessages.length, 1);
  now += 60_001;
  await auth.requestOtp("merchant@example.com");
  assert.equal(sentMessages.length, 2);
});

test("expired OTPs cannot create sessions", async () => {
  let now = 1_000_000;
  const auth = createOtpAuth({
    allowedEmails: ["merchant@example.com"],
    sessionSigningKey: "session-signing-key-that-is-at-least-32-bytes"
  }, {
    now: () => now,
    codeGenerator: () => "123456",
    sendEmail: async () => {}
  });
  await auth.requestOtp("merchant@example.com");
  now += 10 * 60_000;
  assert.equal(await auth.verifyOtp("merchant@example.com", "123456"), null);
});