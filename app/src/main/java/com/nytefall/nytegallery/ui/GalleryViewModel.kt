package com.nytefall.nytegallery.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nytefall.nytegallery.TransferNotices
import com.nytefall.nytegallery.data.BackupPrefs
import com.nytefall.nytegallery.data.GalleryPhoto
import com.nytefall.nytegallery.data.VaultBackup
import com.nytefall.nytegallery.data.VaultStore
import com.nytefall.nytegallery.data.captureMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

data class GalleryUiState(
    val photos: List<GalleryPhoto>,
    val trash: List<GalleryPhoto>,
    val rotation: Float,
    val activeIndex: Int,
)

/** imported > 0 means the originals are kept until the user picks Remove or Keep. */
data class ImportOutcome(val imported: Int)

/** Result of removing the originals after the user chose "Remove originals". */
data class RemovalOutcome(
    val consent: IntentSender?,
    val alreadyRemoved: Boolean,
)

data class TransferProgress(val label: String, val done: Int, val total: Int)

data class RestoreOutcome(val added: Int, val skipped: Int, val problem: String?, val failed: Int = 0)

/** saved > 0 with problem == null is success; left = photos that could not be read and were left out. */
data class BackupOutcome(val saved: Int, val left: Int, val problem: String?, val fileName: String? = null)

/** problem == null means every photo in the file opened with the password. */
data class VerifyOutcome(val photos: Int, val problem: String?)

private class BackupProblem(message: String) : Exception(message)

/** Turns a write/read failure into a sentence the user can act on. */
private fun explainIo(error: Throwable, writing: Boolean): String {
    val text = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ").lowercase()
    return when {
        error is OutOfMemoryError -> "A photo was too large to process on this phone"
        "enospc" in text || "no space" in text || "space left" in text || "disk full" in text ->
            if (writing) "Not enough space where the backup is saved. Free up space or pick another place"
            else "Not enough space on this phone to restore. Free up space and try again"
        error is SecurityException || "eacces" in text || "permission" in text ->
            if (writing) "Nyte is not allowed to write there. Pick another folder"
            else "Nyte is not allowed to open that file. Pick it again"
        error is java.io.FileNotFoundException ->
            if (writing) "That location is no longer available. Pick another place"
            else "That file is no longer available. Pick it again"
        error is java.io.IOException ->
            if (writing) "The connection to that location was lost before the backup finished. Try again, or save to this phone first"
            else "The file stopped loading before the restore finished. Try again, or copy it to this phone first"
        else -> if (writing) "Couldn't write that backup" else "Couldn't read that backup"
    }
}

private data class PreparedRestore(
    val photos: List<GalleryPhoto>,
    val skipped: Int,
    val result: VaultBackup.ReadResult,
    val note: String?,
    val failed: Int = 0,
)

sealed class DeleteStep {
    data class Consent(val sender: IntentSender) : DeleteStep()
    data object Done : DeleteStep()
    data object Failed : DeleteStep()
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val vault = VaultStore(app)
    private val backupPrefs = BackupPrefs(app)
    private val all = MutableStateFlow(vault.load())
    private val rotation = MutableStateFlow(0f)
    private var settleJob: Job? = null
    private var dragOrigin = 0f
    private val _transfer = MutableStateFlow<TransferProgress?>(null)
    val transfer: StateFlow<TransferProgress?> = _transfer
    private var transferring = false
    private var deleteQueue: List<List<Uri>> = emptyList()
    // Originals of the last import, untouched until the user chooses to remove or keep them.
    private var pendingOriginals: List<Uri> = emptyList()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            var changed = false
            val next = all.value.map { photo ->
                if (photo.takenAt > 0L || photo.vaultFile == null) photo
                else {
                    val millis = vault.openPlain(photo)?.use { captureMillis(it) } ?: 0L
                    if (millis > 0L) {
                        changed = true
                        photo.copy(takenAt = millis)
                    } else {
                        photo
                    }
                }
            }
            if (changed) {
                all.value = next
                vault.save(next)
            }
            delay(2_000)
            all.value.filter { it.vaultFile != null }.forEach { vault.ensureThumb(it) }
        }
    }

    val uiState: StateFlow<GalleryUiState> = combine(all, rotation) { list, angle ->
        val visible = list.filter { it.trashedAt == null }
        GalleryUiState(
            photos = visible,
            trash = list.filter { it.trashedAt != null },
            rotation = angle,
            activeIndex = activeIndexFor(angle, visible.size),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        run {
            val visible = all.value.filter { it.trashedAt == null }
            GalleryUiState(visible, all.value.filter { it.trashedAt != null }, 0f, 0)
        },
    )

    fun onDragStart() {
        settleJob?.cancel()
        dragOrigin = rotation.value
    }

    fun onDrag(deltaPx: Float) {
        val count = visible().size
        if (count == 0) return
        settleJob?.cancel()
        val step = 360f / count
        val next = rotation.value - deltaPx * (step / PIXELS_PER_CARD)
        rotation.value = next.coerceIn(dragOrigin - step, dragOrigin + step)
    }

    fun onDragEnd(velocityPx: Float) {
        val count = visible().size
        if (count == 0) return
        val step = 360f / count
        val delta = rotation.value - dragOrigin
        val steps = if (abs(delta) > step * 0.5f) {
            if (delta > 0f) 1 else -1
        } else {
            0
        }
        animateTo(dragOrigin + steps * step)
    }

    fun showIndex(index: Int) {
        val count = visible().size
        if (count == 0) return
        val step = 360f / count
        val currentIndex = activeIndexFor(rotation.value, count)
        var delta = index - currentIndex
        if (delta > count / 2) delta -= count
        if (delta < -count / 2) delta += count
        if (abs(delta) <= 1) animateTo(rotation.value + delta * step)
        else rotation.value = index * step
    }

    fun rename(index: Int, name: String) {
        val clean = name.trim()
        val photo = visible().getOrNull(index) ?: return
        if (clean.isEmpty()) return
        update(photo.id) { it.copy(name = clean) }
    }

    fun trashExplained(): Boolean = vault.trashExplained()

    fun markTrashExplained() = vault.markTrashExplained()

    fun moveToTrash(index: Int) {
        settleJob?.cancel()
        val visible = visible()
        val photo = visible.getOrNull(index) ?: return
        update(photo.id) { it.copy(trashedAt = System.currentTimeMillis()) }
        val left = visible.size - 1
        rotation.value = if (left <= 0) 0f else index.coerceAtMost(left - 1) * (360f / left)
    }

    fun restore(id: String) {
        settleJob?.cancel()
        update(id) { it.copy(trashedAt = null) }
        val after = visible()
        val index = after.indexOfFirst { it.id == id }
        if (index >= 0 && after.isNotEmpty()) {
            rotation.value = index * (360f / after.size)
        }
    }

    fun emptyTrash() {
        val trashed = all.value.filter { it.trashedAt != null }
        trashed.forEach { vault.deleteFile(it) }
        all.value = all.value.filter { it.trashedAt == null }
        vault.save(all.value)
    }

    fun exportToPublic(ids: Set<String>, onDone: (Int) -> Unit) {
        val before = visible()
        val targets = before.filter { it.id in ids }
        if (targets.isEmpty()) {
            onDone(0)
            return
        }
        if (!openTransfer("Restoring photos", targets.size)) return
        val activeId = before.getOrNull(activeIndexFor(rotation.value, before.size))?.id
        settleJob?.cancel()
        viewModelScope.launch {
            var exported = 0
            try {
                targets.forEachIndexed { index, photo ->
                    val wrote = withContext(Dispatchers.IO) { writePublic(photo) }
                    if (wrote) {
                        val next = all.value.filter { it.id != photo.id }
                        all.value = next
                        withContext(Dispatchers.IO) {
                            vault.save(next)
                            vault.deleteFile(photo)
                        }
                        exported++
                    }
                    markTransfer(index + 1, targets.size)
                }
            } finally {
                val after = all.value.filter { it.trashedAt == null }
                val index = after.indexOfFirst { it.id == activeId }.let { if (it >= 0) it else 0 }
                rotation.value = if (after.isEmpty()) 0f else index * (360f / after.size)
                closeTransfer()
                onDone(exported)
            }
        }
    }

    private fun writePublic(photo: GalleryPhoto): Boolean {
        val resolver = getApplication<Application>().contentResolver
        var uri: Uri? = null
        return try {
            val safe = photo.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifBlank { "Nyte" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$safe-${photo.id.take(6)}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nyte Gallery")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val created = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            uri = created
            val wrote = resolver.openOutputStream(created)?.use { out -> writePhoto(photo, out) } ?: false
            if (!wrote) {
                resolver.delete(created, null, null)
                return false
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(created, values, null, null)
            }
            true
        } catch (_: Exception) {
            uri?.let { resolver.delete(it, null, null) }
            false
        }
    }

    private fun writePhoto(photo: GalleryPhoto, out: java.io.OutputStream): Boolean {
        if (photo.vaultFile != null) return vault.copyPlain(photo, out)
        val bytes = bundledBytes(photo) ?: return false
        out.write(bytes)
        return true
    }

    /** Removes a half-written or empty backup so it isn't mistaken for a good one later. */
    private fun dropDocument(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val deleted = try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (_: Exception) {
            false
        }
        if (!deleted) {
            try {
                resolver.openOutputStream(uri, "wt")?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun plainBytes(photo: GalleryPhoto): ByteArray? {
        if (photo.vaultFile != null) return vault.read(photo)
        return bundledBytes(photo)
    }

    private fun bundledBytes(photo: GalleryPhoto): ByteArray? {
        if (photo.imageRes == 0) return null
        val bitmap = BitmapFactory.decodeResource(getApplication<Application>().resources, photo.imageRes) ?: return null
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            if (!bitmap.isRecycled) bitmap.recycle()
            stream.toByteArray()
        }
    }

    fun importFromDevice(uriStrings: List<String>, onReady: (ImportOutcome) -> Unit) {
        if (uriStrings.isEmpty()) return
        if (!openTransfer("Moving into Nyte", uriStrings.size)) return
        val start = visible().size
        viewModelScope.launch {
            val imported = mutableListOf<Pair<Uri, GalleryPhoto>>()
            try {
                val resolver = getApplication<Application>().contentResolver
                uriStrings.forEachIndexed { index, raw ->
                    val uri = Uri.parse(raw)
                    val photo = withContext(Dispatchers.IO) {
                        val rawName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "Photo"
                        val taken = takenMillis(resolver, uri).takeIf { it > 0L } ?: System.currentTimeMillis()
                        resolver.openInputStream(uri)?.use { input ->
                            vault.importStream(rawName.substringBeforeLast('.'), input, UUID.randomUUID().toString(), taken)
                        }
                    }
                    if (photo != null) {
                        val next = all.value + photo
                        all.value = next
                        withContext(Dispatchers.IO) { vault.save(next) }
                        imported += uri to photo
                    }
                    markTransfer(index + 1, uriStrings.size)
                }
            } finally {
                closeTransfer()
            }
            if (imported.isEmpty()) {
                onReady(ImportOutcome(0))
                return@launch
            }
            showIndex(start)
            deleteQueue = emptyList()
            pendingOriginals = imported.map { it.first }
            onReady(ImportOutcome(imported.size))
        }
    }

    /** The user chose "Remove originals": start removing the last import's originals from the device gallery. */
    fun removeOriginals(onReady: (RemovalOutcome) -> Unit) {
        val uris = pendingOriginals
        if (uris.isEmpty()) return
        pendingOriginals = emptyList()
        deleteQueue = emptyList()
        viewModelScope.launch {
            val removal = withContext(Dispatchers.IO) { planRemoval(uris) }
            deleteQueue = removal.rest
            onReady(RemovalOutcome(removal.consent, removal.alreadyRemoved))
        }
    }

    /** The user chose "Keep originals": nothing is deleted. */
    fun keepOriginals() {
        pendingOriginals = emptyList()
        deleteQueue = emptyList()
    }

    private fun takenMillis(resolver: android.content.ContentResolver, uri: Uri): Long {
        val stored = try {
            resolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
        if (stored > 0L) return stored
        return try {
            resolver.openInputStream(uri)?.use { captureMillis(it) } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun planRemoval(uris: List<Uri>): Removal {
        if (uris.isEmpty()) return Removal(null, true)
        val resolver = getApplication<Application>().contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val chunks = uris.chunked(DELETE_BATCH)
            return try {
                Removal(
                    MediaStore.createDeleteRequest(resolver, chunks.first()).intentSender,
                    false,
                    chunks.drop(1),
                )
            } catch (_: Exception) {
                Removal(null, false)
            }
        }
        var removed = true
        for (uri in uris) {
            try {
                if (resolver.delete(uri, null, null) <= 0) removed = false
            } catch (error: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29) {
                    recoverableSender(error)?.let { return Removal(it, false) }
                }
                removed = false
            }
        }
        return Removal(null, removed)
    }

    fun nextDeleteStep(): DeleteStep {
        val queue = deleteQueue
        if (queue.isEmpty()) return DeleteStep.Done
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            deleteQueue = emptyList()
            return DeleteStep.Failed
        }
        return try {
            val sender = MediaStore.createDeleteRequest(
                getApplication<Application>().contentResolver,
                queue.first(),
            ).intentSender
            deleteQueue = queue.drop(1)
            DeleteStep.Consent(sender)
        } catch (_: Exception) {
            deleteQueue = emptyList()
            DeleteStep.Failed
        }
    }

    fun clearDeleteQueue() {
        deleteQueue = emptyList()
    }

    fun writeBackup(uriString: String, password: String, onDone: (BackupOutcome) -> Unit) {
        val photos = all.value.toList()
        if (!openTransfer("Saving backup", photos.size)) return
        viewModelScope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val uri = Uri.parse(uriString)
                    var written = 0
                    var left = 0
                    var finished = false
                    try {
                        val out = (try { app.contentResolver.openOutputStream(uri, "wt") } catch (_: Exception) { null })
                            ?: app.contentResolver.openOutputStream(uri)
                            ?: throw BackupProblem("Couldn't open that location. Pick another place")
                        var sealed = false
                        out.use { stream ->
                            VaultBackup.begin(password, stream, photos.size).use { writer ->
                                photos.forEachIndexed { index, photo ->
                                    val bytes = plainBytes(photo)
                                    if (bytes != null) {
                                        writer.add(VaultBackup.Entry(photo.id, photo.name, photo.takenAt, photo.trashedAt, bytes))
                                        bytes.fill(0)
                                        written++
                                    } else {
                                        left++
                                    }
                                    markTransfer(index + 1, photos.size)
                                }
                                if (written > 0) {
                                    writer.finish()
                                    sealed = true
                                }
                            }
                        }
                        finished = sealed
                        when {
                            finished -> {
                                backupPrefs.recordBackup(System.currentTimeMillis(), written)
                                BackupOutcome(written, left, null, displayName(uri))
                            }
                            photos.isEmpty() -> BackupOutcome(0, 0, "Nothing to back up yet")
                            else -> BackupOutcome(0, left, "None of the photos could be read, so no backup was made")
                        }
                    } catch (problem: BackupProblem) {
                        BackupOutcome(0, left, problem.message)
                    } catch (error: Throwable) {
                        BackupOutcome(0, left, explainIo(error, writing = true))
                    } finally {
                        if (!finished) dropDocument(uri)
                    }
                }
            } finally {
                closeTransfer()
            }
            onDone(outcome)
        }
    }

    /** Reads the whole file with the password without importing anything. */
    fun verifyBackup(uriString: String, password: String, onDone: (VerifyOutcome) -> Unit) {
        if (!openTransfer("Checking backup", 0)) {
            onDone(VerifyOutcome(0, "Another task is still running. Try again when it finishes"))
            return
        }
        viewModelScope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val input = try {
                        app.contentResolver.openInputStream(Uri.parse(uriString))
                    } catch (error: Exception) {
                        return@withContext VerifyOutcome(0, explainIo(error, writing = false))
                    } ?: return@withContext VerifyOutcome(0, "Couldn't open that file. Pick it again")
                    var seen = 0
                    var ioNote: String? = null
                    val result = try {
                        input.use { stream ->
                            VaultBackup.verify(password, stream, onCount = { total ->
                                markTransfer(0, total, "Checking backup")
                            }) {
                                seen++
                                markTransfer(seen, maxOf(_transfer.value?.total ?: seen, seen), "Checking backup")
                            }
                        }
                    } catch (error: Throwable) {
                        ioNote = explainIo(error, writing = false)
                        VaultBackup.ReadResult.Damaged(seen)
                    }
                    when (result) {
                        is VaultBackup.ReadResult.Ok -> VerifyOutcome(result.added, null)
                        VaultBackup.ReadResult.BadPassword -> VerifyOutcome(0, "That password does not match this backup")
                        VaultBackup.ReadResult.NotBackup -> VerifyOutcome(0, "That file is not a Nyte backup")
                        is VaultBackup.ReadResult.Damaged -> VerifyOutcome(
                            result.added,
                            ioNote ?: if (result.added == 0) {
                                "This backup is damaged or incomplete and nothing could be read from it"
                            } else {
                                "This backup is incomplete, maybe the copy or upload didn't finish. Only ${result.added} photos could be read"
                            },
                        )
                    }
                }
            } finally {
                closeTransfer()
            }
            onDone(outcome)
        }
    }

    private fun displayName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    fun readBackup(uriString: String, password: String, onDone: (RestoreOutcome) -> Unit) {
        if (!openTransfer("Restoring backup", 0)) return
        viewModelScope.launch {
            val activeId = visible().getOrNull(activeIndexFor(rotation.value, visible().size))?.id
            val prepared = try {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val uri = Uri.parse(uriString)
                    val input = try {
                        app.contentResolver.openInputStream(uri)
                    } catch (error: Exception) {
                        return@withContext PreparedRestore(emptyList(), 0, VaultBackup.ReadResult.Damaged(0), explainIo(error, writing = false))
                    } ?: return@withContext PreparedRestore(emptyList(), 0, VaultBackup.ReadResult.Damaged(0), "Couldn't open that file. Pick it again")
                    val existing = all.value.map { it.id }.toMutableSet()
                    val added = mutableListOf<GalleryPhoto>()
                    var skipped = 0
                    var seen = 0
                    var failed = 0
                    var ioNote: String? = null
                    val result = try { input.use { stream ->
                        VaultBackup.restore(password, stream, onCount = { total ->
                            markTransfer(0, total, "Restoring backup")
                        }) { entry ->
                            seen++
                            if (entry.id in existing) {
                                skipped++
                            } else {
                                val photo = ByteArrayInputStream(entry.bytes).use { raw ->
                                    vault.importStream(entry.name, raw, entry.id, entry.takenAt)
                                }?.let { saved ->
                                    if (entry.trashedAt != null) saved.copy(trashedAt = entry.trashedAt) else saved
                                }
                                if (photo == null) failed++
                                if (photo != null) {
                                    existing += photo.id
                                    added += photo
                                    val updated = all.value + photo
                                    all.value = updated
                                    vault.save(updated)
                                }
                            }
                            markTransfer(seen, _transfer.value?.total ?: seen, "Restoring backup")
                        }
                    } } catch (error: Throwable) {
                        ioNote = explainIo(error, writing = false)
                        VaultBackup.ReadResult.Damaged(added.size)
                    }
                    val lowSpace = app.filesDir.usableSpace < 64L * 1024 * 1024
                    val failNote = if (failed > 0) {
                        if (lowSpace) "Phone storage is full. $failed photos couldn't be restored. Free up space and restore again"
                        else "$failed photos couldn't be saved. Restore again to retry them"
                    } else null
                    PreparedRestore(added, skipped, result, ioNote ?: failNote, failed)
                }
            } finally {
                closeTransfer()
            }
            if (prepared.photos.isNotEmpty()) {
                val after = all.value.filter { it.trashedAt == null }
                val index = after.indexOfFirst { it.id == activeId }.let { if (it >= 0) it else 0 }
                rotation.value = if (after.isEmpty()) 0f else index * (360f / after.size)
            }
            val outcome = when (val result = prepared.result) {
                is VaultBackup.ReadResult.Ok -> RestoreOutcome(prepared.photos.size, prepared.skipped, prepared.note, prepared.failed)
                VaultBackup.ReadResult.BadPassword -> RestoreOutcome(0, 0, "That password does not match this backup")
                VaultBackup.ReadResult.NotBackup -> RestoreOutcome(0, 0, prepared.note ?: "That file is not a Nyte backup")
                is VaultBackup.ReadResult.Damaged -> RestoreOutcome(
                    prepared.photos.size,
                    prepared.skipped,
                    prepared.note ?: if (prepared.photos.isEmpty() && prepared.skipped == 0) {
                        "This backup is damaged or incomplete and nothing could be read from it"
                    } else {
                        "This backup is incomplete, maybe the copy or upload didn't finish. Restored ${prepared.photos.size} photos before it stopped"
                    },
                    prepared.failed,
                )
            }
            onDone(outcome)
        }
    }

    private fun openTransfer(label: String, total: Int): Boolean {
        if (transferring) return false
        transferring = true
        markTransfer(0, total, label)
        return true
    }

    private fun markTransfer(done: Int, total: Int, label: String = _transfer.value?.label ?: "Nyte Gallery") {
        val progress = TransferProgress(label, done, total)
        _transfer.value = progress
        TransferNotices.show(getApplication(), progress)
    }

    private fun closeTransfer() {
        transferring = false
        _transfer.value = null
        TransferNotices.hide(getApplication())
    }

    @RequiresApi(29)
    private fun recoverableSender(error: SecurityException): IntentSender? {
        return (error as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
    }

    fun addPicked(items: List<Pair<String, String>>) {
        if (items.isEmpty()) return
        val start = visible().size
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                items.mapNotNull { (name, uriText) ->
                    val uri = Uri.parse(uriText)
                    val taken = takenMillis(resolver, uri).takeIf { it > 0L } ?: System.currentTimeMillis()
                    resolver.openInputStream(uri)?.use { input ->
                        vault.importStream(name, input, UUID.randomUUID().toString(), taken)
                    }
                }
            }
            if (added.isEmpty()) return@launch
            all.value = all.value + added
            vault.save(all.value)
            showIndex(start)
        }
    }

    fun openVault(photo: GalleryPhoto): ByteArray? = vault.read(photo)

    private fun update(id: String, change: (GalleryPhoto) -> GalleryPhoto) {
        all.value = all.value.map { if (it.id == id) change(it) else it }
        vault.save(all.value)
    }

    private fun visible() = all.value.filter { it.trashedAt == null }

    private fun animateTo(target: Float) {
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            val start = rotation.value
            val durationMs = 480L
            val started = System.nanoTime()
            while (isActive) {
                val t = ((System.nanoTime() - started) / 1_000_000f) / durationMs
                if (t >= 1f) {
                    rotation.value = target
                    break
                }
                val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                rotation.value = start + (target - start) * eased
                delay(16)
            }
        }
    }

    private fun activeIndexFor(angle: Float, count: Int): Int {
        if (count == 0) return 0
        val step = 360f / count
        return Math.floorMod((angle / step).roundToInt(), count)
    }

    private companion object {
        const val PIXELS_PER_CARD = 150f
        const val DELETE_BATCH = 20
    }
}

private data class Removal(
    val consent: IntentSender?,
    val alreadyRemoved: Boolean,
    val rest: List<List<Uri>> = emptyList(),
)
