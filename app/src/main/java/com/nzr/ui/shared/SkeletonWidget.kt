package com.nzr.ui.shared

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nzr.ui.theme.ShimmerBase
import com.nzr.ui.theme.ShimmerHighlight

@Composable
fun ShimmerEffect(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_shimmer"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun SkeletonCard() {
    ShimmerEffect(
        modifier = Modifier
            .width(140.dp)
            .aspectRatio(2f / 3f)
    )
}

@Composable
fun SkeletonRow() {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        ShimmerEffect(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(150.dp).height(24.dp),
            shape = RoundedCornerShape(8.dp)
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) { SkeletonCard() }
        }
    }
}

@Composable
fun SkeletonDetail() {
    Column(modifier = Modifier.fillMaxSize()) {
        ShimmerEffect(modifier = Modifier.fillMaxWidth().height(450.dp), shape = RoundedCornerShape(0.dp))
        Spacer(modifier = Modifier.height(24.dp))
        ShimmerEffect(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(0.6f).height(40.dp), shape = RoundedCornerShape(8.dp))
        Spacer(modifier = Modifier.height(12.dp))
        ShimmerEffect(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(0.3f).height(20.dp), shape = RoundedCornerShape(8.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShimmerEffect(modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp))
            ShimmerEffect(modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        SkeletonRow()
    }
}

@Composable
fun SkeletonGrid() {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonCard()
            SkeletonCard()
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonCard()
            SkeletonCard()
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonCard()
            SkeletonCard()
        }
    }
}
