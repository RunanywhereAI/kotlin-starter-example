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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.transcribe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioData = ByteArrayOutputStream()

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun startRecording(): Boolean {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) return false
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize * 2)
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) return false
            audioData.reset()
            audioRecord?.startRecording()
            isRecording = true
            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) synchronized(audioData) { audioData.write(buffer, 0, read) }
                }
            }.start()
            return true
        } catch (e: SecurityException) { return false }
    }

    fun stopRecording(): ByteArray {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        synchronized(audioData) { return audioData.toByteArray() }
    }
}

@Composable
fun SpeechToTextScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var transcription by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorder() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) errorMessage = "Microphone permission is required for speech recognition"
    }

    AppScaffold(title = "Speech to Text", subtitle = "Whisper STT", onBack = onNavigateBack) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!modelService.isSTTLoaded) {
                ModelLoaderWidget(modelName = "Whisper Tiny", isDownloading = modelService.isSTTDownloading, isLoading = modelService.isSTTLoading, isLoaded = modelService.isSTTLoaded, downloadProgress = modelService.sttDownloadProgress, onLoadClick = { modelService.downloadAndLoadSTT() })
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (!hasPermission) {
                AppCard {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Microphone permission required", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(10.dp))
                        AppButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant Permission") }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (modelService.isSTTLoaded && hasPermission) {
                RecordingButton(isRecording = isRecording, isTranscribing = isTranscribing, onClick = {
                    if (!isRecording && !isTranscribing) {
                        scope.launch {
                            try {
                                val started = withContext(Dispatchers.IO) { audioRecorder.startRecording() }
                                if (started) { isRecording = true; errorMessage = null } else errorMessage = "Failed to start audio recording"
                            } catch (e: Exception) { errorMessage = "Recording failed: ${e.message}" }
                        }
                    } else if (isRecording) {
                        isRecording = false; isTranscribing = true
                        scope.launch {
                            try {
                                val audioData = withContext(Dispatchers.IO) { audioRecorder.stopRecording() }
                                if (audioData.isEmpty()) { errorMessage = "No audio recorded"; return@launch }
                                val result = withContext(Dispatchers.IO) { RunAnywhere.transcribe(audioData) }
                                if (result.isNotBlank()) { transcription = result; errorMessage = null } else errorMessage = "No speech detected"
                            } catch (e: Exception) { errorMessage = "Transcription failed: ${e.message}" } finally { isTranscribing = false }
                        }
                    }
                })
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = when { isRecording -> "Tap to stop recording"; isTranscribing -> "Transcribing..."; else -> "Tap to start recording" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when { isRecording -> colors.tintPurple; isTranscribing -> colors.accent; else -> colors.textSecondary }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            if (transcription.isNotEmpty()) {
                AppCard {
                    Text("Transcription", style = MaterialTheme.typography.titleMedium, color = colors.accent)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(transcription, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                }
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(14.dp))
                AppCard { Text(error, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
            }

            if (modelService.isSTTLoaded && hasPermission) {
                Spacer(modifier = Modifier.height(28.dp))
                AppCard {
                    Text("How it works", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Tap the microphone to start recording\nSpeak clearly into your device\nTap the stop button when finished\nView your transcribed text below", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun RecordingButton(isRecording: Boolean, isTranscribing: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(1f, if (isRecording) 1.08f else 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")

    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        if (isRecording) Box(modifier = Modifier.size(100.dp).scale(scale).background(colors.tintPurple.copy(alpha = 0.15f), CircleShape))
        IconButton(
            onClick = onClick, modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = when { isRecording -> colors.tintPurple; isTranscribing -> colors.accent; else -> colors.accent },
                contentColor = Color.White
            )
        ) {
            if (isTranscribing) CircularProgressIndicator(Modifier.size(28.dp), Color.White, strokeWidth = 2.5.dp)
            else Icon(if (isRecording) TablerIcons.PlayerStop else TablerIcons.Microphone, if (isRecording) "Stop" else "Record", Modifier.size(28.dp))
        }
    }
}
