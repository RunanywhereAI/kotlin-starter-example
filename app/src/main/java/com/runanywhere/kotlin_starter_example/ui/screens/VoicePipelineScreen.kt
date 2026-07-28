package com.runanywhere.kotlin_starter_example.ui.screens

import ai.runanywhere.proto.v1.PipelineState
import ai.runanywhere.proto.v1.TokenKind
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.cleanupVoiceAgent
import com.runanywhere.sdk.public.extensions.initializeVoiceAgentWithLoadedModels
import com.runanywhere.sdk.public.extensions.streamVoiceAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Voice Pipeline Screen - Full STT -> LLM -> TTS with automatic silence detection
 *
 * This screen demonstrates the simplest way to use RunAnywhere's voice pipeline.
 * All the business logic (audio capture, silence detection, STT->LLM->TTS
 * orchestration, and playback) is handled by the SDK's voice agent APIs.
 *
 * The app only needs to:
 * 1. Call RunAnywhere.initializeVoiceAgentWithLoadedModels() once the LLM/STT/TTS
 *    models are loaded.
 * 2. Collect RunAnywhere.streamVoiceAgent() to update the UI from typed VoiceEvents.
 * 3. Call RunAnywhere.cleanupVoiceAgent() when the session ends.
 */

enum class VoiceSessionState {
    IDLE,
    LISTENING,
    SPEECH_DETECTED,
    PROCESSING,
    SPEAKING
}

data class VoiceMessage(
    val text: String,
    val type: String, // "user", "ai", "status"
    val timestamp: Long = System.currentTimeMillis()
)

private fun PipelineState.toVoiceSessionState(current: VoiceSessionState): VoiceSessionState = when (this) {
    PipelineState.PIPELINE_STATE_IDLE,
    PipelineState.PIPELINE_STATE_STOPPED,
    -> VoiceSessionState.IDLE
    PipelineState.PIPELINE_STATE_LISTENING,
    PipelineState.PIPELINE_STATE_WAITING_WAKEWORD,
    -> VoiceSessionState.LISTENING
    PipelineState.PIPELINE_STATE_PROCESSING_SPEECH -> VoiceSessionState.SPEECH_DETECTED
    PipelineState.PIPELINE_STATE_THINKING,
    PipelineState.PIPELINE_STATE_GENERATING_RESPONSE,
    -> VoiceSessionState.PROCESSING
    PipelineState.PIPELINE_STATE_SPEAKING,
    PipelineState.PIPELINE_STATE_PLAYING_TTS,
    -> VoiceSessionState.SPEAKING
    PipelineState.PIPELINE_STATE_ERROR -> VoiceSessionState.IDLE
    PipelineState.PIPELINE_STATE_COOLDOWN,
    PipelineState.PIPELINE_STATE_UNSPECIFIED,
    -> current
}

private fun TokenKind.isDisplayableVoiceAnswer(): Boolean =
    this == TokenKind.TOKEN_KIND_ANSWER || this == TokenKind.TOKEN_KIND_UNSPECIFIED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePipelineScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    var sessionState by remember { mutableStateOf(VoiceSessionState.IDLE) }
    var messages by remember { mutableStateOf(listOf<VoiceMessage>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Voice session + cleanup jobs
    var sessionJob by remember { mutableStateOf<Job?>(null) }
    var cleanupJob by remember { mutableStateOf<Job?>(null) }

    // Check permission
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) errorMessage = "Microphone permission is required"
    }

    /**
     * Start a voice session using the SDK's voice agent APIs.
     *
     * This is the key integration point - the SDK handles all the business
     * logic: mic capture, silence detection, STT -> LLM -> TTS orchestration,
     * and audio playback. The app only reacts to the typed VoiceEvent stream.
     */
    fun startSession() {
        if (sessionState != VoiceSessionState.IDLE) return
        errorMessage = null
        sessionState = VoiceSessionState.PROCESSING
        val pendingCleanup = cleanupJob

        sessionJob = scope.launch {
            var assistantIndex: Int? = null

            fun ensureAssistantIndex(): Int {
                assistantIndex?.let { return it }
                messages = messages + VoiceMessage("", "ai")
                val index = messages.size - 1
                assistantIndex = index
                return index
            }

            try {
                // A previous session may still be tearing down its native
                // handle; never reinitialize until cleanup has completed.
                pendingCleanup?.join()

                RunAnywhere.initializeVoiceAgentWithLoadedModels()
                sessionState = VoiceSessionState.LISTENING
                messages = messages + VoiceMessage("Listening... speak and pause to send", "status")
                listState.animateScrollToItem(messages.size)

                RunAnywhere.streamVoiceAgent().collect { event ->
                    event.state?.current?.let { pipelineState ->
                        sessionState = pipelineState.toVoiceSessionState(sessionState)
                    }
                    event.audio_level?.let { audioLevel = it.rms.coerceIn(0f, 1f) }
                    event.vad?.let {
                        if (sessionState == VoiceSessionState.LISTENING ||
                            sessionState == VoiceSessionState.SPEECH_DETECTED
                        ) {
                            sessionState = if (it.is_speech) {
                                VoiceSessionState.SPEECH_DETECTED
                            } else {
                                VoiceSessionState.LISTENING
                            }
                        }
                    }
                    event.user_said?.let { userSaid ->
                        val text = userSaid.text.trim()
                        if (userSaid.is_final && text.isNotBlank()) {
                            messages = messages + VoiceMessage(text, "user")
                            assistantIndex = null
                            listState.animateScrollToItem(messages.size)
                        }
                    }
                    event.agent_response_started?.let {
                        sessionState = VoiceSessionState.PROCESSING
                        ensureAssistantIndex()
                    }
                    event.assistant_token?.let { token ->
                        if (token.text.isNotEmpty() && token.kind.isDisplayableVoiceAnswer()) {
                            sessionState = VoiceSessionState.PROCESSING
                            val index = ensureAssistantIndex()
                            messages = messages.toMutableList().also { list ->
                                list[index] = list[index].copy(text = list[index].text + token.text)
                            }
                            listState.animateScrollToItem(messages.size)
                        }
                    }
                    if (event.audio != null || event.agent_response_completed != null) {
                        sessionState = VoiceSessionState.SPEAKING
                    }
                    event.session_stopped?.let {
                        sessionState = VoiceSessionState.IDLE
                        audioLevel = 0f
                    }
                    val message = event.session_error?.message?.takeIf { it.isNotBlank() }
                        ?: event.error?.message?.takeIf { it.isNotBlank() }
                    if (message != null) {
                        errorMessage = message
                        sessionState = VoiceSessionState.IDLE
                        audioLevel = 0f
                    }
                }
            } catch (e: CancellationException) {
                // Expected when stopping
            } catch (e: Exception) {
                errorMessage = "Session error: ${e.message}"
                sessionState = VoiceSessionState.IDLE
                audioLevel = 0f
            }
        }
    }

    /**
     * Stop the voice session and release the native voice agent resources.
     */
    fun stopSession() {
        val worker = sessionJob
        worker?.cancel()
        sessionJob = null
        sessionState = VoiceSessionState.IDLE
        audioLevel = 0f
        cleanupJob = scope.launch(Dispatchers.IO) {
            worker?.join()
            runCatching { RunAnywhere.cleanupVoiceAgent() }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            sessionJob?.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { RunAnywhere.cleanupVoiceAgent() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Pipeline") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDark)
            )
        },
        containerColor = PrimaryDark
    ) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding)) {
            val allModelsLoaded = modelService.isLLMLoaded &&
                                 modelService.isSTTLoaded &&
                                 modelService.isTTSLoaded

            // Model loader section
            if (!allModelsLoaded) {
                ModelLoaderSection(modelService)
            }

            // Permission check
            if (!hasPermission && allModelsLoaded) {
                PermissionCard { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            }

            // Main content
            if (allModelsLoaded && hasPermission) {
                // Messages list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    if (messages.isEmpty()) {
                        item { EmptyStateMessage() }
                    }
                    items(messages) { message -> VoiceMessageBubble(message) }
                }

                // Control section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard.copy(alpha = 0.8f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Audio level indicator (when listening)
                    if (sessionState == VoiceSessionState.LISTENING || sessionState == VoiceSessionState.SPEECH_DETECTED) {
                        AudioLevelIndicator(audioLevel, sessionState == VoiceSessionState.SPEECH_DETECTED)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    StatusIndicator(sessionState)
                    Spacer(modifier = Modifier.height(24.dp))

                    VoiceButton(
                        sessionState = sessionState,
                        onClick = {
                            when (sessionState) {
                                VoiceSessionState.IDLE -> startSession()
                                else -> stopSession()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = getStatusText(sessionState),
                        style = MaterialTheme.typography.bodyLarge,
                        color = getStatusColor(sessionState)
                    )
                }
            }

            // Error message
            errorMessage?.let { ErrorCard(it) }
        }
    }
}

@Composable
private fun ModelLoaderSection(modelService: ModelService) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Voice Pipeline requires all models", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

        ModelLoaderWidget(
            modelName = modelService.llmModelName,
            isDownloading = modelService.isLLMDownloading,
            isLoading = modelService.isLLMLoading,
            isLoaded = modelService.isLLMLoaded,
            downloadProgress = modelService.llmDownloadProgress,
            onLoadClick = { modelService.downloadAndLoadLLM() },
            backendLabel = if (modelService.llmUsesNpu) "QHexRT · Hexagon NPU" else "llama.cpp",
            enabled = modelService.isSdkReady,
        )

        ModelLoaderWidget(
            modelName = modelService.sttModelName,
            isDownloading = modelService.isSTTDownloading,
            isLoading = modelService.isSTTLoading,
            isLoaded = modelService.isSTTLoaded,
            downloadProgress = modelService.sttDownloadProgress,
            onLoadClick = { modelService.downloadAndLoadSTT() },
            backendLabel = if (modelService.sttUsesNpu) "QHexRT · Hexagon NPU" else "Sherpa-ONNX",
            enabled = modelService.isSdkReady,
        )

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

        Button(onClick = { modelService.downloadAndLoadAllModels() }, modifier = Modifier.fillMaxWidth()) {
            Text("Load All Models")
        }
    }
}

@Composable
private fun AudioLevelIndicator(audioLevel: Float, isSpeechDetected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Recording badge
        Row(
            modifier = Modifier
                .background(if (isSpeechDetected) AccentGreen.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pulsing dot
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.5f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                label = "dot"
            )
            Box(
                modifier = Modifier.size(8.dp).background(
                    if (isSpeechDetected) AccentGreen.copy(alpha = pulseAlpha) else Color.Red.copy(alpha = pulseAlpha),
                    CircleShape
                )
            )
            Text(
                text = if (isSpeechDetected) "SPEECH DETECTED" else "LISTENING",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSpeechDetected) AccentGreen else Color.Red
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Audio level bars
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(10) { index ->
                val isActive = index < (audioLevel * 10).toInt()
                Box(
                    modifier = Modifier
                        .width(25.dp)
                        .height(8.dp)
                        .background(
                            if (isActive) AccentGreen else TextMuted.copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Microphone permission required", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestPermission) { Text("Grant Permission") }
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(error, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusIndicator(state: VoiceSessionState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot("Listen", state == VoiceSessionState.LISTENING || state == VoiceSessionState.SPEECH_DETECTED, AccentCyan)
        StatusDot("Process", state == VoiceSessionState.PROCESSING, AccentViolet)
        StatusDot("Speak", state == VoiceSessionState.SPEAKING, AccentPink)
    }
}

@Composable
private fun StatusDot(label: String, isActive: Boolean, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Box(modifier = Modifier.size(12.dp).background(if (isActive) color else TextMuted.copy(alpha = 0.3f), CircleShape))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = if (isActive) color else TextMuted)
    }
}

@Composable
private fun VoiceButton(sessionState: VoiceSessionState, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (sessionState != VoiceSessionState.IDLE) 1.1f else 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        if (sessionState != VoiceSessionState.IDLE) {
            Box(
                modifier = Modifier.size(120.dp).scale(scale).background(
                    brush = Brush.radialGradient(listOf(AccentGreen.copy(alpha = 0.3f), Color.Transparent)),
                    shape = CircleShape
                )
            )
        }

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            containerColor = when (sessionState) {
                VoiceSessionState.IDLE -> AccentGreen
                VoiceSessionState.LISTENING, VoiceSessionState.SPEECH_DETECTED -> AccentViolet
                else -> AccentCyan
            },
            contentColor = Color.White
        ) {
            when (sessionState) {
                VoiceSessionState.PROCESSING -> CircularProgressIndicator(Modifier.size(32.dp), Color.White)
                VoiceSessionState.SPEAKING -> Icon(Icons.Rounded.VolumeUp, "Speaking", Modifier.size(32.dp))
                VoiceSessionState.IDLE -> Icon(Icons.Rounded.Mic, "Start", Modifier.size(32.dp))
                else -> Icon(Icons.Rounded.Stop, "Stop", Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun EmptyStateMessage() {
    Column(modifier = Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = AccentGreen, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Voice Pipeline Ready", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap mic to start. Speak, then pause - it auto-detects silence and processes.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
private fun VoiceMessageBubble(message: VoiceMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (message.type) {
            "user" -> Arrangement.End
            "status" -> Arrangement.Center
            else -> Arrangement.Start
        }
    ) {
        if (message.type == "ai") {
            Icon(Icons.Rounded.SmartToy, null, tint = AccentCyan, modifier = Modifier.size(32.dp).padding(top = 4.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier.widthIn(max = if (message.type == "status") 300.dp else 280.dp),
            shape = RoundedCornerShape(
                topStart = if (message.type == "user") 16.dp else 4.dp,
                topEnd = if (message.type == "user") 4.dp else 16.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when (message.type) {
                    "user" -> AccentCyan
                    "status" -> SurfaceCard.copy(alpha = 0.5f)
                    else -> SurfaceCard
                }
            )
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (message.type == "user") Color.White else TextPrimary)
        }

        if (message.type == "user") {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Rounded.Person, null, tint = AccentViolet, modifier = Modifier.size(32.dp).padding(top = 4.dp))
        }
    }
}

private fun getStatusText(state: VoiceSessionState) = when (state) {
    VoiceSessionState.IDLE -> "Tap to start"
    VoiceSessionState.LISTENING -> "Listening... pause to send"
    VoiceSessionState.SPEECH_DETECTED -> "Speaking detected..."
    VoiceSessionState.PROCESSING -> "Processing..."
    VoiceSessionState.SPEAKING -> "Speaking..."
}

private fun getStatusColor(state: VoiceSessionState) = when (state) {
    VoiceSessionState.IDLE -> TextMuted
    VoiceSessionState.LISTENING -> AccentCyan
    VoiceSessionState.SPEECH_DETECTED -> AccentGreen
    VoiceSessionState.PROCESSING -> AccentViolet
    VoiceSessionState.SPEAKING -> AccentPink
}
