package com.runanywhere.kotlin_starter_example.ui.screens

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
import kotlinx.coroutines.launch

enum class VoiceSessionState {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    SPEAKING
}

data class VoiceMessage(
    val text: String,
    val type: String, // "user", "ai", "status"
    val timestamp: Long = System.currentTimeMillis()
)

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
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Check permission
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            errorMessage = "Microphone permission is required"
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
        ) {
            // Model loader section
            val allModelsLoaded = modelService.isLLMLoaded && 
                                 modelService.isSTTLoaded && 
                                 modelService.isTTSLoaded
            
            if (!allModelsLoaded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Voice Agent requires all models",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    
                    ModelLoaderWidget(
                        modelName = "SmolLM2 360M (LLM)",
                        isDownloading = modelService.isLLMDownloading,
                        isLoading = modelService.isLLMLoading,
                        isLoaded = modelService.isLLMLoaded,
                        downloadProgress = modelService.llmDownloadProgress,
                        onLoadClick = { modelService.downloadAndLoadLLM() }
                    )
                    
                    ModelLoaderWidget(
                        modelName = "Whisper Tiny (STT)",
                        isDownloading = modelService.isSTTDownloading,
                        isLoading = modelService.isSTTLoading,
                        isLoaded = modelService.isSTTLoaded,
                        downloadProgress = modelService.sttDownloadProgress,
                        onLoadClick = { modelService.downloadAndLoadSTT() }
                    )
                    
                    ModelLoaderWidget(
                        modelName = "Piper TTS (TTS)",
                        isDownloading = modelService.isTTSDownloading,
                        isLoading = modelService.isTTSLoading,
                        isLoaded = modelService.isTTSLoaded,
                        downloadProgress = modelService.ttsDownloadProgress,
                        onLoadClick = { modelService.downloadAndLoadTTS() }
                    )
                    
                    Button(
                        onClick = { modelService.downloadAndLoadAllModels() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !allModelsLoaded
                    ) {
                        Text("Load All Models")
                    }
                }
            }
            
            // Permission check
            if (!hasPermission && allModelsLoaded) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Microphone permission required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        ) {
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
                            EmptyStateMessage()
                        }
                    }
                    
                    items(messages) { message ->
                        VoiceMessageBubble(message)
                    }
                }
                
                // Control section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceCard.copy(alpha = 0.8f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status indicator
                    StatusIndicator(sessionState)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Voice button
                    VoiceButton(
                        sessionState = sessionState,
                        onClick = {
                            if (sessionState == VoiceSessionState.IDLE) {
                                sessionState = VoiceSessionState.LISTENING
                                messages = messages + VoiceMessage(
                                    "Starting voice session...",
                                    "status"
                                )
                                scope.launch {
                                    listState.animateScrollToItem(messages.size)
                                }
                                // TODO: Start voice session
                                // This would use RunAnywhere.startVoiceSession()
                            } else {
                                sessionState = VoiceSessionState.IDLE
                                messages = messages + VoiceMessage(
                                    "Session ended",
                                    "status"
                                )
                                // TODO: Stop voice session
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
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
        }
    }
}

@Composable
private fun StatusIndicator(state: VoiceSessionState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            label = "Listen",
            isActive = state == VoiceSessionState.LISTENING,
            color = AccentCyan
        )
        StatusDot(
            label = "Think",
            isActive = state == VoiceSessionState.THINKING,
            color = AccentViolet
        )
        StatusDot(
            label = "Speak",
            isActive = state == VoiceSessionState.SPEAKING,
            color = AccentPink
        )
    }
}

@Composable
private fun StatusDot(
    label: String,
    isActive: Boolean,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (isActive) color else TextMuted.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) color else TextMuted
        )
    }
}

@Composable
private fun VoiceButton(
    sessionState: VoiceSessionState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (sessionState != VoiceSessionState.IDLE) 1.1f else 1f,
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
        if (sessionState != VoiceSessionState.IDLE) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AccentGreen.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
        
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            containerColor = if (sessionState != VoiceSessionState.IDLE) AccentViolet else AccentGreen,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = if (sessionState != VoiceSessionState.IDLE) 
                    Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = if (sessionState != VoiceSessionState.IDLE) "Stop" else "Start",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Voice Agent Ready",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the button below to start a voice conversation",
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
            Icon(
                imageVector = Icons.Rounded.SmartToy,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Card(
            modifier = Modifier.widthIn(max = if (message.type == "status") 300.dp else 280.dp),
            shape = RoundedCornerShape(
                topStart = if (message.type == "user") 16.dp else 4.dp,
                topEnd = if (message.type == "user") 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when (message.type) {
                    "user" -> AccentCyan
                    "status" -> SurfaceCard.copy(alpha = 0.5f)
                    else -> SurfaceCard
                }
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.type == "user") Color.White else TextPrimary
            )
        }
        
        if (message.type == "user") {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier
                    .size(32.dp)
                    .padding(top = 4.dp)
            )
        }
    }
}

private fun getStatusText(state: VoiceSessionState): String = when (state) {
    VoiceSessionState.IDLE -> "Tap to start"
    VoiceSessionState.LISTENING -> "Listening..."
    VoiceSessionState.TRANSCRIBING -> "Transcribing..."
    VoiceSessionState.THINKING -> "Thinking..."
    VoiceSessionState.SPEAKING -> "Speaking..."
}

private fun getStatusColor(state: VoiceSessionState): Color = when (state) {
    VoiceSessionState.IDLE -> TextMuted
    VoiceSessionState.LISTENING -> AccentCyan
    VoiceSessionState.TRANSCRIBING -> AccentViolet
    VoiceSessionState.THINKING -> AccentViolet
    VoiceSessionState.SPEAKING -> AccentPink
}
