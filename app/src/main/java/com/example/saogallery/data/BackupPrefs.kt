package com.example.saogallery.data

import android.content.Context

/** Small flags about backups. Stays on this phone (allowBackup is off). */
class BackupPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("nyte_backup", Context.MODE_PRIVATE)

    fun guideSeen(): Boolean = prefs.getBoolean(GUIDE_SEEN, false)

    fun markGuideSeen() {
        prefs.edit().putBoolean(GUIDE_SEEN, true).apply()
    }

    /** Last successful backup, kept for a future reminder. 0 = never. */
    fun lastBackupAt(): Long = prefs.getLong(LAST_AT, 0L)

    fun lastBackupPhotos(): Int = prefs.getInt(LAST_PHOTOS, 0)

    fun recordBackup(at: Long, photos: Int) {
        prefs.edit().putLong(LAST_AT, at).putInt(LAST_PHOTOS, photos).apply()
    }

    private companion object {
        const val GUIDE_SEEN = "guide_seen"
        const val LAST_AT = "last_backup_at"
        const val LAST_PHOTOS = "last_backup_photos"
    }
}
