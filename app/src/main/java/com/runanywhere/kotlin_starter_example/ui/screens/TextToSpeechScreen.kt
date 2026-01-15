package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("Hello! This is a test of the text-to-speech system.") }
    var isSpeaking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text to Speech") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryDark
                )
            )
        },
        containerColor = PrimaryDark
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Model loader section
            if (!modelService.isTTSLoaded) {
                ModelLoaderWidget(
                    modelName = "Piper TTS",
                    isDownloading = modelService.isTTSDownloading,
                    isLoading = modelService.isTTSLoading,
                    isLoaded = modelService.isTTSLoaded,
                    downloadProgress = modelService.ttsDownloadProgress,
                    onLoadClick = { modelService.downloadAndLoadTTS() }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Text input
            if (modelService.isTTSLoaded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceCard
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Enter text to speak",
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentPink
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Type something...") },
                            enabled = !isSpeaking,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = PrimaryMid,
                                unfocusedContainerColor = PrimaryMid,
                                disabledContainerColor = PrimaryMid,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 4,
                            maxLines = 8
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Speak button with animation
                SpeakButton(
                    isSpeaking = isSpeaking,
                    onClick = {
                        if (!isSpeaking && inputText.isNotBlank()) {
                            isSpeaking = true
                            scope.launch {
                                try {
                                    com.runanywhere.sdk.public.RunAnywhere.speak(inputText)
                                    errorMessage = null
                                } catch (e: Exception) {
                                    errorMessage = "TTS failed: ${e.message}"
                                } finally {
                                    isSpeaking = false
                                }
                            }
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (isSpeaking) "Speaking..." else "Tap to speak",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSpeaking) AccentPink else TextMuted
                )
            }
            
            // Error message
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Sample texts
            if (modelService.isTTSLoaded) {
                Spacer(modifier = Modifier.height(32.dp))
                SampleTextsCard { sampleText ->
                    inputText = sampleText
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                InfoCard()
            }
        }
    }
}

@Composable
private fun SpeakButton(
    isSpeaking: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSpeaking) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow effect when speaking
        if (isSpeaking) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AccentPink.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
        
        // Button
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            containerColor = if (isSpeaking) AccentViolet else AccentPink,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = if (isSpeaking) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeUp,
                contentDescription = "Speak",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun SampleTextsCard(onSelectSample: (String) -> Unit) {
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
            Text(
                text = "Sample Texts",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            val samples = listOf(
                "Hello! This is a test of the text-to-speech system.",
                "The quick brown fox jumps over the lazy dog.",
                "Artificial intelligence is transforming how we interact with technology.",
                "Welcome to the future of on-device AI processing."
            )
            
            samples.forEach { sample ->
                TextButton(
                    onClick = { onSelectSample(sample) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TextFields,
                            contentDescription = null,
                            tint = AccentPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sample.take(50) + if (sample.length > 50) "..." else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard() {
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
            Text(
                text = "How it works",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "• Enter text in the field above\n" +
                        "• Or select a sample text\n" +
                        "• Tap the speaker button to hear it\n" +
                        "• All processing happens on-device",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}
