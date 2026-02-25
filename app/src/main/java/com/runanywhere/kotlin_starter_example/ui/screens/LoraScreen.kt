package com.runanywhere.kotlin_starter_example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.AppButton
import com.runanywhere.kotlin_starter_example.ui.components.AppCard
import com.runanywhere.kotlin_starter_example.ui.components.AppOutlinedButton
import com.runanywhere.kotlin_starter_example.ui.components.AppScaffold
import com.runanywhere.kotlin_starter_example.ui.components.ChatInputBar
import com.runanywhere.kotlin_starter_example.ui.components.EmptyChat
import com.runanywhere.kotlin_starter_example.ui.components.MessageBubble
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.LLM.LoRAAdapterConfig
import com.runanywhere.sdk.public.extensions.LLM.LoRAAdapterInfo
import com.runanywhere.sdk.public.extensions.generateStream
import com.runanywhere.sdk.public.extensions.clearLoraAdapters
import com.runanywhere.sdk.public.extensions.getLoadedLoraAdapters
import com.runanywhere.sdk.public.extensions.loadLoraAdapter
import com.runanywhere.sdk.public.extensions.removeLoraAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun LoraScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // LoRA state
    var adapterScale by remember { mutableFloatStateOf(1.0f) }
    var loadedAdapters by remember { mutableStateOf(listOf<LoRAAdapterInfo>()) }
    var isLoadingAdapter by remember { mutableStateOf(false) }
    var loraError by remember { mutableStateOf<String?>(null) }

    // Translator LoRA download state
    var isTranslatorDownloading by remember { mutableStateOf(false) }
    var translatorDownloadProgress by remember { mutableFloatStateOf(0f) }
    var isTranslatorDownloaded by remember {
        val loraDir = File(context.filesDir, "lora_adapters")
        val adapterFile = File(loraDir, ModelService.TRANSLATOR_LORA_FILENAME)
        mutableStateOf(adapterFile.exists())
    }

    // Chat state
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    suspend fun refreshAdapters() {
        try {
            loadedAdapters = RunAnywhere.getLoadedLoraAdapters()
        } catch (_: Exception) {
            loadedAdapters = emptyList()
        }
    }

    suspend fun downloadFile(url: String, destFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                destFile.parentFile?.mkdirs()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                connection.connect()

                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L

                connection.inputStream.use { input ->
                    destFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                translatorDownloadProgress = downloadedBytes.toFloat() / totalBytes.toFloat()
                            }
                        }
                    }
                }
                true
            } catch (e: Exception) {
                destFile.delete()
                loraError = "Download failed: ${e.message}"
                false
            }
        }
    }

    fun downloadAndLoadTranslator() {
        scope.launch {
            val loraDir = File(context.filesDir, "lora_adapters")
            val adapterFile = File(loraDir, ModelService.TRANSLATOR_LORA_FILENAME)

            loraError = null

            if (!adapterFile.exists()) {
                isTranslatorDownloading = true
                translatorDownloadProgress = 0f

                val success = downloadFile(ModelService.TRANSLATOR_LORA_URL, adapterFile)
                isTranslatorDownloading = false

                if (!success) return@launch
                isTranslatorDownloaded = true
            }

            isLoadingAdapter = true
            try {
                RunAnywhere.loadLoraAdapter(
                    LoRAAdapterConfig(path = adapterFile.absolutePath, scale = adapterScale)
                )
                refreshAdapters()
            } catch (e: Exception) {
                loraError = "Failed to load translator adapter: ${e.message}"
            } finally {
                isLoadingAdapter = false
            }
        }
    }

    fun copyUriToLocal(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val loraDir = File(context.filesDir, "lora_adapters").apply { mkdirs() }
            val fileName = uri.lastPathSegment?.substringAfterLast("/")
                ?.takeIf { it.endsWith(".gguf") }
                ?: "adapter_${System.currentTimeMillis()}.gguf"
            val destFile = File(loraDir, fileName)
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            loraError = "Failed to copy adapter file: ${e.message}"
            null
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoadingAdapter = true
                loraError = null
                try {
                    val localPath = withContext(Dispatchers.IO) { copyUriToLocal(uri) }
                    if (localPath != null) {
                        RunAnywhere.loadLoraAdapter(
                            LoRAAdapterConfig(path = localPath, scale = adapterScale)
                        )
                        refreshAdapters()
                    }
                } catch (e: Exception) {
                    loraError = "Failed to load adapter: ${e.message}"
                } finally {
                    isLoadingAdapter = false
                }
            }
        }
    }

    AppScaffold(
        title = "LoRA Adapters",
        subtitle = "Fine-Tune with Adapters",
        onBack = onNavigateBack,
        bottomBar = {
            if (modelService.isLoraBaseLoaded) {
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
                    isGenerating = isGenerating,
                    placeholder = "Test with LoRA adapters...",
                    accentColor = colors.tintCyan
                )
            }
        }
    ) {
        // Step 1: Load LoRA-compatible base model
        if (!modelService.isLoraBaseLoaded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                ModelLoaderWidget(
                    modelName = "LFM2 350M (LoRA Base)",
                    isDownloading = modelService.isLoraBaseDownloading,
                    isLoading = modelService.isLoraBaseLoading,
                    isLoaded = modelService.isLoraBaseLoaded,
                    downloadProgress = modelService.loraBaseDownloadProgress,
                    onLoadClick = { modelService.downloadAndLoadLoraBase() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Load the LFM2 base model first. This model supports LoRA adapters for fine-tuned behavior.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
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

        // LoRA controls (only when base model is loaded)
        if (modelService.isLoraBaseLoaded) {
            // Translator LoRA card
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        TablerIcons.Language,
                        contentDescription = null,
                        tint = colors.tintPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Translator LoRA",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Translation adapter fine-tuned for multilingual tasks",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scale slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scale",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "%.2f".format(adapterScale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accent,
                        fontWeight = FontWeight.Bold
                    )
                }

                androidx.compose.material3.Slider(
                    value = adapterScale,
                    onValueChange = { adapterScale = it },
                    valueRange = 0f..2f,
                    steps = 39,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.border
                    )
                )

                // Download progress
                AnimatedVisibility(
                    visible = isTranslatorDownloading,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Column {
                        Text(
                            text = "Downloading ${(translatorDownloadProgress * 100).toInt()}%...",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { translatorDownloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = colors.accent,
                            trackColor = colors.border,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        onClick = { downloadAndLoadTranslator() },
                        enabled = !isLoadingAdapter && !isTranslatorDownloading,
                        color = colors.tintPurple,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLoadingAdapter || isTranslatorDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Text(if (isTranslatorDownloading) "Downloading..." else "Loading...")
                        } else {
                            Icon(
                                if (isTranslatorDownloaded) TablerIcons.Tune else TablerIcons.Download,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(if (isTranslatorDownloaded) "Load" else "Download")
                        }
                    }

                    AppOutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !isLoadingAdapter && !isTranslatorDownloading,
                    ) {
                        Icon(TablerIcons.Plus, null, modifier = Modifier.size(16.dp))
                        Text("Custom")
                    }
                }
            }

            // Error display
            loraError?.let { error ->
                Text(
                    text = error,
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // Loaded adapters list
            AnimatedVisibility(visible = loadedAdapters.isNotEmpty()) {
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Adapters (${loadedAdapters.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )

                        if (loadedAdapters.size > 1) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            loraError = null
                                            RunAnywhere.clearLoraAdapters()
                                            refreshAdapters()
                                        } catch (e: Exception) {
                                            loraError = "Clear failed: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    TablerIcons.TrashX,
                                    contentDescription = "Clear all",
                                    tint = colors.tintPink,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    loadedAdapters.forEach { adapter ->
                        LoraAdapterInfoRow(
                            adapter = adapter,
                            onRemove = {
                                scope.launch {
                                    try {
                                        loraError = null
                                        RunAnywhere.removeLoraAdapter(adapter.path)
                                        refreshAdapters()
                                    } catch (e: Exception) {
                                        loraError = "Remove failed: ${e.message}"
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // Chat section
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
                                    imageVector = if (loadedAdapters.isNotEmpty()) TablerIcons.Language else TablerIcons.Tune,
                                    contentDescription = null,
                                    tint = if (loadedAdapters.isNotEmpty()) colors.tintPurple else colors.tintCyan,
                                    modifier = Modifier.size(48.dp)
                                )
                            },
                            title = if (loadedAdapters.isNotEmpty()) "Translator adapter active" else "No adapters loaded",
                            subtitle = if (loadedAdapters.isNotEmpty())
                                "Try chatting below - ask it to translate something!"
                            else
                                "Load the Translator LoRA or pick a custom .gguf adapter"
                        )
                    }
                }

                items(messages) { message ->
                    MessageBubble(
                        text = message.text,
                        isUser = message.isUser,
                        accentColor = colors.tintCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun LoraAdapterInfoRow(
    adapter: LoRAAdapterInfo,
    onRemove: () -> Unit
) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            TablerIcons.Tune,
            contentDescription = null,
            tint = colors.tintOrange,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = adapter.path.substringAfterLast("/"),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                maxLines = 1
            )
            Text(
                text = "Scale: ${adapter.scale}" +
                        if (adapter.applied) " · Applied" else " · Pending",
                style = MaterialTheme.typography.labelSmall,
                color = if (adapter.applied) colors.success else colors.textSecondary
            )
        }

        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                TablerIcons.X,
                contentDescription = "Remove adapter",
                tint = colors.tintPink,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
