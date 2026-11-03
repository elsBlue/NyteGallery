# 6. Backup and restore (password backup files)

Code: `data/VaultBackup.kt` (format), `data/BackupPrefs.kt`, `GalleryViewModel.writeBackup`, `verifyBackup` and
`readBackup`, and the pages in `ui/VaultMenu.kt`.

## 6.1 Why it exists

The vault key lives in this phone's Keystore and can't be exported. Uninstalling the app, clearing its data or
changing phones makes the `.nyt` files unreadable. Android's automatic backup is turned off on purpose. A **password
backup** is therefore the only way to move photos to a new phone or keep a copy. It is encrypted with a key derived
from a password only the user knows, so it doesn't depend on the device key.

The menu footer says: "The phone key cannot move to a new device. A backup uses a password only you know."

## 6.2 File format `NYTEBAK1`

All integers are **big-endian** (`DataOutputStream`).

### Header (34 bytes)

| Offset | Size | Field | Value |
|---:|---:|---|---|
| 0 | 8 | magic | ASCII `NYTEBAK1` |
| 8 | 1 | version | `2` when writing; `1` and `2` are read |
| 9 | 1 | kdf | `1` = PBKDF2-HMAC-SHA256 (the only value accepted) |
| 10 | 4 | iterations (u32) | `600000` when writing; `1..5,000,000` accepted on read |
| 14 | 16 | salt | random (`SecureRandom`) |
| 30 | 4 | count (u32) | v2: **a hint only** (the number of photos planned, capped at 100,000). v1: the exact count. Must be `0..100000` on read. |

### Entries (repeated)

| Size | Field |
|---:|---|
| 12 | IV (random per entry) |
| 4 | `cipherLen` (u32), `16 .. 80 MiB` |
| `cipherLen` | AES-256-GCM(payload), 128-bit tag at the end; no AAD |

**Payload** (plaintext inside each entry):

| Size | Field |
|---|---|
| u16 + bytes | `id`, UTF-8, truncated to 80 characters |
| u16 + bytes | `name`, UTF-8, truncated to 180 characters |
| i64 | `takenAt` (epoch ms) |
| i64 | `trashedAt` (epoch ms, `0` = not in Trash) |
| u32 + bytes | image: the **original file bytes** (bundled samples as JPEG q92) |

### v2 end mark and sealed trailer

After the last entry:

```
end mark : 12 zero bytes | u32 0
trailer  : IV 12 | u32 len (16..64 on read) | AES-256-GCM( u32 count )
```

On read, the trailer must decrypt to 4 bytes, and its count must equal the number of entries actually read.
Otherwise the result is `Damaged`. A file that was cut off, or had entries removed or reordered at the end, is
therefore detected even though the header count is only a hint.

### v1 (read only)

Old v1 files have the exact count in the header (it was patched in after writing, which needed a seekable file).
There is no end mark and no trailer. The reader reads exactly `count` entries.

### Properties and limits

- The header (salt, iterations, count) is **not authenticated**. Tampering with it only causes a key mismatch, which
  reads as `BadPassword`, or a `Damaged` result.
- Entries are authenticated one by one. Their order isn't bound to anything, except that the v2 trailer seals the
  total.
- Names, dates and trash state are **inside** the encrypted payload. Unlike `index.json`, the backup leaks nothing
  except the approximate photo count and sizes.
- One photo can be at most about 80 MiB inside a backup (the `cipherLen` limit). Larger photos fail on read.
  **(The writer doesn't check this limit, so a photo above about 80 MiB would produce a backup that can't be fully
  restored. This is an edge case and not verified.)**

## 6.3 Key derivation

- `VaultBackup.pbkdf2()` is a hand-written **PBKDF2-HMAC-SHA256** (using `javax.crypto.Mac`), with **600,000
  iterations** and a 32-byte output, which becomes the AES-256 key.
- It uses the 16-byte random salt from the header. The password is UTF-8 encoded.
- The key bytes are zeroed when the writer is closed or the read finishes. Payload buffers are zeroed after use.

## 6.4 Writing a backup

1. In Menu, choose Backup (or Settings, Back up). On the first visit, a guide opens: **BEFORE YOU BACK UP**, with
   three points (the password can't be recovered; save the file off the phone; how to restore), and "Got it". After
   that, `guide_seen = true`.
2. `BackupPage`:
   - "New backup password": at least 8 characters, with the hint "Use at least 8 characters. A longer phrase is
     safer".
   - "Confirm password".
   - A required tick: "I understand Nyte cannot recover this password".
   - Button: "Create backup".
3. The system "Create document" picker opens with the default name **`nyte-backup.nybak`** and MIME type
   `application/octet-stream`. Any SAF location works: local storage, an SD card, Drive and so on.
4. `writeBackup` runs as a transfer ("Saving backup"). It opens the URI in mode `"wt"` (falling back to the default
   mode) and writes the header. Then, for **every photo including Trash**, it decrypts the photo into memory, writes
   one entry, and zeroes the buffer. The output goes straight to the destination through a 256 KB buffer, with no
   temp file. This is the "streaming" v2 design, and it also works on cloud providers that can't seek.
5. `finish()` writes the end mark and trailer, but only if at least one photo was written.
6. **On any failure**, or if nothing was written, `dropDocument(uri)` tries `DocumentsContract.deleteDocument`, and
   truncates the file to 0 bytes if that fails. A half-written file is never left looking like a good backup.
7. On success, `BackupPrefs.recordBackup(now, written)` runs, and **Backup saved** opens. It shows the file name, the
   number of photos saved, and "N photos couldn't be read and were left out" if any were skipped. It also has a
   "**Check backup**" button that can run once, because the password is kept in memory (`checkSecret`) only until
   that check or until the page closes.

## 6.5 Checking (verify) a backup

- From **Backup saved**, "Check backup" runs right away with the remembered password.
- From **Restore backup** (Settings, "Check a backup file", which opens the Restore backup page), the user types the
  password and taps "Check a backup file", then picks the file.
- `VaultBackup.verify()` runs the full restore logic, decrypting every entry and the trailer, but keeps no photo.
  Result: "Backup checked: N photos can be restored", or one of the error messages in 6.7.

## 6.6 Restoring a backup

1. In Menu, choose Restore backup (or the Settings equivalent). The `RestoreBackupPage` text says: "Choose the backup
   file and the password you set when it was created. Photos already in this gallery are left as they are."
2. Enter the password, tap "Choose backup file", and the system "Open document" picker opens (MIME filter
   `application/octet-stream`, `application/*`, `*/*`).
3. `readBackup` runs as a transfer ("Restoring backup"). Progress uses the header count as the total. For each entry:
   - if its `id` already exists in the vault, it's **skipped**, so restoring the same backup twice doesn't create
     duplicates;
   - otherwise the bytes are encrypted into the vault with the **same id**, using the device key (`importStream`).
     `trashedAt` is restored, so photos that were in Trash go back to Trash, and their 30-day timer continues from
     the original date. The index is saved after every photo.
4. Result toast: "Restored N photos." plus " K already here." when some were skipped. On a partial restore, the page
   stays open with the error text.
5. If any photo failed to save, and less than 64 MB of internal space is free, the message is "Phone storage is full.
   N photos couldn't be restored. Free up space and restore again". Otherwise it's "N photos couldn't be saved.
   Restore again to retry them".

Restore isn't atomic. Photos restored before an error are kept, and running the restore again fills in the rest,
because existing ids are skipped.

## 6.7 Read results and error messages

| `ReadResult` | When | Message shown |
|---|---|---|
| `NotBackup` | Shorter than 34 bytes, or the magic isn't `NYTEBAK1` | "That file is not a Nyte backup" |
| `BadPassword` | The **first** entry's length is outside `16..80 MiB`, or its GCM check fails. In v2 this also covers an empty backup whose trailer fails to decrypt. | "That password does not match this backup" |
| `Damaged(n)` | Unknown version or kdf, iterations or count out of range, a short read, a bad entry after the first, a parse error, a bad trailer, or a count mismatch | n = 0: "This backup is damaged or incomplete and nothing could be read from it". n > 0 (verify): "This backup is incomplete, maybe the copy or upload didn't finish. Only n photos could be read". n > 0 (restore): "…Restored n photos before it stopped" |
| `Ok(n)` | Everything read, and the trailer matches (v2) | "Backup checked: n photos can be restored" / "Restored n photos." |

**I/O errors** are turned into readable text by `explainIo(error, writing)`:

| Cause | Writing | Reading |
|---|---|---|
| `OutOfMemoryError` | "A photo was too large to process on this phone" | same |
| ENOSPC / "no space" / "disk full" | "Not enough space where the backup is saved. Free up space or pick another place" | "Not enough space on this phone to restore. Free up space and try again" |
| `SecurityException` / EACCES / "permission" | "Nyte is not allowed to write there. Pick another folder" | "Nyte is not allowed to open that file. Pick it again" |
| `FileNotFoundException` | "That location is no longer available. Pick another place" | "That file is no longer available. Pick it again" |
| other `IOException` | "The connection to that location was lost before the backup finished. Try again, or save to this phone first" | "The file stopped loading before the restore finished. Try again, or copy it to this phone first" |
| anything else | "Couldn't write that backup" | "Couldn't read that backup" |

Other messages:
- "Couldn't open that location. Pick another place" (`openOutputStream` returned null).
- "Nothing to back up yet".
- "None of the photos could be read, so no backup was made".
- "Couldn't open that file. Pick it again".
- "Another task is still running. Try again when it finishes" (verify while another transfer runs).

## 6.8 BackupPrefs

SharedPreferences `nyte_backup`: `guide_seen`, `last_backup_at` (ms, 0 = never) and `last_backup_photos`.
Settings shows "Last backup: Never" or "Last backup: 3 Nov 2026, 14:05, 12 photos" (`d MMM yyyy, HH:mm`, US
locale). A code comment says the timestamp is "kept for a future reminder". No backup reminder exists yet.
