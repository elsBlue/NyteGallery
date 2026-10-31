package com.nytefall.nytegallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Night = Color(0xFF120308)
private val Crimson = Color(0xFFE10600)
private val Paper = Color(0xFFF4F0EA)

private val scheme = darkColorScheme(
    primary = Crimson,
    background = Night,
    surface = Color(0xFF2A0C14),
    onBackground = Paper,
    onSurface = Paper,
)

@Composable
fun SAOGalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
