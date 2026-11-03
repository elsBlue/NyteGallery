# 1. Overview

## 1.1 Purpose

Nyte Gallery is a **private photo vault**. The user picks photos from the phone's shared gallery (MediaStore). The app
encrypts them into its private storage and can then remove the originals from the shared gallery. Inside the app,
photos can be:

- browsed in a 3D carousel of "floating window" cards, or in a grid;
- opened full screen with pinch zoom;
- renamed;
- moved to Trash, which deletes them automatically after 30 days, or restored from there;
- "restored" (exported) back to the shared gallery, into `Pictures/Nyte Gallery`, which removes them from the vault;
- backed up to a password-encrypted file and restored from one;
- protected with an app lock that has an offline recovery code.

**First run.** The launcher activity starts on `Theme.SAOGallery.Starting`, so a splash screen
(`androidx.core:core-splashscreen`, on every supported Android version) shows the night-card icon on `bg_night`
only while the app starts. There's no added delay. On a fresh install, three **welcome pages** follow (see
[UI §7.10](07-ui.md#710-splash-screen-and-welcome-pages)). They're shown once and end either in lock setup ("Get
started") or on the home screen ("Later" / "Skip"). Installs that already have a vault or a lock never see them.

On first launch, six sample photos are bundled as drawables and seeded into the gallery (see
`data/GalleryRepository.kt`). They are not encrypted, because they ship inside the APK.

## 1.2 Tech stack

| Item | Value | Source |
|---|---|---|
| Language | Kotlin (JVM target 17) | `app/build.gradle.kts` |
| Kotlin plugin | `org.jetbrains.kotlin.android` **2.0.21** with the Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` 2.0.21 | `build.gradle.kts` |
| Android Gradle Plugin | `com.android.application` **8.7.3** | `build.gradle.kts` |
| Gradle wrapper | **8.11.1** | `gradle/wrapper/gradle-wrapper.properties` |
| UI | Jetpack Compose, BOM **2024.12.01** (ui, ui-graphics, foundation, material3, tooling-preview; ui-tooling for debug only) | `app/build.gradle.kts` |
| Activity | `androidx.activity:activity-compose:1.9.3` | |
| Core | `androidx.core:core-ktx:1.15.0` | |
| Splash | `androidx.core:core-splashscreen:1.0.1` | launch theme `Theme.SAOGallery.Starting` |
| EXIF | `androidx.exifinterface:exifinterface:1.3.7` | |
| Lifecycle | `lifecycle-viewmodel-ktx`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-ktx` **2.8.7** | |
| Biometrics | `androidx.biometric:biometric:1.1.0` | |
| Fragment | `androidx.fragment:fragment-ktx:1.8.5` (`MainActivity` is a `FragmentActivity`, which `BiometricPrompt` needs) | |
| Java toolchain on CI | Temurin JDK 17 | `.github/workflows/android-build.yml` |

The app uses no image loading library (no Coil or Glide), no DI framework, no database and no network library.
Images are decoded by hand with `BitmapFactory`, and data is stored in JSON plus `SharedPreferences`.

## 1.3 SDK levels

| Setting | Value | Meaning |
|---|---|---|
| `minSdk` | **26** | Android 8.0 Oreo |
| `targetSdk` | **35** | Android 15 |
| `compileSdk` | **35** | |

Google Play usually raises the minimum `targetSdk` for new apps each year. Check the current Play requirement before
the first upload, because it may need to be 36. **(not verified against current Play policy in this doc)**

Code paths that depend on the Android version are listed in [Import and export](05-import-export.md),
[App lock](04-app-lock.md) (clipboard, StrongBox) and [Architecture](02-architecture.md) (foreground service types).

## 1.4 Permissions

From `app/src/main/AndroidManifest.xml`:

| Permission | Limits | Why |
|---|---|---|
| `READ_MEDIA_IMAGES` | Android 13+ | List and read photos in the device gallery so they can be moved in |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14+ | Lets the user grant access to only some photos; the app requests it together with `READ_MEDIA_IMAGES` |
| `READ_EXTERNAL_STORAGE` | `maxSdkVersion=32` | Reading photos on Android 12L and below |
| `WRITE_EXTERNAL_STORAGE` | `maxSdkVersion=28` | Deleting originals and writing exported photos on Android 9 and below |
| `FOREGROUND_SERVICE` | | Keeps long vault transfers (import, export, backup, restore) running if the user leaves the app |
| `FOREGROUND_SERVICE_DATA_SYNC` | | Foreground service type used on Android 10–14 |
| `FOREGROUND_SERVICE_MEDIA_PROCESSING` | | Foreground service type used on Android 15+ |
| `POST_NOTIFICATIONS` | runtime on 13+ | Progress notification for the transfer service; requested the first time a transfer starts |

Not requested:

- **`INTERNET` is not declared.** The app can't open network connections. The links under "Privacy policy" and
  "Terms of service" open in the user's browser through an `ACTION_VIEW` intent, and "Send feedback" opens the
  user's email app through `ACTION_SENDTO mailto:`.
- **`android.permission.DUMP`** may show up in the merged manifest or in Play Console. It comes from the AndroidX
  **ProfileInstaller** library, which Compose pulls in transitively, and is declared for its profile-installation
  receiver. The app's own manifest doesn't request it, and an ordinary app can't hold it, because it's a
  signature/privileged permission. **(The dependency chain was not re-checked with a build in this doc, since no
  local builds are done.)**

## 1.5 Privacy stance (what the code guarantees)

- No network access (see above), no accounts, no analytics, no crash reporting and no ads SDKs: there are no such
  dependencies in `app/build.gradle.kts`.
- `android:allowBackup="false"`. `backup_rules.xml` and `data_extraction_rules.xml` also exclude every domain
  (root, file, database, sharedpref, external) from cloud backup and device-to-device transfer. The vault, the
  lock settings and the prefs therefore never go into Google's automatic backup.
- Photos are encrypted at rest with a hardware-backed Keystore key (see [Storage](03-storage-encryption.md)).
- `FLAG_SECURE` is set on the window before the first frame, so screenshots, screen recording and the Recent-apps
  preview are blocked (see [App lock §4.9](04-app-lock.md#49-flag_secure-screenshots-and-recents)).
- The only data that can leave the phone does so through the user's own actions: backup files saved through the
  system file picker, exported photos written to the public gallery, and feedback emails sent with the user's own
  email app.
- For the Play Data safety form, the intended answer is **"No data collected, no data shared"**. See
  `docs/PRIVACY_NOTES.md` for the working notes.
