package com.nzr.ui.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

fun Modifier.tvFocusGlow(isTv: Boolean, isFocused: Boolean, shape: Shape): Modifier = composed {
    if (!isTv) return@composed this

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "tv_focus_scale"
    )

    this
        .scale(scale)
        .graphicsLayer {
            if (isFocused) {
                this.shape = shape
                clip = false
            }
        }
        .then(if (isFocused) Modifier.border(3.dp, Color.White, shape) else Modifier)
}
