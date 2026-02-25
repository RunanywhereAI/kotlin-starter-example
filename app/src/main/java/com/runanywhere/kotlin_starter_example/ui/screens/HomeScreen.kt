package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.ui.components.AppCard
import com.runanywhere.kotlin_starter_example.ui.components.AppScaffold
import com.runanywhere.kotlin_starter_example.ui.components.FeatureCard
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme

@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToSTT: () -> Unit,
    onNavigateToTTS: () -> Unit,
    onNavigateToVoicePipeline: () -> Unit,
    onNavigateToToolCalling: () -> Unit,
    onNavigateToVision: () -> Unit,
    onNavigateToLora: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    AppScaffold(
        title = "RunAnywhere",
        subtitle = "Kotlin SDK Starter"
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Privacy info
            PrivacyInfoCard()

            Spacer(modifier = Modifier.height(24.dp))

            // Feature grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureCard(
                    title = "Chat",
                    subtitle = "LLM Text Generation",
                    icon = TablerIcons.Message,
                    gradientColors = listOf(colors.tintBlue),
                    onClick = onNavigateToChat,
                    modifier = Modifier.weight(1f)
                )
                FeatureCard(
                    title = "Speech",
                    subtitle = "Speech to Text",
                    icon = TablerIcons.Microphone,
                    gradientColors = listOf(colors.tintPurple),
                    onClick = onNavigateToSTT,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureCard(
                    title = "Voice",
                    subtitle = "Text to Speech",
                    icon = TablerIcons.Volume,
                    gradientColors = listOf(colors.tintPink),
                    onClick = onNavigateToTTS,
                    modifier = Modifier.weight(1f)
                )
                FeatureCard(
                    title = "Pipeline",
                    subtitle = "Voice Agent",
                    icon = TablerIcons.Sparkles,
                    gradientColors = listOf(colors.tintGreen),
                    onClick = onNavigateToVoicePipeline,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureCard(
                    title = "Tools",
                    subtitle = "Function Calling",
                    icon = TablerIcons.Tool,
                    gradientColors = listOf(colors.tintOrange),
                    onClick = onNavigateToToolCalling,
                    modifier = Modifier.weight(1f)
                )
                FeatureCard(
                    title = "Vision",
                    subtitle = "Image Understanding",
                    icon = TablerIcons.Eye,
                    gradientColors = listOf(colors.tintPink),
                    onClick = onNavigateToVision,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureCard(
                    title = "LoRA",
                    subtitle = "Fine-Tune Adapters",
                    icon = TablerIcons.Tune,
                    gradientColors = listOf(colors.tintCyan),
                    onClick = onNavigateToLora,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Model info
            ModelInfoSection()

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PrivacyInfoCard() {
    val colors = AppTheme.colors

    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TablerIcons.ShieldCheck,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Privacy-First On-Device AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "All processing happens locally. No data leaves your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun ModelInfoSection() {
    val colors = AppTheme.colors

    AppCard {
        ModelInfoRow(icon = TablerIcons.Cpu, title = "LLM", value = "SmolLM2 360M")
        Spacer(modifier = Modifier.height(10.dp))
        ModelInfoRow(icon = TablerIcons.Eye, title = "VLM", value = "SmolVLM 256M")
        Spacer(modifier = Modifier.height(10.dp))
        ModelInfoRow(icon = TablerIcons.Ear, title = "STT", value = "Whisper Tiny")
        Spacer(modifier = Modifier.height(10.dp))
        ModelInfoRow(icon = TablerIcons.Speakerphone, title = "TTS", value = "Piper Lessac")
    }
}

@Composable
private fun ModelInfoRow(icon: ImageVector, title: String, value: String) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = colors.accent)
    }
}
