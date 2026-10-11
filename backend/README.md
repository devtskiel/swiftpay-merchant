# SwiftPay backend

This service is the trusted boundary between the Android client and SwiftPay. Merchant credentials and order signing stay on the server. The service defaults to the SwiftPay sandbox; live mode requires an explicit opt-in.

## Run

Use Node.js 20.6 or newer. Copy `.env.example` to `.env`, configure sandbox credentials, an allowlisted merchant email, and a verified Resend sender, then run:

```sh
npm install
npm test
npm start
```

The API refuses to start without SwiftPay credentials, an allowlist, a session-signing key, and a Resend key/from address. Sign-in codes are sent only to `AUTH_ALLOWED_EMAILS`; non-allowlisted addresses receive the same accepted response without an email. Codes expire after ten minutes, are single-use, and allow five attempts. The server issues an eight-hour scoped JWT after successful verification.

Set Android `PAYMENT_BACKEND_URL` to this service’s HTTPS base URL. Android debug builds default to the emulator host `http://10.0.2.2:3000/` when `PAYMENT_BACKEND_URL` is unset; release builds default to `https://swiftpay-merchant.onrender.com/` and can be overridden with a Gradle property, environment variable, or `local.properties` value.

- `swiftpay:payments:read`
- `swiftpay:payments:write`
- `swiftpay:disbursements:read`
- `swiftpay:disbursements:write`

The old app login creates a local UUID and is not accepted by this backend. The mobile client must use the backend OTP endpoints and store the returned bearer token securely.

## Routes

- `POST /v1/payments/orders` creates an HMAC-signed SwiftPay order and returns a hosted checkout URL.
- `GET /v1/payments/:paymentToken/status` checks payment status using the payment-scoped token.
- `GET /v1/wallet/balance` returns the available disbursement-wallet balance and inbound top-up account number.
- `POST /v1/payment-links` creates a fixed-amount hosted payment link.
- `GET /v1/payment-links/:code` reads a link; `DELETE` invalidates it.
- `POST /v1/disbursements` submits an InstaPay or PESONet transfer.
- `GET /v1/disbursements` and `GET /v1/disbursements/:id` read transfer state.
- `GET /v1/disbursement-institutions` retrieves provider institution codes.
- `GET /healthz` is an unauthenticated health check.

All `/v1` routes require an issuer- and audience-verified JWT. Order creation requires a caller-provided reference number for provider idempotency. The backend returns disbursements as `PENDING` after SwiftPay accepts them; acceptance is not settlement. Poll the disbursement status until a terminal state.

OTP challenges are currently held in process memory. Run a single backend instance until a shared persistent challenge store is configured; otherwise a restart or load balancing between instances can invalidate sign-in challenges.

The published SwiftPay API exposes the available disbursement-wallet ledger balance, not a combined settlement or card balance. Dynamic QR creation is not documented by the live API and is not proxied. Payment status, disbursement history, and the disbursement balance are read from SwiftPay; the app does not treat a locally cached value as authoritative.

Deploy behind HTTPS, keep the environment variables in a secrets manager, and configure production only with `SWIFTPAY_BASE_URL=https://api.pay.live.swiftpay.ph/api/` and `SWIFTPAY_ALLOW_LIVE=true`. Do not place SwiftPay credentials or a shared backend secret in the Android build.

## Render

The repository-root `render.yaml` defines the live SwiftPay Docker web service. In Render, provide the rotated production credentials, session-signing key, allowed operator email, and verified Resend settings as secrets. The Android release build defaults to the service HTTPS URL as `PAYMENT_BACKEND_URL`.