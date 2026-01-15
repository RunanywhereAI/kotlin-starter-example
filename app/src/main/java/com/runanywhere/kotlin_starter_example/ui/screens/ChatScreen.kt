package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat - LLM") },
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
            if (!modelService.isLLMLoaded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    ModelLoaderWidget(
                        modelName = "SmolLM2 360M",
                        isDownloading = modelService.isLLMDownloading,
                        isLoading = modelService.isLLMLoading,
                        isLoaded = modelService.isLLMLoaded,
                        downloadProgress = modelService.llmDownloadProgress,
                        onLoadClick = { modelService.downloadAndLoadLLM() }
                    )
                    
                    modelService.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            
            // Chat messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (messages.isEmpty() && modelService.isLLMLoaded) {
                    item {
                        EmptyStateMessage()
                    }
                }
                
                items(messages) { message ->
                    ChatMessageBubble(message)
                }
            }
            
            // Input section
            if (modelService.isLLMLoaded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard.copy(alpha = 0.8f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message...") },
                            enabled = !isGenerating,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = PrimaryMid,
                                unfocusedContainerColor = PrimaryMid,
                                disabledContainerColor = PrimaryMid,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 4
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        FloatingActionButton(
                            onClick = {
                                if (inputText.isNotBlank() && !isGenerating) {
                                    val userMessage = inputText
                                    messages = messages + ChatMessage(userMessage, isUser = true)
                                    inputText = ""
                                    
                                    scope.launch {
                                        isGenerating = true
                                        listState.animateScrollToItem(messages.size)
                                        
                                        try {
                                            val response = com.runanywhere.sdk.public.RunAnywhere.chat(userMessage)
                                            messages = messages + ChatMessage(response, isUser = false)
                                            listState.animateScrollToItem(messages.size)
                                        } catch (e: Exception) {
                                            messages = messages + ChatMessage(
                                                "Error: ${e.message}",
                                                isUser = false
                                            )
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                }
                            },
                            containerColor = if (isGenerating) AccentViolet else AccentCyan,
                            enabled = !isGenerating && inputText.isNotBlank()
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.Send, "Send")
                            }
                        }
                    }
                }
            }
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
            imageVector = Icons.Rounded.SmartToy,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Start a conversation",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Type a message below to chat with the AI",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
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
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 16.dp else 4.dp,
                topEnd = if (message.isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) AccentCyan else SurfaceCard
            )
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isUser) Color.White else TextPrimary
            )
        }
        
        if (message.isUser) {
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
