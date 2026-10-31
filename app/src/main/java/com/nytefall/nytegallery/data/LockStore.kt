package com.nytefall.nytegallery.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

class LockStore(context: Context) {
    private val prefs = context.getSharedPreferences("nyte_lock", Context.MODE_PRIVATE)

    fun configured(): Boolean = prefs.getBoolean(ON, false)

    fun email(): String = prefs.getString(EMAIL, "").orEmpty()

    fun methods(): Set<String> = prefs.getStringSet(METHODS, emptySet()).orEmpty()

    fun save(email: String, password: String?, pin: String?, pattern: String?, finger: Boolean) {
        val salt = prefs.getString(SALT, null) ?: newSalt().also { prefs.edit().putString(SALT, it).apply() }
        val methods = linkedSetOf<String>()
        val edit = prefs.edit()
        if (!password.isNullOrBlank()) {
            edit.putString(PASSWORD, hash(salt, password))
            methods += PASSWORD
        } else edit.remove(PASSWORD)
        if (!pin.isNullOrBlank()) {
            edit.putString(PIN, hash(salt, pin))
            methods += PIN
        } else edit.remove(PIN)
        if (!pattern.isNullOrBlank()) {
            edit.putString(PATTERN, hash(salt, pattern))
            methods += PATTERN
        } else edit.remove(PATTERN)
        if (finger) methods += FINGER
        edit.putStringSet(METHODS, methods)
            .putString(EMAIL, email.trim())
            .putBoolean(ON, true)
            .apply()
    }

    fun matches(method: String, value: String): Boolean {
        val salt = prefs.getString(SALT, null) ?: return false
        val saved = prefs.getString(method, null) ?: return false
        return saved == hash(salt, value)
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

    fun matchesCode(code: String): Boolean {
        val at = prefs.getLong(CODE_AT, 0L)
        if (at == 0L || System.currentTimeMillis() - at > TEN_MINUTES) return false
        val salt = prefs.getString(SALT, null) ?: return false
        val saved = prefs.getString(CODE, null) ?: return false
        return saved == hash(salt, code.trim())
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
