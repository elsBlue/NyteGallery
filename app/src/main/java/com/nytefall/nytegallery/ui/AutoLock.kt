package com.nytefall.nytegallery.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Auto-lock: when a lock is set, the app locks after it leaves the foreground (a short grace avoids flicker on
 * quick activity restarts) and at once when the screen turns off. Leaving the app for a system screen the app
 * opened itself and waits on (permission dialog, delete consent, file picker, fingerprint prompt, email or browser
 * link) doesn't lock. Locking only shows the lock screen on top, so running imports or backups go on.
 */
internal object AutoLock {
    /** Time away before the app locks; covers rotation and quick activity restarts. */
    const val GRACE_MS = 3_000L

    /** Longer time away while a new recovery code is on screen, so it can be copied into another app. */
    const val RECOVERY_GRACE_MS = 60_000L

    // How long after an external launch an onStop still counts as caused by it.
    private const val EXTERNAL_WINDOW_MS = 5_000L

    @Volatile private var externalAt = 0L

    /** Call right before the app opens a system screen and expects to come back. */
    fun expectExternal() {
        externalAt = SystemClock.elapsedRealtime()
    }

    internal fun stopIsExternal(): Boolean =
        externalAt != 0L && SystemClock.elapsedRealtime() - externalAt <= EXTERNAL_WINDOW_MS

    internal fun clearExternal() {
        externalAt = 0L
    }
}

/**
 * Watches the activity's lifecycle and the screen. [shouldLock] says whether locking applies now (lock set, not
 * already locked, no recovery reset in progress); [onLock] shows the lock screen. [graceMs] is the time away
 * allowed before locking, read when the app leaves the foreground. Screen off always locks at once.
 */
@Composable
internal fun AutoLockEffect(shouldLock: () -> Boolean, onLock: () -> Unit, graceMs: () -> Long = { AutoLock.GRACE_MS }) {
    val host = LocalHostActivity.current ?: return
    val canLock by rememberUpdatedState(shouldLock)
    val lock by rememberUpdatedState(onLock)
    val grace by rememberUpdatedState(graceMs)
    val scope = rememberCoroutineScope()
    DisposableEffect(host) {
        var stoppedAt = 0L
        var stoppedGrace = AutoLock.GRACE_MS
        var stoppedForExternal = false
        var pending: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // A configuration change recreates the activity; the new one starts locked anyway.
                    if (host.isChangingConfigurations) return@LifecycleEventObserver
                    stoppedAt = SystemClock.elapsedRealtime()
                    stoppedGrace = grace()
                    stoppedForExternal = AutoLock.stopIsExternal()
                    if (!stoppedForExternal) {
                        pending?.cancel()
                        pending = scope.launch {
                            delay(stoppedGrace)
                            if (canLock()) lock()
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    pending?.cancel()
                    pending = null
                    val away = if (stoppedAt == 0L) 0L else SystemClock.elapsedRealtime() - stoppedAt
                    if (stoppedAt != 0L && !stoppedForExternal && away >= stoppedGrace && canLock()) lock()
                    stoppedAt = 0L
                    stoppedForExternal = false
                    AutoLock.clearExternal()
                }
                else -> Unit
            }
        }
        host.lifecycle.addObserver(observer)
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF && !AutoLock.stopIsExternal() && canLock()) lock()
            }
        }
        ContextCompat.registerReceiver(
            host,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            pending?.cancel()
            host.lifecycle.removeObserver(observer)
            runCatching { host.unregisterReceiver(screenOff) }
        }
    }
}
