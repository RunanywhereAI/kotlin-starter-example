package com.runanywhere.kotlin_starter_example.ui.screens

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.AppCard
import com.runanywhere.kotlin_starter_example.ui.components.AppScaffold
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.synthesize
import com.runanywhere.sdk.public.extensions.TTS.TTSOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

private suspend fun playWavAudio(wavData: ByteArray) = withContext(Dispatchers.IO) {
    if (wavData.size < 44) return@withContext
    val buffer = ByteBuffer.wrap(wavData).order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(20)
    buffer.short.toInt(); val numChannels = buffer.short.toInt(); val sampleRate = buffer.int; buffer.int; buffer.short; val bitsPerSample = buffer.short.toInt()
    var dataOffset = 36
    while (dataOffset < wavData.size - 8) { if (wavData[dataOffset].toInt().toChar() == 'd' && wavData[dataOffset + 1].toInt().toChar() == 'a' && wavData[dataOffset + 2].toInt().toChar() == 't' && wavData[dataOffset + 3].toInt().toChar() == 'a') break; dataOffset++ }
    dataOffset += 8
    if (dataOffset >= wavData.size) return@withContext
    val pcmData = wavData.copyOfRange(dataOffset, wavData.size)
    val channelConfig = if (numChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
    val audioFormatConfig = if (bitsPerSample == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
    val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormatConfig)
    val audioTrack = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(audioFormatConfig).setChannelMask(channelConfig).build()).setBufferSizeInBytes(maxOf(minBufferSize, pcmData.size)).setTransferMode(AudioTrack.MODE_STATIC).build()
    audioTrack.write(pcmData, 0, pcmData.size); audioTrack.play()
    Thread.sleep((pcmData.size.toLong() * 1000) / (sampleRate * numChannels * (bitsPerSample / 8)) + 100)
    audioTrack.stop(); audioTrack.release()
}

@Composable
fun TextToSpeechScreen(onNavigateBack: () -> Unit, modelService: ModelService = viewModel(), modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    var inputText by remember { mutableStateOf("Hello! This is a test of the text-to-speech system.") }
    var isSpeaking by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AppScaffold(title = "Text to Speech", subtitle = "Piper TTS", onBack = onNavigateBack) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!modelService.isTTSLoaded) {
                ModelLoaderWidget(modelName = "Piper TTS", isDownloading = modelService.isTTSDownloading, isLoading = modelService.isTTSLoading, isLoaded = modelService.isTTSLoaded, downloadProgress = modelService.ttsDownloadProgress, onLoadClick = { modelService.downloadAndLoadTTS() })
                Spacer(modifier = Modifier.height(20.dp))
            }
            if (modelService.isTTSLoaded) {
                AppCard {
                    Text("Enter text to speak", style = MaterialTheme.typography.titleMedium, color = colors.tintPink)
                    Spacer(modifier = Modifier.height(10.dp))
                    TextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Type something...", color = colors.textTertiary) }, enabled = !isSpeaking,
                        colors = TextFieldDefaults.colors(focusedContainerColor = colors.surfaceContainer, unfocusedContainerColor = colors.surfaceContainer, disabledContainerColor = colors.surfaceContainer, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = colors.tintPink),
                        shape = RoundedCornerShape(12.dp), minLines = 4, maxLines = 8, textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary))
                }
                Spacer(modifier = Modifier.height(28.dp))
                SpeakButton(isSpeaking = isSpeaking, onClick = {
                    if (!isSpeaking && inputText.isNotBlank()) { isSpeaking = true
                        scope.launch { try { val output = withContext(Dispatchers.IO) { RunAnywhere.synthesize(inputText, TTSOptions()) }; playWavAudio(output.audioData); errorMessage = null } catch (e: Exception) { errorMessage = "TTS failed: ${e.message}" } finally { isSpeaking = false } }
                    }
                })
                Spacer(modifier = Modifier.height(14.dp))
                Text(if (isSpeaking) "Speaking..." else "Tap to speak", style = MaterialTheme.typography.bodyMedium, color = if (isSpeaking) colors.tintPink else colors.textSecondary)
            }
            errorMessage?.let { Spacer(modifier = Modifier.height(14.dp)); AppCard { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) } }
            if (modelService.isTTSLoaded) {
                Spacer(modifier = Modifier.height(28.dp))
                SampleTextsCard { inputText = it }
                Spacer(modifier = Modifier.height(20.dp))
                AppCard { Text("How it works", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary); Spacer(Modifier.height(10.dp)); Text("Enter text in the field above\nOr select a sample text\nTap the speaker button to hear it\nAll processing happens on-device", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun SpeakButton(isSpeaking: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(1f, if (isSpeaking) 1.08f else 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale")
    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        if (isSpeaking) Box(modifier = Modifier.size(100.dp).scale(scale).background(colors.tintPink.copy(alpha = 0.15f), CircleShape))
        IconButton(onClick = onClick, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.iconButtonColors(containerColor = if (isSpeaking) colors.tintPink.copy(alpha = 0.8f) else colors.tintPink, contentColor = Color.White)) {
            Icon(TablerIcons.Volume, "Speak", Modifier.size(28.dp))
        }
    }
}

@Composable
private fun SampleTextsCard(onSelectSample: (String) -> Unit) {
    val colors = AppTheme.colors
    AppCard {
        Text("Sample Texts", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        listOf("Hello! This is a test of the text-to-speech system.", "The quick brown fox jumps over the lazy dog.", "Artificial intelligence is transforming how we interact with technology.", "Welcome to the future of on-device AI processing.").forEach { sample ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelectSample(sample) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(TablerIcons.Typography, null, tint = colors.tintPink, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(sample.take(50) + if (sample.length > 50) "..." else "", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}
