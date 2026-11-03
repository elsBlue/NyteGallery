# 3. Storage and encryption

## 3.1 On-disk layout

Everything lives in app-private internal storage (`Context.filesDir`). Other apps can't read it, and with
`allowBackup=false` plus the exclusion rules, Android's automatic backup never copies it.

```
/data/data/com.nytefall.nytegallery/
├── files/
│   └── vault/
│       ├── .nomedia                  # created on first use; stops media scanners
│       ├── index.json                # PLAINTEXT list of photos (see 3.2)
│       ├── index.json.bak            # previous good copy of index.json (fallback)
│       ├── <uuid>.nyt                # encrypted original photo bytes
│       ├── <uuid>.thumb2.nyt         # encrypted 384×384 JPEG thumbnail (q72)
│       └── *.tmp                     # short-lived, written then renamed
├── shared_prefs/
│   ├── nyte_gallery.xml              # seeded, trash_explained
│   ├── nyte_lock.xml                 # app-lock settings and hashes (see 04-app-lock.md)
│   ├── nyte_backup.xml               # guide_seen, last_backup_at, last_backup_photos
│   └── nyte_onboarding.xml           # done (welcome pages finished or skipped)
└── cache/                            # only temporary files; "Clear cache" empties it
```

`VaultStore` creates `files/vault` and its `.nomedia` file in its constructor.

## 3.2 `index.json`

A JSON array, rewritten on every change (`VaultStore.write`, then `writeIndexAtomically`):

```json
[
  {"id":"valley","name":"Bright Lake Valley","file":"","trashedAt":null,"takenAt":1794369060000},
  {"id":"5d0c…","name":"IMG_20261101_091233","file":"5d0c….nyt","trashedAt":null,"takenAt":1793500353000}
]
```

| Field | Meaning |
|---|---|
| `id` | Random UUID for imported photos; `valley`, `japan`, `cyber`, `sea`, `cat` or `lantern` for samples |
| `name` | Display name: the original file name without its extension, or a name the user set |
| `file` | `<id>.nyt`, or `""` for bundled samples, which have no vault file |
| `trashedAt` | Epoch ms when the photo was moved to Trash, or `null` |
| `takenAt` | Epoch ms capture time: MediaStore `DATE_TAKEN`, then EXIF, then the import time |

**Not encrypted.** Photo names, capture dates and trash dates sit in plaintext inside app-private storage. Only the
image bytes are encrypted. Someone with root or forensic access to the app's data could read the names and dates,
but not the photos.

**Atomic write:**
1. The new JSON goes to `index.json.tmp` in the same folder and is flushed with `fd.sync()`.
2. If the current `index.json` parses, it's copied to `index.json.bak` (the previous good copy).
3. The temp file is renamed over `index.json`.

A crash leaves either the old or the new index, never half of one. The format is unchanged.

**Fallback on read:**
- If `index.json` can't be read or parsed, `read()` uses `index.json.bak` and rewrites `index.json` from it. Only
  the very last change can be lost.
- If `index.json` is missing but `.bak` is valid, `load()` restores it first.
- If both are bad, the read throws as before.

### Seeding and legacy names

- `load()` first calls `purgeExpired()` (see 3.6).
- If `index.json` doesn't exist and the `seeded` pref is false, it writes the 6 bundled samples and sets
  `seeded=true`. If the user later deletes every photo, the index exists but is empty, so the samples don't come
  back.
- For bundled entries (with no `file`), it restores `imageRes`. If the stored name equals the old Indonesian name in
  `LEGACY_NAMES`, it renames the entry to the English one.

## 3.3 Encryption of photos

| Item | Value |
|---|---|
| Key store | `AndroidKeyStore` |
| Alias | `nyte_gallery_vault` |
| Algorithm | AES-256, `AES/GCM/NoPadding`, 128-bit tag, Keystore-generated 12-byte IV (`setRandomizedEncryptionRequired(true)`) |
| Purposes | Encrypt and decrypt |
| StrongBox | On API 28+, `setIsStrongBoxBacked(true)` is tried first. If generation fails, the half-made alias is deleted and a normal TEE/software-backed key is generated instead. |
| User auth binding | **None.** The key has no `setUserAuthenticationRequired`, so the Keystore doesn't tie it to the app lock or the device lock. The app lock is a UI gate (see [App lock](04-app-lock.md)). |
| Export | Keystore keys can't be exported. If the app is uninstalled, or its data is cleared, the key is gone and the `.nyt` files can't be read. Only a password backup survives that. |

**File format of `<id>.nyt`:**

```
IV (12 bytes) | AES-256-GCM ciphertext of the original file bytes | GCM tag (16 bytes)
```

- **Writing** (`encryptTo`): the source stream is encrypted in 64 KB chunks into `<name>.tmp`, then the tmp file is
  renamed to the destination, falling back to copy and delete if the rename fails. The original bytes, including
  EXIF and the original format (JPEG, PNG, HEIC, ...), are stored unchanged. The app doesn't re-encode.
- **Reading** (`GcmPlainStream`): reads the IV, initialises the cipher, feeds the ciphertext in 64 KB chunks, and
  finally calls `doFinal(tag)`. `wasAuthenticated()` only becomes true if the GCM tag checks out. Every consumer
  (`read`, `copyPlain`, `decodeSampled`) throws away its result when authentication failed. A tampered or truncated
  file therefore gives "no image" rather than a corrupted image.

## 3.4 Decoding and thumbnails

- `decodeEncrypted(file, w, h, fill)`:
  1. Reads the bounds and the EXIF orientation (two extra passes, each decrypting the stream).
  2. Picks a power-of-two `inSampleSize`. "fit" means the decoded image fits inside `w×h`. "fill" means it covers
     `w×h`. The sample is then raised further until each side is at most **4096 px** and the image is at most
     **12 MP**.
  3. Decodes, drains the stream to check the tag, and applies the EXIF rotation and flip plus a scale or crop
     (`shape`).
- **Thumbnails** (`ensureThumb`): a 384 px centre-square JPEG at quality 72, encrypted with the same key into
  `<id>.thumb2.nyt`. Created at import, lazily by `decodeFill` for square requests of 384 px or less, and for every
  photo 2 s after the ViewModel starts. Any old `<id>.thumb.nyt` (v1) file is deleted at the same time. A
  `ThumbGate` per-file lock (`ConcurrentHashMap<String, Any>`) stops two threads from building the same thumbnail.
- **Which source is used:**
  - grid tiles use `maxEdge = VAULT_THUMB_EDGE` (384), so they read the thumbnail;
  - carousel cards use `decodeFill` at card size, from the full file;
  - the full-screen view uses `decode(maxEdge = 2400)`.
- **Bundled samples** are decoded from resources with `BitmapFactory`, not through the vault.

## 3.5 In-memory cache (`ThumbMemory`)

- An `LruCache<String, Bitmap>` sized to 25% of `Runtime.maxMemory()`, clamped to **12–48 MB**. Size is measured
  by `allocationByteCount`.
- Bitmaps over 8 MB are never cached.
- Keys: `"<id>:v2:fit:<maxEdge>"` and `"<id>:v2:fill:<w>x<h>"`. `drop(id)` removes every key for a photo, and is
  called when its file is deleted.
- The cache lives in RAM only. Decrypted bitmaps are never written to disk.

## 3.6 Trash and 30-day auto-delete

- "Delete" on a photo sets `trashedAt = now`. The photo leaves the carousel and grid and appears in Trash. Its
  files stay encrypted on disk.
- Restoring from Trash sets `trashedAt = null` and turns the carousel to that photo.
- "Empty" (Trash header) deletes `.nyt`, `.thumb2.nyt`, any `.tmp` and legacy thumbnail files for every trashed
  photo, then saves the index.
- **Auto-delete:** `purgeExpired()` runs at the start of every `VaultStore.load()`. It permanently deletes photos
  whose `now - trashedAt >= 30 days` (`TRASH_MS`). `load()` is only called when `GalleryViewModel` is created,
  which in practice means when the app process starts. Expired photos are therefore removed the next time the app
  is opened, not at the exact 30-day mark.
- The comparison uses the wall clock (`System.currentTimeMillis()`), so changing the device clock moves the purge.

## 3.7 Other storage

| Prefs file | Keys | Owner |
|---|---|---|
| `nyte_gallery` | `seeded` (bool), `trash_explained` (bool: the first-time "Move to Trash" notice was shown) | `VaultStore` |
| `nyte_lock` | see [App lock §4.3](04-app-lock.md#43-lockstore-storage) | `LockStore` |
| `nyte_backup` | `guide_seen` (bool), `last_backup_at` (ms), `last_backup_photos` (int) | `BackupPrefs` |
| `nyte_onboarding` | `done` (bool: welcome pages finished, skipped, or not needed because the install already had a vault or a lock) | `OnboardingPrefs` |

"Clear cache" (Settings) deletes the contents of `cacheDir` and `externalCacheDir` only, and reports the bytes
freed. It never touches `files/vault` or the prefs. In the current code nothing obvious writes to the cache
directories, so the size is usually small or zero. **(unclear what besides system/library temp files ends up there)**
