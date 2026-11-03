package com.nytefall.nytegallery.data

import android.content.Context
import java.io.File

/** "Welcome" pages are shown once, on a fresh install only. Stays on this phone (allowBackup is off). */
class OnboardingPrefs(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("nyte_onboarding", Context.MODE_PRIVATE)

    fun isDone(): Boolean = prefs.getBoolean(DONE, false)

    fun markDone() {
        prefs.edit().putBoolean(DONE, true).apply()
    }

    /**
     * True only for a fresh install. An install that already opened its vault before (photos saved or samples
     * seeded) or has an app lock is treated as done, so existing users never see the welcome pages.
     * Call before the gallery loads, because loading the vault marks it as seeded.
     */
    fun shouldShow(): Boolean {
        if (isDone()) return false
        val vaultUsed = app.getSharedPreferences("nyte_gallery", Context.MODE_PRIVATE).getBoolean("seeded", false) ||
            File(File(app.filesDir, "vault"), "index.json").exists()
        if (vaultUsed || LockStore(app).configured()) {
            markDone()
            return false
        }
        return true
    }

    private companion object {
        const val DONE = "done"
    }
}
