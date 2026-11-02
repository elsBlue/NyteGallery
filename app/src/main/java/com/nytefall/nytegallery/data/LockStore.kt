package com.nytefall.nytegallery.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Result of one unlock try with a password, PIN, pattern or recovery code. */
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
 * Recovery is offline: a random 16-character recovery code, shown once, stored only as a PBKDF2 hash (same scheme).
 * After it is used a new one is created, which invalidates it. Older installs used an email code; that email is dropped
 * when the first recovery code is created.
 *
 * Wrong tries (password, PIN, pattern and recovery code together) are counted in prefs and survive restarts.
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

    fun methods(): Set<String> = prefs.getStringSet(METHODS, emptySet()).orEmpty()

    /** Saves a new lock. Slow (PBKDF2 for each secret): call off the main thread. */
    fun save(password: String?, pin: String?, pattern: String?, finger: Boolean) {
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
            .remove(EMAIL)
            .remove(CODE)
            .remove(CODE_AT)
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

    /** Turns the app lock off: removes every saved unlock way and the recovery code. */
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

    /**
     * Checks a recovery code (case and dashes/spaces ignored), sharing the same wrong-try counter.
     * After a match the caller lets the user set a new lock and creates a new code, which replaces this one.
     * The old code stays valid until then, so closing the app halfway can never lock the user out.
     */
    @Synchronized
    fun checkRecovery(code: String): LockCheck = guarded {
        val saved = prefs.getString(RECOVERY, null)
        saved != null && verifySlow(saved, normalizeRecovery(code))
    }

    /** True while an old email recovery address is still stored (installs from before recovery codes). */
    fun hadEmailRecovery(): Boolean = prefs.contains(EMAIL)

    fun hasRecoveryCode(): Boolean = prefs.getString(RECOVERY, null) != null

    /** True when the lock is on but no recovery code has been created and confirmed yet (also old email installs). */
    fun needsRecoveryCode(): Boolean =
        configured() && (prefs.getString(RECOVERY, null) == null || !prefs.getBoolean(RECOVERY_SAVED, false))

    /**
     * Creates a new recovery code, replacing the old one, and drops any old recovery email.
     * Returns it formatted as XXXX-XXXX-XXXX-XXXX. Slow (PBKDF2): call off the main thread.
     */
    @Synchronized
    fun newRecoveryCode(): String {
        val random = SecureRandom()
        val raw = String(CharArray(RECOVERY_LENGTH) { RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)] })
        prefs.edit()
            .putString(RECOVERY, slowHash(raw))
            .putBoolean(RECOVERY_SAVED, false)
            .remove(EMAIL)
            .remove(CODE)
            .remove(CODE_AT)
            .commit()
        return raw.chunked(4).joinToString("-")
    }

    /** The user ticked "I have written down my recovery code". */
    fun markRecoverySaved() {
        prefs.edit().putBoolean(RECOVERY_SAVED, true).commit()
    }

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

    private fun normalizeRecovery(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

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

    companion object {
        const val PASSWORD = "password"
        const val PIN = "pin"
        const val PATTERN = "pattern"
        const val FINGER = "finger"
        private const val ON = "on"
        // Old email recovery keys, only removed now.
        private const val EMAIL = "email"
        private const val METHODS = "methods"
        private const val SALT = "salt"
        private const val CODE = "code"
        private const val CODE_AT = "code_at"
        private const val RECOVERY = "recovery"
        private const val RECOVERY_SAVED = "recovery_saved"
        private const val RECOVERY_LENGTH = 16

        // No 0/O, 1/I/L: easy to read and copy by hand. 31 symbols x 16 is about 79 bits.
        private const val RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
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
