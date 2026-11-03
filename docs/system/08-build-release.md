# 8. Build and release

## 8.1 Toolchain (from the Gradle files)

| Item | Value |
|---|---|
| Gradle wrapper | 8.11.1 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin / Compose compiler plugin | 2.0.21 |
| Java / JVM target | 17 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| applicationId = namespace | `com.nytefall.nytegallery` (**don't change it**) |
| versionCode / versionName | 1 / "1.0" |
| Compose BOM | 2024.12.01 (ui, foundation, material3) |
| Other libraries | activity-compose 1.9.3, core-ktx 1.15.0, core-splashscreen 1.0.1, exifinterface 1.3.7, lifecycle 2.8.7 (viewmodel, viewmodel-compose, runtime), biometric 1.1.0, fragment-ktx 1.8.5 |
| `buildFeatures` | `compose = true`, `buildConfig = true` (for `BuildConfig.VERSION_NAME`) |
| Repositories | `google()`, `mavenCentral()` (plus `gradlePluginPortal()` for plugins), `FAIL_ON_PROJECT_REPOS` |

## 8.2 How builds are made: CI only

**Project rule: don't build locally. Push to `main` and let GitHub Actions build.**

Workflow `.github/workflows/android-build.yml`, named **"Android Build"**:

- Triggers: `push` to `main` and every `pull_request`. There's no path filter, so documentation-only commits
  also run a build.
- Job `build` on `ubuntu-latest`:
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4`: Temurin 17 with the Gradle cache
  3. `android-actions/setup-android@v4`
  4. `sdkmanager --licenses`, then `sdkmanager "platforms;android-35" "build-tools;35.0.0"`
  5. `chmod +x ./gradlew`
  6. `./gradlew assembleDebug --stacktrace --no-daemon`
  7. Upload the artifact **`nytegallery-debug-apk`** from `app/build/outputs/apk/debug/*.apk`, failing if it's
     missing
- There are no tests, lint or release steps in CI. The repo has no unit or instrumented tests.

Getting a build:
1. Commit and push to `main`. Before any `git push` or `gh` command, run `unset GH_TOKEN` (gh is logged in through
   `hosts.yml`).
2. `gh run list --limit 5`, then `gh run watch <id> --exit-status`.
3. Download the APK from the run's **Artifacts** section (`nytegallery-debug-apk`, a zip containing
   `app-debug.apk`).

## 8.3 Signing

- **Debug:** the Android Gradle Plugin signs with a debug keystore. CI has no saved keystore, so each runner creates
  a new one. **Debug APKs from different CI runs may have different signatures**, and installing one over another
  can fail with "App not installed" or a signature conflict. The fix is to uninstall first, which **deletes the
  vault**. Make a password backup before that (see [Backup](06-backup-restore.md)). **(This follows from the
  workflow. It wasn't tested across runs.)**
- **Release:** `buildTypes.release` has **no `signingConfig`**, and no release keystore is in the repo. `.gitignore`
  excludes `*.apk`, `*.ap_`, `*.aab` and `build/`. **Never commit a keystore.** A release build needs:
  1. an upload or release key created and stored outside the repo (for example in GitHub Secrets, or Play App
     Signing with an upload key);
  2. a `signingConfig` that reads it from env vars or secrets;
  3. a CI job for `bundleRelease` (AAB for Play).
  None of this exists yet.
- The vault key is per-device in the Keystore and has nothing to do with the signing key. But Android only allows
  updating an app that has the same signature, so changing the signing key means reinstalling, which wipes the
  vault.

## 8.4 Release build settings

- `isMinifyEnabled = false`, so there's no R8 shrinking or obfuscation. `proguard-rules.pro` is empty ("Default
  empty rules. Release minify is off for this sample.").
- No `isShrinkResources`, no ABI or density splits, no `bundle {}` config.
- `packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"`.
- `versionCode` must be raised by hand for each Play upload.

## 8.5 Store and policy files

- `docs/policy/PRIVACY_POLICY.md` and `docs/policy/TERMS.md` (linked from About through the GitHub URLs).
- `docs/PRIVACY_NOTES.md` holds working notes with `[CODE]`/`[ASSUMPTION]` markers.
- The Play **Data safety** form should match: no data collected, no data shared, and no network access, since there
  is no `INTERNET` permission (see [Overview](01-overview.md)).

## 8.6 Before a Play release

- [ ] Release signing (8.3) and an AAB build job.
- [ ] Check Google Play's current minimum `targetSdk`. It's 35 now; Play raises the minimum every year, so check
      whether 36 is required for new uploads at release time.
- [ ] Decide on minify (R8) and test it. Keep rules for the Compose and biometric reflection, if any.
- [ ] The owner confirms the Terms governing law. TERMS.md §8 currently says the Republic of Indonesia.
- [ ] Turn the remaining items in [Limitations](09-limitations-todo.md) into tickets.
- [ ] Keep `SCREENSHOTS_ALLOWED = false`.
