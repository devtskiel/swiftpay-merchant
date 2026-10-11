import { createHmac, randomUUID } from "node:crypto";

export function canonicalOrderMessage(fields) {
  return Object.entries(fields)
    .filter(([key, value]) => key.startsWith("x_") && value !== null && value !== undefined)
    .sort(([left], [right]) => left < right ? -1 : left > right ? 1 : 0)
    .map(([key, value]) => `${key}${key === "x_amount" ? Number(value).toFixed(2) : String(value)}`)
    .join("");
}

export function signOrder(fields, secretKey) {
  return createHmac("sha256", Buffer.from(secretKey, "utf8"))
    .update(canonicalOrderMessage(fields), "utf8")
    .digest("hex");
}

export function buildOrderPayload(input, config, paymentId = randomUUID()) {
  const fields = {
    x_access_key: config.swiftPayAccessKey,
    x_amount: Number(input.amount.toFixed(2)),
    x_reference_no: input.referenceNo,
    x_payment_id: paymentId,
    x_currency: input.currency
  };
  return {
    ...fields,
    signature: signOrder(fields, config.swiftPaySecretKey),
    details: input.details,
    generate_customer_redirect_url: true,
    ...(input.institutionCode ? { institution_code: input.institutionCode } : {}),
    ...(input.merchantTag ? { merchant_tag: input.merchantTag } : {})
  };
}

export class SwiftPayError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export async function callSwiftPay(config, fetchImpl, path, options = {}) {
  const headers = { Accept: "application/json" };
  if (options.auth === "basic") {
    const credentials = Buffer.from(`${config.swiftPayAccessKey}:${config.swiftPaySecretKey}`).toString("base64");
    headers.Authorization = `Basic ${credentials}`;
  }
  if (options.paymentToken) {
    headers["X-Swiftpay-Payment-Token"] = options.paymentToken;
  }
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  let response;
  try {
    response = await fetchImpl(new URL(path.replace(/^\//, ""), config.swiftPayBaseUrl), {
      method: options.method || "GET",
      headers,
      ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
      signal: AbortSignal.timeout(15000)
    });
  } catch {
    throw new SwiftPayError(502, "SwiftPay is temporarily unavailable");
  }

  const responseText = await response.text();
  let data = null;
  if (responseText) {
    try {
      data = JSON.parse(responseText);
    } catch {
      throw new SwiftPayError(502, "SwiftPay returned an invalid response");
    }
  }
  if (!response.ok) {
    const code = data?.errorCode || data?.code;
    const message = code ? `SwiftPay request failed (${code})` : "SwiftPay request failed";
    throw new SwiftPayError(response.status >= 500 ? 502 : response.status, message);
  }
  return { status: response.status, data };
}

export function newReference() {
  return randomUUID();
}