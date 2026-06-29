package com.nzr.ui.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nzr.api.models.ContentItem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandardContentCard(item: ContentItem, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, progressRatio: Float? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else if (isFocused) 1.05f else 1f, label = "scale")
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember(context) { 
        val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) 
    }
    
    // Smooth enter animation
    var isVisible by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { isVisible = true }
    val enterScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.85f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "enterScale"
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
        label = "enterAlpha"
    )

    val finalModifier = if (modifier == Modifier) {
        Modifier.width(if (isTv) 180.dp else 140.dp)
    } else modifier

    Card(
        modifier = finalModifier
            .graphicsLayer {
                this.scaleX = enterScale * (if (!isTv) scale else 1f)
                this.scaleY = enterScale * (if (!isTv) scale else 1f)
                this.alpha = enterAlpha
            }
            .aspectRatio(2f / 3f)
            .border(if (!isTv && isFocused) 3.dp else 0.dp, borderColor, RoundedCornerShape(16.dp))
            .tvFocusGlow(isTv, isFocused, RoundedCornerShape(16.dp))
            .focusable(interactionSource = interactionSource)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 12.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.coverUrlResolved ?: "",
                contentDescription = item.title ?: "Unknown",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Corner badge
            item.corner?.takeIf { it.isNotEmpty() }?.let { badge ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }

            // Rating
            item.ratingValue?.let { rating ->
                if (rating > 0.0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.Star, 
                            contentDescription = "Rating", 
                            tint = Color(0xFFFFD700), 
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = rating.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            
            // Subtle gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = item.title ?: "Unknown",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.year?.let { y ->
                    if (y.isNotEmpty()) {
                        Text(
                            text = y,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            if (progressRatio != null && progressRatio > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progressRatio.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(com.nzr.ui.theme.ErrorRed)
                )
            }
        }
    }
}
