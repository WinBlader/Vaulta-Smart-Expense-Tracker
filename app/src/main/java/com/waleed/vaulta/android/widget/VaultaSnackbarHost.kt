package com.waleed.vaulta.android.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleed.vaulta.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VaultaSnackbarMessage(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 3000L
)

object VaultaSnackbarController {
    private val _currentMessage = MutableStateFlow<VaultaSnackbarMessage?>(null)
    val currentMessage = _currentMessage.asStateFlow()

    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMs: Long = 3000L
    ) {
        if (message.isBlank()) return
        _currentMessage.value = VaultaSnackbarMessage(
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
            durationMs = durationMs
        )
    }

    fun dismiss() {
        _currentMessage.value = null
    }
}

@Composable
fun VaultaSnackbarHost(
    modifier: Modifier = Modifier
) {
    val message by VaultaSnackbarController.currentMessage.collectAsState()

    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        delay(current.durationMs)
        VaultaSnackbarController.dismiss()
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        message?.let { current ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .shadow(12.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF102220),
                border = BorderStroke(1.2.dp, Color(0xFF264D46))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Vaulta App Icon
                    Image(
                        painter = painterResource(id = R.mipmap.final_icon),
                        contentDescription = "Vaulta Logo",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )

                    // Message Text
                    Text(
                        text = current.message,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Optional Action Button (e.g. "OPEN")
                    if (!current.actionLabel.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF004D40))
                                .border(1.dp, Color(0xFF80CBC4).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable {
                                    current.onAction?.invoke()
                                    VaultaSnackbarController.dismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = current.actionLabel,
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Dismiss Button
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { VaultaSnackbarController.dismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = Color(0xFFB0BEC5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
