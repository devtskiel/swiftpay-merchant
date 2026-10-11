import { createJwtVerifier, createOtpAuth, sendOtpEmail } from "./auth.js";
import { createApp } from "./app.js";
import { loadConfig } from "./config.js";

const config = loadConfig();
const otpAuth = createOtpAuth(config, { sendEmail: (email, code) => sendOtpEmail(config, email, code) });
const app = createApp({
  config,
  verifyAccessToken: createJwtVerifier(config),
  requestOtp: otpAuth.requestOtp,
  verifyOtp: otpAuth.verifyOtp
});
app.listen(config.port, "0.0.0.0", () => {
  console.log(`SwiftPay backend listening on port ${config.port}`);
});