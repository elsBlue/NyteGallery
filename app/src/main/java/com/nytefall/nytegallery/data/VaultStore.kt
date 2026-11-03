package com.nytefall.nytegallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val VAULT_THUMB_EDGE = 384
private const val DECODE_MAX_SIDE = 4096
private const val DECODE_MAX_PIXELS = 12_000_000L

class VaultStore(context: Context) {
    private val dir = File(context.filesDir, "vault").apply {
        mkdirs()
        File(this, ".nomedia").takeIf { !it.exists() }?.createNewFile()
    }
    private val index = File(dir, "index.json")
    // Previous good copy of index.json, used if the main file is ever unreadable.
    private val indexBackup = File(dir, "index.json.bak")
    private val prefs = context.getSharedPreferences("nyte_gallery", Context.MODE_PRIVATE)

    fun load(): List<GalleryPhoto> {
        recoverIndexIfMissing()
        purgeExpired()
        if (!index.exists()) {
            if (!prefs.getBoolean(SEEDED, false)) {
                prefs.edit().putBoolean(SEEDED, true).apply()
                val seed = GalleryRepository.photos
                write(seed)
                return seed
            }
            return emptyList()
        }
        val saved = read()
        return saved.map { photo ->
            val bundled = GalleryRepository.photos.find { it.id == photo.id }
            if (bundled != null && photo.vaultFile == null) {
                val legacy = LEGACY_NAMES[photo.id]
                photo.copy(
                    imageRes = bundled.imageRes,
                    name = if (photo.name == legacy) bundled.name else photo.name,
                    takenAt = if (photo.takenAt > 0L) photo.takenAt else bundled.takenAt,
                )
            } else {
                photo
            }
        }
    }

    fun save(photos: List<GalleryPhoto>) {
        prefs.edit().putBoolean(SEEDED, true).apply()
        write(photos)
    }

    fun importStream(name: String, source: InputStream, id: String, takenAt: Long): GalleryPhoto? {
        val file = File(dir, "$id.nyt")
        if (!encryptTo(file, source)) {
            file.delete()
            return null
        }
        val photo = GalleryPhoto(id = id, name = name, vaultFile = file.name, takenAt = takenAt)
        ensureThumb(photo)
        return photo
    }

    fun openPlain(photo: GalleryPhoto): InputStream? = openDecrypt(photo)

    fun copyPlain(photo: GalleryPhoto, out: OutputStream): Boolean {
        val input = openDecrypt(photo) ?: return false
        return try {
            input.use { stream ->
                stream.copyTo(out)
                stream.wasAuthenticated()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun decode(photo: GalleryPhoto, maxEdge: Int): Bitmap? {
        val name = photo.vaultFile ?: return null
        val file = File(dir, name)
        if (!file.isFile) return null
        val key = "${photo.id}:v2:fit:$maxEdge"
        ThumbMemory.get(key)?.let { return it }
        val bitmap = decodeEncrypted(file, maxEdge, maxEdge, fill = false)
        if (bitmap != null) ThumbMemory.put(key, bitmap)
        return bitmap
    }

    fun decodeFill(photo: GalleryPhoto, width: Int, height: Int): Bitmap? {
        val name = photo.vaultFile ?: return null
        val file = File(dir, name)
        if (!file.isFile) return null
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val key = "${photo.id}:v2:fill:${w}x$h"
        ThumbMemory.get(key)?.let { return it }
        val bitmap = if (w == h && w <= VAULT_THUMB_EDGE) {
            ensureThumb(photo)
            val thumb = thumbFile(name)
            if (thumb.isFile) decodeEncrypted(thumb, w, h, fill = true) else decodeEncrypted(file, w, h, fill = true)
        } else {
            decodeEncrypted(file, w, h, fill = true)
        }
        if (bitmap != null) ThumbMemory.put(key, bitmap)
        return bitmap
    }

    fun ensureThumb(photo: GalleryPhoto) {
        val name = photo.vaultFile ?: return
        val lock = ThumbGate.locks.computeIfAbsent(name) { Any() }
        synchronized(lock) {
            legacyThumbFile(name).delete()
            val dest = thumbFile(name)
            if (dest.isFile && dest.length() > IV_BYTES + TAG_BYTES) return
            val sampled = decodeEncrypted(File(dir, name), VAULT_THUMB_EDGE, VAULT_THUMB_EDGE, fill = true) ?: return
            var fitted: Bitmap? = null
            try {
                fitted = centerSquare(sampled, VAULT_THUMB_EDGE)
                val jpeg = ByteArrayOutputStream()
                if (fitted.compress(Bitmap.CompressFormat.JPEG, 72, jpeg)) {
                    val bytes = jpeg.toByteArray()
                    if (bytes.isNotEmpty() && !encryptTo(dest, ByteArrayInputStream(bytes))) dest.delete()
                    bytes.fill(0)
                }
            } catch (_: Exception) {
                dest.delete()
            } finally {
                if (fitted != null && fitted !== sampled && !fitted.isRecycled) fitted.recycle()
                if (!sampled.isRecycled) sampled.recycle()
            }
        }
    }

    fun read(photo: GalleryPhoto): ByteArray? {
        val input = openDecrypt(photo) ?: return null
        return try {
            input.use { stream ->
                val bytes = stream.readBytes()
                if (stream.wasAuthenticated()) bytes else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun deleteFile(photo: GalleryPhoto) {
        val name = photo.vaultFile ?: return
        File(dir, name).delete()
        val thumb = thumbFile(name)
        thumb.delete()
        File(dir, thumb.name + ".tmp").delete()
        legacyThumbFile(name).delete()
        ThumbMemory.drop(photo.id)
    }

    fun trashExplained(): Boolean = prefs.getBoolean(TRASH_EXPLAINED, false)

    fun markTrashExplained() {
        prefs.edit().putBoolean(TRASH_EXPLAINED, true).apply()
    }

    private fun purgeExpired() {
        if (!index.exists()) return
        val now = System.currentTimeMillis()
        val kept = read().filter { photo ->
            val trashed = photo.trashedAt
            val expired = trashed != null && now - trashed >= TRASH_MS
            if (expired) deleteFile(photo)
            !expired
        }
        if (kept.size != read().size) write(kept)
    }

    /** index.json gone but its backup is there (never expected with the atomic write): bring the backup back. */
    private fun recoverIndexIfMissing() {
        if (index.exists() || !indexBackup.exists()) return
        runCatching { parse(indexBackup.readText()) }.onSuccess { indexBackup.copyTo(index, overwrite = true) }
    }

    /** Reads index.json; if it can't be read or parsed, falls back to index.json.bak and repairs the main file. */
    private fun read(): List<GalleryPhoto> {
        val main = runCatching { parse(index.readText()) }
        main.getOrNull()?.let { return it }
        if (indexBackup.exists()) {
            val previous = runCatching { parse(indexBackup.readText()) }.getOrNull()
            if (previous != null) {
                runCatching { writeIndexAtomically(indexBackup.readText(), keepBackup = false) }
                return previous
            }
        }
        throw main.exceptionOrNull() ?: IllegalStateException("index.json unreadable")
    }

    private fun parse(text: String): List<GalleryPhoto> {
        val array = JSONArray(text)
        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            GalleryPhoto(
                id = item.getString("id"),
                name = item.getString("name"),
                vaultFile = item.optString("file").ifBlank { null },
                trashedAt = item.optLong("trashedAt").takeIf { item.has("trashedAt") && !item.isNull("trashedAt") && it > 0L },
                takenAt = item.optLong("takenAt"),
            )
        }
    }

    private fun write(photos: List<GalleryPhoto>) {
        val array = JSONArray()
        photos.forEach { photo ->
            array.put(
                JSONObject()
                    .put("id", photo.id)
                    .put("name", photo.name)
                    .put("file", photo.vaultFile ?: "")
                    .put("trashedAt", photo.trashedAt ?: JSONObject.NULL)
                    .put("takenAt", photo.takenAt),
            )
        }
        writeIndexAtomically(array.toString(), keepBackup = true)
    }

    /**
     * Writes index.json safely: the new text goes to a temp file in the same folder, is flushed to disk (fsync), and
     * then replaces index.json with one rename, so a crash or power loss leaves either the old or the new file, never
     * half of one. With [keepBackup] the current index.json is first copied to index.json.bak. The format is unchanged.
     */
    private fun writeIndexAtomically(text: String, keepBackup: Boolean) {
        val tmp = File(dir, "index.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (keepBackup && index.exists() && runCatching { parse(index.readText()) }.isSuccess) {
            runCatching { index.copyTo(indexBackup, overwrite = true) }
        }
        if (!tmp.renameTo(index)) {
            // renameTo replaces the target on Android's file systems; this is only a last resort.
            index.delete()
            if (!tmp.renameTo(index)) {
                tmp.inputStream().use { input -> FileOutputStream(index).use { input.copyTo(it); it.fd.sync() } }
                tmp.delete()
            }
        }
    }

    private fun thumbFile(vaultName: String): File {
        val id = vaultName.removeSuffix(".nyt")
        return File(dir, "$id.thumb2.nyt")
    }

    private fun legacyThumbFile(vaultName: String): File {
        val id = vaultName.removeSuffix(".nyt")
        return File(dir, "$id.thumb.nyt")
    }

    private fun encryptTo(dest: File, source: InputStream): Boolean {
        val tmp = File(dir, dest.name + ".tmp")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            if (iv == null || iv.size != IV_BYTES) error("vault iv")
            FileOutputStream(tmp).use { out ->
                out.write(iv)
                val buf = ByteArray(CHUNK)
                while (true) {
                    val n = source.read(buf)
                    if (n < 0) break
                    val part = cipher.update(buf, 0, n)
                    if (part != null && part.isNotEmpty()) out.write(part)
                }
                val tail = cipher.doFinal()
                if (tail != null && tail.isNotEmpty()) out.write(tail)
            }
            if (dest.exists() && !dest.delete()) error("replace")
            if (!tmp.renameTo(dest)) {
                tmp.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                if (!tmp.delete()) tmp.deleteOnExit()
            }
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        }
    }

    private fun decodeEncrypted(file: File, width: Int, height: Int, fill: Boolean): Bitmap? {
        if (!file.isFile || file.length() < IV_BYTES + TAG_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            plainStream(file)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (_: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val orientation = readOrientation(file)
        val turned = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val srcWidth = if (turned) bounds.outHeight else bounds.outWidth
        val srcHeight = if (turned) bounds.outWidth else bounds.outHeight
        var sample = 1
        if (fill) {
            val need = maxOf(width.toFloat() / srcWidth, height.toFloat() / srcHeight)
            val needWidth = srcWidth * need
            val needHeight = srcHeight * need
            while (srcWidth / (sample * 2) >= needWidth && srcHeight / (sample * 2) >= needHeight) sample *= 2
        } else {
            while (srcWidth / sample > width || srcHeight / sample > height) sample *= 2
        }
        while (
            srcWidth / sample > DECODE_MAX_SIDE ||
            srcHeight / sample > DECODE_MAX_SIDE ||
            (srcWidth / sample).toLong() * (srcHeight / sample) > DECODE_MAX_PIXELS
        ) sample *= 2
        val decoded = decodeSampled(file, sample) ?: return null
        return try {
            shape(decoded, orientation, turned, width, height, fill)
        } catch (_: Throwable) {
            decoded
        }
    }

    private fun decodeSampled(file: File, sample: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val input = plainStream(file) ?: return null
        return try {
            input.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream, null, options) ?: return null
                val buf = ByteArray(CHUNK)
                while (stream.read(buf) >= 0) Unit
                if (stream.wasAuthenticated()) bitmap else {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readOrientation(file: File): Int = try {
        plainStream(file)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun shape(bitmap: Bitmap, orientation: Int, turned: Boolean, width: Int, height: Int, fill: Boolean): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
        }
        val shownWidth = if (turned) bitmap.height else bitmap.width
        val shownHeight = if (turned) bitmap.width else bitmap.height
        val scale = if (fill) {
            maxOf(width.toFloat() / shownWidth, height.toFloat() / shownHeight)
        } else {
            minOf(width.toFloat() / shownWidth, height.toFloat() / shownHeight)
        }
        if (scale < 0.9f) matrix.postScale(scale, scale)
        if (matrix.isIdentity) return bitmap
        val shaped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (shaped !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return shaped
    }

    private fun centerSquare(bitmap: Bitmap, edge: Int): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (side <= 0) return bitmap
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        val matrix = Matrix()
        if (side > edge) matrix.postScale(edge.toFloat() / side, edge.toFloat() / side)
        if (left == 0 && top == 0 && matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, left, top, side, side, matrix, true)
    }

    private fun openDecrypt(photo: GalleryPhoto): GcmPlainStream? {
        val name = photo.vaultFile ?: return null
        val file = File(dir, name)
        if (!file.isFile) return null
        return plainStream(file)
    }

    private fun plainStream(file: File): GcmPlainStream? {
        if (file.length() < IV_BYTES + TAG_BYTES) return null
        val input = FileInputStream(file)
        val iv = ByteArray(IV_BYTES)
        if (!input.readExact(iv)) {
            input.close()
            return null
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            GcmPlainStream(input, cipher, file.length() - IV_BYTES - TAG_BYTES)
        } catch (_: Exception) {
            input.close()
            null
        }
    }

    private fun InputStream.readExact(buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = read(buf, off, buf.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                return generateKey(strongBox = true)
            } catch (_: Exception) {
                val fresh = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (fresh.containsAlias(KEY_ALIAS)) fresh.deleteEntry(KEY_ALIAS)
            }
        }
        return generateKey(strongBox = false)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        if (strongBox && Build.VERSION.SDK_INT >= 28) builder.setIsStrongBoxBacked(true)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private class GcmPlainStream(
        private val source: InputStream,
        private val cipher: Cipher,
        private var cipherLeft: Long,
    ) : InputStream() {
        private val chunk = ByteArray(CHUNK)
        private var pending = ByteArray(0)
        private var pendingPos = 0
        private var finished = false
        private var authenticated = false

        fun wasAuthenticated(): Boolean = authenticated

        override fun read(): Int {
            val one = ByteArray(1)
            val n = read(one, 0, 1)
            return if (n < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pendingPos >= pending.size && !pull()) return -1
            val n = minOf(len, pending.size - pendingPos)
            pending.copyInto(b, off, pendingPos, pendingPos + n)
            pendingPos += n
            return n
        }

        override fun available(): Int {
            val left = pending.size - pendingPos
            if (left > 0) return left
            return if (finished) 0 else 1
        }

        override fun close() {
            source.close()
        }

        private fun pull(): Boolean {
            if (finished) return false
            while (true) {
                if (cipherLeft > 0) {
                    val want = minOf(chunk.size.toLong(), cipherLeft).toInt()
                    val n = source.read(chunk, 0, want)
                    if (n < 0) {
                        finished = true
                        return false
                    }
                    cipherLeft -= n
                    val produced = cipher.update(chunk, 0, n)
                    if (produced != null && produced.isNotEmpty()) {
                        pending = produced
                        pendingPos = 0
                        return true
                    }
                } else {
                    val tag = source.readBytes()
                    val produced = try {
                        cipher.doFinal(tag)
                    } catch (_: Exception) {
                        finished = true
                        return false
                    }
                    finished = true
                    authenticated = true
                    if (produced != null && produced.isNotEmpty()) {
                        pending = produced
                        pendingPos = 0
                        return true
                    }
                    return false
                }
            }
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nyte_gallery_vault"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = 128
        const val CHUNK = 64 * 1024
        const val SEEDED = "seeded"
        const val TRASH_EXPLAINED = "trash_explained"
        const val TRASH_MS = 30L * 24L * 60L * 60L * 1000L
        val LEGACY_NAMES = mapOf(
            "valley" to "Lembah Danau Terang",
            "japan" to "Gadis Kota Jepang",
            "cyber" to "Pasar Neon Cyberpunk",
            "sea" to "Pantai Payung Kuning",
            "cat" to "Kucing di Taman Bunga",
            "lantern" to "Lentera Festival Siang",
        )
    }
}

private object ThumbGate {
    val locks = ConcurrentHashMap<String, Any>()
}

private object ThumbMemory {
    private val cache = object : LruCache<String, Bitmap>(budget()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    private fun budget(): Int {
        val share = (Runtime.getRuntime().maxMemory() * 0.25).toInt()
        return share.coerceIn(12 * 1024 * 1024, 48 * 1024 * 1024)
    }

    fun get(key: String): Bitmap? = cache.get(key)?.takeIf { !it.isRecycled }

    fun put(key: String, bitmap: Bitmap) {
        if (bitmap.isRecycled || bitmap.allocationByteCount > 8 * 1024 * 1024) return
        cache.put(key, bitmap)
    }

    fun drop(id: String) {
        cache.snapshot().keys.filter { it.startsWith("$id:") }.forEach { cache.remove(it) }
    }
}
