package com.runanywhere.kotlin_starter_example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.AppButton
import com.runanywhere.kotlin_starter_example.ui.components.AppCard
import com.runanywhere.kotlin_starter_example.ui.components.AppScaffold
import com.runanywhere.kotlin_starter_example.ui.components.EmptyChat
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.VoiceAgent.VoiceSessionConfig
import com.runanywhere.sdk.public.extensions.VoiceAgent.VoiceSessionEvent
import com.runanywhere.sdk.public.extensions.streamVoiceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class VoiceSessionState {
    IDLE, LISTENING, SPEECH_DETECTED, PROCESSING, SPEAKING
}

data class VoiceMessage(
    val text: String,
    val type: String, // "user", "ai", "status"
    val timestamp: Long = System.currentTimeMillis()
)

private class AudioCaptureService {
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var isCapturing = false

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_SIZE_MS = 100
    }

    fun startCapture(): Flow<ByteArray> = callbackFlow {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val chunkSize = (SAMPLE_RATE * 2 * CHUNK_SIZE_MS) / 1000

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(bufferSize, chunkSize * 2)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                close(IllegalStateException("AudioRecord initialization failed"))
                return@callbackFlow
            }

            audioRecord?.startRecording()
            isCapturing = true

            val readJob = launch(Dispatchers.IO) {
                val buffer = ByteArray(chunkSize)
                while (isActive && isCapturing) {
                    val bytesRead = audioRecord?.read(buffer, 0, chunkSize) ?: -1
                    if (bytesRead > 0) {
                        trySend(buffer.copyOf(bytesRead))
                    }
                }
            }

            awaitClose {
                readJob.cancel()
                stopCapture()
            }
        } catch (e: Exception) {
            stopCapture()
            close(e)
        }
    }

    fun stopCapture() {
        isCapturing = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }
}

private suspend fun playWavAudio(wavData: ByteArray) = withContext(Dispatchers.IO) {
    if (wavData.size < 44) return@withContext

    val headerSize = if (wavData.size > 44 &&
        wavData[0] == 'R'.code.toByte() &&
        wavData[1] == 'I'.code.toByte()) 44 else 0

    val pcmData = wavData.copyOfRange(headerSize, wavData.size)
    val sampleRate = 22050

    val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
    )

    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(maxOf(bufferSize, pcmData.size))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    audioTrack.write(pcmData, 0, pcmData.size)
    audioTrack.play()

    val durationMs = (pcmData.size.toLong() * 1000) / (sampleRate * 2)
    delay(durationMs + 100)

    audioTrack.stop()
    audioTrack.release()
}

@Composable
fun VoicePipelineScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var sessionState by remember { mutableStateOf(VoiceSessionState.IDLE) }
    var messages by remember { mutableStateOf(listOf<VoiceMessage>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableFloatStateOf(0f) }

    val audioCaptureService = remember { AudioCaptureService() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var sessionJob by remember { mutableStateOf<Job?>(null) }

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

    fun startSession() {
        sessionState = VoiceSessionState.LISTENING
        errorMessage = null
        messages = messages + VoiceMessage("Listening... speak and pause to send", "status")
        scope.launch { listState.animateScrollToItem(messages.size) }

        val audioFlow = audioCaptureService.startCapture()

        val config = VoiceSessionConfig(
            silenceDuration = 1.5,
            speechThreshold = 0.1f,
            autoPlayTTS = false,
            continuousMode = true
        )

        sessionJob = scope.launch {
            try {
                RunAnywhere.streamVoiceSession(audioFlow, config).collect { event ->
                    when (event) {
                        is VoiceSessionEvent.Started -> sessionState = VoiceSessionState.LISTENING
                        is VoiceSessionEvent.Listening -> audioLevel = event.audioLevel
                        is VoiceSessionEvent.SpeechStarted -> sessionState = VoiceSessionState.SPEECH_DETECTED
                        is VoiceSessionEvent.Processing -> {
                            sessionState = VoiceSessionState.PROCESSING
                            audioLevel = 0f
                        }
                        is VoiceSessionEvent.Transcribed -> {
                            messages = messages + VoiceMessage(event.text, "user")
                            listState.animateScrollToItem(messages.size)
                        }
                        is VoiceSessionEvent.Responded -> {
                            messages = messages + VoiceMessage(event.text, "ai")
                            listState.animateScrollToItem(messages.size)
                        }
                        is VoiceSessionEvent.Speaking -> sessionState = VoiceSessionState.SPEAKING
                        is VoiceSessionEvent.TurnCompleted -> {
                            event.audio?.let { audio ->
                                sessionState = VoiceSessionState.SPEAKING
                                playWavAudio(audio)
                            }
                            sessionState = VoiceSessionState.LISTENING
                            audioLevel = 0f
                        }
                        is VoiceSessionEvent.Stopped -> {
                            sessionState = VoiceSessionState.IDLE
                            audioLevel = 0f
                        }
                        is VoiceSessionEvent.Error -> {
                            errorMessage = event.message
                            sessionState = VoiceSessionState.IDLE
                        }
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                errorMessage = "Session error: ${e.message}"
                sessionState = VoiceSessionState.IDLE
            }
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        audioCaptureService.stopCapture()
        sessionState = VoiceSessionState.IDLE
        audioLevel = 0f
    }

    DisposableEffect(Unit) {
        onDispose {
            sessionJob?.cancel()
            audioCaptureService.stopCapture()
        }
    }

    val allModelsLoaded = modelService.isLLMLoaded &&
            modelService.isSTTLoaded &&
            modelService.isTTSLoaded

    AppScaffold(
        title = "Voice Pipeline",
        subtitle = "STT + LLM + TTS",
        onBack = onNavigateBack,
        bottomBar = {
            if (allModelsLoaded && hasPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Audio level indicator
                    if (sessionState == VoiceSessionState.LISTENING || sessionState == VoiceSessionState.SPEECH_DETECTED) {
                        VoiceAudioLevelIndicator(audioLevel, sessionState == VoiceSessionState.SPEECH_DETECTED)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    VoiceStatusIndicator(sessionState)
                    Spacer(modifier = Modifier.height(16.dp))

                    VoiceButton(
                        sessionState = sessionState,
                        onClick = {
                            when (sessionState) {
                                VoiceSessionState.IDLE -> startSession()
                                else -> stopSession()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = getVoiceStatusText(sessionState),
                        style = MaterialTheme.typography.bodyMedium,
                        color = getVoiceStatusColor(sessionState)
                    )
                }
            }
        }
    ) {
        // Model loader section
        if (!allModelsLoaded) {
            VoiceModelLoaderSection(modelService)
        }

        // Permission check
        if (!hasPermission && allModelsLoaded) {
            AppCard(modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Microphone permission required",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AppButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Messages
        if (allModelsLoaded && hasPermission) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyChat(
                            icon = {
                                Icon(
                                    TablerIcons.Sparkles,
                                    null,
                                    tint = colors.tintGreen,
                                    modifier = Modifier.size(48.dp)
                                )
                            },
                            title = "Voice Pipeline Ready",
                            subtitle = "Tap mic to start. Speak, then pause - it auto-detects silence."
                        )
                    }
                }
                items(messages) { message -> VoiceMessageBubble(message) }
            }
        }

        // Error
        errorMessage?.let { error ->
            AppCard(modifier = Modifier.padding(16.dp)) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = colors.error)
            }
        }
    }
}

@Composable
private fun VoiceModelLoaderSection(modelService: ModelService) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Voice Pipeline requires all models", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)

        ModelLoaderWidget(
            modelName = "SmolLM2 (LLM)",
            isDownloading = modelService.isLLMDownloading,
            isLoading = modelService.isLLMLoading,
            isLoaded = modelService.isLLMLoaded,
            downloadProgress = modelService.llmDownloadProgress,
            onLoadClick = { modelService.downloadAndLoadLLM() }
        )

        ModelLoaderWidget(
            modelName = "Whisper (STT)",
            isDownloading = modelService.isSTTDownloading,
            isLoading = modelService.isSTTLoading,
            isLoaded = modelService.isSTTLoaded,
            downloadProgress = modelService.sttDownloadProgress,
            onLoadClick = { modelService.downloadAndLoadSTT() }
        )

        ModelLoaderWidget(
            modelName = "Piper (TTS)",
            isDownloading = modelService.isTTSDownloading,
            isLoading = modelService.isTTSLoading,
            isLoaded = modelService.isTTSLoaded,
            downloadProgress = modelService.ttsDownloadProgress,
            onLoadClick = { modelService.downloadAndLoadTTS() }
        )

        AppButton(
            onClick = { modelService.downloadAndLoadAllModels() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Load All Models")
        }
    }
}

@Composable
private fun VoiceAudioLevelIndicator(audioLevel: Float, isSpeechDetected: Boolean) {
    val colors = AppTheme.colors

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .background(
                    if (isSpeechDetected) colors.success.copy(alpha = 0.12f) else colors.error.copy(alpha = 0.08f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 0.5f,
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                label = "dot"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isSpeechDetected) colors.success.copy(alpha = pulseAlpha) else colors.error.copy(alpha = pulseAlpha),
                        CircleShape
                    )
            )
            Text(
                text = if (isSpeechDetected) "SPEECH DETECTED" else "LISTENING",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSpeechDetected) colors.success else colors.error
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(10) { index ->
                val isActive = index < (audioLevel * 10).toInt()
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(6.dp)
                        .background(
                            if (isActive) colors.success else colors.textTertiary.copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun VoiceStatusIndicator(state: VoiceSessionState) {
    val colors = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        VoiceStatusDot("Listen", state == VoiceSessionState.LISTENING || state == VoiceSessionState.SPEECH_DETECTED, colors.tintCyan)
        VoiceStatusDot("Process", state == VoiceSessionState.PROCESSING, colors.tintPurple)
        VoiceStatusDot("Speak", state == VoiceSessionState.SPEAKING, colors.tintPink)
    }
}

@Composable
private fun VoiceStatusDot(label: String, isActive: Boolean, color: Color) {
    val colors = AppTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (isActive) color else colors.textTertiary.copy(alpha = 0.3f), CircleShape)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isActive) color else colors.textSecondary)
    }
}

@Composable
private fun VoiceButton(sessionState: VoiceSessionState, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (sessionState != VoiceSessionState.IDLE) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        if (sessionState != VoiceSessionState.IDLE) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .background(colors.tintGreen.copy(alpha = 0.15f), CircleShape)
            )
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = when (sessionState) {
                    VoiceSessionState.IDLE -> colors.tintGreen
                    VoiceSessionState.LISTENING, VoiceSessionState.SPEECH_DETECTED -> colors.tintPurple
                    else -> colors.tintCyan
                },
                contentColor = Color.White
            )
        ) {
            when (sessionState) {
                VoiceSessionState.PROCESSING -> CircularProgressIndicator(
                    Modifier.size(28.dp), Color.White, strokeWidth = 2.5.dp
                )
                VoiceSessionState.SPEAKING -> Icon(TablerIcons.Volume, "Speaking", Modifier.size(28.dp))
                VoiceSessionState.IDLE -> Icon(TablerIcons.Microphone, "Start", Modifier.size(28.dp))
                else -> Icon(TablerIcons.PlayerStop, "Stop", Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun VoiceMessageBubble(message: VoiceMessage) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (message.type) {
            "user" -> Arrangement.End
            "status" -> Arrangement.Center
            else -> Arrangement.Start
        }
    ) {
        if (message.type == "ai") {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(colors.tintCyan.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(TablerIcons.Robot, null, tint = colors.tintCyan, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        androidx.compose.material3.Card(
            modifier = Modifier.widthIn(max = if (message.type == "status") 300.dp else 290.dp),
            shape = RoundedCornerShape(
                topStart = if (message.type == "user") 14.dp else 4.dp,
                topEnd = if (message.type == "user") 4.dp else 14.dp,
                bottomStart = 14.dp, bottomEnd = 14.dp
            ),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = when (message.type) {
                    "user" -> colors.accent
                    "status" -> colors.surfaceElevated.copy(alpha = 0.5f)
                    else -> colors.surfaceElevated
                }
            ),
            border = if (message.type != "user") androidx.compose.foundation.BorderStroke(0.5.dp, colors.border) else null,
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.type == "user") Color.White else colors.textPrimary
            )
        }

        if (message.type == "user") {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(colors.tintPurple.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(TablerIcons.User, null, tint = colors.tintPurple, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun getVoiceStatusText(state: VoiceSessionState) = when (state) {
    VoiceSessionState.IDLE -> "Tap to start"
    VoiceSessionState.LISTENING -> "Listening... pause to send"
    VoiceSessionState.SPEECH_DETECTED -> "Speaking detected..."
    VoiceSessionState.PROCESSING -> "Processing..."
    VoiceSessionState.SPEAKING -> "Speaking..."
}

@Composable
private fun getVoiceStatusColor(state: VoiceSessionState): Color {
    val colors = AppTheme.colors
    return when (state) {
        VoiceSessionState.IDLE -> colors.textSecondary
        VoiceSessionState.LISTENING -> colors.tintCyan
        VoiceSessionState.SPEECH_DETECTED -> colors.success
        VoiceSessionState.PROCESSING -> colors.tintPurple
        VoiceSessionState.SPEAKING -> colors.tintPink
    }
}
