package com.nytefall.nytegallery.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nytefall.nytegallery.R
import com.nytefall.nytegallery.data.LockStore
import kotlin.math.min

private val LockPink = Color(0xFFFF4D6A)
private val LockGreen = Color(0xFF2F9B68)

fun fingerprintAvailable(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    val weak = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    if (weak == BiometricManager.BIOMETRIC_SUCCESS || weak == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) return true
    val strong = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    return strong == BiometricManager.BIOMETRIC_SUCCESS || strong == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
}

val LocalHostActivity = staticCompositionLocalOf<FragmentActivity?> { null }

private fun findActivity(context: Context): FragmentActivity? {
    var current: Context? = context
    val seen = HashSet<Context>()
    while (current is ContextWrapper && seen.add(current)) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

fun promptFingerprint(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val enrolled = BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    if (enrolled == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
        val strong = BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (strong == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return onError("Add a fingerprint in system settings first")
        }
    }
    try {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError(errString.toString())
                    }
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use the fingerprint saved on this phone")
                .setNegativeButtonText("Cancel")
                .build(),
        )
    } catch (error: Exception) {
        onError(error.message ?: "Couldn't open the fingerprint prompt")
    }
}

@Composable
fun LockConfirm(light: Boolean, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit, title: String = "LOCK APP") {
    val panel = if (light) Color(0xFFF7F3EE) else Color(0xFF1C1E26)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.56f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
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
                LockButton("Close", ink.copy(alpha = 0.12f), ink, Modifier.weight(1f), onDismiss)
                LockButton(confirm, LockPink, Color.White, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

@Composable
fun LockSetupPage(
    light: Boolean,
    onClose: () -> Unit,
    onSave: (email: String, password: String?, pin: String?, pattern: String?, finger: Boolean) -> Unit,
    title: String = "LOCK",
    intro: String = "Set how Nyte Gallery locks. Choose at least two ways to get back in: password, PIN, pattern, or fingerprint. Add an email so a forgotten method can be recovered with a code.",
    saveLabel: String = "Lock app",
    fixedEmail: String? = null,
    onTurnOff: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = LocalHostActivity.current ?: findActivity(context)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    val fingerOk = remember { fingerprintAvailable(context) }
    var email by remember { mutableStateOf(fixedEmail ?: "") }
    var password by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf<String?>(null) }
    var pattern by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val readyCount = listOf(password, pin, pattern).count { it != null } + if (finger) 1 else 0
    val emailOk = email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
    Column(
        Modifier
            .fillMaxSize()
            .background(panel)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            intro,
            color = ink.copy(alpha = 0.8f),
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        if (fixedEmail == null) {
            Spacer(Modifier.height(16.dp))
            Text("Recovery email", color = ink.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            LockField(email, field, ink, KeyboardType.Email, false, "name@email.com") { email = it }
        }
        Spacer(Modifier.height(16.dp))
        MethodRow("Password", password != null, ink, field) { open = if (open == LockStore.PASSWORD) null else LockStore.PASSWORD }
        if (open == LockStore.PASSWORD) SecretPair(ink, field, KeyboardType.Password, "New password", "At least 6 characters", "Confirm password", "Type it again") { first, second ->
            error = when {
                first.length < 6 -> "Password needs at least 6 characters"
                first != second -> "Those passwords do not match"
                else -> {
                    password = first
                    open = null
                    ""
                }
            }
        }
        MethodRow("PIN", pin != null, ink, field) { open = if (open == LockStore.PIN) null else LockStore.PIN }
        if (open == LockStore.PIN) SecretPair(ink, field, KeyboardType.NumberPassword, "New PIN", "4 to 6 digits", "Confirm PIN", "Type the same PIN again") { first, second ->
            error = when {
                !first.matches(Regex("\\d{4,6}")) -> "PIN must be 4 to 6 digits"
                first != second -> "Those PINs do not match"
                else -> {
                    pin = first
                    open = null
                    ""
                }
            }
        }
        MethodRow("Pattern", pattern != null, ink, field) { open = if (open == LockStore.PATTERN) null else LockStore.PATTERN }
        if (open == LockStore.PATTERN) {
            PatternConfirm(ink) { value ->
                pattern = value
                open = null
                error = ""
            }
        }
        if (fingerOk) {
            MethodRow("Fingerprint", finger, ink, field) {
                val host = activity
                if (host == null) error = "Couldn't open the fingerprint prompt"
                else promptFingerprint(host, "Confirm fingerprint", { finger = true }, { error = it })
            }
        } else {
            Text("Fingerprint is not available on this phone.", color = ink.copy(alpha = 0.45f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (error.isNotEmpty()) {
            Text(error, color = LockPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Text("$readyCount of 2 ways set", color = ink.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        val canSave = emailOk && readyCount >= 2
        LockButton(saveLabel, if (canSave) LockPink else ink.copy(alpha = 0.15f), if (canSave) Color.White else ink.copy(alpha = 0.4f), Modifier.fillMaxWidth()) {
            if (canSave) onSave(email.trim(), password, pin, pattern, finger)
            else error = if (!emailOk) "Enter a valid email" else "Set at least two unlock ways"
        }
        if (onTurnOff != null) {
            Text(
                "Turn off app lock",
                color = LockPink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 18.dp)
                    .clickable(onClick = onTurnOff)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
fun LockGate(
    light: Boolean,
    store: LockStore,
    onUnlocked: () -> Unit,
    onSendCode: (onResult: (Boolean) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalHostActivity.current ?: findActivity(context)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    val methods = store.methods().toList()
    var method by remember { mutableStateOf(methods.firstOrNull { it != LockStore.FINGER } ?: methods.first()) }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var emailMode by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(panel).windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_art),
            contentDescription = "Nyte Gallery",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)),
        )
        Spacer(Modifier.height(14.dp))
        Text("Nyte Gallery", color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Text(
            if (emailMode) "Enter the code sent to ${store.email()}" else "This gallery is locked. Use one of the ways you set.",
            color = ink.copy(alpha = 0.75f),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        if (!emailMode && methods.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { item ->
                    val on = item == method
                    Text(
                        item.replaceFirstChar { it.uppercase() },
                        color = if (on) Color.White else ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) LockPink else field)
                            .clickable { method = item; value = ""; error = "" }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        when {
            emailMode -> {
                LockField(value, field, ink, KeyboardType.Number, false, "6-digit code") { if (it.length <= 6) value = it.filter { ch -> ch.isDigit() } }
                Spacer(Modifier.height(14.dp))
                LockButton("Unlock", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    if (store.matchesCode(value)) onUnlocked() else error = "That code is wrong or expired"
                }
                Text(
                    if (sending) "Sending..." else "Send a new code",
                    color = ink.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 14.dp).clickable(enabled = !sending) {
                        sending = true
                        onSendCode { ok ->
                            sending = false
                            error = if (ok) "Code sent. The first time, confirm the email from Nyte Gallery, then send again." else "Couldn't send the code"
                        }
                    },
                )
                Text("Use another way", color = ink, modifier = Modifier.padding(top = 10.dp).clickable { emailMode = false; error = "" })
            }
            method == LockStore.FINGER -> {
                LockButton("Use fingerprint", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    val host = activity
                    if (host == null) error = "Couldn't open the fingerprint prompt"
                    else promptFingerprint(host, "Unlock Nyte Gallery", onUnlocked) { error = it }
                }
            }
            method == LockStore.PATTERN -> {
                PatternPad(ink) { drawn ->
                    if (store.matches(LockStore.PATTERN, drawn)) onUnlocked() else error = "Pattern does not match"
                }
            }
            else -> {
                val numeric = method == LockStore.PIN
                LockField(value, field, ink, if (numeric) KeyboardType.NumberPassword else KeyboardType.Password, true, if (numeric) "PIN" else "Password") {
                    value = if (numeric) it.filter { ch -> ch.isDigit() }.take(6) else it
                }
                Spacer(Modifier.height(14.dp))
                LockButton("Unlock", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    if (store.matches(method, value)) onUnlocked() else error = "That ${method.replaceFirstChar { it.uppercase() }} is not right"
                }
            }
        }
        if (error.isNotEmpty()) Text(error, color = LockPink, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        if (!emailMode) {
            Spacer(Modifier.height(18.dp))
            Text(
                "Use email code",
                color = ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    emailMode = true
                    value = ""
                    error = ""
                    sending = true
                    onSendCode { ok ->
                        sending = false
                        error = if (ok) "Code sent to ${store.email()}" else "Couldn't send the code. Try another way."
                    }
                },
            )
        }
        }
    }
}

@Composable
private fun MethodRow(label: String, set: Boolean, ink: Color, field: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(field)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(
            if (set) "Ready" else "Set up",
            color = if (set) Color.White else ink.copy(alpha = 0.72f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (set) LockGreen else ink.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun SecretPair(
    ink: Color,
    field: Color,
    keyboard: KeyboardType,
    firstLabel: String,
    firstHint: String,
    secondLabel: String,
    secondHint: String,
    onSubmit: (String, String) -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    Column(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ink.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(firstLabel, color = ink.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        LockField(first, field, ink, keyboard, true, firstHint) { first = it }
        Spacer(Modifier.height(8.dp))
        Text(secondLabel, color = ink.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        LockField(second, field, ink, keyboard, true, secondHint) { second = it }
        Spacer(Modifier.height(12.dp))
        LockButton("Save", LockPink, Color.White, Modifier.fillMaxWidth()) {
            onSubmit(first.trim(), second.trim())
        }
    }
}

@Composable
private fun PatternConfirm(ink: Color, onConfirmed: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Draw at least 4 dots") }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = ink.copy(alpha = 0.72f), fontSize = 14.sp, textAlign = TextAlign.Center)
        PatternPad(ink) { drawn ->
            val saved = first
            if (saved == null) {
                if (drawn.split("-").size < 4) message = "Use at least 4 dots"
                else {
                    first = drawn
                    message = "Draw that pattern again to confirm"
                }
            } else if (saved == drawn) onConfirmed(drawn) else {
                first = null
                message = "That didn't match. Draw a new pattern."
            }
        }
    }
}

@Composable
private fun PatternPad(ink: Color, onComplete: (String) -> Unit) {
    var selected by remember { mutableStateOf(listOf<Int>()) }
    val complete = rememberUpdatedState(onComplete)
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(248.dp)
                .pointerInput(Unit) {
                    val current = mutableListOf<Int>()
                    detectDragGestures(
                        onDragStart = { start ->
                            current.clear()
                            hit(start, size.width.toFloat())?.let { current += it }
                            selected = current.toList()
                        },
                        onDrag = { change, _ ->
                            val next = hit(change.position, size.width.toFloat())
                            if (next != null && next !in current) {
                                current += next
                                selected = current.toList()
                            }
                        },
                        onDragEnd = {
                            if (current.isNotEmpty()) complete.value(current.joinToString("-"))
                            current.clear()
                            selected = emptyList()
                        },
                        onDragCancel = {
                            current.clear()
                            selected = emptyList()
                        },
                    )
                },
        ) {
            val cell = size.minDimension / 3f
            fun centerOf(index: Int) = Offset(cell * (index % 3 + 0.5f), cell * (index / 3 + 0.5f))
            for (i in 0 until selected.lastIndex) {
                drawLine(LockPink, centerOf(selected[i]), centerOf(selected[i + 1]), 3.dp.toPx(), StrokeCap.Round)
            }
            repeat(9) { index ->
                val on = index in selected
                val center = centerOf(index)
                drawCircle(ink.copy(alpha = if (on) 0.16f else 0.08f), radius = 22.dp.toPx(), center = center)
                drawCircle(if (on) LockPink else ink.copy(alpha = 0.72f), radius = if (on) 8.dp.toPx() else 6.dp.toPx(), center = center)
            }
        }
    }
}

private fun hit(position: Offset, width: Float): Int? {
    val cell = width / 3f
    val reach = cell * 0.34f
    for (index in 0..8) {
        val center = Offset(cell * (index % 3 + 0.5f), cell * (index / 3 + 0.5f))
        val dx = position.x - center.x
        val dy = position.y - center.y
        if (dx * dx + dy * dy <= reach * reach) return index
    }
    return null
}

@Composable
private fun LockField(
    value: String,
    field: Color,
    ink: Color,
    keyboard: KeyboardType,
    hidden: Boolean,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    val mask = hidden && !shown
    Box(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(field)
            .padding(start = 16.dp, end = if (hidden) 8.dp else 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = ink.copy(alpha = 0.32f), fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = ink, fontSize = 16.sp),
            cursorBrush = SolidColor(LockPink),
            visualTransformation = if (mask) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth().padding(end = if (hidden) 36.dp else 0.dp),
        )
        if (hidden) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { shown = !shown },
                contentAlignment = Alignment.Center,
            ) {
                EyeMark(ink.copy(alpha = 0.72f), concealed = !shown)
            }
        }
    }
}

@Composable
private fun EyeMark(color: Color, concealed: Boolean) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        val eye = Path().apply {
            moveTo(w * 0.08f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.12f, w * 0.92f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.88f, w * 0.08f, h * 0.5f)
            close()
        }
        drawPath(eye, color, style = stroke)
        drawCircle(color, radius = w * 0.11f, center = center)
        if (concealed) {
            drawLine(color, Offset(w * 0.2f, h * 0.78f), Offset(w * 0.8f, h * 0.22f), stroke.width, StrokeCap.Round)
        }
    }
}

@Composable
private fun LockButton(label: String, background: Color, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun LockGlyph() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.18f, h * 0.44f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.42f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = stroke,
        )
        val path = Path().apply {
            arcTo(
                androidx.compose.ui.geometry.Rect(Offset(w * 0.30f, h * 0.16f), androidx.compose.ui.geometry.Size(w * 0.40f, h * 0.40f)),
                200f,
                140f,
                false,
            )
        }
        drawPath(path, color, style = stroke)
        drawCircle(color, radius = min(w, h) * 0.045f, center = Offset(w * 0.5f, h * 0.64f))
    }
}

/**
 * Change the lock from Settings. The current lock is checked first with one of the saved ways
 * (password, PIN, pattern or fingerprint), then the same setup rules apply to the new lock.
 * The recovery email is kept. Saving uses LockStore.save, so the stored format does not change.
 */
@Composable
fun LockChangePage(
    light: Boolean,
    store: LockStore,
    onClose: () -> Unit,
    onChanged: () -> Unit,
    onTurnedOff: () -> Unit,
) {
    var verified by remember { mutableStateOf(false) }
    var confirmingOff by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (!verified) {
            CurrentLockCheck(
                light = light,
                store = store,
                title = "CHANGE LOCK",
                body = "Confirm it's you with one of the ways you set now. Then choose new ways to unlock.",
                onClose = onClose,
                onVerified = { verified = true },
            )
        } else {
            LockSetupPage(
                light = light,
                onClose = onClose,
                onSave = { _, password, pin, pattern, finger ->
                    store.save(store.email(), password, pin, pattern, finger)
                    onChanged()
                },
                title = "CHANGE LOCK",
                intro = "Choose at least two new ways to get back in: password, PIN, pattern, or fingerprint. They replace the ways you use now. Your recovery email stays the same.",
                saveLabel = "Save new lock",
                fixedEmail = store.email(),
                onTurnOff = { confirmingOff = true },
            )
        }
        if (confirmingOff) {
            LockConfirm(
                light = light,
                title = "TURN OFF LOCK",
                body = "Turn off the app lock? Anyone with this phone can open Nyte Gallery until you set a lock again. Your photos stay encrypted.",
                confirm = "Turn off",
                onDismiss = { confirmingOff = false },
                onConfirm = {
                    confirmingOff = false
                    store.clear()
                    onTurnedOff()
                },
            )
        }
    }
}

/** Change the recovery email from Settings, after the current lock is checked. */
@Composable
fun RecoveryEmailPage(
    light: Boolean,
    store: LockStore,
    onClose: () -> Unit,
    onChanged: () -> Unit,
) {
    var verified by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (!verified) {
            CurrentLockCheck(
                light = light,
                store = store,
                title = "RECOVERY EMAIL",
                body = "Confirm it's you with one of the ways you set. Then enter the new email.",
                onClose = onClose,
                onVerified = { verified = true },
            )
        } else {
            val ink = if (light) Color(0xFF2A2428) else Color.White
            val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
            val field = if (light) Color.White else Color(0xFF1C1E26)
            var email by remember { mutableStateOf(store.email()) }
            var error by remember { mutableStateOf("") }
            Column(
                Modifier
                    .fillMaxSize()
                    .background(panel)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("RECOVERY EMAIL", color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "If you forget how to unlock, Nyte Gallery sends a one-time code to this email. Codes sent to the old email stop working.",
                    color = ink.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text("New recovery email", color = ink.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                LockField(email, field, ink, KeyboardType.Email, false, "name@email.com") { email = it; error = "" }
                if (error.isNotEmpty()) {
                    Text(error, color = LockPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(16.dp))
                LockButton("Save email", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    val next = email.trim()
                    when {
                        !next.matches(EMAIL_PATTERN) -> error = "Enter a valid email"
                        next.equals(store.email(), ignoreCase = true) -> onClose()
                        else -> {
                            store.updateEmail(next)
                            onChanged()
                        }
                    }
                }
            }
        }
    }
}

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Checks the current lock with one of the saved ways. The email code is not offered here. */
@Composable
private fun CurrentLockCheck(
    light: Boolean,
    store: LockStore,
    title: String,
    body: String,
    onClose: () -> Unit,
    onVerified: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalHostActivity.current ?: findActivity(context)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    val methods = remember { store.methods().toList() }
    var method by remember { mutableStateOf(methods.firstOrNull { it != LockStore.FINGER } ?: methods.firstOrNull()) }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(panel)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(body, color = ink.copy(alpha = 0.8f), fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(18.dp))
        if (methods.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { item ->
                    val on = item == method
                    Text(
                        item.replaceFirstChar { it.uppercase() },
                        color = if (on) Color.White else ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) LockPink else field)
                            .clickable { method = item; value = ""; error = "" }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        val current: String = method ?: ""
        when (current) {
            "" -> Text("No unlock way is saved.", color = ink.copy(alpha = 0.7f), fontSize = 14.sp)
            LockStore.FINGER -> {
                LockButton("Use fingerprint", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    val host = activity
                    if (host == null) error = "Couldn't open the fingerprint prompt"
                    else promptFingerprint(host, "Confirm it's you", onVerified) { error = it }
                }
            }
            LockStore.PATTERN -> {
                PatternPad(ink) { drawn ->
                    if (store.matches(LockStore.PATTERN, drawn)) onVerified() else error = "Pattern does not match"
                }
            }
            else -> {
                val numeric = current == LockStore.PIN
                LockField(value, field, ink, if (numeric) KeyboardType.NumberPassword else KeyboardType.Password, true, if (numeric) "Current PIN" else "Current password") {
                    value = if (numeric) it.filter { ch -> ch.isDigit() }.take(6) else it
                }
                Spacer(Modifier.height(14.dp))
                LockButton("Continue", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    if (store.matches(current, value)) onVerified() else error = "That ${current.replaceFirstChar { it.uppercase() }} is not right"
                }
            }
        }
        if (error.isNotEmpty()) Text(error, color = LockPink, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    }
}
