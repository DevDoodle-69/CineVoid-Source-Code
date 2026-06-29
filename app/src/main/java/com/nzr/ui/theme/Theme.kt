package com.nzr.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val AppColorScheme = darkColorScheme(
    primary = AccentPurple,
    onPrimary = TextPrimary,
    secondary = AccentPurpleLight,
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    surface = BackgroundSecondary,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundTertiary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.statusBarColor = android.graphics.Color.TRANSPARENT
            window?.navigationBarColor = android.graphics.Color.parseColor("#0A0A0F")
        }
    }

    MaterialTheme(colorScheme = AppColorScheme, typography = Typography, content = content)
}
