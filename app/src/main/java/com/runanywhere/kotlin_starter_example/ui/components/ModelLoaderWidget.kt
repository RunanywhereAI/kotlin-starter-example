package com.runanywhere.kotlin_starter_example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme

@Composable
fun ModelLoaderWidget(
    modelName: String,
    isDownloading: Boolean,
    isLoading: Boolean,
    isLoaded: Boolean,
    downloadProgress: Float,
    onLoadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = downloadProgress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    AppCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        isLoaded -> {
                            Icon(
                                TablerIcons.CircleCheck,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.success
                            )
                        }
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.accent
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Loading model...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accent
                            )
                        }
                        isDownloading -> {
                            Text(
                                text = "Downloading ${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accent
                            )
                        }
                        else -> {
                            Text(
                                text = "Not loaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            if (!isLoaded) {
                AppButton(
                    onClick = onLoadClick,
                    enabled = !isDownloading && !isLoading,
                ) {
                    if (isDownloading || isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    } else {
                        Icon(
                            TablerIcons.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Load", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Progress bar
        AnimatedVisibility(
            visible = isDownloading,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = colors.accent,
                    trackColor = colors.border,
                )
            }
        }

        // Loading indeterminate
        AnimatedVisibility(
            visible = isLoading && !isDownloading,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = colors.accent,
                    trackColor = colors.border,
                )
            }
        }
    }
}
