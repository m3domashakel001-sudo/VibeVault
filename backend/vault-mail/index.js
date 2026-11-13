// VibeVault - vault PIN recovery mail service (Firebase Cloud Functions, 2nd gen).
// The app POSTs {to, code, lang}; THIS service writes the e-mail, so the app cannot be used to send other content.
const { onRequest } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const nodemailer = require("nodemailer");

const SMTP_URL = defineSecret("SMTP_URL");          // e.g. smtps://user:password@smtp.example.com:465
const FROM = "VibeVault <no-reply@your-domain.example>";

// very small in-memory limiter (use Firestore / App Check for production): 3 mails per address per hour
const sent = new Map();

exports.vaultPin = onRequest({ secrets: [SMTP_URL], cors: false, maxInstances: 3 }, async (req, res) => {
  if (req.method !== "POST") return res.status(405).send("POST only");
  const { to, code, lang } = req.body || {};
  if (typeof to !== "string" || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(to) || to.length > 254) return res.status(400).send("bad address");
  if (typeof code !== "string" || !/^\d{8}$/.test(code)) return res.status(400).send("bad code");

  const now = Date.now();
  const list = (sent.get(to) || []).filter((t) => now - t < 3600_000);
  if (list.length >= 3) return res.status(429).send("too many");
  sent.set(to, [...list, now]);

  const ar = lang === "ar";
  const subject = ar ? "رمز VibeVault المؤقت" : "Your VibeVault temporary PIN";
  const text = ar
    ? `رمزك المؤقت لإعادة تعيين رمز الخزنة: ${code}\nيعمل مرة واحدة ولمدة 15 دقيقة. إذا لم تطلبه فتجاهل هذه الرسالة.`
    : `Your temporary PIN to reset the vault PIN: ${code}\nIt works once and expires in 15 minutes. If you did not ask for it, ignore this e-mail.`;
  try {
    await nodemailer.createTransport(SMTP_URL.value()).sendMail({ from: FROM, to, subject, text });
    return res.status(200).send("ok");
  } catch (e) {
    console.error("mail failed", e.code || e.message);   // never log the address or the code
    return res.status(502).send("mail failed");
  }
});
