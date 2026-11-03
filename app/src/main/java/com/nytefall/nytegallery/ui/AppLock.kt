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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nytefall.nytegallery.data.LockCheck
import com.nytefall.nytegallery.data.LockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

internal val LockPink = Color(0xFFFF4D6A)
private val LockGreen = Color(0xFF2F9B68)

// Only Class 3 (BIOMETRIC_STRONG) biometrics are accepted, so weak face unlock can never open the app.
private const val STRONG_BIOMETRIC = BiometricManager.Authenticators.BIOMETRIC_STRONG

/** Lock setup may offer fingerprint: the phone has strong biometric hardware (enrolled, or can be enrolled). */
fun fingerprintAvailable(context: Context): Boolean {
    val strong = BiometricManager.from(context).canAuthenticate(STRONG_BIOMETRIC)
    return strong == BiometricManager.BIOMETRIC_SUCCESS || strong == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
}

/** Fingerprint can be used to unlock right now (strong biometric enrolled and working). */
fun fingerprintUsable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(STRONG_BIOMETRIC) == BiometricManager.BIOMETRIC_SUCCESS

/** Saved unlock ways that work on this phone now. A saved fingerprint is hidden when no strong biometric is usable. */
internal fun usableMethods(store: LockStore, context: Context): List<String> {
    val saved = store.methods().toList()
    if (LockStore.FINGER !in saved || fingerprintUsable(context)) return saved
    return saved.filter { it != LockStore.FINGER }
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
    when (BiometricManager.from(activity).canAuthenticate(STRONG_BIOMETRIC)) {
        BiometricManager.BIOMETRIC_SUCCESS -> Unit
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> return onError("Add a fingerprint in system settings first")
        else -> return onError("Fingerprint isn't available on this phone right now")
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
        AutoLock.expectExternal()
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Use the fingerprint saved on this phone")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(STRONG_BIOMETRIC)
                .build(),
        )
    } catch (error: Exception) {
        onError(error.message ?: "Couldn't open the fingerprint prompt")
    }
}

@Composable
fun LockConfirm(
    light: Boolean,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "LOCK APP",
    dismissLabel: String = "Close",
) {
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
                LockButton(dismissLabel, ink.copy(alpha = 0.12f), ink, Modifier.weight(1f), onDismiss)
                LockButton(confirm, LockPink, Color.White, Modifier.weight(1f), onConfirm)
            }
        }
    }
}

@Composable
fun LockSetupPage(
    light: Boolean,
    onClose: () -> Unit,
    onSave: suspend (password: String?, pin: String?, pattern: String?, finger: Boolean) -> Unit,
    title: String = "LOCK",
    intro: String = "Set how Nyte Gallery locks. Choose at least two ways to get back in: password, PIN, pattern, or fingerprint. Next you get a recovery code for the day you forget them.",
    saveLabel: String = "Lock app",
) {
    val context = LocalContext.current
    val activity = LocalHostActivity.current ?: findActivity(context)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    val fingerOk = remember { fingerprintAvailable(context) }
    var password by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf<String?>(null) }
    var pattern by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val readyCount = listOf(password, pin, pattern).count { it != null } + if (finger) 1 else 0
    Column(
        Modifier
            .fillMaxSize()
            .background(panel)
            .windowInsetsPadding(WindowInsets.stableBars)
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
        val canSave = readyCount >= 2
        LockButton(if (saving) "Saving..." else saveLabel, if (canSave) LockPink else ink.copy(alpha = 0.15f), if (canSave) Color.White else ink.copy(alpha = 0.4f), Modifier.fillMaxWidth()) {
            if (saving) return@LockButton
            if (canSave) {
                saving = true
                scope.launch {
                    try {
                        onSave(password, pin, pattern, finger)
                    } finally {
                        saving = false
                    }
                }
            } else error = "Set at least two unlock ways"
        }
    }
}

@Composable
fun LockGate(
    light: Boolean,
    store: LockStore,
    onUnlocked: () -> Unit,
    onRecovered: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalHostActivity.current ?: findActivity(context)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    val methods = remember { usableMethods(store, context) }
    var method by remember { mutableStateOf(methods.firstOrNull { it != LockStore.FINGER } ?: methods.firstOrNull() ?: "") }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var emailMode by remember { mutableStateOf(false) } // true: entering the recovery code
    val canRecover = remember { store.hasRecoveryCode() }
    val tries = rememberLockTries(store)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(panel).windowInsetsPadding(WindowInsets.stableBars),
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
            if (emailMode) "Enter your recovery code. You will then set a new lock and get a new code." else "This gallery is locked. Use one of the ways you set.",
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
                            .clickable { method = item; value = ""; error = ""; tries.failure = "" }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        when {
            emailMode -> {
                LockField(value, field, ink, KeyboardType.Ascii, false, "XXXX-XXXX-XXXX-XXXX") { if (it.length <= 24) value = it }
                Spacer(Modifier.height(14.dp))
                LockButton(if (tries.checking) "Checking..." else "Unlock", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    val typed = value
                    if (store.recoveryInputEmpty(typed)) {
                        error = "Enter your recovery code"
                        return@LockButton
                    }
                    tries.attempt({ store.checkRecovery(typed) }) { result ->
                        when (result) {
                            LockCheck.Ok -> onRecovered()
                            is LockCheck.Wrong -> error = if (result.waitMs > 0L) "" else "That recovery code is not right"
                            is LockCheck.Wait -> error = ""
                        }
                    }
                }
                Text("Use another way", color = ink, modifier = Modifier.padding(top = 14.dp).clickable { emailMode = false; value = ""; error = ""; tries.failure = "" })
            }
            method == LockStore.FINGER -> {
                LockButton("Use fingerprint", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    val host = activity
                    if (host == null) error = "Couldn't open the fingerprint prompt"
                    else promptFingerprint(host, "Unlock Nyte Gallery", { store.noteSuccess(); onUnlocked() }) { error = it }
                }
            }
            method == LockStore.PATTERN -> {
                PatternPad(ink) { drawn ->
                    tries.attempt({ store.check(LockStore.PATTERN, drawn) }) { result ->
                        when (result) {
                            LockCheck.Ok -> onUnlocked()
                            is LockCheck.Wrong -> error = if (result.waitMs > 0L) "" else "Pattern does not match"
                            is LockCheck.Wait -> error = ""
                        }
                    }
                }
            }
            else -> {
                val numeric = method == LockStore.PIN
                LockField(value, field, ink, if (numeric) KeyboardType.NumberPassword else KeyboardType.Password, true, if (numeric) "PIN" else "Password") {
                    value = if (numeric) it.filter { ch -> ch.isDigit() }.take(6) else it
                }
                Spacer(Modifier.height(14.dp))
                LockButton(if (tries.checking) "Checking..." else "Unlock", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    val way = method
                    val typed = value
                    if (typed.isBlank()) {
                        error = emptyInputMessage(way)
                        return@LockButton
                    }
                    tries.attempt({ store.check(way, typed) }) { result ->
                        when (result) {
                            LockCheck.Ok -> onUnlocked()
                            is LockCheck.Wrong -> error = if (result.waitMs > 0L) "" else "That ${way.replaceFirstChar { it.uppercase() }} is not right"
                            is LockCheck.Wait -> error = ""
                        }
                    }
                }
            }
        }
        val shown = if (tries.waitMs > 0L) waitMessage(tries.waitMs) else tries.failure.ifEmpty { error }
        if (shown.isNotEmpty()) Text(shown, color = LockPink, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        if (!emailMode && canRecover) {
            Spacer(Modifier.height(18.dp))
            Text(
                "Forgot? Use recovery code",
                color = ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    emailMode = true
                    value = ""
                    error = ""
                    tries.failure = ""
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

private fun methodLabel(method: String): String = when (method) {
    LockStore.PASSWORD -> "Password"
    LockStore.PIN -> "PIN"
    LockStore.PATTERN -> "Pattern"
    LockStore.FINGER -> "Fingerprint"
    else -> method.replaceFirstChar { it.uppercase() }
}

/**
 * Settings > Manage lock. The current lock is checked first with one of the saved ways, then each way
 * (password, PIN, pattern, fingerprint) can be added, changed or removed on its own, keeping at least
 * [LockStore.MIN_METHODS]. The other ways and the recovery code are not touched.
 */
@Composable
fun ManageLockPage(light: Boolean, store: LockStore, onClose: () -> Unit) {
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
                title = "MANAGE LOCK",
                body = "Confirm it's you with one of the ways you set now. Then you can add, change or remove ways to unlock.",
                onClose = onClose,
                onVerified = { verified = true },
            )
        } else {
            ManageLockMethods(light, store, onClose)
        }
    }
}

@Composable
private fun ManageLockMethods(light: Boolean, store: LockStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalHostActivity.current ?: findActivity(context)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    val fingerOk = remember { fingerprintAvailable(context) }
    var saved by remember { mutableStateOf(store.methods()) }
    var open by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canRemove = saved.size > LockStore.MIN_METHODS

    fun saveWay(method: String, value: String?) {
        if (busy) return
        busy = true
        error = ""
        notice = ""
        scope.launch {
            val ok = try {
                withContext(Dispatchers.Default) { store.setMethod(method, value) }
                true
            } catch (cancel: kotlin.coroutines.cancellation.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            } finally {
                busy = false
            }
            saved = store.methods()
            if (ok) {
                open = null
                notice = "${methodLabel(method)} saved"
            } else {
                error = "Couldn't save that. Please try again."
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .background(panel)
                .windowInsetsPadding(WindowInsets.stableBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MANAGE LOCK", color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Add, change or remove the ways Nyte Gallery unlocks. Keep at least two on. Your recovery code stays the same.",
                color = ink.copy(alpha = 0.8f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(16.dp))
            listOf(LockStore.PASSWORD, LockStore.PIN, LockStore.PATTERN, LockStore.FINGER).forEach { method ->
                androidx.compose.runtime.key(method) {
                    val on = method in saved
                    val isFinger = method == LockStore.FINGER
                    if (isFinger && !fingerOk && !on) {
                        Text("Fingerprint is not available on this phone.", color = ink.copy(alpha = 0.45f), fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        ManageMethodRow(
                            label = methodLabel(method),
                            on = on,
                            ink = ink,
                            field = field,
                            canAddOrChange = !isFinger || fingerOk,
                            changeable = !isFinger,
                            canRemove = canRemove,
                            onAddOrChange = {
                                error = ""
                                notice = ""
                                if (isFinger) {
                                    open = null
                                    val host = activity
                                    if (host == null) error = "Couldn't open the fingerprint prompt"
                                    else promptFingerprint(host, "Confirm fingerprint", { saveWay(LockStore.FINGER, null) }, { error = it })
                                } else {
                                    open = if (open == method) null else method
                                }
                            },
                            onRemove = {
                                error = ""
                                notice = ""
                                if (canRemove) removing = method else error = "Keep at least 2 ways to unlock"
                            },
                        )
                        if (open == method) {
                            when (method) {
                                LockStore.PASSWORD -> SecretPair(ink, field, KeyboardType.Password, "New password", "At least 6 characters", "Confirm password", "Type it again") { first, second ->
                                    when {
                                        first.length < 6 -> error = "Password needs at least 6 characters"
                                        first != second -> error = "Those passwords do not match"
                                        else -> saveWay(LockStore.PASSWORD, first)
                                    }
                                }
                                LockStore.PIN -> SecretPair(ink, field, KeyboardType.NumberPassword, "New PIN", "4 to 6 digits", "Confirm PIN", "Type the same PIN again") { first, second ->
                                    when {
                                        !first.matches(Regex("\\d{4,6}")) -> error = "PIN must be 4 to 6 digits"
                                        first != second -> error = "Those PINs do not match"
                                        else -> saveWay(LockStore.PIN, first)
                                    }
                                }
                                LockStore.PATTERN -> PatternConfirm(ink) { value -> saveWay(LockStore.PATTERN, value) }
                            }
                        }
                    }
                }
            }
            if (!canRemove) {
                Text("Keep at least 2 ways to unlock", color = ink.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            if (busy) {
                Text("Saving...", color = ink.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (error.isNotEmpty()) {
                Text(error, color = LockPink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            } else if (notice.isNotEmpty()) {
                Text(notice, color = LockGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            }
        }
        val pendingRemove = removing
        if (pendingRemove != null) {
            LockConfirm(
                light = light,
                title = "REMOVE",
                body = "Remove ${methodLabel(pendingRemove)} as a way to unlock Nyte Gallery?",
                confirm = "Remove",
                dismissLabel = "Cancel",
                onDismiss = { removing = null },
                onConfirm = {
                    removing = null
                    if (open == pendingRemove) open = null
                    scope.launch {
                        val removed = withContext(Dispatchers.Default) { store.removeMethod(pendingRemove) }
                        saved = store.methods()
                        if (removed) notice = "${methodLabel(pendingRemove)} removed" else error = "Keep at least 2 ways to unlock"
                    }
                },
            )
        }
    }
}

@Composable
private fun ManageMethodRow(
    label: String,
    on: Boolean,
    ink: Color,
    field: Color,
    canAddOrChange: Boolean,
    changeable: Boolean,
    canRemove: Boolean,
    onAddOrChange: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(field)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                if (on) "On" else "Off",
                color = if (on) Color.White else ink.copy(alpha = 0.72f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (on) LockGreen else ink.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!on && canAddOrChange) ManageChip("Add", ink, ink, true, onAddOrChange)
            if (on && changeable && canAddOrChange) ManageChip("Change", ink, ink, true, onAddOrChange)
            if (on) ManageChip("Remove", LockPink, ink, canRemove, onRemove)
        }
    }
}

@Composable
private fun ManageChip(label: String, color: Color, ink: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = color.copy(alpha = if (enabled) 1f else 0.35f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * Settings > Turn off lock: check the current lock, then warn. Turning off deletes every unlock way, the
 * recovery code and any unconfirmed code. The photos' encryption doesn't change.
 */
@Composable
fun TurnOffLockPage(light: Boolean, store: LockStore, onClose: () -> Unit, onTurnedOff: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        CurrentLockCheck(
            light = light,
            store = store,
            title = "TURN OFF LOCK",
            body = "Confirm it's you with one of the ways you set now.",
            onClose = onClose,
            onVerified = { verified = true },
        )
        if (verified) {
            LockConfirm(
                light = light,
                title = "TURN OFF LOCK",
                body = "Anyone with this phone can open Nyte Gallery. Your recovery code will stop working. Photos stay encrypted.",
                confirm = "Turn off",
                dismissLabel = "Cancel",
                onDismiss = onClose,
                onConfirm = {
                    store.clear()
                    onTurnedOff()
                },
            )
        }
    }
}

/** Settings > Recovery code: check the current lock, then create a new code (the old one stops working). */
@Composable
fun RecoveryCodeSettingsPage(
    light: Boolean,
    store: LockStore,
    onClose: () -> Unit,
    onCreate: () -> Unit,
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
                title = "RECOVERY CODE",
                body = "Confirm it's you with one of the ways you set. Then you can create a new recovery code.",
                onClose = onClose,
                onVerified = { verified = true },
            )
        } else {
            val ink = if (light) Color(0xFF2A2428) else Color.White
            val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
            Column(
                Modifier
                    .fillMaxSize()
                    .background(panel)
                    .windowInsetsPadding(WindowInsets.stableBars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("RECOVERY CODE", color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your recovery code gets you back in if you forget your unlock ways. Nyte Gallery cannot show your current code again. Creating a new code makes the old one stop working.",
                    color = ink.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(16.dp))
                LockButton("Create new recovery code", LockPink, Color.White, Modifier.fillMaxWidth(), onCreate)
            }
        }
    }
}

const val RECOVERY_WARNING =
    "Write this code down and keep it somewhere safe. It is the ONLY way back in if you forget your unlock methods. " +
        "Nyte Gallery cannot recover it for you. If you lose it and forget your lock, your photos cannot be opened."

/**
 * Shows a new recovery code once. [code] is null while it is being created.
 * Continue needs the "written down" checkbox. There is no Close: the code is never shown again.
 */
@Composable
fun RecoveryCodePage(light: Boolean, code: String?, note: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    var written by remember(code) { mutableStateOf(false) }
    var copied by remember(code) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(panel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.stableBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("RECOVERY CODE", color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (note != null) {
            Text(note, color = ink.copy(alpha = 0.8f), fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(12.dp))
        }
        Text(RECOVERY_WARNING, color = LockPink, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(field)
                .padding(vertical = 18.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                code ?: "Creating code...",
                color = ink,
                fontSize = if (code != null) 22.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (code != null) {
            Text(
                if (copied) "Copied. The clipboard is cleared in 1 minute." else "Copy",
                color = ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .clickable { copyRecoveryCode(context, code); copied = true }
                    .padding(8.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { written = !written }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (written) LockPink else field),
                    contentAlignment = Alignment.Center,
                ) {
                    if (written) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(12.dp))
                Text("I have written down my recovery code", color = ink, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        val ready = code != null && written
        LockButton("Continue", if (ready) LockPink else ink.copy(alpha = 0.15f), if (ready) Color.White else ink.copy(alpha = 0.4f), Modifier.fillMaxWidth()) {
            if (ready) onDone()
        }
    }
}

/** Copies the code as sensitive (hidden in the clipboard preview on Android 13+) and clears it after about 60 s. */
private fun copyRecoveryCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
    val clip = android.content.ClipData.newPlainText("Nyte Gallery recovery code", code)
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
            // Android 10+ may not let a background app read the clipboard; then clear anyway (best effort).
            val current = clipboard.primaryClip
            val ours = current == null || current.itemCount == 0 || current.getItemAt(0).text?.toString() == code
            if (ours) {
                if (android.os.Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                else clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {
        }
    }, 60_000L)
}

/** Checks the current lock with one of the saved ways. The recovery code is not offered here. */
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
    val methods = remember { usableMethods(store, context) }
    var method by remember { mutableStateOf(methods.firstOrNull { it != LockStore.FINGER } ?: methods.firstOrNull()) }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val tries = rememberLockTries(store)
    Column(
        Modifier
            .fillMaxSize()
            .background(panel)
            .windowInsetsPadding(WindowInsets.stableBars)
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
                            .clickable { method = item; value = ""; error = ""; tries.failure = "" }
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
                    else promptFingerprint(host, "Confirm it's you", { store.noteSuccess(); onVerified() }) { error = it }
                }
            }
            LockStore.PATTERN -> {
                PatternPad(ink) { drawn ->
                    tries.attempt({ store.check(LockStore.PATTERN, drawn) }) { result ->
                        when (result) {
                            LockCheck.Ok -> onVerified()
                            is LockCheck.Wrong -> error = if (result.waitMs > 0L) "" else "Pattern does not match"
                            is LockCheck.Wait -> error = ""
                        }
                    }
                }
            }
            else -> {
                val numeric = current == LockStore.PIN
                LockField(value, field, ink, if (numeric) KeyboardType.NumberPassword else KeyboardType.Password, true, if (numeric) "Current PIN" else "Current password") {
                    value = if (numeric) it.filter { ch -> ch.isDigit() }.take(6) else it
                }
                Spacer(Modifier.height(14.dp))
                LockButton(if (tries.checking) "Checking..." else "Continue", LockPink, Color.White, Modifier.fillMaxWidth()) {
                    val typed = value
                    if (typed.isBlank()) {
                        error = emptyInputMessage(current)
                        return@LockButton
                    }
                    tries.attempt({ store.check(current, typed) }) { result ->
                        when (result) {
                            LockCheck.Ok -> onVerified()
                            is LockCheck.Wrong -> error = if (result.waitMs > 0L) "" else "That ${current.replaceFirstChar { it.uppercase() }} is not right"
                            is LockCheck.Wait -> error = ""
                        }
                    }
                }
            }
        }
        val shown = if (tries.waitMs > 0L) waitMessage(tries.waitMs) else tries.failure.ifEmpty { error }
        if (shown.isNotEmpty()) Text(shown, color = LockPink, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * Runs lock checks off the main thread (PBKDF2 is slow) and keeps the cooldown countdown.
 * The wrong-try counter itself lives in [LockStore], so it is shared by every lock screen and survives restarts.
 */
private class LockTries(private val store: LockStore, private val scope: CoroutineScope) {
    var checking by mutableStateOf(false)
    var waitMs by mutableLongStateOf(store.waitRemainingMs())

    /** Shown instead of crashing when a check throws. Cleared by the next try. */
    var failure by mutableStateOf("")

    fun attempt(run: () -> LockCheck, onResult: (LockCheck) -> Unit) {
        if (checking || waitMs > 0L) return
        checking = true
        failure = ""
        scope.launch {
            val result = try {
                withContext(Dispatchers.Default) { run() }
            } catch (cancel: kotlin.coroutines.cancellation.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            } finally {
                checking = false
            }
            if (result == null) {
                failure = "Couldn't check that. Please try again."
                return@launch
            }
            when (result) {
                is LockCheck.Wrong -> if (result.waitMs > 0L) waitMs = result.waitMs
                is LockCheck.Wait -> waitMs = result.waitMs
                LockCheck.Ok -> waitMs = 0L
            }
            onResult(result)
        }
    }
}

@Composable
private fun rememberLockTries(store: LockStore): LockTries {
    val scope = rememberCoroutineScope()
    val tries = remember(store) { LockTries(store, scope) }
    val waiting = tries.waitMs > 0L
    LaunchedEffect(tries, waiting) {
        while (tries.waitMs > 0L) {
            val step = tries.waitMs % 1000L
            delay(if (step == 0L) 1000L else step)
            tries.waitMs = store.waitRemainingMs()
        }
    }
    return tries
}

/** Inline message when Unlock or Continue is tapped with nothing typed. */
private fun emptyInputMessage(method: String): String = when (method) {
    LockStore.PIN -> "Enter your PIN"
    LockStore.PASSWORD -> "Enter your password"
    else -> "Enter your ${method.replaceFirstChar { it.uppercase() }}"
}

private fun waitMessage(ms: Long): String {
    val seconds = (ms + 999L) / 1000L
    val time = if (seconds >= 3600L) {
        "%d:%02d:%02d".format(seconds / 3600L, seconds / 60L % 60L, seconds % 60L)
    } else {
        "%d:%02d".format(seconds / 60L, seconds % 60L)
    }
    return "Too many wrong tries. Try again in $time."
}
