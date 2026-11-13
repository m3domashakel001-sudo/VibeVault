# VibeVault 7.7.0 — security audit notes

## Fixed in 7.7.0
- Signing passwords removed from `app/build.gradle.kts`; credentials come from environment / Gradle properties; CI uses secrets;
  `.gitignore` added (keystores, secrets); stray duplicate `build.yml` and `download` file removed.
- `DownloadEngine.saveToDownloads` and the Vault restore: no `!!`; the MediaStore entry is deleted on any failure, so no
  half-written or permanently "pending" file is left; the copy length is verified before the file is published.
- WebView bridge (`MSBridge`): a password-save request is accepted only when it is for the page the user is really on, over
  https, with size limits; auto-fill and form watching only run on https pages. Mixed content is never allowed; content/file
  access from file URLs disabled.
- `BootReceiver` is no longer exported (it only needs system broadcasts).
- `PlaybackService` (exported for the car / system): media items sent by OTHER apps are limited to http(s)/content URIs, so
  another app can no longer make VibeVault open `file://` paths in its private storage.
- Removed logging of recognition-service reply bodies.
- 8 unsafe `!!` removed (decoder buffers, file chooser, Bluetooth rule dialog, browser URL, detector, radio, platform links).
- Interrupt flag preserved after cancellable sleeps in the download engine.

## Reviewed, no change needed
- Firestore rules: every collection is limited to `users/{uid}` of the signed-in owner; everything else is closed.
- Saved browser passwords: AES-256-GCM with an Android Keystore key, saved only after the user confirms the dialog.
- TLS: `onReceivedSslError` cancels the load (certificates are validated).
- Vault is excluded from backups.

## OPEN — needs your decision
1. **Third-party service secrets are compiled into the app** (`DetectorKeys` in `RecognitionChain.kt`: ACRCloud access key +
   secret, RapidAPI key, Last.fm key). Anyone can extract them from the APK. Rotate them and, ideally, call those services
   through a small backend/Cloud Function of yours that holds the secrets. I did not move them to BuildConfig because that
   would not hide them and would break the detector if a build lacked the values.
2. **Firebase API key** (`google-services.json`): restrict it in Google Cloud Console to package `com.example.ytsaver` + your
   release SHA-1 and to the Firebase APIs only.
3. `usesCleartextTraffic="true"` stays: the in-app browser must open plain-http sites. Insecure sub-resources on https pages
   are blocked, and passwords are only handled on https.
4. `SYSTEM_ALERT_WINDOW` / `USE_FULL_SCREEN_INTENT` are kept for "open the app when Bluetooth connects"; Google Play may
   require a justification. About 100 `catch (_: Exception)` blocks remain; they were not individually audited.

## 7.7.2 additions
- Vault recovery: the temporary PIN is random (8 digits), stored only as a salted PBKDF2 hash, one-time, valid 15 minutes, cancelled after
  5 wrong tries; wrong recovery e-mails are rate-limited (5 per 10 minutes) and at most 3 codes are sent per hour. The recovery e-mail is
  stored on the phone (app-private storage) because its first two characters must be shown; it can only be changed with the current PIN.
  A reset never reveals files without a NEW PIN being chosen. The mail text is built by the server, not by the app.
- Recommendations send only artist / channel / title words to YouTube; artist pictures send the artist name to Deezer (switchable).
