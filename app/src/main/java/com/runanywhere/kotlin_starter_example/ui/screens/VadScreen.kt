package com.runanywhere.kotlin_starter_example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.pcm16ToFloat32
import com.runanywhere.sdk.public.extensions.resetVAD
import com.runanywhere.sdk.public.extensions.streamVAD
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

// Streaming PCM16 microphone recorder. Unlike the STT recorder it does not
// buffer — it delivers each captured chunk (plus a normalized level) to the
// caller so audio can be fed straight into the SDK's streaming VAD session.
private class StreamingAudioRecorder {
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun start(onChunk: (chunk: ByteArray, level: Float) -> Unit): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) return false
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBuffer * 2,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }
            audioRecord = record
            record.startRecording()
            isRecording = true
            Thread {
                val buffer = ByteArray(minBuffer)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        onChunk(chunk, computeLevel(chunk, read))
                    }
                }
            }.start()
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun stop() {
        isRecording = false
        audioRecord?.runCatching { stop() }
        audioRecord?.release()
        audioRecord = null
    }

    // Normalized RMS of the little-endian PCM16 chunk, for a simple level meter.
    private fun computeLevel(bytes: ByteArray, length: Int): Float {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
            sum += (sample * sample).toDouble()
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sum / count) / 32768.0
        return (rms * 4).coerceIn(0.0, 1.0).toFloat()
    }
}

enum class VadActivity { SPEECH_STARTED, SPEECH_ENDED }

data class VadLogEntry(val type: VadActivity, val timestampMs: Long)

/**
 * Owns the streaming VAD session. Mic chunks are converted to Float32 PCM and
 * fed into [RunAnywhere.streamVAD]; framing is handled natively. Mirrors the
 * monorepo Android VadViewModel.
 */
class VadViewModel : ViewModel() {

    var isListening by mutableStateOf(false)
        private set
    var isSpeechDetected by mutableStateOf(false)
        private set
    var audioLevel by mutableStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val activityLog = mutableStateListOf<VadLogEntry>()

    private val recorder = StreamingAudioRecorder()
    private var audio: Channel<ByteArray>? = null
    private var detectionJob: Job? = null

    fun toggle() {
        if (isListening) stop() else start()
    }

    fun clearLog() {
        activityLog.clear()
    }

    private fun start() {
        error = null
        isSpeechDetected = false
        audioLevel = 0f
        startDetectionStream()
        isListening = true
        val started = recorder.start { chunk, level ->
            // SDK expects Float32 PCM; framing is handled natively.
            audio?.trySend(RunAnywhere.pcm16ToFloat32(chunk))
            audioLevel = level
        }
        if (!started) {
            error = "Could not start the microphone"
            stop()
        }
    }

    fun stop() {
        isListening = false
        recorder.stop()
        stopDetectionStream()
        isSpeechDetected = false
        audioLevel = 0f
        viewModelScope.launch {
            runCatching { RunAnywhere.resetVAD() }
        }
    }

    private fun startDetectionStream() {
        val channel = Channel<ByteArray>(
            capacity = AUDIO_CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        audio = channel
        detectionJob = viewModelScope.launch {
            try {
                var wasSpeechActive = false
                RunAnywhere.streamVAD(channel.receiveAsFlow()).collect { result ->
                    val message = result.error_message
                    if (!message.isNullOrEmpty()) {
                        error = message
                        return@collect
                    }
                    isSpeechDetected = result.is_speech
                    if (result.is_speech && !wasSpeechActive) {
                        addLogEntry(VadActivity.SPEECH_STARTED)
                        wasSpeechActive = true
                    } else if (!result.is_speech && wasSpeechActive) {
                        addLogEntry(VadActivity.SPEECH_ENDED)
                        wasSpeechActive = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Voice activity detection failed"
            }
            if (isListening) stop()
        }
    }

    private fun stopDetectionStream() {
        audio?.close()
        audio = null
        detectionJob?.cancel()
        detectionJob = null
    }

    private fun addLogEntry(type: VadActivity) {
        activityLog.add(0, VadLogEntry(type, System.currentTimeMillis()))
        if (activityLog.size > MAX_LOG_ENTRIES) activityLog.removeAt(activityLog.lastIndex)
    }

    override fun onCleared() {
        recorder.stop()
        audio?.close()
        detectionJob?.cancel()
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 50
        const val AUDIO_CHANNEL_CAPACITY = 8
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VadScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier,
) {
    val vadViewModel: VadViewModel = viewModel()
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    // Stop the mic if the user navigates away.
    DisposableEffect(Unit) {
        onDispose { vadViewModel.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Activity Detection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDark),
            )
        },
        containerColor = PrimaryDark,
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!modelService.isVADLoaded) {
                ModelLoaderWidget(
                    modelName = modelService.vadModelName,
                    isDownloading = modelService.isVADDownloading,
                    isLoading = modelService.isVADLoading,
                    isLoaded = modelService.isVADLoaded,
                    downloadProgress = modelService.vadDownloadProgress,
                    onLoadClick = { modelService.downloadAndLoadVAD() },
                    backendLabel = "ONNX",
                    enabled = modelService.isSdkReady,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (!hasPermission) {
                PermissionCard(onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) })
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (modelService.isVADLoaded && hasPermission) {
                Spacer(modifier = Modifier.height(16.dp))
                ListeningIndicator(
                    isListening = vadViewModel.isListening,
                    isSpeech = vadViewModel.isSpeechDetected,
                    level = vadViewModel.audioLevel,
                    onToggle = { vadViewModel.toggle() },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when {
                        vadViewModel.isSpeechDetected -> "Speech detected"
                        vadViewModel.isListening -> "Listening — silence"
                        else -> "Tap to start listening"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        vadViewModel.isSpeechDetected -> AccentGreen
                        vadViewModel.isListening -> AccentCyan
                        else -> TextMuted
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))
                ActivityLogCard(
                    entries = vadViewModel.activityLog,
                    onClear = { vadViewModel.clearLog() },
                )
            }

            vadViewModel.error?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                ErrorCard(err)
            }
            modelService.errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                ErrorCard(err)
            }
        }
    }
}

@Composable
private fun ListeningIndicator(
    isListening: Boolean,
    isSpeech: Boolean,
    level: Float,
    onToggle: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vad")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ring by animateFloatAsState(targetValue = 1f + level * 0.6f, label = "ring")

    Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(ring * pulse)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                (if (isSpeech) AccentGreen else AccentCyan).copy(alpha = 0.28f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
        }
        FloatingActionButton(
            onClick = onToggle,
            modifier = Modifier.size(96.dp),
            containerColor = when {
                isSpeech -> AccentGreen
                isListening -> AccentCyan
                else -> AccentViolet
            },
            contentColor = Color.White,
        ) {
            Icon(
                imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (isListening) "Stop" else "Start",
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun ActivityLogCard(entries: List<VadLogEntry>, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Activity Log",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
                if (entries.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    text = "Speech start / end events appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(entries) { entry -> VadLogRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun VadLogRow(entry: VadLogEntry) {
    val started = entry.type == VadActivity.SPEECH_STARTED
    val time = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestampMs))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (started) AccentGreen else TextMuted, CircleShape),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (started) "Speech started" else "Speech ended",
                style = MaterialTheme.typography.bodyMedium,
                color = if (started) AccentGreen else TextMuted,
            )
        }
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Microphone permission required",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequest) { Text("Grant Permission") }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
