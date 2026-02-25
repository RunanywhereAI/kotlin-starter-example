package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.AppScaffold
import com.runanywhere.kotlin_starter_example.ui.components.ChatInputBar
import com.runanywhere.kotlin_starter_example.ui.components.EmptyChat
import com.runanywhere.kotlin_starter_example.ui.components.MessageBubble
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.generateStream
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    AppScaffold(
        title = "Chat",
        subtitle = "LLM Text Generation",
        onBack = onNavigateBack,
        bottomBar = {
            if (modelService.isLLMLoaded) {
                ChatInputBar(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() && !isGenerating) {
                            val userMessage = inputText
                            messages = messages + ChatMessage(userMessage, isUser = true)
                            inputText = ""

                            scope.launch {
                                isGenerating = true
                                messages = messages + ChatMessage("", isUser = false)
                                listState.animateScrollToItem(messages.size)

                                try {
                                    RunAnywhere.generateStream(userMessage)
                                        .collect { token ->
                                            val lastIndex = messages.lastIndex
                                            val current = messages[lastIndex]
                                            messages = messages.toMutableList().apply {
                                                set(lastIndex, current.copy(text = current.text + token))
                                            }
                                        }
                                    listState.animateScrollToItem(messages.size)
                                } catch (e: Exception) {
                                    val lastIndex = messages.lastIndex
                                    messages = messages.toMutableList().apply {
                                        set(lastIndex, ChatMessage("Error: ${e.message}", isUser = false))
                                    }
                                } finally {
                                    isGenerating = false
                                }
                            }
                        }
                    },
                    isGenerating = isGenerating
                )
            }
        }
    ) {
        // Model loader
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
                        color = colors.error,
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
                    EmptyChat(
                        icon = {
                            Icon(
                                TablerIcons.Robot,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(48.dp)
                            )
                        },
                        title = "Start a conversation",
                        subtitle = "Type a message below to chat with the AI"
                    )
                }
            }

            items(messages.size) { index ->
                val message = messages[index]
                val isLastAiMessage = !message.isUser && index == messages.lastIndex
                MessageBubble(
                    text = message.text,
                    isUser = message.isUser,
                    isStreaming = isLastAiMessage && isGenerating
                )
            }
        }
    }
}
