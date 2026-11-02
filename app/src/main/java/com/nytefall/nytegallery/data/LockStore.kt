package com.nytefall.nytegallery.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Result of one unlock try with a password, PIN, pattern or email code. */
sealed class LockCheck {
    object Ok : LockCheck()
    /** Wrong value. [waitMs] is the cooldown that starts now (0 if none yet). */
    data class Wrong(val waitMs: Long) : LockCheck()
    /** A cooldown is running, the value was not checked. */
    data class Wait(val waitMs: Long) : LockCheck()
}

/**
 * App lock storage.
 *
 * Secrets (password, PIN, pattern) are stored as `pbkdf2$<iterations>$<salt hex>$<hash hex>`:
 * PBKDF2-HMAC-SHA256, a fresh 16-byte random salt per secret, 256-bit output, compared in constant time.
 * Older installs stored `SHA-256("<install salt>:<value>")` as 64 hex chars. Those still verify, and the first
 * successful check re-saves that secret in the PBKDF2 format, so nobody is locked out by the upgrade.
 *
 * Wrong tries (password, PIN, pattern and email code together) are counted in prefs and survive restarts.
 * From the 5th wrong try in a row a cooldown starts (see [cooldownFor]). The cooldown end is stored both as
 * elapsedRealtime (monotonic, not affected by changing the clock) with the boot count, and as wall-clock time.
 * In the same boot only elapsedRealtime is used. After a reboot the wall clock is used, clamped so moving
 * the clock back can only make the wait longer, never longer than the full cooldown, and a clock set to before
 * the cooldown started restarts the full cooldown. A success resets the counter.
 *
 * [check] and [save] run PBKDF2 and must be called off the main thread.
 */
class LockStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("nyte_lock", Context.MODE_PRIVATE)

    fun configured(): Boolean = prefs.getBoolean(ON, false)

    fun email(): String = prefs.getString(EMAIL, "").orEmpty()

    fun methods(): Set<String> = prefs.getStringSet(METHODS, emptySet()).orEmpty()

    /** Saves a new lock. Slow (PBKDF2 for each secret): call off the main thread. */
    fun save(email: String, password: String?, pin: String?, pattern: String?, finger: Boolean) {
        // The install salt is still used for the email code hash.
        if (prefs.getString(SALT, null) == null) prefs.edit().putString(SALT, newSalt()).commit()
        val methods = linkedSetOf<String>()
        val edit = prefs.edit()
        if (!password.isNullOrBlank()) {
            edit.putString(PASSWORD, slowHash(password))
            methods += PASSWORD
        } else edit.remove(PASSWORD)
        if (!pin.isNullOrBlank()) {
            edit.putString(PIN, slowHash(pin))
            methods += PIN
        } else edit.remove(PIN)
        if (!pattern.isNullOrBlank()) {
            edit.putString(PATTERN, slowHash(pattern))
            methods += PATTERN
        } else edit.remove(PATTERN)
        if (finger) methods += FINGER
        edit.putStringSet(METHODS, methods)
            .putString(EMAIL, email.trim())
            .putBoolean(ON, true)
            .remove(FAILS)
            .remove(WAIT_MS)
            .remove(UNTIL_ELAPSED)
            .remove(START_ELAPSED)
            .remove(UNTIL_WALL)
            .remove(START_WALL)
            .remove(BOOT)
            .commit()
    }

    /** Changes only the recovery email. Any code sent to the old address stops working. */
    fun updateEmail(email: String) {
        prefs.edit()
            .putString(EMAIL, email.trim())
            .remove(CODE)
            .remove(CODE_AT)
            .apply()
    }

    /** Turns the app lock off: removes every saved unlock way, the email and any pending code. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * Checks a password, PIN or pattern, counting the try. Slow: call off the main thread.
     * The try is counted (and any cooldown armed) before the value is checked, so closing the app
     * during the check does not skip it. A match clears the counter.
     */
    @Synchronized
    fun check(method: String, value: String): LockCheck = guarded { matches(method, value) }

    /** Checks an email code, sharing the same wrong-try counter. */
    @Synchronized
    fun checkCode(code: String): LockCheck = guarded { matchesCode(code) }

    /** Fingerprint passed (the system prompt limits fingerprint tries itself). Clears the counter. */
    @Synchronized
    fun noteSuccess() = resetTries()

    /** Milliseconds left in the current cooldown, 0 if none. */
    @Synchronized
    fun waitRemainingMs(): Long {
        val until = prefs.getLong(UNTIL_ELAPSED, 0L)
        if (until == 0L && prefs.getLong(UNTIL_WALL, 0L) == 0L) return 0L
        val full = prefs.getLong(WAIT_MS, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()
        val sameBoot = prefs.getInt(BOOT, -2) == bootCount() && nowElapsed >= prefs.getLong(START_ELAPSED, 0L)
        if (sameBoot) return (until - nowElapsed).coerceIn(0L, full)
        // Rebooted: elapsedRealtime restarted. Fall back to the wall clock, clamped, then re-anchor to this boot.
        val nowWall = System.currentTimeMillis()
        val startWall = prefs.getLong(START_WALL, 0L)
        val left = if (nowWall < startWall) full else (prefs.getLong(UNTIL_WALL, 0L) - nowWall).coerceIn(0L, full)
        anchor(left, full)
        return left
    }

    private inline fun guarded(test: () -> Boolean): LockCheck {
        val left = waitRemainingMs()
        if (left > 0L) return LockCheck.Wait(left)
        val fails = prefs.getInt(FAILS, 0) + 1
        val wait = cooldownFor(fails)
        prefs.edit().putInt(FAILS, fails).commit()
        if (wait > 0L) anchor(wait, wait)
        if (test()) {
            resetTries()
            return LockCheck.Ok
        }
        return LockCheck.Wrong(wait)
    }

    private fun anchor(left: Long, full: Long) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        prefs.edit()
            .putLong(WAIT_MS, full)
            .putLong(UNTIL_ELAPSED, nowElapsed + left)
            .putLong(START_ELAPSED, nowElapsed)
            .putLong(UNTIL_WALL, nowWall + left)
            .putLong(START_WALL, nowWall)
            .putInt(BOOT, bootCount())
            .commit()
    }

    private fun resetTries() {
        prefs.edit()
            .remove(FAILS)
            .remove(WAIT_MS)
            .remove(UNTIL_ELAPSED)
            .remove(START_ELAPSED)
            .remove(UNTIL_WALL)
            .remove(START_WALL)
            .remove(BOOT)
            .commit()
    }

    private fun bootCount(): Int =
        try {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }

    private fun matches(method: String, value: String): Boolean {
        val saved = prefs.getString(method, null) ?: return false
        if (saved.startsWith(PBKDF2_PREFIX)) return verifySlow(saved, value)
        // Old format: SHA-256("<install salt>:<value>"). Upgrade on the first match.
        val salt = prefs.getString(SALT, null) ?: return false
        val ok = sameText(saved, hash(salt, value))
        if (ok) prefs.edit().putString(method, slowHash(value)).commit()
        return ok
    }

    fun issueCode(): String {
        val code = (100000 + SecureRandom().nextInt(900000)).toString()
        val salt = prefs.getString(SALT, null) ?: return code
        prefs.edit()
            .putString(CODE, hash(salt, code))
            .putLong(CODE_AT, System.currentTimeMillis())
            .apply()
        return code
    }

    private fun matchesCode(code: String): Boolean {
        val at = prefs.getLong(CODE_AT, 0L)
        if (at == 0L || System.currentTimeMillis() - at > TEN_MINUTES) return false
        val salt = prefs.getString(SALT, null) ?: return false
        val saved = prefs.getString(CODE, null) ?: return false
        return sameText(saved, hash(salt, code.trim()))
    }

    private fun slowHash(value: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PBKDF2_PREFIX + PBKDF2_ITERATIONS + "$" + salt.toHex() + "$" + pbkdf2(value, salt, PBKDF2_ITERATIONS).toHex()
    }

    private fun verifySlow(saved: String, value: String): Boolean {
        val parts = saved.split('$')
        if (parts.size != 4) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations <= 0) return false
        val salt = parts[2].fromHex() ?: return false
        val expected = parts[3].fromHex() ?: return false
        return MessageDigest.isEqual(expected, pbkdf2(value, salt, iterations))
    }

    private fun pbkdf2(value: String, salt: ByteArray, iterations: Int): ByteArray {
        val chars = value.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    private fun sameText(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun hash(salt: String, value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$salt:$value".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PASSWORD = "password"
        const val PIN = "pin"
        const val PATTERN = "pattern"
        const val FINGER = "finger"
        private const val ON = "on"
        private const val EMAIL = "email"
        private const val METHODS = "methods"
        private const val SALT = "salt"
        private const val CODE = "code"
        private const val CODE_AT = "code_at"
        private const val TEN_MINUTES = 10L * 60L * 1000L
        private const val FAILS = "fails"
        private const val WAIT_MS = "wait_ms"
        private const val UNTIL_ELAPSED = "wait_until_elapsed"
        private const val START_ELAPSED = "wait_start_elapsed"
        private const val UNTIL_WALL = "wait_until_wall"
        private const val START_WALL = "wait_start_wall"
        private const val BOOT = "wait_boot"
        private const val PBKDF2_PREFIX = "pbkdf2$"

        /** PBKDF2-HMAC-SHA256 iterations for new lock hashes. Stored in each hash, so it can be raised later. */
        const val PBKDF2_ITERATIONS = 210_000

        /** Free tries, then 30 s, 1 min, 5 min, 15 min, 30 min, and 1 hour for every wrong try after that. */
        fun cooldownFor(fails: Int): Long = when {
            fails < 5 -> 0L
            fails == 5 -> 30_000L
            fails == 6 -> 60_000L
            fails == 7 -> 5 * 60_000L
            fails == 8 -> 15 * 60_000L
            fails == 9 -> 30 * 60_000L
            else -> 60 * 60_000L
        }
    }
}

fun sendUnlockCode(email: String, code: String): Boolean {
    val connection = (URL("https://formsubmit.co/ajax/$email").openConnection() as HttpURLConnection)
    return try {
        val body = """{"name":"Nyte Gallery","message":"Your Nyte Gallery unlock code is $code. It expires in 10 minutes.","_subject":"Nyte Gallery unlock code","_captcha":"false"}"""
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        connection.responseCode in 200..299
    } catch (_: Exception) {
        false
    } finally {
        connection.disconnect()
    }
}
