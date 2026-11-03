# Nyte Gallery: system documentation

This documentation describes the Nyte Gallery Android app **as the code is today**, at commit `ae58d3b` on `main`
(2026-11-03). Everything here was read from the source. If the code doesn't settle something, the text says so
and marks it **(unclear)** or **(not verified)**.

| | |
|---|---|
| App | Nyte Gallery, a private photo vault for Android |
| Developer | Nytefall Interactive |
| Package / applicationId | `com.nytefall.nytegallery` |
| Repository | https://github.com/elsBlue/NyteGallery (branch `main`) |
| Version | `versionName "1.0"`, `versionCode 1` |
| UI language | English |

## Contents

1. [Overview](01-overview.md): purpose, tech stack, SDK levels, permissions, privacy stance
2. [Architecture](02-architecture.md): packages, every source file, state flow, the transfer service, data classes
3. [Storage and encryption](03-storage-encryption.md): vault layout, `index.json`, the Keystore key, thumbnails, caches, trash
4. [App lock](04-app-lock.md): unlock methods, hashing, cooldown, recovery code, change or turn off, FLAG_SECURE
5. [Import and export](05-import-export.md): moving photos in from the device gallery, removing or keeping originals, restoring photos to Pictures
6. [Backup and restore](06-backup-restore.md): the `NYTEBAK1` file format (v1 and v2), the KDF, streaming, checks, error messages
7. [User interface](07-ui.md): screens, overlays, navigation, adaptive layouts, carousel, themes, shimmer, toasts
8. [Build and release](08-build-release.md): the GitHub Actions workflow, debug signing, release-key status, Play notes
9. [Known limitations and TODO](09-limitations-todo.md)

Related documents already in the repo:
- [`docs/PRIVACY_NOTES.md`](../PRIVACY_NOTES.md): working notes for Play Console Data safety
- [`docs/policy/PRIVACY_POLICY.md`](../policy/PRIVACY_POLICY.md) and [`docs/policy/TERMS.md`](../policy/TERMS.md): the published policy texts, which the About screen links to

## One-paragraph summary

Nyte Gallery moves photos out of the phone's shared gallery into app-private storage. Each photo is stored as a file
encrypted with AES-256-GCM, using a non-exportable key in the Android Keystore (StrongBox when available). Photos are
shown in a 3D "floating window" carousel and in a grid. They can be renamed, moved to a 30-day Trash, or written back
to `Pictures/Nyte Gallery`. The app can also make password-encrypted backup files (`NYTEBAK1`), which don't depend on
the device key, and restore from them. An optional app lock needs at least two of: password, PIN, pattern or
fingerprint. It has a persistent wrong-try cooldown and an offline recovery code. The app has **no INTERNET
permission**, no accounts, no analytics and no ads. `FLAG_SECURE` is always on.
