package com.nytefall.nytegallery.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nytefall.nytegallery.BuildConfig
import com.nytefall.nytegallery.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SettingsPink = Color(0xFFFF4D6A)

const val CONTACT_EMAIL = "nytefall.interactive@gmail.com"
const val PRIVACY_POLICY_URL = "https://github.com/elsBlue/NyteGallery/blob/main/docs/policy/PRIVACY_POLICY.md"
const val TERMS_URL = "https://github.com/elsBlue/NyteGallery/blob/main/docs/policy/TERMS.md"

@Composable
fun SettingsPage(
    light: Boolean,
    lockOn: Boolean,
    lastBackupAt: Long,
    lastBackupPhotos: Int,
    onClose: () -> Unit,
    onRecycleBin: () -> Unit,
    onAppLock: () -> Unit,
    onChangeLock: () -> Unit,
    onRecoveryCode: () -> Unit,
    onBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onCheckBackup: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val card = if (light) Color.White else Color(0xFF1C1E26)
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var confirmingClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(panel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .wideCap()
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 22.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SectionLabel("General", ink)
                SettingsCard(card) {
                    SettingsRow("Recycle bin", "Photos in Trash are deleted after 30 days", ink, onRecycleBin)
                    RowDivider(ink)
                    SettingsRow(
                        "Clear cache",
                        "Removes temporary files only. Photos, Trash and backups stay",
                        ink,
                        enabled = !clearing,
                        onClick = {
                            scope.launch {
                                cacheBytes = withContext(Dispatchers.IO) { cacheSize(context) }
                                confirmingClear = true
                            }
                        },
                    )
                }

                SectionLabel("Security", ink)
                SettingsCard(card) {
                    if (lockOn) {
                        SettingsRow("Change lock", "On · Change how Nyte Gallery unlocks", ink, onChangeLock)
                    } else {
                        SettingsRow("App lock", "Off · Tap to set up a lock", ink, onAppLock)
                    }
                    RowDivider(ink)
                    if (lockOn) {
                        SettingsRow("Recovery code", "Create a new code if yours is lost", ink, onRecoveryCode)
                    } else {
                        SettingsRow("Recovery code", "Created when you set up the lock", ink, onAppLock)
                    }
                }

                SectionLabel("Backup", ink)
                SettingsCard(card) {
                    Text(
                        lastBackupLine(lastBackupAt, lastBackupPhotos),
                        color = ink.copy(alpha = 0.62f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    RowDivider(ink)
                    SettingsRow("Back up", "Save an encrypted copy you can keep", ink, onBackup)
                    RowDivider(ink)
                    SettingsRow("Restore backup", "Bring photos back from a backup file", ink, onRestoreBackup)
                    RowDivider(ink)
                    SettingsRow("Check a backup file", "Make sure a file opens, without importing", ink, onCheckBackup)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (confirmingClear) {
            ConfirmOverlay(
                light = light,
                title = "CLEAR CACHE",
                body = if (cacheBytes > 0) {
                    "Remove ${formatBytes(cacheBytes)} of temporary files? Your photos, Trash and backups are not touched."
                } else {
                    "There are no temporary files to clear right now. Your photos, Trash and backups are not touched."
                },
                confirm = if (cacheBytes > 0) "Clear" else "OK",
                onDismiss = { confirmingClear = false },
                onConfirm = {
                    confirmingClear = false
                    if (cacheBytes > 0) {
                        clearing = true
                        scope.launch {
                            val freed = withContext(Dispatchers.IO) { clearCache(context) }
                            clearing = false
                            Toast.makeText(context, "Cleared ${formatBytes(freed)}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun AboutPage(light: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val card = if (light) Color.White else Color(0xFF1C1E26)
    Box(
        Modifier
            .fillMaxSize()
            .background(panel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .wideCap()
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 22.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("About", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                Image(
                    painter = painterResource(R.drawable.ic_launcher_art),
                    contentDescription = "Nyte Gallery",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)),
                )
                Spacer(Modifier.height(14.dp))
                Text("Nyte Gallery", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    color = ink.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "by Nytefall Interactive",
                    color = ink.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.height(22.dp))
                SettingsCard(card) {
                    SettingsRow("Send feedback", CONTACT_EMAIL, ink, { sendFeedback(context) })
                    RowDivider(ink)
                    SettingsRow("Privacy policy", "Opens in your browser", ink, { openLink(context, PRIVACY_POLICY_URL) })
                    RowDivider(ink)
                    SettingsRow("Terms of service", "Opens in your browser", ink, { openLink(context, TERMS_URL) })
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, ink: Color) {
    Text(
        text.uppercase(Locale.US),
        color = ink.copy(alpha = 0.55f),
        fontSize = 12.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(card: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(card),
    ) { content() }
}

@Composable
private fun RowDivider(ink: Color) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(ink.copy(alpha = 0.08f)))
}

@Composable
private fun SettingsRow(title: String, body: String, ink: Color, onClick: (() -> Unit)?, enabled: Boolean = true) {
    val base = Modifier.fillMaxWidth()
    val clickable = if (onClick != null) base.clickable(enabled = enabled, onClick = onClick) else base
    Row(clickable.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ink.copy(alpha = if (enabled) 1f else 0.45f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = ink.copy(alpha = 0.62f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (onClick != null) {
            Text("›", color = ink.copy(alpha = 0.4f), fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ConfirmOverlay(
    light: Boolean,
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val panel = if (light) Color(0xFFF7F3EE) else Color(0xFF1C1E26)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.56f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .wideCap()
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(body, color = ink.copy(alpha = 0.82f), fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton("Cancel", ink.copy(alpha = 0.12f), ink, Modifier.weight(1f), onDismiss)
                DialogButton(confirm, SettingsPink, Color.White, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

@Composable
private fun DialogButton(label: String, background: Color, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

private fun lastBackupLine(at: Long, photos: Int): String {
    if (at <= 0L) return "Last backup: Never"
    val date = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(Date(at))
    val count = if (photos == 1) "1 photo" else "$photos photos"
    return "Last backup: $date, $count"
}

/** Temporary files only: the app cache folders. The vault lives in files/vault and is never touched here. */
private fun cacheDirs(context: Context): List<File> = listOfNotNull(context.cacheDir, context.externalCacheDir)

private fun cacheSize(context: Context): Long =
    cacheDirs(context).sumOf { dir -> dir.listFiles().orEmpty().sumOf { sizeOf(it) } }

private fun sizeOf(file: File): Long =
    if (file.isDirectory) file.listFiles().orEmpty().sumOf { sizeOf(it) } else file.length()

private fun clearCache(context: Context): Long {
    var freed = 0L
    cacheDirs(context).forEach { dir ->
        dir.listFiles().orEmpty().forEach { child ->
            val size = sizeOf(child)
            if (child.deleteRecursively()) freed += size
        }
    }
    return freed
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun sendFeedback(context: Context) {
    val subject = "Nyte Gallery feedback (version ${BuildConfig.VERSION_NAME})"
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL?subject=" + Uri.encode(subject))).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    try {
        AutoLock.expectExternal()
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No email app found. Write to $CONTACT_EMAIL", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "No email app found. Write to $CONTACT_EMAIL", Toast.LENGTH_LONG).show()
    }
}

private fun openLink(context: Context, url: String) {
    try {
        AutoLock.expectExternal()
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found to open this page", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "No browser found to open this page", Toast.LENGTH_LONG).show()
    }
}
