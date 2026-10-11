import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { after, before, test } from "node:test";
import { createApp } from "../src/app.js";
import { canonicalOrderMessage, buildOrderPayload } from "../src/swiftpay.js";

const config = {
  swiftPayBaseUrl: new URL("https://provider.example/api/"),
  swiftPayAccessKey: "A".repeat(32),
  swiftPaySecretKey: "S".repeat(32)
};
const tokenPrincipal = {
  sub: "merchant-user-1",
  scope: "swiftpay:payments:write swiftpay:payments:read swiftpay:disbursements:write swiftpay:disbursements:read"
};
let server;
let baseUrl;
let requests;
let failOtpRequest = false;
let accountResponse;

before(async () => {
  requests = [];
  accountResponse = { number: "001002003004", balance: 12500.75 };
  let app;
  app = createApp({
    config,
    requestOtp: async () => {
      if (failOtpRequest) throw new Error("OTP email provider rejected request (HTTP 403)");
    },
    verifyOtp: async () => null,
    verifyAccessToken: async (token) => {
      if (!new Set(["valid-test-token", "read-only-test-token"]).has(token)) throw new Error("invalid token");
      if (token === "read-only-test-token") return { sub: "reader-1", scope: "swiftpay:payments:read" };
      return tokenPrincipal;
    },
    fetchImpl: async (url, options) => {
      requests.push({ url: url.toString(), options });
      const response = url.pathname.endsWith("/payments/links")
        ? { status: 200, body: { code: "PL-1", paymentUrl: "https://pay.example/link/PL-1" } }
        : url.pathname.endsWith("/payments/status")
          ? { status: 200, body: "EXECUTED" }
          : url.pathname.endsWith("/disbursements/account")
          ? { status: 200, body: accountResponse }
          : url.pathname.endsWith("/disbursements/send")
            ? { status: 200, body: { id: "9d51c8e2-3211-4c01-a9a1-19a9a8bd5c01" } }
            : { status: 202, body: { customerRedirectUrl: "https://pay.example/checkout" } };
      return new Response(JSON.stringify(response.body), {
        status: response.status,
        headers: { "content-type": "application/json" }
      });
    }
  });
  assert.equal(app.get("trust proxy"), 1);
  server = app.listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
});

test("failed OTP delivery returns a retryable error without exposing provider details", async () => {
  failOtpRequest = true;
  try {
    const response = await fetch(`${baseUrl}/v1/auth/request-otp`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: "merchant@example.com" })
    });
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { error: "Sign-in email could not be sent; try again later" });
  } finally {
    failOtpRequest = false;
  }
});

test("order signature uses sorted x-fields and two-decimal amount", () => {
  const fields = {
    x_reference_no: "R-1",
    details: { note: "ignored" },
    x_amount: 100,
    x_access_key: "access"
  };
  const canonical = "x_access_keyaccessx_amount100.00x_reference_noR-1";
  assert.equal(canonicalOrderMessage(fields), canonical);

  const payload = buildOrderPayload({
    amount: 100,
    referenceNo: "R-1",
    currency: "PHP",
    details: {}
  }, config, "00000000-0000-4000-8000-000000000001");
  const expected = createHmac("sha256", config.swiftPaySecretKey).update(
    "x_access_key" + config.swiftPayAccessKey +
    "x_amount100.00" +
    "x_currencyPHP" +
    "x_payment_id00000000-0000-4000-8000-000000000001" +
    "x_reference_noR-1"
  ).digest("hex");
  assert.equal(payload.signature, expected);
  assert.equal(payload.generate_customer_redirect_url, true);
});

test("payment order requires a verified scoped bearer token and signs upstream", async () => {
  const denied = await fetch(`${baseUrl}/v1/payments/orders`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ amount: 15, referenceNo: "ORDER-1", details: {} })
  });
  assert.equal(denied.status, 401);

  const response = await fetch(`${baseUrl}/v1/payments/orders`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 15, referenceNo: "ORDER-1", details: {} })
  });
  assert.equal(response.status, 202);
  const result = await response.json();
  assert.equal(result.status, "PENDING");
  assert.equal(result.redirectUrl, "https://pay.example/checkout");
  assert.equal(requests.at(-1).url, "https://provider.example/api/orders");
  const providerBody = JSON.parse(requests.at(-1).options.body);
  assert.equal(providerBody.x_amount, 15);
  assert.equal(providerBody.x_reference_no, "ORDER-1");
  assert.match(providerBody.signature, /^[a-f0-9]{64}$/);
});

test("read-only scope can poll payment status using the provider payment token", async () => {
  const response = await fetch(`${baseUrl}/v1/payments/payment-token-1/status`, {
    headers: { authorization: "Bearer read-only-test-token" }
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { paymentId: "payment-token-1", status: "EXECUTED" });
  assert.equal(requests.at(-1).url, "https://provider.example/api/payments/status");
  assert.equal(requests.at(-1).options.headers["X-Swiftpay-Payment-Token"], "payment-token-1");

  const forbidden = await fetch(`${baseUrl}/v1/payments/orders`, {
    method: "POST",
    headers: {
      authorization: "Bearer read-only-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 10, referenceNo: "DENIED-1", details: {} })
  });
  assert.equal(forbidden.status, 403);
});

test("wallet balance is read from the documented provider endpoint using Basic auth", async () => {
  const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
    headers: { authorization: "Bearer valid-test-token" }
  });
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { balance: 12500.75, accountNumber: "001002003004" });
  assert.equal(requests.at(-1).url, "https://provider.example/api/disbursements/account");
  assert.match(requests.at(-1).options.headers.Authorization, /^Basic /);
});

test("wallet balance accepts the published available_balance field without an account number", async () => {
  accountResponse = { available_balance: 9876.5 };
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { balance: 9876.5 });
  } finally {
    accountResponse = { number: "001002003004", balance: 12500.75 };
  }
});

test("wallet balance accepts a numeric string from the provider", async () => {
  accountResponse = { available_balance: "9876.50" };
  try {
    const response = await fetch(`${baseUrl}/v1/wallet/balance`, {
      headers: { authorization: "Bearer valid-test-token" }
    });
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { balance: 9876.5 });
  } finally {
    accountResponse = { number: "001002003004", balance: 12500.75 };
  }
});

test("payment links use Basic auth and require a fixed PHP amount", async () => {
  const response = await fetch(`${baseUrl}/v1/payment-links`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 125.5, currency: "PHP", description: "Invoice" })
  });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).code, "PL-1");
  assert.equal(requests.at(-1).url, "https://provider.example/api/payments/links");
  assert.match(requests.at(-1).options.headers.Authorization, /^Basic /);
  assert.equal(JSON.parse(requests.at(-1).options.body).amount, 125.5);

  const invalid = await fetch(`${baseUrl}/v1/payment-links`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({ amount: 0, currency: "PHP" })
  });
  assert.equal(invalid.status, 400);
});

test("disbursement is represented as pending after provider acceptance", async () => {
  const appResponse = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({
      amount: 75,
      referenceNo: "TRANSFER-1",
      channel: "INSTAPAY",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "123456", fullName: "Test Recipient" }
    })
  });
  assert.equal(appResponse.status, 202);
  assert.equal((await appResponse.json()).status, "PENDING");
  assert.equal(requests.at(-1).url, "https://provider.example/api/disbursements/send");
  assert.match(requests.at(-1).options.headers.Authorization, /^Basic /);
});

test("rejects invalid transfer amount before contacting provider", async () => {
  const callCount = requests.length;
  const response = await fetch(`${baseUrl}/v1/disbursements`, {
    method: "POST",
    headers: {
      authorization: "Bearer valid-test-token",
      "content-type": "application/json"
    },
    body: JSON.stringify({
      amount: 50001,
      referenceNo: "TRANSFER-2",
      channel: "INSTAPAY",
      institutionCode: "TESTBANK",
      recipientInformation: { accountNumber: "123456", fullName: "Test Recipient" }
    })
  });
  assert.equal(response.status, 400);
  assert.equal(requests.length, callCount);
});