# Vault PIN recovery mail service

The app cannot send e-mail by itself (that would need mail-account passwords inside the APK, which anyone can extract).
"Reset PIN" therefore posts `{to, code, lang}` to a small HTTPS service of yours, which e-mails the one-time PIN.

1. `firebase init functions`, copy `index.js` / `package.json` here, set your sender address in `FROM`.
2. `firebase functions:secrets:set SMTP_URL`  (e.g. `smtps://user:pass@smtp.example.com:465`)
3. `firebase deploy --only functions:vaultPin`
4. Put the function's HTTPS address in `RECOVERY_MAIL_ENDPOINT` (VaultRecovery.kt) and rebuild.

Until step 4 is done the app says that e-mail sending isn't set up and does NOT create or show a temporary PIN.
Add rate limiting / App Check for production use.
