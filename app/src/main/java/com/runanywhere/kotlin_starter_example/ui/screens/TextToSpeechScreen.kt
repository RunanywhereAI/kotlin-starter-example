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
import com.runanywhere.kotlin_starter_example.ui.components.ModelPicker
import com.runanywhere.kotlin_starter_example.ui.theme.*
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.speak
import com.runanywhere.sdk.public.types.RATTSOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun isKittenTts(modelId: String): Boolean = modelId.startsWith("kitten_")

/** Kitten E2E phrases — short + long, including the known regression cases. */
private val KITTEN_E2E_SUITE = listOf(
    "Hexagon neural processing unit.",
    "The weather is sunny and warm today.",
    "Can you hear me clearly right now?",
    "Hello! This is a test of the text-to-speech system.",
    "My name is a test of the text to speech system. My name is Southampton.",
    "Artificial intelligence on Snapdragon Hexagon can run speech synthesis fully on the neural processing unit without sending audio to the cloud.",
    "Please read this longer paragraph carefully. The Qualcomm Hexagon NPU is designed for efficient on-device inference. " +
        "We want Kitten to speak every sentence clearly, keep punctuation pauses natural, and not fall back to any baked fixture phrase. " +
        "If variable-length Graph A is working, even this multi-sentence block should complete in one synthesis pass on v75.",
    "Southampton is a major port city on the south coast of England. Text to speech quality matters when product demos run on a phone. " +
        "Load one HNPU model, speak several prompts in a row, and listen for dropped words, silence gaps, or wrong canned phrases.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    val kittenMode = isKittenTts(modelService.ttsModelId)
    var inputText by remember {
        mutableStateOf("Hello! This is a test of the text-to-speech system.")
    }
    var isSpeaking by remember { mutableStateOf(false) }
    var isRunningSuite by remember { mutableStateOf(false) }
    var suiteStatus by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val busy = isSpeaking || isRunningSuite
    
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
            ModelPicker(
                label = "Voice model",
                options = modelService.ttsOptions,
                selectedId = modelService.ttsModelId,
                onSelect = { modelService.selectTts(it) },
                enabled = modelService.isSdkReady &&
                    !modelService.isTTSDownloading &&
                    !modelService.isTTSLoading,
            )
            if (modelService.ttsOptions.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (!modelService.isTTSLoaded) {
                ModelLoaderWidget(
                    modelName = modelService.ttsModelName,
                    isDownloading = modelService.isTTSDownloading,
                    isLoading = modelService.isTTSLoading,
                    isLoaded = modelService.isTTSLoaded,
                    downloadProgress = modelService.ttsDownloadProgress,
                    onLoadClick = { modelService.downloadAndLoadTTS() },
                    backendLabel = if (modelService.ttsUsesNpu) "QHexRT · Hexagon NPU" else "Sherpa-ONNX",
                    enabled = modelService.isSdkReady,
                )
                modelService.statusMessage?.let { note ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
                modelService.errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
                            enabled = !busy,
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
                        if (kittenMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Varlen Kitten (Lmax=128): long text should synthesize in " +
                                    "one pass — use the E2E suite below to stress it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Speak button with animation
                SpeakButton(
                    isSpeaking = isSpeaking || isRunningSuite,
                    onClick = {
                        if (!busy && inputText.isNotBlank()) {
                            isSpeaking = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        RunAnywhere.speak(inputText, RATTSOptions())
                                    }
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
                    text = when {
                        isRunningSuite -> suiteStatus ?: "Running Kitten suite…"
                        isSpeaking -> "Speaking..."
                        else -> "Tap to speak"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (busy) AccentPink else TextMuted
                )

                if (kittenMode) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (busy) return@Button
                            isRunningSuite = true
                            errorMessage = null
                            scope.launch {
                                val total = KITTEN_E2E_SUITE.size
                                try {
                                    for ((index, phrase) in KITTEN_E2E_SUITE.withIndex()) {
                                        val n = index + 1
                                        inputText = phrase
                                        suiteStatus = "Kitten suite $n/$total — speaking…"
                                        withContext(Dispatchers.IO) {
                                            RunAnywhere.speak(phrase, RATTSOptions())
                                        }
                                        suiteStatus = "Kitten suite $n/$total — done"
                                        if (index < total - 1) delay(700)
                                    }
                                    suiteStatus = "Kitten suite complete ($total phrases)"
                                } catch (e: Exception) {
                                    errorMessage = "Kitten suite failed: ${e.message}"
                                    suiteStatus = null
                                } finally {
                                    isRunningSuite = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Run Kitten E2E suite (${KITTEN_E2E_SUITE.size} phrases)")
                    }
                    suiteStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGreen,
                        )
                    }
                }
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
                SampleTextsCard(kittenMode = kittenMode) { sampleText ->
                    inputText = sampleText
                    errorMessage = null
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                InfoCard(kittenMode = kittenMode)
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
private fun SampleTextsCard(
    kittenMode: Boolean,
    onSelectSample: (String) -> Unit,
) {
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
            
            val samples = if (kittenMode) {
                KITTEN_E2E_SUITE
            } else {
                listOf(
                    "Hello! This is a test of the text-to-speech system.",
                    "The quick brown fox jumps over the lazy dog.",
                    "Artificial intelligence is transforming how we interact with technology.",
                    "Welcome to the future of on-device AI processing.",
                )
            }
            
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
private fun InfoCard(kittenMode: Boolean) {
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
                text = if (kittenMode) {
                    "• Use Run Kitten E2E suite for short + long phrases in a loop\n" +
                        "• Varlen Graph A targets single-shot synthesis up to Lmax=128 phonemes\n" +
                        "• Listen for dropped words, silence, or baked-fixture fallbacks\n" +
                        "• All processing happens on-device"
                } else {
                    "• Enter text in the field above\n" +
                        "• Or select a sample text\n" +
                        "• Tap the speaker button to hear it\n" +
                        "• All processing happens on-device"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}
