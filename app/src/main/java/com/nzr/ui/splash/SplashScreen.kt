package com.nzr.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    LaunchedEffect(Unit) {
        delay(2000)
        navController.navigate("main") {
            popUpTo("splash") { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CineVoid",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Every Frame. Every Story. Yours.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val wText = remember {
            val b = intArrayOf(0x4d, 0x41, 0x44, 0x45, 0x20, 0x57, 0x49, 0x54, 0x48, 0x20, 0x2764, 0xfe0f, 0x20, 0x42, 0x59, 0x20, 0x4e, 0x5a, 0x20, 0x52)
            b.map { it.toChar() }.joinToString("")
        }

        Text(
            text = wText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        )
    }
}
