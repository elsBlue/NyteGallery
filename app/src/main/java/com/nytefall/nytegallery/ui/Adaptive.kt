package com.nytefall.nytegallery.ui

import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

internal enum class WindowShape { Phone, TallWide, Landscape }

@Composable
internal fun windowShape(): WindowShape {
    val config = LocalConfiguration.current
    return when {
        config.screenWidthDp > config.screenHeightDp -> WindowShape.Landscape
        config.screenWidthDp >= 600 -> WindowShape.TallWide
        else -> WindowShape.Phone
    }
}

@Composable
internal fun vaultGridColumns(): GridCells =
    if (windowShape() == WindowShape.Phone) GridCells.Fixed(4) else GridCells.Adaptive(88.dp)

@Composable
internal fun Modifier.wideCap(): Modifier =
    if (windowShape() == WindowShape.Phone) this else this.widthIn(max = 520.dp)


/**
 * System bar space that stays the same whether the status bar is hidden or shown,
 * so hiding it (immersive mode) never moves the layout.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
internal val androidx.compose.foundation.layout.WindowInsets.Companion.stableBars: androidx.compose.foundation.layout.WindowInsets
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.NonRestartableComposable
    get() = androidx.compose.foundation.layout.WindowInsets.systemBarsIgnoringVisibility
