package com.nytefall.nytegallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nytefall.nytegallery.R
import kotlinx.coroutines.launch

private val WelcomePink = Color(0xFFFF4D6A)

private class WelcomePage(val title: String, val body: String)

private val WELCOME_PAGES = listOf(
    WelcomePage(
        "Your private photo vault",
        "Photos you move into Nyte Gallery are encrypted and open only with your lock.",
    ),
    WelcomePage(
        "Stays on your phone",
        "No internet, no account, no ads. Nothing you put here leaves your device unless you save a backup somewhere else.",
    ),
    WelcomePage(
        "Set your lock",
        "Choose at least two ways to unlock, and get a recovery code for the day you forget them.",
    ),
)

/**
 * First-launch welcome: three swipeable pages. [onSkip] and [onLater] go home, [onGetStarted] opens lock setup.
 * The caller marks onboarding as done in every case.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WelcomePages(
    light: Boolean,
    onSkip: () -> Unit,
    onGetStarted: () -> Unit,
    onLater: () -> Unit,
) {
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    val pager = rememberPagerState(pageCount = { WELCOME_PAGES.size })
    val scope = rememberCoroutineScope()
    val shape = windowShape()
    val landscape = shape == WindowShape.Landscape
    val last = pager.currentPage == WELCOME_PAGES.lastIndex

    // Back goes to the previous page. On the first page the gallery's own back handling runs (press back twice to exit).
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(panel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .windowInsetsPadding(WindowInsets.stableBars),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.fillMaxSize().wideCap()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "NYTE GALLERY",
                    color = ink.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                Text(
                    "Skip",
                    color = ink.copy(alpha = 0.75f),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onSkip).padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                WelcomePageContent(index, WELCOME_PAGES[index], ink, field, landscape)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = if (landscape) 6.dp else 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(WELCOME_PAGES.size) { dot ->
                    val on = dot == pager.currentPage
                    val width by animateDpAsState(if (on) 22.dp else 8.dp, label = "dotWidth")
                    val color by animateColorAsState(if (on) WelcomePink else ink.copy(alpha = 0.25f), label = "dotColor")
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = if (landscape) 10.dp else 22.dp),
            ) {
                if (last) {
                    if (landscape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WelcomeButton("Later", Color.Transparent, ink, Modifier.weight(1f), border = ink.copy(alpha = 0.3f), onClick = onLater)
                            WelcomeButton("Get started", WelcomePink, Color.White, Modifier.weight(1f), onClick = onGetStarted)
                        }
                    } else {
                        WelcomeButton("Get started", WelcomePink, Color.White, Modifier.fillMaxWidth(), onClick = onGetStarted)
                        Spacer(Modifier.height(10.dp))
                        WelcomeButton("Later", Color.Transparent, ink, Modifier.fillMaxWidth(), border = ink.copy(alpha = 0.3f), onClick = onLater)
                    }
                } else {
                    WelcomeButton("Next", WelcomePink, Color.White, Modifier.fillMaxWidth()) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePageContent(index: Int, page: WelcomePage, ink: Color, field: Color, landscape: Boolean) {
    val art: @Composable (Dp) -> Unit = { size ->
        when (index) {
            0 -> Image(
                painter = painterResource(R.drawable.ic_launcher_art),
                contentDescription = "Nyte Gallery",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.27f)),
            )
            1 -> WelcomeBadge(size, field) { PhoneShieldGlyph(size * 0.58f, ink) }
            else -> WelcomeBadge(size, field) { BigLockGlyph(size * 0.5f, ink) }
        }
    }
    val texts: @Composable (Alignment.Horizontal, TextAlign) -> Unit = { align, textAlign ->
        Column(horizontalAlignment = align) {
            Text(page.title, color = ink, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = textAlign)
            Spacer(Modifier.height(12.dp))
            Text(page.body, color = ink.copy(alpha = 0.75f), fontSize = 16.sp, lineHeight = 23.sp, textAlign = textAlign)
        }
    }
    if (landscape) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            art(120.dp)
            Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                texts(Alignment.Start, TextAlign.Start)
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            art(168.dp)
            Spacer(Modifier.height(36.dp))
            texts(Alignment.CenterHorizontally, TextAlign.Center)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WelcomeBadge(size: Dp, field: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.27f)).background(field),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun WelcomeButton(
    label: String,
    background: Color,
    ink: Color,
    modifier: Modifier,
    border: Color? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    var base = modifier.height(48.dp).clip(shape).background(background)
    if (border != null) base = base.border(1.dp, border, shape)
    Box(base.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** A phone outline with a pink shield on its screen. */
@Composable
private fun PhoneShieldGlyph(size: Dp, ink: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            ink,
            topLeft = Offset(w * 0.24f, h * 0.06f),
            size = Size(w * 0.52f, h * 0.88f),
            cornerRadius = CornerRadius(w * 0.09f, w * 0.09f),
            style = stroke,
        )
        drawLine(ink.copy(alpha = 0.6f), Offset(w * 0.44f, h * 0.84f), Offset(w * 0.56f, h * 0.84f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.27f)
            lineTo(w * 0.66f, h * 0.33f)
            lineTo(w * 0.66f, h * 0.47f)
            cubicTo(w * 0.66f, h * 0.58f, w * 0.58f, h * 0.65f, w * 0.5f, h * 0.69f)
            cubicTo(w * 0.42f, h * 0.65f, w * 0.34f, h * 0.58f, w * 0.34f, h * 0.47f)
            lineTo(w * 0.34f, h * 0.33f)
            close()
        }
        drawPath(shield, WelcomePink)
        val tick = Path().apply {
            moveTo(w * 0.43f, h * 0.48f)
            lineTo(w * 0.485f, h * 0.535f)
            lineTo(w * 0.58f, h * 0.43f)
        }
        drawPath(tick, Color.White, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A large padlock: outline body, shackle and a pink keyhole. */
@Composable
private fun BigLockGlyph(size: Dp, ink: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
        val shackle = Path().apply {
            moveTo(w * 0.3f, h * 0.46f)
            lineTo(w * 0.3f, h * 0.32f)
            cubicTo(w * 0.3f, h * 0.06f, w * 0.7f, h * 0.06f, w * 0.7f, h * 0.32f)
            lineTo(w * 0.7f, h * 0.46f)
        }
        drawPath(shackle, ink, style = stroke)
        drawRoundRect(
            ink,
            topLeft = Offset(w * 0.16f, h * 0.44f),
            size = Size(w * 0.68f, h * 0.5f),
            cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
            style = stroke,
        )
        drawCircle(WelcomePink, radius = w * 0.075f, center = Offset(w * 0.5f, h * 0.64f))
        drawLine(WelcomePink, Offset(w * 0.5f, h * 0.66f), Offset(w * 0.5f, h * 0.79f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    }
}
