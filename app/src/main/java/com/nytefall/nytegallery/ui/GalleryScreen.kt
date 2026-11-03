package com.nytefall.nytegallery.ui

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.nytefall.nytegallery.R
import com.nytefall.nytegallery.data.BackupPrefs
import com.nytefall.nytegallery.data.GalleryPhoto
import com.nytefall.nytegallery.data.LockStore
import com.nytefall.nytegallery.data.VAULT_THUMB_EDGE
import com.nytefall.nytegallery.data.VaultStore
import com.nytefall.nytegallery.data.formatTaken
import com.nytefall.nytegallery.data.yearOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Paper = Color(0xFFF7F3EE)
private val Ink = Color(0xFF3A2C32)
private val DotA = Color(0xFFFF4D6A)
private val DotB = Color(0xFFFF8AA0)
private val DotC = Color(0xFFFFC0CC)
private val AddGreen = Color(0xFF2F9B68)

private const val CARD_LIMIT = 16
private const val LABEL_LIMIT = 22
// false: FLAG_SECURE is on for the whole app (also the lock screen), which blocks screenshots and screen
// recording and hides the app's preview in Recent apps. Also applied in MainActivity.onCreate before the first frame.
// Comes from BuildConfig: false in debug and release, true only in the separate "screenshot" build type.
internal val SCREENSHOTS_ALLOWED: Boolean = com.nytefall.nytegallery.BuildConfig.SCREENSHOTS_ALLOWED
private val CardHeader = 34.dp

// Landscape (list closed): cards live in the left share of the width, the info panel in the rest.
private const val LandscapeStageShare = 0.58f
private val LandscapeCardTop = 28.dp
private val LandscapeCardBottom = 20.dp

private class CardFit(val width: Dp, val shiftX: Dp, val shiftY: Dp?)

private fun cardFit(shape: WindowShape, maxWidth: Dp, maxHeight: Dp, compact: Boolean, insetTop: Dp, insetBottom: Dp): CardFit {
    val byWidth = maxWidth * if (compact) 0.42f else 0.62f
    if (shape == WindowShape.Phone) return CardFit(byWidth, 0.dp, null)
    if (shape == WindowShape.TallWide) {
        val band = maxHeight - if (compact) 140.dp else 400.dp
        return CardFit(minOf(byWidth, widthForBand(band)), 0.dp, null)
    }
    val top = insetTop + if (compact) 60.dp else LandscapeCardTop
    val bottom = insetBottom + if (compact) 112.dp else LandscapeCardBottom
    val band = maxHeight - top - bottom
    // Landscape without the list: the whole carousel (featured card plus both side cards,
    // roughly 2.42 card widths) must stay inside the left region.
    val cap = if (compact) byWidth else (maxWidth * LandscapeStageShare - 32.dp) / 2.42f
    return CardFit(
        width = minOf(cap, widthForBand(band)),
        shiftX = if (compact) 0.dp else maxWidth * (LandscapeStageShare / 2f - 0.5f),
        shiftY = top + band / 2 - maxHeight / 2,
    )
}

private fun widthForBand(band: Dp): Dp =
    ((band / ((0.62f + 0.5f) * 1.04f) - CardHeader) / 1.32f).coerceAtLeast(56.dp)

@Composable
private fun cardFitFor(maxWidth: Dp, maxHeight: Dp, compact: Boolean): CardFit {
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    val top = with(density) { insets.getTop(density).toDp() }
    val bottom = with(density) { insets.getBottom(density).toDp() }
    return cardFit(windowShape(), maxWidth, maxHeight, compact, top, bottom)
}

@Composable
fun GalleryScreen(
    state: GalleryUiState,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onShowIndex: (Int) -> Unit,
    onImport: (List<String>, (ImportOutcome) -> Unit) -> Unit,
    onNextDelete: () -> DeleteStep,
    onCancelDelete: () -> Unit,
    onRemoveOriginals: ((RemovalOutcome) -> Unit) -> Unit,
    onKeepOriginals: () -> Unit,
    onRename: (Int, String) -> Unit,
    onTrash: (Int) -> Unit,
    onRestore: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    onExport: (Set<String>, (Int) -> Unit) -> Unit,
    onBackup: (String, String, (BackupOutcome) -> Unit) -> Unit,
    onRestoreBackup: (String, String, (RestoreOutcome) -> Unit) -> Unit,
    onVerifyBackup: (String, String, (VerifyOutcome) -> Unit) -> Unit,
    trashExplained: () -> Boolean,
    onTrashExplained: () -> Unit,
    transfer: TransferProgress? = null,
    showOnboarding: Boolean = false,
    onOnboardingDone: () -> Unit = {},
) {
    val context = LocalContext.current
    val lockStore = remember { LockStore(context) }
    val scope = rememberCoroutineScope()
    var light by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(lockStore.configured()) }
    var confirmingLock by remember { mutableStateOf(false) }
    var settingLock by remember { mutableStateOf(false) }
    // First-launch welcome pages; saveable so a recreated activity keeps showing them.
    var onboarding by rememberSaveable { mutableStateOf(showOnboarding) }
    // "lock" or "code" while changing the lock or creating a new recovery code from Settings.
    var changingLock by remember { mutableStateOf<String?>(null) }
    // A new recovery code is being created (null code) or shown once.
    var makingRecovery by remember { mutableStateOf(false) }
    var shownRecoveryCode by remember { mutableStateOf<String?>(null) }
    var recoveryNote by remember { mutableStateOf<String?>(null) }
    var lockAfterRecovery by remember { mutableStateOf(false) }
    // Unlocked with the recovery code: set a new lock next.
    var resettingLock by remember { mutableStateOf(false) }
    fun showNewRecoveryCode(note: String?, lockAfter: Boolean) {
        if (makingRecovery) return
        makingRecovery = true
        shownRecoveryCode = null
        recoveryNote = note
        lockAfterRecovery = lockAfter
        scope.launch {
            shownRecoveryCode = withContext(Dispatchers.Default) { lockStore.newRecoveryCode() }
        }
    }
    var galleryOpen by remember { mutableStateOf(false) }
    var trashOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var explainingTrash by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    var restoreOpen by remember { mutableStateOf(false) }
    var emptyingTrash by remember { mutableStateOf(false) }
    var choosingDevice by remember { mutableStateOf(false) }
    // Photos just moved in from the device gallery; > 0 shows the "remove originals?" sheet.
    var originalsToAsk by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var importBackup by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }
    val backupPrefs = remember { BackupPrefs(context) }
    var backupGuide by remember { mutableStateOf(false) }
    var backupSaved by remember { mutableStateOf<SavedBackup?>(null) }
    // Password of the backup just written, kept in memory only so "Check backup" can run once.
    var checkSecret by remember { mutableStateOf<String?>(null) }
    var backupChecking by remember { mutableStateOf(false) }
    var checkNote by remember { mutableStateOf<String?>(null) }
    var checkFailed by remember { mutableStateOf(false) }
    var restoreNote by remember { mutableStateOf<String?>(null) }
    var pendingExportIds by remember { mutableStateOf(emptySet<String>()) }
    var draft by remember { mutableStateOf("") }
    val exitArmedUntil = remember { longArrayOf(0L) }
    val view = LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }
    val host = LocalHostActivity.current
    DisposableEffect(host) {
        val window = host?.window
        if (window != null) {
            if (SCREENSHOTS_ALLOWED) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { }
    }

    AutoLockEffect(
        shouldLock = { !locked && !resettingLock && lockStore.configured() },
        onLock = { locked = true },
    )

    val chain = remember { arrayOfNulls<ActivityResultLauncher<IntentSenderRequest>>(1) }
    val deleteConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            when (val step = onNextDelete()) {
                is DeleteStep.Consent -> {
                    AutoLock.expectExternal()
                    chain[0]?.launch(IntentSenderRequest.Builder(step.sender).build())
                }
                DeleteStep.Done -> Toast.makeText(context, "Encrypted inside Nyte Gallery and removed from your gallery", Toast.LENGTH_LONG).show()
                DeleteStep.Failed -> Toast.makeText(context, "Saved in Nyte Gallery, but still visible in your other gallery", Toast.LENGTH_LONG).show()
            }
        } else {
            onCancelDelete()
            Toast.makeText(context, "Saved in Nyte Gallery. Any photo you did not confirm is still in your other gallery", Toast.LENGTH_LONG).show()
        }
    }
    chain[0] = deleteConsent
    val removeOriginals = {
        originalsToAsk = 0
        onRemoveOriginals { outcome ->
            when {
                outcome.consent != null -> {
                    AutoLock.expectExternal()
                    deleteConsent.launch(IntentSenderRequest.Builder(outcome.consent).build())
                }
                outcome.alreadyRemoved -> Toast.makeText(context, "Encrypted inside Nyte Gallery and removed from your gallery", Toast.LENGTH_LONG).show()
                else -> Toast.makeText(context, "Saved in Nyte Gallery, but still visible in your other gallery", Toast.LENGTH_LONG).show()
            }
        }
    }
    val keepOriginals = {
        originalsToAsk = 0
        onKeepOriginals()
        Toast.makeText(context, "Originals kept in your gallery", Toast.LENGTH_SHORT).show()
    }
    val photoAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) choosingDevice = true
        else Toast.makeText(context, "Photo access is needed to move images out of your gallery", Toast.LENGTH_SHORT).show()
    }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    var pendingCheckOnly by remember { mutableStateOf(false) }
    val openBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val secret = pendingBackupPassword
        pendingBackupPassword = null
        val checkOnly = pendingCheckOnly
        pendingCheckOnly = false
        if (uri == null || secret.isNullOrEmpty()) {
            backupBusy = false
            backupChecking = false
            return@rememberLauncherForActivityResult
        }
        backupBusy = true
        if (checkOnly) {
            backupChecking = true
            onVerifyBackup(uri.toString(), secret) { outcome ->
                backupBusy = false
                backupChecking = false
                if (outcome.problem == null) {
                    backupError = null
                    restoreNote = "Backup checked: ${outcome.photos} photos can be restored"
                } else {
                    restoreNote = null
                    backupError = outcome.problem
                }
            }
            return@rememberLauncherForActivityResult
        }
        onRestoreBackup(uri.toString(), secret) { outcome ->
            backupBusy = false
            if (outcome.problem == null) {
                val extra = if (outcome.skipped > 0) " ${outcome.skipped} already here." else ""
                Toast.makeText(context, "Restored ${outcome.added} photos.$extra", Toast.LENGTH_LONG).show()
                importBackup = false
                backupError = null
            } else {
                backupError = outcome.problem
            }
        }
    }
    val createBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val secret = pendingBackupPassword
        pendingBackupPassword = null
        if (uri == null || secret.isNullOrEmpty()) {
            backupBusy = false
            return@rememberLauncherForActivityResult
        }
        backupBusy = true
        onBackup(uri.toString(), secret) { outcome ->
            backupBusy = false
            if (outcome.problem == null && outcome.saved > 0) {
                backupError = null
                checkNote = null
                checkFailed = false
                checkSecret = secret
                backupSaved = SavedBackup(uri.toString(), outcome.fileName, outcome.saved, outcome.left)
            } else {
                backupError = outcome.problem ?: "Couldn't write that backup"
            }
        }
    }
    val openPicker = {
        if (hasPhotoAccess(context)) {
            choosingDevice = true
        } else {
            AutoLock.expectExternal()
            photoAccess.launch(photoPermissions())
        }
    }
    val openRename = {
        val current = state.photos.getOrNull(state.activeIndex)
        if (current != null) {
            draft = current.name
            editing = true
        }
    }
    val requestTrash = {
        if (state.photos.isNotEmpty()) {
            if (trashExplained()) onTrash(state.activeIndex) else explainingTrash = true
        }
    }
    val finishExport: (Set<String>) -> Unit = { ids ->
        restoreOpen = false
        onExport(ids) { count ->
            val message = if (count > 0) {
                "Restored to Pictures/Nyte Gallery"
            } else {
                "Couldn't restore those photos"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) finishExport(pendingExportIds) else Toast.makeText(context, "Storage permission is needed to restore photos", Toast.LENGTH_SHORT).show()
    }
    val beginExport: (Set<String>) -> Unit = { ids ->
        if (ids.isNotEmpty()) {
            val needsPermission = Build.VERSION.SDK_INT <= 28 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                pendingExportIds = ids
                AutoLock.expectExternal()
                writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                finishExport(ids)
            }
        }
    }

    LaunchedEffect(backupOpen) {
        if (!backupOpen) {
            backupSaved = null
            checkSecret = null
            checkNote = null
            checkFailed = false
            backupGuide = false
        }
    }
    LaunchedEffect(importBackup) {
        if (!importBackup) restoreNote = null
    }

    LaunchedEffect(locked) {
        // Lock set up before recovery codes (email), or a code that was never confirmed: make one now.
        if (!locked && !resettingLock && !makingRecovery && lockStore.needsRecoveryCode()) {
            showNewRecoveryCode(
                if (lockStore.hadEmailRecovery()) {
                    "Nyte Gallery no longer uses email to recover your lock. It now uses a recovery code that never leaves this phone. Your old recovery email has been removed."
                } else {
                    "Your recovery code was not confirmed, so here is a new one. The old one no longer works."
                },
                false,
            )
        }
        if (locked) {
            menuOpen = false
            backupOpen = false
            importBackup = false
            settingsOpen = false
            aboutOpen = false
            changingLock = null
        }
    }

    LaunchedEffect(state.photos.isEmpty()) {
        if (state.photos.isEmpty()) {
            galleryOpen = false
            restoreOpen = false
        }
    }

    var askedNotify by remember { mutableStateOf(false) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(transfer != null) {
        if (transfer != null && !askedNotify && Build.VERSION.SDK_INT >= 33) {
            askedNotify = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                AutoLock.expectExternal()
                askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            transfer != null -> Unit
            makingRecovery -> Unit
            resettingLock -> Unit
            settingLock -> settingLock = false
            confirmingLock -> confirmingLock = false
            locked -> Unit
            originalsToAsk > 0 -> keepOriginals()
            changingLock != null -> changingLock = null
            backupOpen -> if (!backupBusy) backupOpen = false
            importBackup -> if (!backupBusy) importBackup = false
            aboutOpen -> aboutOpen = false
            settingsOpen -> settingsOpen = false
            menuOpen -> menuOpen = false
            choosingDevice -> choosingDevice = false
            viewing -> viewing = false
            editing -> editing = false
            explainingTrash -> explainingTrash = false
            emptyingTrash -> emptyingTrash = false
            restoreOpen -> restoreOpen = false
            trashOpen -> trashOpen = false
            galleryOpen -> galleryOpen = false
            else -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now < exitArmedUntil[0]) {
                    (context as? Activity)?.finish()
                } else {
                    exitArmedUntil[0] = now + 2_000L
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val wash = if (light) {
        Brush.verticalGradient(listOf(Color(0xB8FBF9F6), Color(0x9EE7E1D8)))
    } else {
        Brush.verticalGradient(listOf(Color(0xB823262E), Color(0x8012141A), Color(0xC70D0E12)))
    }
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val chrome = if (light) Color.White else Color(0xFF1C1E26)
    val chromeInk = if (light) Color(0xFF2A2428) else Color.White

    Box(Modifier.fillMaxSize().background(if (light) Color(0xFFFBF9F6) else Color(0xFF12141A))) {
        Image(
            painter = painterResource(if (light) R.drawable.bg_light else R.drawable.bg_dark),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.5f),
        )
        Box(Modifier.fillMaxSize().background(wash))
        val shape = windowShape()
        val stage: @Composable (Modifier) -> Unit = { boxModifier ->
            Box(
                boxModifier
                    .pointerInput(state.photos.size) {
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                tracker.resetTracking()
                                onDragStart()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                onDrag(dragAmount)
                            },
                            onDragEnd = { onDragEnd(tracker.calculateVelocity().x) },
                            onDragCancel = { onDragEnd(0f) },
                        )
                    },
            ) {
                Stage(
                    state = state,
                    light = light,
                    compact = galleryOpen,
                    showPencil = galleryOpen,
                    showActions = !galleryOpen && !trashOpen,
                    ink = ink,
                    onEdit = openRename,
                    onDelete = requestTrash,
                    onOpen = { viewing = true },
                    panelActions = {
                        LandscapePanelActions(
                            chrome = chrome,
                            chromeInk = chromeInk,
                            onAdd = openPicker,
                            onRestore = { restoreOpen = true },
                        )
                    },
                )
            }
        }
        if (shape == WindowShape.Landscape && galleryOpen && !trashOpen) {
            Row(Modifier.fillMaxSize()) {
                stage(Modifier.weight(0.5f).fillMaxHeight())
                PhotoGrid(
                    photos = state.photos,
                    activeIndex = state.activeIndex,
                    light = light,
                    onAdd = openPicker,
                    onPick = onShowIndex,
                    inset = true,
                    modifier = Modifier.weight(0.5f).fillMaxHeight(),
                )
            }
        } else Column(Modifier.fillMaxSize()) {
            if (!trashOpen) {
                stage(Modifier.weight(if (galleryOpen) 0.6f else 1f).fillMaxWidth())
            }
            if (trashOpen) {
                TrashGrid(
                    photos = state.trash,
                    light = light,
                    onRestore = onRestore,
                    onEmpty = { emptyingTrash = true },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else if (galleryOpen) {
                PhotoGrid(
                    photos = state.photos,
                    activeIndex = state.activeIndex,
                    light = light,
                    onAdd = openPicker,
                    onPick = onShowIndex,
                    modifier = Modifier.weight(0.4f).fillMaxWidth(),
                )
            }
        }

        ChromeButton(
            container = chrome,
            icon = chromeInk,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 12.dp, start = 12.dp),
            onClick = { confirmingLock = true },
        ) { LockGlyph() }

        if (!locked) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(top = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChromeButton(
                    container = chrome,
                    icon = chromeInk,
                    modifier = Modifier,
                    onClick = { light = !light },
                ) { SunMoonIcon(light = light) }
                ChromeButton(
                    container = chrome,
                    icon = chromeInk,
                    modifier = Modifier,
                    onClick = {
                        backupError = null
                        menuOpen = true
                    },
                ) { MenuGlyph() }
            }
        } else {
            ChromeButton(
                container = chrome,
                icon = chromeInk,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(top = 12.dp, end = 12.dp),
                onClick = { light = !light },
            ) { SunMoonIcon(light = light) }
        }

        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.trash.isNotEmpty()) {
                ChromeButton(
                    container = chrome,
                    icon = chromeInk,
                    modifier = Modifier,
                    onClick = {
                        trashOpen = !trashOpen
                        if (trashOpen) galleryOpen = false
                    },
                ) { TrashGlyph() }
            }
            if (state.photos.isNotEmpty()) {
                ChromeButton(
                    container = chrome,
                    icon = chromeInk,
                    modifier = Modifier,
                    onClick = {
                        trashOpen = false
                        galleryOpen = !galleryOpen
                    },
                ) { GalleryGlyph() }
            }
        }

        // Landscape with the list closed: Add Images and Restore sit in the side panel instead.
        val actionsInPanel = shape == WindowShape.Landscape && !galleryOpen && !trashOpen &&
            state.photos.getOrNull(state.activeIndex) != null
        if (state.photos.isNotEmpty() && !actionsInPanel) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(start = 12.dp, bottom = 12.dp)
                    .height(44.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(chrome)
                    .clickable { restoreOpen = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Restore", color = chromeInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        val addAtBottom = state.photos.isNotEmpty() || (trashOpen && state.trash.isNotEmpty())
        if (!actionsInPanel) Row(
            (if (addAtBottom) {
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(bottom = 12.dp)
            } else {
                Modifier.align(Alignment.Center)
            })
                .height(44.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(AddGreen)
                .clickable(onClick = openPicker)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(16.dp)) { PlusMark() }
            Text("Add Images", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        if (editing) {
            RenameOverlay(
                light = light,
                value = draft,
                onChange = { draft = it },
                onDismiss = { editing = false },
                onSave = {
                    onRename(state.activeIndex, draft)
                    editing = false
                },
            )
        }
        if (explainingTrash) {
            NoticeOverlay(
                light = light,
                title = "MOVE TO TRASH",
                body = "This image goes to Trash. Trash deletes it automatically after 30 days. You can restore it before then.",
                onDismiss = { explainingTrash = false },
                onConfirm = {
                    onTrashExplained()
                    explainingTrash = false
                    onTrash(state.activeIndex)
                },
            )
        }
        if (emptyingTrash) {
            EmptyTrashOverlay(
                light = light,
                onDismiss = { emptyingTrash = false },
                onConfirm = {
                    onEmptyTrash()
                    emptyingTrash = false
                    trashOpen = false
                },
            )
        }
        if (restoreOpen) {
            RestorePage(
                photos = state.photos,
                light = light,
                onClose = { restoreOpen = false },
                onExport = beginExport,
            )
        }
        val openPhoto = state.photos.getOrNull(state.activeIndex)
        if (viewing && openPhoto != null && !locked) {
            FullView(photo = openPhoto, onClose = { viewing = false })
        }
        if (choosingDevice && !locked) {
            DeviceImportPage(
                light = light,
                onClose = { choosingDevice = false },
                onMove = { uris ->
                    choosingDevice = false
                    galleryOpen = true
                    onImport(uris.map { it.toString() }) { outcome ->
                        if (outcome.imported == 0) {
                            Toast.makeText(context, "Couldn't read those photos", Toast.LENGTH_SHORT).show()
                        } else {
                            originalsToAsk = outcome.imported
                        }
                    }
                },
            )
        }
        if (originalsToAsk > 0 && !locked && transfer == null) {
            RemoveOriginalsOverlay(
                light = light,
                count = originalsToAsk,
                systemConfirms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                onRemove = removeOriginals,
                onKeep = keepOriginals,
            )
        }
        if (confirmingLock && !locked) {
            LockConfirm(
                light = light,
                body = if (lockStore.configured()) {
                    "Lock Nyte Gallery now? Opening it again needs one of the ways you set."
                } else {
                    "Lock Nyte Gallery? Next you will set at least two ways to unlock, and get a recovery code."
                },
                confirm = if (lockStore.configured()) "Lock" else "Continue",
                onDismiss = { confirmingLock = false },
                onConfirm = {
                    confirmingLock = false
                    if (lockStore.configured()) locked = true else settingLock = true
                },
            )
        }
        if (settingLock) {
            LockSetupPage(
                light = light,
                onClose = { settingLock = false },
                onSave = { password, pin, pattern, finger ->
                    withContext(Dispatchers.Default) { lockStore.save(password, pin, pattern, finger) }
                    settingLock = false
                    showNewRecoveryCode(null, true)
                },
            )
        }
        if (resettingLock && !locked) {
            LockSetupPage(
                light = light,
                onClose = {
                    resettingLock = false
                    showNewRecoveryCode(null, false)
                },
                onSave = { password, pin, pattern, finger ->
                    withContext(Dispatchers.Default) { lockStore.save(password, pin, pattern, finger) }
                    resettingLock = false
                    showNewRecoveryCode(null, false)
                },
                title = "NEW LOCK",
                intro = "You used your recovery code. Choose at least two new ways to get back in: password, PIN, pattern, or fingerprint. Next you get a new recovery code, and the old one stops working.",
                saveLabel = "Save new lock",
            )
        }
        if (menuOpen && !locked) {
            MenuSidebar(
                light = light,
                onClose = { menuOpen = false },
                onBackup = {
                    backupError = null
                    menuOpen = false
                    backupGuide = !backupPrefs.guideSeen()
                    backupOpen = true
                },
                onRestore = {
                    backupError = null
                    restoreNote = null
                    menuOpen = false
                    importBackup = true
                },
                onSettings = {
                    menuOpen = false
                    settingsOpen = true
                },
                onAbout = {
                    menuOpen = false
                    aboutOpen = true
                },
            )
        }
        // Hidden (not closed) while Backup or Restore backup is on top, so Back returns here.
        if (settingsOpen && !locked && !backupOpen && !importBackup && changingLock == null) {
            SettingsPage(
                light = light,
                lockOn = lockStore.configured(),
                lastBackupAt = backupPrefs.lastBackupAt(),
                lastBackupPhotos = backupPrefs.lastBackupPhotos(),
                onClose = { settingsOpen = false },
                onRecycleBin = {
                    if (state.trash.isEmpty()) {
                        Toast.makeText(context, "Trash is empty", Toast.LENGTH_SHORT).show()
                    } else {
                        settingsOpen = false
                        galleryOpen = false
                        trashOpen = true
                    }
                },
                onAppLock = {
                    settingsOpen = false
                    confirmingLock = true
                },
                onChangeLock = { changingLock = "lock" },
                onRecoveryCode = { changingLock = "code" },
                onBackup = {
                    backupError = null
                    backupGuide = !backupPrefs.guideSeen()
                    backupOpen = true
                },
                onRestoreBackup = {
                    backupError = null
                    restoreNote = null
                    importBackup = true
                },
                onCheckBackup = {
                    backupError = null
                    restoreNote = null
                    importBackup = true
                },
            )
        }
        if (changingLock == "lock" && !locked) {
            LockChangePage(
                light = light,
                store = lockStore,
                onClose = { changingLock = null },
                onChanged = {
                    changingLock = null
                    Toast.makeText(context, "Lock updated", Toast.LENGTH_SHORT).show()
                },
                onTurnedOff = {
                    changingLock = null
                    Toast.makeText(context, "App lock is off", Toast.LENGTH_SHORT).show()
                },
            )
        } else if (changingLock == "code" && !locked) {
            RecoveryCodeSettingsPage(
                light = light,
                store = lockStore,
                onClose = { changingLock = null },
                onCreate = {
                    changingLock = null
                    showNewRecoveryCode(null, false)
                },
            )
        }
        if (aboutOpen && !locked) {
            AboutPage(light = light, onClose = { aboutOpen = false })
        }
        val saved = backupSaved
        if (backupOpen && !locked && saved != null) {
            BackupSavedPage(
                light = light,
                fileName = saved.fileName,
                saved = saved.saved,
                left = saved.left,
                checking = backupChecking,
                canCheck = checkSecret != null,
                checkNote = checkNote,
                checkFailed = checkFailed,
                onCheck = {
                    val secret = checkSecret
                    if (secret != null && !backupChecking) {
                        backupChecking = true
                        backupBusy = true
                        onVerifyBackup(saved.uri, secret) { outcome ->
                            backupChecking = false
                            backupBusy = false
                            checkSecret = null
                            checkFailed = outcome.problem != null
                            checkNote = outcome.problem ?: "Backup checked: ${outcome.photos} photos can be restored"
                        }
                    }
                },
                onDone = { if (!backupChecking) backupOpen = false },
            )
        } else if (backupOpen && !locked) {
            BackupPage(
                light = light,
                busy = backupBusy,
                error = backupError,
                showGuide = backupGuide,
                onGuideDone = {
                    backupPrefs.markGuideSeen()
                    backupGuide = false
                },
                onClose = { if (!backupBusy) backupOpen = false },
                onCreate = { secret ->
                    pendingBackupPassword = secret
                    backupError = null
                    AutoLock.expectExternal()
                    createBackupFile.launch("nyte-backup.nybak")
                },
            )
        }
        if (importBackup && !locked) {
            RestoreBackupPage(
                light = light,
                busy = backupBusy,
                error = backupError,
                note = restoreNote,
                checking = backupChecking,
                onClose = { if (!backupBusy) importBackup = false },
                onPick = { secret ->
                    pendingBackupPassword = secret
                    pendingCheckOnly = false
                    backupError = null
                    restoreNote = null
                    AutoLock.expectExternal()
                    openBackupFile.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                },
                onCheck = { secret ->
                    pendingBackupPassword = secret
                    pendingCheckOnly = true
                    backupError = null
                    restoreNote = null
                    AutoLock.expectExternal()
                    openBackupFile.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                },
            )
        }
        val progress = transfer
        if (progress != null) {
            TransferDialog(progress)
        }
        if (locked) {
            LockGate(
                light = light,
                store = lockStore,
                onUnlocked = { locked = false },
                onRecovered = {
                    resettingLock = true
                    locked = false
                },
            )
        }
        if (makingRecovery && !locked) {
            RecoveryCodePage(light = light, code = shownRecoveryCode, note = recoveryNote) {
                lockStore.markRecoverySaved()
                makingRecovery = false
                shownRecoveryCode = null
                recoveryNote = null
                if (lockAfterRecovery) {
                    lockAfterRecovery = false
                    locked = true
                }
            }
        }
        // Later and Skip fade the welcome pages out into home; Get started hands over to lock setup at once.
        var welcomeFade by remember { mutableStateOf(false) }
        androidx.compose.animation.AnimatedVisibility(
            visible = onboarding && !locked,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = if (welcomeFade) {
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(durationMillis = 420))
            } else {
                androidx.compose.animation.ExitTransition.None
            },
        ) {
            WelcomePages(
                light = light,
                onSkip = {
                    welcomeFade = true
                    onboarding = false
                    onOnboardingDone()
                },
                onGetStarted = {
                    welcomeFade = false
                    onboarding = false
                    onOnboardingDone()
                    // Same path as the lock button's "Continue": lock setup, then the recovery code, then locked.
                    settingLock = true
                },
                onLater = {
                    welcomeFade = true
                    onboarding = false
                    onOnboardingDone()
                },
            )
        }
    }
}

@Composable
private fun EmptyTrashOverlay(
    light: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val panel = if (light) Color(0xFFF7F3EE) else Color(0xFF1C1E26)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val field = if (light) Color(0xFFFBF9F6) else Color(0xFF12141A)
    var typed by remember { mutableStateOf("") }
    val ready = typed.trim().equals("delete", ignoreCase = true)
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(70f)
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .wideCap()
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("EMPTY TRASH", color = ink, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(
                "This permanently deletes every photo in Trash. Type delete, then press OK.",
                color = ink.copy(alpha = 0.82f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(field)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (typed.isEmpty()) {
                    Text("delete", color = ink.copy(alpha = 0.35f), fontSize = 16.sp)
                }
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    textStyle = TextStyle(color = ink, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(DotA),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (ready) DotA else ink.copy(alpha = 0.16f))
                    .clickable(enabled = ready, onClick = onConfirm)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("OK", color = if (ready) Color.White else ink.copy(alpha = 0.4f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TransferDialog(progress: TransferProgress) {
    val fraction = if (progress.total <= 0) 0f else progress.done.toFloat() / progress.total
    val percent = if (progress.total <= 0) null else (progress.done * 100 / progress.total)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 32.dp)
                    .wideCap()
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1C1E26))
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (progress.total <= 0) {
                    CircularProgressIndicator(color = DotA)
                } else {
                    CircularProgressIndicator(progress = { fraction }, color = DotA)
                }
                Spacer(Modifier.height(16.dp))
                Text(progress.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (percent == null) "This can take a while" else "${progress.done} of ${progress.total} · $percent%",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun RestorePage(
    photos: List<GalleryPhoto>,
    light: Boolean,
    onClose: () -> Unit,
    onExport: (Set<String>) -> Unit,
) {
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    var selected by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(photos) {
        val ids = photos.map { it.id }.toSet()
        selected = selected.intersect(ids)
    }
    Column(
        Modifier
            .fillMaxSize()
            .zIndex(60f)
            .background(panel)
            .windowInsetsPadding(WindowInsets.systemBars)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "RESTORE",
                color = ink,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            val allIds = photos.map { it.id }.toSet()
            val allOn = allIds.isNotEmpty() && allIds.all { it in selected }
            Text(
                if (allOn) "Clear" else "Select all",
                color = ink.copy(alpha = if (photos.isEmpty()) 0.35f else 0.72f),
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable(enabled = photos.isNotEmpty()) {
                        selected = if (allOn) emptySet() else allIds
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                "Close",
                color = ink.copy(alpha = 0.72f),
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onClose).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Text(
            "Restoring writes these photos back onto this phone, in Pictures/Nyte Gallery. Other apps can open them, and they leave Nyte Gallery.",
            color = ink.copy(alpha = 0.78f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        LazyVerticalGrid(
            columns = vaultGridColumns(),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { _, photo ->
                val on = photo.id in selected
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(photoBase(light))
                        .clickable { selected = if (on) selected - photo.id else selected + photo.id },
                ) {
                    PhotoImage(photo, Modifier.fillMaxSize(), maxEdge = VAULT_THUMB_EDGE, shimmerLight = light)
                    if (on) {
                        Box(Modifier.fillMaxSize().background(Color(0x552F9B68)))
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(AddGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val ready = selected.isNotEmpty()
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (ready) AddGreen else ink.copy(alpha = 0.15f))
                    .clickable(enabled = ready) { onExport(selected) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Restore Selected",
                    color = if (ready) Color.White else ink.copy(alpha = 0.4f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AddGreen)
                    .clickable { onExport(photos.map { it.id }.toSet()) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Restore All", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Stage(
    state: GalleryUiState,
    light: Boolean,
    compact: Boolean,
    showPencil: Boolean,
    showActions: Boolean,
    ink: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    panelActions: @Composable () -> Unit = {},
) {
    val active = state.photos.getOrNull(state.activeIndex)
    Box(Modifier.fillMaxSize()) {
        BoxWithConstraintsFill(
            compact = compact,
            photos = state.photos,
            rotation = state.rotation,
            activeIndex = state.activeIndex,
            onOpen = onOpen,
            light = light,
        )
        Text(
            text = "NYTE GALLERY",
            color = if (light) Color(0xFF5C463F) else Color(0xFFF3ECE8),
            fontSize = 13.sp,
            letterSpacing = 4.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 18.dp),
        )
        val shape = windowShape()
        val sideInfo = shape == WindowShape.Landscape && !compact
        if (active != null && sideInfo) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(1f - LandscapeStageShare)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(start = 8.dp, end = 24.dp, top = 64.dp, bottom = 64.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                ActiveLabel(active, ink, showPencil, onEdit)
                if (showActions) {
                    Spacer(Modifier.height(18.dp))
                    ActionRow(Modifier, ink, onEdit, onDelete)
                    Spacer(Modifier.height(28.dp))
                    panelActions()
                }
            }
        } else if (active != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (shape == WindowShape.Landscape) {
                            Modifier.windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 60.dp, start = 24.dp, end = 24.dp)
                        } else {
                            Modifier.padding(bottom = if (compact) 8.dp else 78.dp, start = 56.dp, end = 56.dp)
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ActiveLabel(active, ink, showPencil, onEdit)
            }
        }
        if (showActions && active != null && !sideInfo) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val cardWidth = cardFitFor(maxWidth, maxHeight, compact = false).width
                val cardHeight = cardWidth * 1.32f
                val visualScale = (0.62f + 0.5f) * 1.04f
                ActionRow(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = maxHeight / 2 + cardHeight / 2 * visualScale - 8.dp + 28.dp)
                        .zIndex(30f),
                    ink,
                    onEdit,
                    onDelete,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LandscapePanelActions(chrome: Color, chromeInk: Color, onAdd: () -> Unit, onRestore: () -> Unit) {
    // Wraps onto a second line on narrow landscape phones instead of clipping.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .height(44.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(AddGreen)
                .clickable(onClick = onAdd)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(16.dp)) { PlusMark() }
            Text("Add Images", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Box(
            Modifier
                .height(44.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(chrome)
                .clickable(onClick = onRestore)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Restore", color = chromeInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun ActiveLabel(active: GalleryPhoto, ink: Color, showPencil: Boolean, onEdit: () -> Unit) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = clipName(active.name, LABEL_LIMIT),
                    color = ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showPencil) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.1f))
                            .clickable(onClick = onEdit),
                        contentAlignment = Alignment.Center,
                    ) {
                        PencilIcon(ink)
                    }
                }
            }
            val taken = formatTaken(active.takenAt)
            if (taken.isNotEmpty()) {
                Text(
                    text = taken,
                    color = ink.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
}

@Composable
private fun ActionRow(modifier: Modifier, ink: Color, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(
            Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(DotA)
                .clickable(onClick = onDelete)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("delete", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        ActionChip(ink, onEdit) { PencilIcon(ink) }
    }
}

@Composable
private fun BoxWithConstraintsFill(
    compact: Boolean,
    photos: List<GalleryPhoto>,
    rotation: Float,
    activeIndex: Int,
    onOpen: () -> Unit,
    light: Boolean,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val phone = windowShape() == WindowShape.Phone
        val fit = cardFitFor(maxWidth, maxHeight, compact)
        val cardWidth = fit.width
        val cardHeight = cardWidth * 1.32f
        val radiusPx = with(density) {
            if (phone) (maxWidth * if (compact) 0.34f else 0.52f).toPx()
            else (cardWidth * if (compact) 0.34f / 0.42f else 0.52f / 0.62f).toPx()
        }
        val fullWidth = cardFitFor(maxWidth, maxHeight, compact = false).width
        val fillWidth = with(density) {
            if (phone) (maxWidth * 0.62f * 1.17f).roundToPx()
            else (maxOf(cardWidth, fullWidth) * 1.17f).roundToPx()
        }.coerceIn(1, 2048)
        val shiftX = fit.shiftX
        val shiftY = fit.shiftY
        val fillHeight = (fillWidth * 1.32f).roundToInt()
        val count = photos.size
        if (count == 0) return@BoxWithConstraints
        val center = rotation / (360f / count)
        val focus = Math.floorMod(center.roundToInt(), count)
        val offsets = when (count) {
            1 -> listOf(0)
            2 -> listOf(-1, 0)
            else -> listOf(-1, 0, 1)
        }
        offsets.forEach { offset ->
            val index = Math.floorMod(focus + offset, count)
            val photo = photos[index]
            var delta = index - center
            delta %= count
            if (delta > count / 2f) delta -= count
            if (delta < -count / 2f) delta += count
            val rel = delta * 58f
            val rad = Math.toRadians(rel.toDouble())
            val depth = cos(rad).toFloat()
            val scale = 0.62f + ((depth + 1f) / 2f) * 0.5f
            val fade = (1.15f - abs(rel) / 130f).coerceIn(0.15f, 1f)
            val boost = if (index == activeIndex) 1.04f else 1f
            val frame = cardHeight + CardHeader
            PortraitWindow(
                photo = photo,
                featured = index == activeIndex,
                onOpen = onOpen,
                light = light,
                fillWidth = fillWidth,
                fillHeight = fillHeight,
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(depth)
                    .width(cardWidth)
                    .height(frame)
                    .graphicsLayer {
                        translationX = sin(rad).toFloat() * radiusPx + shiftX.toPx()
                        translationY = if (shiftY == null) {
                            (1f - depth) * 28.dp.toPx() - 8.dp.toPx() - (CardHeader / 2).toPx() +
                                if (compact) 36.dp.toPx() else 0f
                        } else {
                            (1f - depth) * 28.dp.toPx() + shiftY.toPx()
                        }
                        rotationY = (-rel * 0.72f).coerceIn(-62f, 62f)
                        rotationZ = rel * 0.045f
                        scaleX = scale * boost
                        scaleY = scale * boost
                        alpha = fade
                        cameraDistance = 16.dp.toPx()
                        transformOrigin = TransformOrigin(
                            0.5f,
                            (CardHeader + cardHeight / 2).value / frame.value,
                        )
                        clip = false
                    },
            )
        }
    }
}

@Composable
private fun PortraitWindow(
    photo: GalleryPhoto,
    featured: Boolean,
    onOpen: () -> Unit,
    fillWidth: Int,
    fillHeight: Int,
    light: Boolean,
    modifier: Modifier = Modifier,
) {
    val headerColor = if (light) Paper else Color(0xFF262A35)
    val headerInk = if (light) Ink else Color.White
    val nameAlpha = if (light) 0.72f else 0.96f
    val yearAlpha = if (light) 0.55f else 0.82f
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(CardHeader)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(headerColor)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(DotA)
            Spacer(Modifier.width(5.dp))
            Dot(DotB)
            Spacer(Modifier.width(5.dp))
            Dot(DotC)
            Text(
                text = clipName(photo.name, CARD_LIMIT),
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                color = headerInk.copy(alpha = nameAlpha),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val year = yearOf(photo.takenAt)
            if (year.isNotEmpty()) {
                Text(
                    text = year,
                    color = headerInk.copy(alpha = yearAlpha),
                    fontSize = 12.sp,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(24.dp, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(photoBase(light)),
        ) {
            PhotoImage(photo, Modifier.fillMaxSize(), maxEdge = fillWidth, fillHeight = fillHeight, shimmerLight = light)
            if (featured) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.48f))
                        .clickable(onClick = onOpen),
                    contentAlignment = Alignment.Center,
                ) {
                    LensGlyph()
                }
            }
        }
    }
}

@Composable
private fun FullView(photo: GalleryPhoto, onClose: () -> Unit) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 6f)
        scale = next
        offset = if (next <= 1.01f) {
            androidx.compose.ui.geometry.Offset.Zero
        } else {
            val moved = offset + pan
            val maxX = box.width * (next - 1f) / 2f
            val maxY = box.height * (next - 1f) / 2f
            androidx.compose.ui.geometry.Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { box = it }) {
        PhotoImage(
            photo = photo,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transform),
            contentScale = ContentScale.Fit,
            maxEdge = 2400,
        )
        Text(
            text = "Nyte Gallery",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 16.dp),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 8.dp, end = 12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) {
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(Color.White, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun LensGlyph() {
    Canvas(Modifier.size(18.dp)) {
        val color = Color.White
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.7.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        val radius = size.minDimension * 0.28f
        val center = Offset(size.width * 0.40f, size.height * 0.40f)
        drawCircle(color, radius = radius, center = center, style = stroke)
        drawLine(
            color,
            start = center + Offset(radius * 0.72f, radius * 0.72f),
            end = Offset(size.width * 0.84f, size.height * 0.84f),
            strokeWidth = stroke.width,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val vaultDecodeDispatcher = Dispatchers.IO.limitedParallelism(2)

@Composable
private fun PhotoImage(
    photo: GalleryPhoto,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxEdge: Int = 1080,
    fillHeight: Int = maxEdge,
    // Card/grid placeholders: soft moving highlight while the photo decodes (light picks the highlight strength).
    shimmerLight: Boolean? = null,
) {
    if (photo.vaultFile != null) {
        val context = LocalContext.current
        val fill = contentScale == ContentScale.Crop
        var bitmap by remember(photo.vaultFile, maxEdge, fillHeight, fill) { mutableStateOf<ImageBitmap?>(null) }
        var failed by remember(photo.vaultFile, maxEdge, fillHeight, fill) { mutableStateOf(false) }
        LaunchedEffect(photo.vaultFile, maxEdge, fillHeight, fill) {
            val decoded = withContext(vaultDecodeDispatcher) {
                val store = VaultStore(context)
                if (fill) store.decodeFill(photo, maxEdge, fillHeight) else store.decode(photo, maxEdge)
            }
            bitmap = decoded?.asImageBitmap()
            failed = decoded == null
        }
        bitmap?.let {
            Image(it, contentDescription = photo.name, modifier = modifier, contentScale = contentScale)
        }
        if (shimmerLight != null) {
            val loading = bitmap == null && !failed
            // Short wait first, so photos that are already cached don't flash the shimmer.
            var armed by remember(photo.vaultFile, maxEdge, fillHeight, fill) { mutableStateOf(false) }
            LaunchedEffect(loading) {
                if (loading) {
                    kotlinx.coroutines.delay(150)
                    armed = true
                }
            }
            val shown by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (loading && armed) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 280),
                label = "shimmer",
            )
            if (shown > 0.01f) {
                LoadingShimmer(modifier.alpha(shown), shimmerLight)
            }
        }
    } else if (photo.imageRes != 0) {
        Image(
            painter = painterResource(photo.imageRes),
            contentDescription = photo.name,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

/** Placeholder color under a photo while it loads (or if it can't be read). */
private fun photoBase(light: Boolean): Color = if (light) Color(0xFFD9D3CC) else Color(0xFF2A2E38)

@Composable
private fun LoadingShimmer(modifier: Modifier, light: Boolean) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(durationMillis = 1300, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "shimmerProgress",
    )
    val glow = if (light) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.09f)
    Box(
        modifier.drawBehind {
            val band = size.width.coerceAtLeast(size.height) * 0.6f
            val travel = size.width + size.height + band * 2f
            val start = -band + travel * progress.value - size.height
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, glow, Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + band, size.height * 0.6f),
                ),
            )
        },
    )
}

@Composable
private fun PhotoGrid(
    photos: List<GalleryPhoto>,
    activeIndex: Int,
    light: Boolean,
    onAdd: () -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    inset: Boolean = false,
) {
    Column(
        modifier
            .background(if (light) Color(0xFFF7F4EF) else Color(0xFF0C0D11))
            .then(if (inset) Modifier.windowInsetsPadding(WindowInsets.systemBars) else Modifier),
    ) {
        LazyVerticalGrid(
            columns = vaultGridColumns(),
            modifier = Modifier.fillMaxSize().padding(6.dp),
            contentPadding = if (inset) PaddingValues(top = 56.dp, bottom = 60.dp) else PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (light) Color(0x14000000) else Color(0x14FFFFFF))
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = if (light) Color(0xFF5C463F) else Color.White, fontSize = 28.sp)
                }
            }
            itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(photoBase(light))
                        .clickable { onPick(index) },
                ) {
                    PhotoImage(photo, Modifier.fillMaxSize(), maxEdge = VAULT_THUMB_EDGE, shimmerLight = light)
                    if (index == activeIndex) {
                        Box(Modifier.fillMaxSize().background(Color(0x33FF4D6A)))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashGrid(
    photos: List<GalleryPhoto>,
    light: Boolean,
    onRestore: (String) -> Unit,
    onEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (light) Color(0xFF2A2428) else Color.White
    // Small green note after restoring; quick taps add up ("3 images restored").
    var restoredCount by remember { mutableStateOf(0) }
    var restoredStamp by remember { mutableStateOf(0L) }
    var noteText by remember { mutableStateOf("") }
    LaunchedEffect(restoredStamp) {
        if (restoredStamp != 0L) {
            kotlinx.coroutines.delay(2000)
            restoredCount = 0
        }
    }
    if (restoredCount > 0) {
        noteText = if (restoredCount == 1) "1 image restored" else "$restoredCount images restored"
    }
    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(if (light) Color(0xFFF7F4EF) else Color(0xFF0C0D11))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(top = 56.dp, bottom = 64.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Trash · deleted automatically after 30 days",
                color = ink.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Empty",
                color = DotA,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEmpty)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        LazyVerticalGrid(
            columns = vaultGridColumns(),
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { _, photo ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(photoBase(light)),
                ) {
                    PhotoImage(photo, Modifier.fillMaxSize(), maxEdge = VAULT_THUMB_EDGE, shimmerLight = light)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable {
                                onRestore(photo.id)
                                restoredCount += 1
                                restoredStamp = System.nanoTime()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        RestoreGlyph()
                    }
                }
            }
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = restoredCount > 0,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(bottom = 68.dp),
        enter = androidx.compose.animation.fadeIn() +
            androidx.compose.animation.slideInVertically(initialOffsetY = { it / 2 }),
        exit = androidx.compose.animation.fadeOut() +
            androidx.compose.animation.slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        Row(
            Modifier
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(AddGreen)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(noteText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    }
}

@Composable
private fun ActionChip(ink: Color, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides ink,
        ) {
            glyph()
        }
    }
}

@Composable
private fun RemoveOriginalsOverlay(
    light: Boolean,
    count: Int,
    systemConfirms: Boolean,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    val panel = if (light) Color(0xFFF7F3EE) else Color(0xFF1C1E26)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val title = if (count == 1) "1 photo is now safe in Nyte Gallery" else "$count photos are now safe in Nyte Gallery"
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(onClick = onKeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 28.dp, vertical = 12.dp)
                .wideCap()
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(panel)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AddGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                title,
                color = ink,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "They're encrypted and only open with your lock.",
                color = ink.copy(alpha = 0.82f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The original copies are still in your phone's gallery, where anyone can see them. Remove them so these photos are only in Nyte Gallery?",
                color = ink.copy(alpha = 0.82f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (systemConfirms) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Android will ask you to confirm on the next screen.",
                    color = ink.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DotA)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text("Remove originals", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ink.copy(alpha = 0.12f))
                    .clickable(onClick = onKeep),
                contentAlignment = Alignment.Center,
            ) {
                Text("Keep originals", color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun NoticeOverlay(
    light: Boolean,
    title: String,
    body: String,
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
                .padding(horizontal = 28.dp)
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
            Text(body, color = ink.copy(alpha = 0.82f), fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(DotA)
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Move to Trash", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PlusMark() {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = this.size.minDimension * 0.12f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            Color.White,
            Offset(this.size.width * 0.18f, this.size.height / 2f),
            Offset(this.size.width * 0.82f, this.size.height / 2f),
            stroke.width,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            Color.White,
            Offset(this.size.width / 2f, this.size.height * 0.18f),
            Offset(this.size.width / 2f, this.size.height * 0.82f),
            stroke.width,
            androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun ChromeButton(
    container: Color,
    icon: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(44.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { }) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides icon,
            ) {
                glyph()
            }
        }
    }
}

@Composable
private fun SaveIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        drawRoundRect(
            color,
            topLeft = Offset(size.width * 0.16f, size.height * 0.14f),
            size = Size(size.width * 0.68f, size.height * 0.72f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = 1.8.dp.toPx()),
        )
        drawRect(
            color,
            topLeft = Offset(size.width * 0.34f, size.height * 0.14f),
            size = Size(size.width * 0.32f, size.height * 0.22f),
        )
        drawLine(
            color,
            Offset(size.width * 0.3f, size.height * 0.68f),
            Offset(size.width * 0.7f, size.height * 0.68f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun RenameOverlay(
    light: Boolean,
    value: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var field by remember(value) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    LaunchedEffect(value) {
        field = TextFieldValue(value, TextRange(value.length))
        focus.requestFocus()
    }
    val panel = if (light) Color(0xFFF7F3EE) else Color(0xFF1C1E26)
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val fieldColor = if (light) Color(0xFFFBF9F6) else Color(0xFF12141A)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .wideCap()
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(panel)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(DotA)
                Spacer(Modifier.width(5.dp))
                Dot(DotB)
                Spacer(Modifier.width(5.dp))
                Dot(DotC)
                Text(
                    "RENAME",
                    modifier = Modifier.padding(start = 10.dp),
                    color = ink.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    onChange(it.text)
                },
                textStyle = TextStyle(color = ink, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(DotA),
                modifier = Modifier
                    .focusRequester(focus)
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(fieldColor)
                    .padding(14.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ink.copy(alpha = 0.12f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Close", color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(DotA)
                        .clickable(onClick = onSave),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(9.dp).background(color, CircleShape))
}

@Composable
private fun PencilIcon(color: Color) {
    val path = remember {
        PathParser().parsePathString(
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
        ).toPath()
    }
    Canvas(Modifier.size(16.dp)) {
        withTransform({
            scale(size.minDimension / 24f, size.minDimension / 24f, Offset.Zero)
        }) {
            drawPath(path, color)
        }
    }
}

@Composable
private fun SunMoonIcon(light: Boolean) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        if (!light) {
            drawCircle(color, radius = size.minDimension * 0.18f, style = stroke)
            val inner = size.minDimension * 0.30f
            val outer = size.minDimension * 0.44f
            repeat(8) { i ->
                val a = Math.toRadians(i * 45.0)
                drawLine(
                    color,
                    center + Offset((cos(a) * inner).toFloat(), (sin(a) * inner).toFloat()),
                    center + Offset((cos(a) * outer).toFloat(), (sin(a) * outer).toFloat()),
                    stroke.width,
                    StrokeCap.Round,
                )
            }
        } else {
            val radius = size.minDimension * 0.32f
            val moonCenter = Offset(size.width * 0.38f, size.height * 0.50f)
            val moon = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        moonCenter - Offset(radius, radius),
                        Size(radius * 2f, radius * 2f),
                    ),
                )
            }
            val biteRadius = radius * 0.78f
            val biteCenter = moonCenter + Offset(radius * 0.55f, -radius * 0.05f)
            val bite = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        biteCenter - Offset(biteRadius, biteRadius),
                        Size(biteRadius * 2f, biteRadius * 2f),
                    ),
                )
            }
            withTransform({
                translate(left = size.width * 0.10f, top = 0f)
            }) {
                clipPath(bite, clipOp = ClipOp.Difference) {
                    drawPath(moon, color)
                }
            }
        }
    }
}

@Composable
private fun TrashGlyph() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawLine(color, Offset(size.width * 0.16f, size.height * 0.24f), Offset(size.width * 0.84f, size.height * 0.24f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.24f), Offset(size.width * 0.44f, size.height * 0.12f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.60f, size.height * 0.24f), Offset(size.width * 0.56f, size.height * 0.12f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.44f, size.height * 0.12f), Offset(size.width * 0.56f, size.height * 0.12f), stroke.width, StrokeCap.Round)
        drawRoundRect(
            color,
            topLeft = Offset(size.width * 0.24f, size.height * 0.30f),
            size = Size(size.width * 0.52f, size.height * 0.56f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = stroke,
        )
        drawLine(color, Offset(size.width * 0.40f, size.height * 0.40f), Offset(size.width * 0.40f, size.height * 0.74f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.60f, size.height * 0.40f), Offset(size.width * 0.60f, size.height * 0.74f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun RestoreGlyph() {
    Canvas(Modifier.size(16.dp)) {
        val color = Color.White
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawLine(color, Offset(size.width * 0.22f, size.height * 0.72f), Offset(size.width * 0.78f, size.height * 0.72f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.50f, size.height * 0.22f), Offset(size.width * 0.50f, size.height * 0.62f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.34f, size.height * 0.38f), Offset(size.width * 0.50f, size.height * 0.22f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.66f, size.height * 0.38f), Offset(size.width * 0.50f, size.height * 0.22f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun GalleryGlyph() {
    val color = androidx.compose.material3.LocalContentColor.current
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.16f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.68f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
        )
        drawCircle(color, radius = 2.2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.38f))
    }
}

private fun clipName(text: String, max: Int): String {
    val clean = text.replace(Regex("\\.[A-Za-z0-9]+$"), "")
    if (clean.length <= max) return clean
    return clean.take(max).trimEnd() + "..."
}

private data class SavedBackup(val uri: String, val fileName: String?, val saved: Int, val left: Int)
