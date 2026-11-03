# 4. App lock

Code: `data/LockStore.kt` (storage, hashing, cooldown) and `ui/AppLock.kt` (screens). It is wired up in
`ui/GalleryScreen.kt`.

## 4.1 What the lock is, and what it is not

- The lock is optional. It is off on a fresh install.
- It is a **UI gate**. When `locked` is true, `LockGate` covers the whole screen, and every other page (menu, backup,
  settings, about, full view, import picker, remove-originals sheet) is hidden or closed.
- It is **not a cryptographic binding**. The vault key in the Keystore doesn't need user authentication (see
  [Storage §3.3](03-storage-encryption.md#33-encryption-of-photos)). The photos are encrypted at rest whether or not
  the lock is on. The lock stops someone who has the unlocked phone from browsing the app. It doesn't protect the
  photos from someone who could run code as the app.
- **When the app locks:**
  - on start, `locked` starts as `lockStore.configured()`, so every new process (cold start) begins locked;
  - when the user taps the lock button (top-left) and confirms "Lock Nyte Gallery now?";
  - right after a new lock is set up, once the user has confirmed the new recovery code.
- **There is no auto-lock on background or after a timeout.** Nothing in the code listens for
  `ON_STOP`/`ON_PAUSE`. The activity isn't recreated on rotation (`configChanges`). Once unlocked, the app stays
  unlocked until it's locked by hand or its process dies. See [Limitations](09-limitations-todo.md).

## 4.2 Unlock methods and the "2 or more" rule

| Method | Rule at setup | How it's checked |
|---|---|---|
| Password | 6 or more characters, typed twice | PBKDF2 hash compare |
| PIN | 4–6 digits (`\d{4,6}`), typed twice; the field accepts digits only, up to 6 | PBKDF2 hash compare |
| Pattern | 3×3 pad, at least 4 dots, drawn twice to confirm. Stored as the dot indices joined with `-` (for example `0-1-2-5`) | PBKDF2 hash compare |
| Fingerprint | Shown only if `BiometricManager` reports `BIOMETRIC_WEAK` or `BIOMETRIC_STRONG` as available or not enrolled. Turned on after one successful `BiometricPrompt` ("Confirm fingerprint") | `BiometricPrompt` success. No `CryptoObject`. |

- `LockSetupPage` shows "**N of 2 ways set**". The save button only works when **2 or more** methods are set ("Set at
  least two unlock ways"). This protects against forgetting a single secret.
- The "fingerprint" option is checked with `BIOMETRIC_WEAK` first, so on some phones a class-2 biometric such as
  face unlock may also pass. **(Not verified on a device; the UI always calls it "Fingerprint".)**
- On the lock screen the default method is the first saved method that isn't fingerprint. The others appear as chips
  when more than one is saved. The order comes from a `StringSet` in prefs, so it isn't guaranteed.
- Fingerprint doesn't prompt automatically. The user taps "Use fingerprint".

## 4.3 LockStore storage

SharedPreferences file `nyte_lock` (app-private, excluded from backup):

| Key | Type | Meaning |
|---|---|---|
| `on` | bool | Lock configured |
| `methods` | string set | Any of `password`, `pin`, `pattern`, `finger` |
| `password`, `pin`, `pattern` | string | Hash of each secret (see 4.4) |
| `recovery` | string | PBKDF2 hash of the recovery code |
| `recovery_saved` | bool | The user ticked "I have written down my recovery code" |
| `fails` | int | Wrong tries in a row, shared by all methods and the recovery code |
| `wait_ms` | long | Length of the current cooldown |
| `wait_until_elapsed`, `wait_start_elapsed` | long | Cooldown end and start on the `elapsedRealtime` clock |
| `wait_until_wall`, `wait_start_wall` | long | Cooldown end and start on the wall clock |
| `wait_boot` | int | `Settings.Global.BOOT_COUNT` when the cooldown was anchored |
| `salt` | string | **Legacy:** install-wide salt for the old SHA-256 format |
| `email`, `code`, `code_at` | | **Legacy:** removed email recovery. Read only to detect old installs, then deleted. |

## 4.4 Hash format and migration

New format, used for every secret and for the recovery code:

```
pbkdf2$210000$<salt: 16 random bytes, hex>$<PBKDF2-HMAC-SHA256 output, 32 bytes, hex>
```

- `PBKDF2WithHmacSHA256` through `SecretKeyFactory`, with 210,000 iterations (`PBKDF2_ITERATIONS`) and a fresh
  salt per secret.
- The iteration count is stored in each hash, so it can be raised later without breaking old hashes.
- Comparison is constant-time (`MessageDigest.isEqual`).
- The `PBEKeySpec` password and the char array are cleared after use.
- Hashing is slow, so `check`, `checkRecovery`, `save` and `newRecoveryCode` run on `Dispatchers.Default`, never
  on the main thread.

**Migration from the old format:** older installs stored `hex(SHA-256("<salt>:<value>"))` with one install-wide
`salt`. `matches()` still accepts that format, and after the first successful match it re-saves that secret in the
PBKDF2 format. Nobody is locked out by the upgrade. Each secret upgrades separately when it's first used.

## 4.5 Wrong tries and cooldown

`check()` and `checkRecovery()` go through `guarded()`:

1. If a cooldown is running, the call returns `Wait(remaining)` **without checking** the value.
2. Otherwise `fails + 1` is **saved first** (with `commit()`), and any cooldown due for that count is armed. Killing
   the app during the slow check can't skip a counted try.
3. The value is checked. A match resets everything and returns `Ok`. A mismatch returns `Wrong(wait)`.

| Wrong tries in a row | Cooldown after that try |
|---|---|
| 1–4 | none |
| 5 | 30 s |
| 6 | 1 min |
| 7 | 5 min |
| 8 | 15 min |
| 9 | 30 min |
| 10 and above | 1 h each |

- The counter is shared by password, PIN, pattern and the recovery code. It survives restarts because it's kept in
  prefs.
- A fingerprint success calls `noteSuccess()`, which resets the counter. Fingerprint failures aren't counted by the
  app, because the system prompt has its own lockout.
- **Resistance to clock changes** (`waitRemainingMs`):
  - **Same boot** (same `BOOT_COUNT`, and `elapsedRealtime` hasn't gone backwards): only the monotonic
    `elapsedRealtime` is used, so changing the wall clock has no effect.
  - **After a reboot:** the wall clock is used. The remaining time is clamped to `[0, full]`. If the clock is set
    to before the cooldown started, the **full** cooldown restarts. The result is then re-anchored to the new
    boot's `elapsedRealtime`.
- The UI (`LockTries`) ticks the countdown once a second and shows
  "Too many wrong tries. Try again in m:ss." (or `h:mm:ss` from 1 hour up). While the countdown runs, the Unlock and
  Continue buttons do nothing.

## 4.6 Recovery code

| Property | Value |
|---|---|
| Alphabet | `ABCDEFGHJKMNPQRSTUVWXYZ23456789`: 31 symbols, without 0/O and 1/I/L |
| Length | 16 characters, from `SecureRandom`, about 79 bits |
| Display format | `XXXX-XXXX-XXXX-XXXX` |
| Storage | PBKDF2 hash only (same format as 4.4). The plain code is never stored. |
| Check | Input is uppercased and stripped to letters and digits, so case, dashes and spaces don't matter. The input field allows up to 24 characters. |
| Wrong tries | Shares the counter and cooldown described above |

**Creating a code** (`newRecoveryCode`) replaces any old code, sets `recovery_saved = false` and deletes the
legacy `email`, `code` and `code_at` keys.

**`RecoveryCodePage`** shows the code once:
- It shows `RECOVERY_WARNING`: "Write this code down and keep it somewhere safe. It is the ONLY way back in if you
  forget your unlock methods. Nyte Gallery cannot recover it for you. If you lose it and forget your lock, your
  photos cannot be opened."
- **Copy** puts the code on the clipboard. On API 33+ the clip is marked `EXTRA_IS_SENSITIVE`, so the system doesn't
  preview it. After 60 s the clipboard is cleared if it still holds the code (best effort, since Android 10+ may
  block clipboard reads from the background).
- **Continue** works only after ticking "I have written down my recovery code". It then calls
  `markRecoverySaved()`. The page has **no Close** button, and Back is blocked while it's open.

**When a code is shown:**
1. Right after the first lock setup. After Continue, the app locks itself.
2. After a recovery-code unlock (see the flow below).
3. From Settings, Recovery code: the user confirms with a current lock method (the recovery code isn't offered
   there), then "Create new recovery code".
4. Automatically after unlocking, when `needsRecoveryCode()` is true, meaning the lock is on but there is no code or
   it was never confirmed:
   - **Old email-recovery installs** (`email` key present) see: "Nyte Gallery no longer uses email to recover your
     lock. It now uses a recovery code that never leaves this phone. Your old recovery email has been removed."
   - **An unconfirmed code** (for example the app was killed on the code page) gives: "Your recovery code was not
     confirmed, so here is a new one. The old one no longer works."

**Using the code:** on the lock screen, "Forgot? Use recovery code" appears only if a code exists. A correct code
calls `onRecovered`. The app unlocks and opens **NEW LOCK** (`LockSetupPage`): "You used your recovery code. Choose
at least two new ways to get back in…". Saving replaces the methods. Closing keeps the old methods. **Either way**, a
new recovery code is created and shown, and the old one stops working. The old code isn't consumed when it's used.
It stays valid until the new one is created, so killing the app in the middle can't lock the user out.

## 4.7 Unlock flow

```mermaid
flowchart TD
    Start([App process starts]) --> Cfg{lockStore.configured?}
    Cfg -- no --> Open[Gallery unlocked]
    Cfg -- yes --> Gate[LockGate]
    Gate --> Wait{Cooldown running?}
    Wait -- yes --> Count[Show 'Too many wrong tries. Try again in m:ss'] --> Gate
    Wait -- no --> Method{Method}
    Method -- Password / PIN / Pattern --> Inc[fails += 1, arm cooldown if fails >= 5] --> Hash{PBKDF2 matches?}
    Hash -- yes --> Reset[reset counter] --> Unlocked
    Hash -- no --> Err[Show 'That PIN is not right' / 'Pattern does not match'] --> Gate
    Method -- Fingerprint --> Bio{BiometricPrompt success?}
    Bio -- yes --> Reset
    Bio -- no / cancel --> Gate
    Method -- Forgot? Use recovery code --> RInc[fails += 1] --> RHash{Code matches?}
    RHash -- no --> Gate
    RHash -- yes --> NewLock[NEW LOCK setup page<br/>save or close] --> NewCode[New recovery code shown<br/>old one invalid] --> Open
    Unlocked[Unlocked] --> Need{needsRecoveryCode?<br/>email-era install or unconfirmed code}
    Need -- yes --> NewCode
    Need -- no --> Open
    Open --> LockBtn[User taps lock button and confirms] --> Gate
```

## 4.8 Change lock and turn off

From Settings, Security:

- When the lock is **off**: "App lock: Off · Tap to set up a lock" opens the same confirm and setup path as the lock
  button. "Recovery code: Created when you set up the lock" also starts setup.
- **Change lock** (`LockChangePage`):
  1. `CurrentLockCheck`: confirm with one current method. The recovery code isn't offered here, and the same
     wrong-try counter applies.
  2. `LockSetupPage` "CHANGE LOCK": choose 2 or more new methods, which **replace** the old ones. **The recovery
     code stays the same.** Toast: "Lock updated".
  3. "Turn off app lock" leads to the confirm dialog **TURN OFF LOCK**: "Turn off the app lock? Anyone with this phone
     can open Nyte Gallery until you set a lock again. Your photos stay encrypted." Confirming runs
     `LockStore.clear()`, which deletes **all** lock prefs, including the recovery code and the try counter. Toast:
     "App lock is off".
- **Recovery code** (`RecoveryCodeSettingsPage`): check the current lock, then "Create new recovery code".

## 4.9 FLAG_SECURE, screenshots and Recents

- `SCREENSHOTS_ALLOWED` is an `internal val` in `ui/GalleryScreen.kt`, read from `BuildConfig.SCREENSHOTS_ALLOWED`.
  It's **`false`** in debug and release, and `true` only in the separate `screenshot` build type. `MainActivity`
  reads it before `setContent`, so the very first frame is already protected. A `DisposableEffect` in `GalleryScreen`
  adds or clears the flag again.
- With `FLAG_SECURE` on, for the whole app including the lock screen:
  - screenshots and screen recordings show black or are refused;
  - the app's preview in **Recent apps** is blank;
  - casting or mirroring the screen to non-secure displays shows black.
- For Play Store screenshots, use the `screenshot` build type (CI artifact `nyte-gallery-screenshots-apk`; see
  [Build §8.2](08-build-release.md#screenshot-build-play-store-listing-screenshots)). Don't change the default.
