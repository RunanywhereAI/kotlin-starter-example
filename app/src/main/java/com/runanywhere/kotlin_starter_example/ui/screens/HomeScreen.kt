package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.ui.components.FeatureCard
import com.runanywhere.kotlin_starter_example.ui.theme.*

private data class Feature(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val gradientColors: List<Color>,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToSTT: () -> Unit,
    onNavigateToTTS: () -> Unit,
    onNavigateToVoicePipeline: () -> Unit,
    onNavigateToToolCalling: () -> Unit,
    onNavigateToVision: () -> Unit,
    onNavigateToVad: () -> Unit,
    onNavigateToRag: () -> Unit,
    onNavigateToEmbeddings: () -> Unit,
    onNavigateToStructuredOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        PrimaryDark,
                        Color(0xFF0F1629),
                        PrimaryMid
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            // Header
            Header()
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Privacy info
            PrivacyInfoCard()
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Feature grid (data-driven, two columns, non-scrolling rows so it
            // nests cleanly inside the page's vertical scroll)
            val features = listOf(
                Feature("Chat", "LLM Text Generation", Icons.Rounded.Chat, listOf(AccentCyan, Color(0xFF0EA5E9)), onNavigateToChat),
                Feature("Speech", "Speech to Text", Icons.Rounded.Mic, listOf(AccentViolet, Color(0xFF7C3AED)), onNavigateToSTT),
                Feature("Voice", "Text to Speech", Icons.Rounded.VolumeUp, listOf(AccentPink, Color(0xFFDB2777)), onNavigateToTTS),
                Feature("Pipeline", "Voice Agent", Icons.Rounded.AutoAwesome, listOf(AccentGreen, Color(0xFF059669)), onNavigateToVoicePipeline),
                Feature("Tools", "Function Calling", Icons.Rounded.Build, listOf(AccentOrange, Color(0xFFEA580C)), onNavigateToToolCalling),
                Feature("Vision", "Image Understanding", Icons.Rounded.RemoveRedEye, listOf(AccentPink, Color(0xFFDB2777)), onNavigateToVision),
                Feature("Voice Activity", "Speech Detection", Icons.Rounded.GraphicEq, listOf(AccentGreen, Color(0xFF0EA5E9)), onNavigateToVad),
                Feature("Documents", "RAG Q&A", Icons.Rounded.LibraryBooks, listOf(AccentCyan, AccentViolet), onNavigateToRag),
                Feature("Embeddings", "Semantic Vectors", Icons.Rounded.Hub, listOf(AccentViolet, Color(0xFF7C3AED)), onNavigateToEmbeddings),
                Feature("Structured", "JSON Output", Icons.Rounded.DataObject, listOf(AccentOrange, AccentPink), onNavigateToStructuredOutput),
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                features.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { feature ->
                            FeatureCard(
                                title = feature.title,
                                subtitle = feature.subtitle,
                                icon = feature.icon,
                                gradientColors = feature.gradientColors,
                                onClick = feature.onClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Model info
            ModelInfoSection()
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(AccentCyan, AccentViolet)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Title
        Column {
            Text(
                text = "RunAnywhere",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Text(
                text = "Kotlin SDK Starter",
                style = MaterialTheme.typography.bodyMedium,
                color = AccentCyan
            )
        }
    }
}

@Composable
private fun PrivacyInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceCard.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.PrivacyTip,
                contentDescription = null,
                tint = AccentCyan.copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "Privacy-First On-Device AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "All AI processing happens locally on your device. No data ever leaves your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ModelInfoSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceCard.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            ModelInfoRow(
                icon = Icons.Rounded.Memory,
                title = "LLM",
                value = "SmolLM2 360M"
            )
            Spacer(modifier = Modifier.height(12.dp))
            ModelInfoRow(
                icon = Icons.Rounded.RemoveRedEye,
                title = "VLM",
                value = "SmolVLM 256M"
            )
            Spacer(modifier = Modifier.height(12.dp))
            ModelInfoRow(
                icon = Icons.Rounded.Hearing,
                title = "STT",
                value = "Whisper Tiny"
            )
            Spacer(modifier = Modifier.height(12.dp))
            ModelInfoRow(
                icon = Icons.Rounded.RecordVoiceOver,
                title = "TTS",
                value = "Piper Lessac"
            )
            Spacer(modifier = Modifier.height(12.dp))
            ModelInfoRow(
                icon = Icons.Rounded.GraphicEq,
                title = "VAD",
                value = "Silero VAD"
            )
            Spacer(modifier = Modifier.height(12.dp))
            ModelInfoRow(
                icon = Icons.Rounded.Hub,
                title = "Embedding",
                value = "MiniLM L6 v2"
            )
        }
    }
}

@Composable
private fun ModelInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = AccentCyan
        )
    }
}
