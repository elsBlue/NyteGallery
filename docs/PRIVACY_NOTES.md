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
backup file. An optional app lock (password, PIN, pattern, fingerprint) protects the app, with an email
recovery code. **[CODE]**

There are no user accounts, no sign-in, and no Nyte server. **[CODE]**

## 2. Data processed and where it lives

| Data | Where it lives | Leaves the phone? |
|---|---|---|
| Photos moved into the vault | `files/vault/<id>.nyt`, app-private, AES-256-GCM with an Android KeyStore key **[CODE]** | Only inside a backup file the user creates and saves where they choose **[CODE]** |
| Thumbnails (384 px) | `files/vault/<id>.thumb2.nyt`, encrypted the same way **[CODE]** | No |
| Photo index: id, name, date taken, trash date, file name | `files/vault/index.json`, app-private, **not encrypted** (plain JSON) **[CODE]** | Inside backup files only (encrypted there) |
| Decoded images | In memory only (LRU cache) while the app runs **[CODE]** | No |
| App lock secrets (password, PIN, pattern) | SharedPreferences `nyte_lock`, stored as salted SHA-256 hashes, never plain text **[CODE]** | No |
| Fingerprint | Handled by Android BiometricPrompt. The app never sees fingerprint data, only "success". **[CODE]** | No |
| Recovery email address | SharedPreferences `nyte_lock`, plain text **[CODE]** | **Yes**, sent to FormSubmit when a recovery code is requested (see 2.1) |
| One-time recovery code | Hash plus time in `nyte_lock`, valid 10 minutes **[CODE]** | **Yes**, the code itself is sent in the email body via FormSubmit |
| Backup flags (guide seen, last backup time and photo count) | SharedPreferences `nyte_backup` **[CODE]** | No |
| Trash-explained / seeded flags | SharedPreferences `nyte_gallery` **[CODE]** | No |
| Built-in sample photos | Bundled app resources (`R.drawable.*`) **[CODE]** | No |

### 2.1 The only network use: recovery email via FormSubmit

- `data/LockStore.kt` `sendUnlockCode()` POSTs to `https://formsubmit.co/ajax/<user email>` with the code,
  subject and the name "Nyte Gallery". **[CODE]**
- This happens only when a locked user taps "Use email code" or asks for a new code. **[CODE]**
- FormSubmit is a third-party service. It receives the user's email address and the code, and the first time
  it asks the recipient to confirm the address. **[CODE: in-app text says so]**
- No photo, photo name or other vault data is ever sent over the network. **[CODE]**
- **[ASSUMPTION]** FormSubmit's own retention and privacy terms have not been reviewed. Decide before release:
  keep FormSubmit (and disclose it), move to a service Nytefall controls, or drop email recovery.

## 3. Permissions and why (from `AndroidManifest.xml`)

| Permission | Why | Notes |
|---|---|---|
| `INTERNET` | Only for the recovery-code email (FormSubmit) **[CODE]** | No analytics, ads or crash reporting use it |
| `READ_MEDIA_IMAGES` (API 33+) | List gallery photos so the user can pick which to move into the vault **[CODE]** | Asked only when the user opens "move from gallery" |
| `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+) | Android "selected photos only" access **[CODE]** | |
| `READ_EXTERNAL_STORAGE` (max API 32) | Same as above on older Android **[CODE]** | |
| `WRITE_EXTERNAL_STORAGE` (max API 28) | Write restored photos to Pictures on Android 9 and older; delete originals there **[CODE]** | |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROCESSING` | Keep a user-started import, export, backup or restore running with a progress notification if the user leaves the app (`TransferService`) **[CODE]** | Not sticky; never restarts on its own |
| `POST_NOTIFICATIONS` (API 33+) | Show that progress notification **[CODE]** | Asked the first time a transfer starts |
| `USE_BIOMETRIC` | Not declared in the app manifest. It comes from the `androidx.biometric` library manifest merge **[ASSUMPTION: confirm in merged manifest]** | Fingerprint unlock |

Not requested: camera, location, contacts, microphone, phone, accounts, `MANAGE_EXTERNAL_STORAGE`. **[CODE]**

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
- **App lock**: password, PIN and pattern are stored as `SHA-256(salt:value)` with one random salt per install. **[CODE]**
  **[ASSUMPTION / improvement]** A slow hash (PBKDF2 or similar) would be stronger. Do not describe this as
  "military grade" in public text.
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
- No selling or sharing of data, except the recovery email sent through FormSubmit (section 2.1).
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
- The app collects no personal data on a server, but the recovery email goes to FormSubmit. If children were a target,
  that would need review.

## 9. Draft Play Console Data safety answers

**[ASSUMPTION]** Draft only. Re-check each answer against the final release build and Google's current form.

- Does the app collect or share any required user data types? **Yes**, only because of the recovery email:
  - Personal info > **Email address**: *Collected* (sent off device to FormSubmit to deliver the code).
    Purpose: **Account management / app functionality** (security recovery). Optional: **Yes** (only if the user
    sets a lock and uses email recovery). **[ASSUMPTION: whether FormSubmit counts as a service provider
    (then "collected", not "shared")]**
  - If email recovery is removed before release, answer **No data collected or shared**.
- Photos and videos: **Not collected**. Google defines "collected" as sent off the device. Photos stay on the phone,
  encrypted. Backups go only where the user saves them, through the system picker. **[ASSUMPTION: confirm this
  reading of Google's definition at the time of filing]**
- Is all user data encrypted in transit? **Yes** (HTTPS to FormSubmit).
- Can users request deletion? **Yes / not applicable**: all data is on the device, uninstall deletes it.
  The email sent to FormSubmit is outside Nytefall's control. **[ASSUMPTION]**
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
> **Recovery email.** If you turn on the app lock, you enter an email address. It is stored on your device. When you
> ask for a recovery code, the app sends your email address and a one-time code to FormSubmit (formsubmit.co), a
> third-party email service, so the code can be emailed to you. Nothing else is sent.
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
- The user is responsible for making and keeping backups, and for remembering the backup password and the app lock
  methods. Nytefall Interactive cannot recover passwords, keys or photos.
- Nytefall Interactive is **not liable for lost, deleted or inaccessible data**, including loss caused by uninstalling,
  clearing app data, device loss or reset, a forgotten password, a damaged backup file, or third-party storage.
- Users must only store content they have the right to store, and must follow the law.
- The app lock is a privacy feature, not a guarantee against every attack (for example a rooted or compromised device).
- Third-party services the user chooses (Google Drive, FormSubmit email delivery) are covered by their own terms.
- Terms may change; continued use means acceptance. Governing law: **[ASSUMPTION: choose, e.g. Indonesia]**.
- Contact: nytefall.interactive@gmail.com.

## 12. Open items before Play release

1. **Rename the package**: DONE 2026-10-31, now `com.nytefall.nytegallery`. Old `com.example.saogallery` installs are a
   different app: back up there, install the new app, then restore.
2. **Release signing key**: create an upload keystore, enrol in Play App Signing, keep the keystore and passwords
   **outside the public repo** (GitHub Actions secrets if CI signs). Never commit it.
3. **Build an AAB** (`bundleRelease`) for Play; keep versionCode increasing.
4. **Set `SCREENSHOTS_ALLOWED = false`** for release (`ui/GalleryScreen.kt`) so FLAG_SECURE blocks screenshots
   and the recents preview. Currently `true` for development.
5. **Decide on FormSubmit** email recovery (keep and disclose, replace, or remove). This changes the Data safety
   answers and the policy text.
6. **Host the Privacy Policy** at a public URL, for example GitHub Pages
   (`https://<user>.github.io/<repo>/privacy`) or a page on a Nytefall site. Play needs the URL in the store listing
   and, ideally, a link inside the app.
7. **Contact email** nytefall.interactive@gmail.com: enable 2FA, use it in Play Console and the policy.
8. Consider encrypting `index.json` (photo names) and using a slow hash for app lock secrets before claiming
   "everything is encrypted".
9. Sample photos bundled in the app: confirm their licence before publishing. **[ASSUMPTION: source unknown]**
10. Re-read the merged manifest of the release build to confirm the final permission list (libraries can add some).
