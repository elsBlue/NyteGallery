package com.nytefall.nytegallery.data

import androidx.annotation.DrawableRes
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Calendar

data class GalleryPhoto(
    val id: String,
    val name: String,
    @DrawableRes val imageRes: Int = 0,
    val vaultFile: String? = null,
    val trashedAt: Long? = null,
    val takenAt: Long = 0L,
)

fun stamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        set(year, month - 1, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

fun formatTaken(millis: Long): String {
    if (millis <= 0L) return ""
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    val hour12 = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val minute = calendar.get(Calendar.MINUTE).toString().padStart(2, '0')
    val suffix = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "am" else "pm"
    val month = MONTHS[calendar.get(Calendar.MONTH)]
    return "${calendar.get(Calendar.DAY_OF_MONTH)} $month ${calendar.get(Calendar.YEAR)}, $hour12:$minute$suffix"
}

fun yearOf(millis: Long): String =
    if (millis <= 0L) "" else Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.YEAR).toString()

private val MONTHS = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun captureMillis(source: InputStream): Long {
    return try {
        val exif = ExifInterface(source)
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return 0L
        val parts = raw.split(" ", ":")
        if (parts.size < 6) return 0L
        Calendar.getInstance().apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), parts[3].toInt(), parts[4].toInt(), parts[5].toInt())
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } catch (_: Exception) {
        0L
    }
}

fun captureMillis(bytes: ByteArray): Long = captureMillis(ByteArrayInputStream(bytes))

