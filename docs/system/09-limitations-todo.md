# 9. Known limitations and TODO

Everything here comes from reading the code at the documented commit. Items are grouped by area, and each has a
pointer to the section that explains it. "Not verified" means it follows from the code but wasn't tested on a device.

## 9.1 Security

| # | Limitation | Where |
|---|---|---|
| S1 | **No auto-lock.** The app locks only on a cold start or a manual lock. Leaving the app in the background keeps it unlocked until the process dies. A `ProcessLifecycleOwner` or `ON_STOP` lock, maybe with a grace period, is the obvious next step. | [4.1](04-app-lock.md#41-what-the-lock-is-and-what-it-is-not) |
| S2 | The lock is a **UI gate only**. The Keystore vault key isn't bound to user authentication (`setUserAuthenticationRequired` isn't used). This is a design choice so that recovery works, but it means the lock adds no cryptographic protection. | [3.3](03-storage-encryption.md), [4.1](04-app-lock.md) |
| S3 | The "Fingerprint" method accepts `BIOMETRIC_WEAK`, so a class-2 face unlock may also pass, and there is no `CryptoObject`. The UI only says "fingerprint". Not verified. | [4.2](04-app-lock.md#42-unlock-methods-and-the-2-or-more-rule) |
| S4 | `index.json` is **plaintext**: photo names, dates, trash state and ids are readable by anyone with root or file access to the app sandbox. The image bytes themselves are encrypted. | [3](03-storage-encryption.md) |
| S5 | The backup header (salt, iterations, count) isn't authenticated as AAD. Tampering only causes a failed read, so the impact is low. | [6.2](06-backup-restore.md#62-file-format-nytebak1) |
| S6 | The RemoveOriginals text says "only open with your lock" even when no lock is set. | [5.4](05-import-export.md) |

## 9.2 Data safety and reliability

| # | Limitation | Where |
|---|---|---|
| D1 | `index.json` is written in place (`writeText`), **not atomically** (no temp file and rename), and there's no recovery path if it's corrupt. A crash or full disk during a write could lose the index, while the `.nyt` files remain but are orphaned. | [3](03-storage-encryption.md) |
| D2 | **Android 10 delete gap:** removing originals stops at the first `RecoverableSecurityException`. After the user consents, the delete isn't retried, and the success toast can show while photos remain. | [5.4](05-import-export.md#54-remove-or-keep-originals-removeoriginalsoverlay) |
| D3 | The 30-day Trash purge runs only in `load()` at process start, and uses the wall clock, so changing the clock moves it. | [3](03-storage-encryption.md) |
| D4 | `TransferService` is `START_NOT_STICKY`. If the system kills the process mid-transfer, the work stops. Photos finished so far are saved. A backup interrupted this way isn't cleaned up (`dropDocument` never runs), and the partial file has no v2 trailer, so a check or restore reports it as damaged or incomplete. | [2](02-architecture.md) |
| D5 | Uninstalling, clearing app data, or installing an APK with a different signature **wipes the vault** for good unless a password backup exists. There's no reminder to back up (`last_backup_at` is stored "for a future reminder"). | [6.1](06-backup-restore.md), [8.3](08-build-release.md) |
| D6 | The backup writer holds each photo fully in memory (decrypted bytes plus the ciphertext). Very large photos can cause an OOM ("A photo was too large to process on this phone"). The reader rejects entries over 80 MiB, but the writer doesn't check this. | [6.2](06-backup-restore.md) |
| D7 | Restoring a backup skips ids that already exist, so a photo changed after the backup (for example renamed) keeps its current version. | [6.6](06-backup-restore.md) |

## 9.3 Import and export

| # | Limitation | Where |
|---|---|---|
| I1 | The picker lists at most **4,000** newest photos. Older photos can't be picked. | [5.2](05-import-export.md) |
| I2 | The picker's intro text ("…then removed from your phone gallery") predates the keep/remove choice and overstates it. | [5.2](05-import-export.md) |
| I3 | Export always uses a `.jpg` name and `image/jpeg` MIME, even for PNG, HEIC or WebP originals. | [5.5](05-import-export.md#55-restore-to-the-device-restore--export) |
| I4 | On Android 9 and below, export doesn't set a folder, so the files probably land in `Pictures/` rather than `Pictures/Nyte Gallery`, but the toast says the latter. Not verified. | [5.5](05-import-export.md) |
| I5 | Only images: no video and no system Photo Picker. | [5.1](05-import-export.md) |

## 9.4 UI and product

| # | Item |
|---|---|
| U1 | The theme isn't saved. Every launch starts dark. |
| U2 | Trash has no multi-select, no "Restore all", and no way to permanently delete a single photo (only Empty all). |
| U3 | Rename has no length limit. The UI clips names for display (22 or 16 characters). |
| U4 | No localisation: all text is hard-coded English. |
| U5 | No search, albums or sorting options. |
| U6 | Premium or billing features aren't implemented (there's no billing library). |
| U7 | Sample photos are seeded once on first run. Their `takenAt` stamps include 11 Nov 2026, a future date at the time of writing. |

## 9.5 Code and housekeeping

| # | Item |
|---|---|
| C1 | `GalleryViewModel.addPicked` is unused dead code. |
| C2 | `README.md` is in Indonesian and out of date compared with the current app. |
| C3 | No unit or instrumented tests, and CI only runs `assembleDebug`. Good first tests would cover the backup format round-trip, `LockStore` cooldown and migration, and `VaultStore` index load and purge. |
| C4 | No release signing, `isMinifyEnabled = false`, and `versionCode` is raised by hand. |
| C5 | `GalleryScreen.kt` is very large (all main UI plus state in one file). Splitting it would make changes safer. |
| C6 | TERMS.md §8 names Indonesian law. The owner needs to confirm this. |

## 9.6 Unclear from code alone

- How `BIOMETRIC_WEAK` behaves on specific phones (S3).
- Which MediaStore folder export uses by default on API 28 and below (I4).
- Whether all viewers open a PNG or HEIC saved under a `.jpg` name (I3).
- Whether CI debug keystores really differ between runs. This depends on the runner image and the cache, and it
  only matters when updating installs from different runs.
- The source of the `DUMP` permission in the merged manifest. It's believed to come from `androidx.profileinstaller`
  (see [Overview](01-overview.md)) but wasn't checked against a merged manifest.
