# Nyte Gallery: privacy, terms and Data safety working notes

Working notes, not a published policy. They describe what the code does as of commit `main` on 2026-10-31,
so the real Privacy Policy, Terms of Service and Play Console Data safety form can be written from facts.

- App: **Nyte Gallery**
- Developer: **Nytefall Interactive**
- Contact (planned): **nytefall.interactive@gmail.com**
- Current package: `com.nytefall.nytegallery` (renamed from `com.example.saogallery` on 2026-10-31)

Markers used below:
- **[CODE]** checked in the source code.
- **[ASSUMPTION]** not proven by the code, or a decision still to make. Confirm before publishing.

---

## 1. App summary

Nyte Gallery is a private photo vault for Android (minSdk 26, targetSdk 35). The user moves photos from the
phone gallery into the app, where they are stored encrypted in the app's private storage. Photos can be renamed,
moved to Trash, put back into the public gallery ("Restore"), and saved to or restored from a password-encrypted
backup file. An optional app lock (password, PIN, pattern, fingerprint) protects the app, with an offline
recovery code. **[CODE]**

Since 2026-11-02 the app has **no internet access at all** (no `INTERNET` permission). **[CODE]**

There are no user accounts, no sign-in, and no Nyte server. **[CODE]**

## 2. Data processed and where it lives

| Data | Where it lives | Leaves the phone? |
|---|---|---|
| Photos moved into the vault | `files/vault/<id>.nyt`, app-private, AES-256-GCM with an Android KeyStore key **[CODE]** | Only inside a backup file the user creates and saves where they choose **[CODE]** |
| Thumbnails (384 px) | `files/vault/<id>.thumb2.nyt`, encrypted the same way **[CODE]** | No |
| Photo index: id, name, date taken, trash date, file name | `files/vault/index.json`, app-private, **not encrypted** (plain JSON) **[CODE]** | Inside backup files only (encrypted there) |
| Decoded images | In memory only (LRU cache) while the app runs **[CODE]** | No |
| App lock secrets (password, PIN, pattern) | SharedPreferences `nyte_lock`, stored as PBKDF2-HMAC-SHA256 hashes with a random salt per secret, never plain text **[CODE]** | No |
| Wrong-try counter and cooldown end time | SharedPreferences `nyte_lock` **[CODE]** | No |
| Fingerprint | Handled by Android BiometricPrompt. The app never sees fingerprint data, only "success". **[CODE]** | No |
| Recovery code (16 characters, shown once) | SharedPreferences `nyte_lock`, PBKDF2 hash only **[CODE]** | No |
| Backup flags (guide seen, last backup time and photo count) | SharedPreferences `nyte_backup` **[CODE]** | No |
| Trash-explained / seeded flags | SharedPreferences `nyte_gallery` **[CODE]** | No |
| Built-in sample photos | Bundled app resources (`R.drawable.*`) **[CODE]** | No |

### 2.1 No network use (email recovery removed 2026-11-02)

- Email recovery through FormSubmit was removed. The app has no `INTERNET` permission and no network code. **[CODE]**
- Recovery is offline: at lock setup the app shows a random 16-character code (`XXXX-XXXX-XXXX-XXXX`, alphabet
  without 0/O/1/I/L, about 79 bits) once, with a warning and a "I have written down my recovery code" checkbox.
  Only a PBKDF2 hash is stored. Copy marks the clip as sensitive (Android 13+) and clears it after about 60 s
  (best effort). **[CODE]**
- "Forgot? Use recovery code" on the unlock screen uses the same wrong-try cooldown. On success the user sets a new
  lock and gets a new code; the old code stays valid until the new one is created, so a crash cannot lock them out.
  Settings > Security > Recovery code creates a new code after the current lock is checked. **[CODE]**
- Users who set up the lock with email: on the next unlock they are shown a new recovery code, and the stored email
  and any pending email code are deleted. **[CODE]**

## 3. Permissions and why (from `AndroidManifest.xml`)

| Permission | Why | Notes |
|---|---|---|
| `READ_MEDIA_IMAGES` (API 33+) | List gallery photos so the user can pick which to move into the vault **[CODE]** | Asked only when the user opens "move from gallery" |
| `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+) | Android "selected photos only" access **[CODE]** | |
| `READ_EXTERNAL_STORAGE` (max API 32) | Same as above on older Android **[CODE]** | |
| `WRITE_EXTERNAL_STORAGE` (max API 28) | Write restored photos to Pictures on Android 9 and older; delete originals there **[CODE]** | |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROCESSING` | Keep a user-started import, export, backup or restore running with a progress notification if the user leaves the app (`TransferService`) **[CODE]** | Not sticky; never restarts on its own |
| `POST_NOTIFICATIONS` (API 33+) | Show that progress notification **[CODE]** | Asked the first time a transfer starts |
| `USE_BIOMETRIC` | Not declared in the app manifest. It comes from the `androidx.biometric` library manifest merge **[ASSUMPTION: confirm in merged manifest]** | Fingerprint unlock |

Not requested: **internet** (removed 2026-11-02; re-check the merged manifest of each release), camera, location, contacts, microphone, phone, accounts, `MANAGE_EXTERNAL_STORAGE`. **[CODE]**

Backup and restore files are picked with the system file picker (Storage Access Framework), so no extra storage
permission is needed for them. **[CODE]**

## 4. Encryption details

- **Vault files**: AES-256-GCM, 12-byte random IV per file, 128-bit tag. The key is created in the Android KeyStore
  (`nyte_gallery_vault`), StrongBox-backed when the phone supports it, otherwise TEE/software KeyStore.
  The key cannot be exported. **[CODE]**
- Consequence: vault files cannot be opened on another phone, and are lost if the app is uninstalled or its data
  cleared. **[CODE: KeyStore behaviour]**
- The key does **not** require user authentication (no `setUserAuthenticationRequired`). The app lock is an
  app-level gate, not a cryptographic one. **[CODE]**
- **Backup files** (`.nybak`, format v2): PBKDF2-HMAC-SHA256 with 600,000 iterations and a 16-byte random salt
  derive a 256-bit key from the user's backup password. Each photo is an AES-256-GCM entry with its own random IV.
  An encrypted trailer records the photo count so a cut-off file is detected. **[CODE]**
- **App lock** (since 2026-11-02): password, PIN and pattern are stored as
  `pbkdf2$<iterations>$<salt>$<hash>`: PBKDF2-HMAC-SHA256, 210,000 iterations, a 16-byte random salt per secret,
  256-bit output, checked with a constant-time compare, off the main thread. **[CODE]**
  - Migration: older installs stored `SHA-256(install salt:value)`. Those still verify; on the first successful
    check the secret is re-saved in the PBKDF2 format. Nobody is locked out. **[CODE]**
  - The iteration count is stored in each hash, so it can be raised later the same way.
  - A 4 to 6 digit PIN is still a small space for an offline attack on a rooted phone; the real protection is
    app-private storage plus the wrong-try limit. Do not describe this as "military grade" in public text.
- **Wrong-try limit** (since 2026-11-02): wrong passwords, PINs, patterns and recovery codes are counted together on
  the unlock screen and on the "confirm it's you" step of Change lock, Recovery code and Turn off lock.
  4 free tries, then a wait of 30 s (5th wrong), 1 min (6th), 5 min (7th), 15 min (8th), 30 min (9th), and 1 hour
  for each wrong try after that. The counter survives closing the app and is cleared by any success (including
  fingerprint). Nothing is ever wiped. The try is counted before the value is checked, so killing the app during
  the check does not skip it. **[CODE]**
  - Clock safety: the end of the wait is stored as `elapsedRealtime` (not changed by the clock setting) plus the
    boot count, and as wall-clock time. In the same boot only `elapsedRealtime` counts. After a reboot the wall
    clock is used, clamped to the full wait, and a clock set to before the wait started restarts the full wait.
    Rebooting *and* moving the clock forward can shorten a wait; acceptable for an app-level lock.
  - Fingerprint tries are limited by Android's own BiometricPrompt, not counted by the app, and still work during
    a wait.
- Index file `index.json` (photo names and dates) is stored in plain text inside app-private storage. **[CODE]**
  Policy text should say "photos are encrypted", not "everything is encrypted", unless this is changed.

## 5. Backup and restore, and user responsibilities

- Android automatic backup is off: `allowBackup="false"`, and `backup_rules.xml` plus `data_extraction_rules.xml`
  exclude every domain for cloud backup and device transfer. **[CODE]**
- The only backup is the manual, password-encrypted backup file. The user chooses where it goes (phone folder,
  Google Drive via the picker, SD card, etc.). Nyte never uploads it anywhere itself. **[CODE]**
- The backup contains the original photo bytes, names, dates and Trash state. **[CODE]**
- **The backup password cannot be recovered.** Nytefall has no copy and no reset. Lost password = backup cannot be
  opened. The app says this on a first-time guide, needs the password typed twice and a checkbox
  "I understand Nyte cannot recover this password". **[CODE]**
- After saving, the user can run "Check backup", which decrypts every entry without importing anything. **[CODE]**
- Restore skips photos already in the vault (same id). **[CODE]**
- Whoever has the backup file and the password can open the photos. Storing the file in a cloud service is subject to
  that service's own terms. **[CODE + general]**
- The app records the time and photo count of the last successful backup on the phone, for a future reminder. **[CODE]**

## 6. What the app does not do

Verified in `app/build.gradle.kts` dependencies (Compose, AndroidX core/activity/lifecycle/fragment, ExifInterface,
Biometric only) and in the code: **[CODE]**

- No user accounts or sign-in.
- No Nytefall server or backend.
- No analytics, crash reporting, advertising, attribution or tracking SDKs (no Firebase, Google Play Services ads, etc.).
- No internet access, no selling or sharing of data.
- No location, contacts, camera or microphone access.
- No in-app purchases. **[CODE: none in this build]**

## 7. Data deletion

- **Uninstall or "Clear storage"** deletes the vault, the index, all prefs, and the KeyStore key. Photos still in the
  vault are then gone for good unless the user has a backup file. **[CODE: Android behaviour + allowBackup=false]**
- **Trash**: a photo moved to Trash is kept for 30 days, then deleted automatically the next time the vault loads.
  "Empty Trash" deletes immediately. **[CODE]**
- **Restore to gallery (export)**: writes the photo as JPEG to `Pictures/Nyte Gallery` in public storage, then
  deletes it from the vault. **[CODE]** Once exported it is a normal, unencrypted photo.
- **Moving photos in**: after encrypting, the app asks Android to delete the originals from the public gallery.
  On Android 11+ this uses the system delete-confirmation dialog (batches of 20); the user can decline, and then the
  original stays. On older versions it deletes directly or asks via a recoverable security prompt. **[CODE]**
- Backup files are not deleted by the app (except a half-written file after a failed backup). The user manages them. **[CODE]**
- Since there are no accounts or servers, there is nothing for Nytefall to delete on request.
  **[ASSUMPTION]** Still give the contact email for questions; Play requires a deletion statement even when no data
  is collected.

## 8. Children

- **[ASSUMPTION]** Target audience: adults / general (13+ or 18+). Not designed for children; do not opt into the
  Families program. Choose the age group in Play Console "Target audience and content".
- The app collects no personal data and has no internet access.

## 9. Draft Play Console Data safety answers

**[ASSUMPTION]** Draft only. Re-check each answer against the final release build and Google's current form.

- Does the app collect or share any required user data types? **No.** Answer **"No data collected"**: the app has
  no internet access, so no user data leaves the device through the app. **[CODE]**
- Photos and videos: **Not collected**. Google defines "collected" as sent off the device. Photos stay on the phone,
  encrypted. Backups go only where the user saves them, through the system picker. **[ASSUMPTION: confirm this
  reading of Google's definition at the time of filing]**
- Encryption in transit: not applicable (nothing is transmitted).
- Can users request deletion? **Yes / not applicable**: all data is on the device, uninstall deletes it.
  **[ASSUMPTION]**
- Independent security review: **No**.

## 10. Draft Privacy Policy (to adapt)

> **Nyte Gallery Privacy Policy**
> Last updated: [DATE]
>
> Nyte Gallery is made by Nytefall Interactive. This policy explains what happens to your information when you use the app.
>
> **Your photos stay on your phone.** Photos you move into Nyte Gallery are encrypted and stored in the app's private
> storage on your device. We do not run servers that receive your photos, and we cannot see them.
>
> **No accounts, ads or analytics.** Nyte Gallery has no sign-in, shows no ads, and does not include analytics or
> tracking tools.
>
> **Permissions.** We ask for photo access only so you can choose photos to move into the vault, and notification
> access to show progress for long tasks. Fingerprint unlock is handled by Android; we never receive fingerprint data.
>
> **Recovery code.** The app has no internet access. If you turn on the app lock, you get a recovery code that is shown
> once and stored on your device only as a hash. We cannot recover or reset it.
>
> **Backups.** Backups are created only when you choose, encrypted with a password you set, and saved where you pick.
> We never receive the file or the password, and we cannot recover a forgotten password.
>
> **Deleting your data.** Uninstalling the app or clearing its storage permanently deletes your vault on that device.
> Photos in Trash are deleted after 30 days.
>
> **Children.** Nyte Gallery is not directed at children under 13.
>
> **Changes.** We will update this page if the app's handling of data changes.
>
> **Contact:** nytefall.interactive@gmail.com

## 11. Draft Terms of Service points

- The app is provided **"as is"** and "as available", without warranties of any kind.
- The user is responsible for making and keeping backups, and for remembering the backup password, the app lock
  methods and the recovery code. Nytefall Interactive cannot recover passwords, keys or photos.
- Nytefall Interactive is **not liable for lost, deleted or inaccessible data**, including loss caused by uninstalling,
  clearing app data, device loss or reset, a forgotten password, a damaged backup file, or third-party storage.
- Users must only store content they have the right to store, and must follow the law.
- The app lock is a privacy feature, not a guarantee against every attack (for example a rooted or compromised device).
- Third-party services the user chooses (for example Google Drive for backup files) are covered by their own terms.
- Terms may change; continued use means acceptance. Governing law: **[ASSUMPTION: choose, e.g. Indonesia]**.
- Contact: nytefall.interactive@gmail.com.

## 12. Open items before Play release

1. **Rename the package**: DONE 2026-10-31, now `com.nytefall.nytegallery`. Old `com.example.saogallery` installs are a
   different app: back up there, install the new app, then restore.
2. **Release signing key**: create an upload keystore, enrol in Play App Signing, keep the keystore and passwords
   **outside the public repo** (GitHub Actions secrets if CI signs). Never commit it.
3. **Build an AAB** (`bundleRelease`) for Play; keep versionCode increasing.
4. **Screenshots locked**: DONE 2026-11-02. `SCREENSHOTS_ALLOWED = false` (`ui/GalleryScreen.kt`), FLAG_SECURE is set
   in `MainActivity.onCreate` and kept on for every screen including the lock screen, so screenshots, screen
   recording and the Recent apps preview are blocked.
5. **FormSubmit email recovery**: DONE 2026-11-02, removed and replaced with an offline recovery code; `INTERNET`
   permission removed. Data safety: "No data collected".
6. **Host the Privacy Policy** at a public URL, for example GitHub Pages
   (`https://<user>.github.io/<repo>/privacy`) or a page on a Nytefall site. Play needs the URL in the store listing
   and, ideally, a link inside the app.
7. **Contact email** nytefall.interactive@gmail.com: enable 2FA, use it in Play Console and the policy.
8. Consider encrypting `index.json` (photo names) before claiming "everything is encrypted".
   Slow hash for app lock secrets: DONE 2026-11-02 (PBKDF2, see section 4), with a wrong-try limit.
9. Sample photos bundled in the app: confirm their licence before publishing. **[ASSUMPTION: source unknown]**
10. Re-read the merged manifest of the release build to confirm the final permission list (libraries can add some).
