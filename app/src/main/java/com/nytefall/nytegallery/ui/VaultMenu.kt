package com.nytefall.nytegallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MenuPink = Color(0xFFFF4D6A)

@Composable
fun MenuGlyph() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val radius = 1.7.dp.toPx()
        val x = size.width / 2f
        drawCircle(color, radius, Offset(x, size.height * 0.22f))
        drawCircle(color, radius, Offset(x, size.height * 0.50f))
        drawCircle(color, radius, Offset(x, size.height * 0.78f))
    }
}

@Composable
fun MenuSidebar(
    light: Boolean,
    onClose: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF171920)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.46f)).clickable(onClick = onClose)) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(292.dp)
                .background(panel)
                .windowInsetsPadding(WindowInsets.systemBars)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Menu", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            Spacer(Modifier.height(22.dp))
            MenuRow("Backup", "Save an encrypted copy you can keep", ink, onBackup)
            Spacer(Modifier.height(8.dp))
            MenuRow("Restore backup", "Bring photos back from a backup file", ink, onRestore)
            Spacer(Modifier.weight(1f))
            Text(
                "The phone key cannot move to a new device. A backup uses a password only you know.",
                color = ink.copy(alpha = 0.55f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun MenuRow(title: String, body: String, ink: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = ink.copy(alpha = 0.62f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

const val RESTORE_STEPS = "To restore later: Menu > Restore backup > choose the file > enter the password."

@Composable
fun BackupPage(
    light: Boolean,
    busy: Boolean,
    error: String?,
    showGuide: Boolean,
    onGuideDone: () -> Unit,
    onClose: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var understood by remember { mutableStateOf(false) }
    val mismatch = confirm.isNotEmpty() && password != confirm
    val short = password.isNotEmpty() && password.length < 8
    val ready = password.length >= 8 && password == confirm && understood && !busy
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(panel).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Backup", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(enabled = !busy, onClick = onClose).padding(8.dp))
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    "Photos are unlocked on this phone, then sealed again with this password. Nyte cannot open the file without it. Save it somewhere else, such as Drive, a computer, or a memory card. Automatic phone backup stays off.",
                    color = ink.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Text(
                    RESTORE_STEPS,
                    color = ink.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text("New backup password", color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
                SecretField(password, field, ink, "At least 8 characters") { password = it }
                if (short) {
                    Text("Use at least 8 characters. A longer phrase is safer", color = ink.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Confirm password", color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
                SecretField(confirm, field, ink, "Repeat the password") { confirm = it }
                if (mismatch) {
                    Text("Those passwords do not match", color = MenuPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !busy) { understood = !understood }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TickBox(understood, ink)
                    Text(
                        "I understand Nyte cannot recover this password",
                        color = ink.copy(alpha = 0.82f),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                if (!error.isNullOrBlank()) {
                    Text(error, color = MenuPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            PrimaryButton(if (busy) "Encrypting…" else "Create backup", ready, busy, ink) { onCreate(password) }
        }
        if (showGuide) BackupGuide(light, onGuideDone)
    }
}

/** First visit to Backup: the three things people most often miss. */
@Composable
private fun BackupGuide(light: Boolean, onDone: () -> Unit) {
    val panel = if (light) Color(0xFFF7F3EE) else Color(0xFF1C1E26)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("BEFORE YOU BACK UP", color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            GuidePoint("1", "Nyte cannot recover the backup password. If it is lost, the backup cannot be opened.", ink)
            GuidePoint("2", "Save the backup file outside this phone, such as Drive, a computer, or a memory card.", ink)
            GuidePoint("3", "To restore later: Menu > Restore backup > choose the file > enter the password.", ink)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MenuPink)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Got it", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun GuidePoint(number: String, body: String, ink: Color) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(MenuPink.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, color = MenuPink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(body, color = ink.copy(alpha = 0.82f), fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Shown after a backup is written. [canCheck] is false once the in-memory password is gone. */
@Composable
fun BackupSavedPage(
    light: Boolean,
    fileName: String?,
    saved: Int,
    left: Int,
    checking: Boolean,
    canCheck: Boolean,
    checkNote: String?,
    checkFailed: Boolean,
    onCheck: () -> Unit,
    onDone: () -> Unit,
) {
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val card = if (light) Color.White else Color(0xFF1C1E26)
    Column(Modifier.fillMaxSize().background(panel).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Backup saved", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(card)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("File", color = ink.copy(alpha = 0.55f), fontSize = 12.sp)
                Text(fileName ?: "Backup file", color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(10.dp))
                Text("Photos saved", color = ink.copy(alpha = 0.55f), fontSize = 12.sp)
                Text("$saved", color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                if (left > 0) {
                    Text(
                        "$left photos couldn't be read and were left out",
                        color = MenuPink,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Keep the password somewhere safe. Nyte cannot recover it. Move this file off the phone if it is saved here.",
                color = ink.copy(alpha = 0.72f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Text(RESTORE_STEPS, color = ink.copy(alpha = 0.55f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
            if (!checkNote.isNullOrBlank()) {
                Text(
                    checkNote,
                    color = if (checkFailed) MenuPink else ink,
                    fontSize = 14.sp,
                    fontWeight = if (checkFailed) FontWeight.Normal else FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp),
                )
            } else if (!canCheck) {
                Text(
                    "To check this file later: Menu > Restore backup > Check a backup file.",
                    color = ink.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (canCheck || checking) {
            SecondaryButton(if (checking) "Checking…" else "Check backup", enabled = !checking, ink = ink, onClick = onCheck)
            Spacer(Modifier.height(10.dp))
        }
        PrimaryButton("Done", ready = !checking, busy = false, ink = ink, onClick = onDone)
    }
}

@Composable
fun RestoreBackupPage(
    light: Boolean,
    busy: Boolean,
    error: String?,
    note: String?,
    checking: Boolean,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
    onCheck: (String) -> Unit,
) {
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    var password by remember { mutableStateOf("") }
    val ready = password.isNotEmpty() && !busy
    Column(Modifier.fillMaxSize().background(panel).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Restore backup", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(enabled = !busy, onClick = onClose).padding(8.dp))
        }
        Text(
            "Choose the backup file and the password you set when it was created. Photos already in this gallery are left as they are.",
            color = ink.copy(alpha = 0.72f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text("Backup password", color = ink.copy(alpha = 0.7f), fontSize = 13.sp)
        SecretField(password, field, ink, "Password for this file") { password = it }
        if (!error.isNullOrBlank()) {
            Text(error, color = MenuPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        } else if (!note.isNullOrBlank()) {
            Text(note, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Check a backup file",
            color = if (ready) ink.copy(alpha = 0.75f) else ink.copy(alpha = 0.3f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = ready) { onCheck(password) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .padding(bottom = 18.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (ready) MenuPink else ink.copy(alpha = 0.12f))
                .clickable(enabled = ready) { onPick(password) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (busy) (if (checking) "Checking…" else "Restoring…") else "Choose backup file",
                color = if (ready || busy) Color.White else ink.copy(alpha = 0.4f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, ready: Boolean, busy: Boolean, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(bottom = 18.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (ready) MenuPink else ink.copy(alpha = 0.12f))
            .clickable(enabled = ready, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (ready || busy) Color.White else ink.copy(alpha = 0.4f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryButton(label: String, enabled: Boolean, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, ink.copy(alpha = if (enabled) 0.35f else 0.15f), RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink.copy(alpha = if (enabled) 0.9f else 0.45f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TickBox(checked: Boolean, ink: Color) {
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) MenuPink else Color.Transparent)
            .border(1.5.dp, if (checked) MenuPink else ink.copy(alpha = 0.45f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(14.dp)) {
                val tick = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.52f)
                    lineTo(size.width * 0.40f, size.height * 0.80f)
                    lineTo(size.width * 0.88f, size.height * 0.22f)
                }
                drawPath(tick, Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun SecretField(value: String, field: Color, ink: Color, placeholder: String, onChange: (String) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    Box(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(field)
            .padding(start = 16.dp, end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, color = ink.copy(alpha = 0.32f), fontSize = 15.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = ink, fontSize = 16.sp),
            cursorBrush = SolidColor(MenuPink),
            visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
        )
        Box(
            Modifier.align(Alignment.CenterEnd).size(36.dp).clip(CircleShape).clickable { shown = !shown },
            contentAlignment = Alignment.Center,
        ) {
            SecretEye(ink.copy(alpha = 0.72f), concealed = !shown)
        }
    }
}

@Composable
private fun SecretEye(color: Color, concealed: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        val eye = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            quadraticTo(size.width * 0.5f, size.height * 0.12f, size.width * 0.92f, size.height * 0.5f)
            quadraticTo(size.width * 0.5f, size.height * 0.88f, size.width * 0.08f, size.height * 0.5f)
            close()
        }
        drawPath(eye, color, style = stroke)
        drawCircle(color, radius = size.width * 0.11f, center = center)
        if (concealed) {
            drawLine(color, Offset(size.width * 0.2f, size.height * 0.78f), Offset(size.width * 0.8f, size.height * 0.22f), stroke.width, StrokeCap.Round)
        }
    }
}
