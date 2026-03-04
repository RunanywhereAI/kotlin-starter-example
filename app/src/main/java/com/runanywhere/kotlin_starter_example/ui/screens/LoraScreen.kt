package com.runanywhere.kotlin_starter_example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalLayoutApi::class)
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
    var isAdaptersPanelExpanded by remember { mutableStateOf(true) }

    // Per-adapter download state
    val downloadingAdapters = remember { mutableStateMapOf<String, Boolean>() }
    val downloadProgress = remember { mutableStateMapOf<String, Float>() }
    val downloadedAdapters = remember {
        mutableStateMapOf<String, Boolean>().apply {
            val loraDir = File(context.filesDir, "lora_adapters")
            ModelService.LORA_ADAPTERS.forEach { adapter ->
                put(adapter.id, File(loraDir, adapter.filename).exists())
            }
        }
    }

    // Chat state
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Current example prompts based on loaded adapters
    val currentExamplePrompts = remember(loadedAdapters) {
        loadedAdapters.flatMap { adapter ->
            val filename = adapter.path.substringAfterLast("/")
            ModelService.LORA_ADAPTERS
                .find { it.filename == filename }
                ?.examplePrompts ?: emptyList()
        }
    }

    val activeCount = remember(loadedAdapters) {
        ModelService.LORA_ADAPTERS.count { def ->
            loadedAdapters.any { it.path.substringAfterLast("/") == def.filename }
        }
    }

    suspend fun refreshAdapters() {
        try {
            loadedAdapters = RunAnywhere.getLoadedLoraAdapters()
        } catch (_: Exception) {
            loadedAdapters = emptyList()
        }
    }

    fun downloadAndLoadAdapter(adapterDef: ModelService.Companion.LoraAdapterDef) {
        scope.launch {
            val loraDir = File(context.filesDir, "lora_adapters")
            val adapterFile = File(loraDir, adapterDef.filename)

            loraError = null

            if (!adapterFile.exists()) {
                downloadingAdapters[adapterDef.id] = true
                downloadProgress[adapterDef.id] = 0f

                val success = withContext(Dispatchers.IO) {
                    try {
                        adapterFile.parentFile?.mkdirs()
                        val connection = URL(adapterDef.url).openConnection() as HttpURLConnection
                        connection.connectTimeout = 30_000
                        connection.readTimeout = 30_000
                        connection.connect()

                        val totalBytes = connection.contentLengthLong
                        var downloadedBytes = 0L

                        connection.inputStream.use { input ->
                            adapterFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead
                                    if (totalBytes > 0) {
                                        downloadProgress[adapterDef.id] = downloadedBytes.toFloat() / totalBytes.toFloat()
                                    }
                                }
                            }
                        }
                        true
                    } catch (e: Exception) {
                        adapterFile.delete()
                        loraError = "Download failed: ${e.message}"
                        false
                    }
                }

                downloadingAdapters[adapterDef.id] = false

                if (!success) return@launch
                downloadedAdapters[adapterDef.id] = true
            }

            isLoadingAdapter = true
            try {
                RunAnywhere.loadLoraAdapter(
                    LoRAAdapterConfig(path = adapterFile.absolutePath, scale = adapterScale)
                )
                refreshAdapters()
            } catch (e: Exception) {
                loraError = "Failed to load ${adapterDef.name} adapter: ${e.message}"
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

    fun sendMessage(text: String) {
        if (text.isBlank() || isGenerating) return
        val userMessage = text
        messages = messages + ChatMessage(userMessage, isUser = true)
        inputText = ""
        // Collapse the panel once user starts chatting
        isAdaptersPanelExpanded = false

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

    @Composable
    fun adapterIcon(adapterId: String): ImageVector = when (adapterId) {
        "code-assistant-lora" -> TablerIcons.Tool
        "reasoning-logic-lora" -> TablerIcons.Calculator
        "medical-qa-lora" -> TablerIcons.ShieldCheck
        "creative-writing-lora" -> TablerIcons.Typography
        else -> TablerIcons.Tune
    }

    fun adapterColor(adapterId: String): Color = when (adapterId) {
        "code-assistant-lora" -> colors.tintBlue
        "reasoning-logic-lora" -> colors.tintOrange
        "medical-qa-lora" -> colors.tintGreen
        "creative-writing-lora" -> colors.tintPurple
        else -> colors.tintCyan
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
                    onSend = { sendMessage(inputText) },
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
                    modelName = "Qwen 2.5 0.5B (LoRA Base)",
                    isDownloading = modelService.isLoraBaseDownloading,
                    isLoading = modelService.isLoraBaseLoading,
                    isLoaded = modelService.isLoraBaseLoaded,
                    downloadProgress = modelService.loraBaseDownloadProgress,
                    onLoadClick = { modelService.downloadAndLoadLoraBase() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Load the Qwen 2.5 base model first. This model supports LoRA adapters for fine-tuned behavior.",
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
            // Collapsible adapters panel
            val chevronRotation by animateFloatAsState(
                targetValue = if (isAdaptersPanelExpanded) 90f else 0f,
                label = "chevron"
            )

            AppCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .animateContentSize()
            ) {
                // Header row - always visible, tappable to expand/collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAdaptersPanelExpanded = !isAdaptersPanelExpanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        TablerIcons.Tune,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "LoRA Adapters",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (activeCount > 0) "$activeCount active" else "None loaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeCount > 0) colors.success else colors.textSecondary
                        )
                    }

                    // Active adapter dots (compact indicator when collapsed)
                    if (!isAdaptersPanelExpanded && activeCount > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            loadedAdapters.forEach { adapter ->
                                val filename = adapter.path.substringAfterLast("/")
                                val def = ModelService.LORA_ADAPTERS.find { it.filename == filename }
                                if (def != null) {
                                    Icon(
                                        adapterIcon(def.id),
                                        contentDescription = def.name,
                                        tint = adapterColor(def.id),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

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
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                TablerIcons.TrashX,
                                contentDescription = "Clear all",
                                tint = colors.tintPink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Icon(
                        TablerIcons.ArrowLeft,
                        contentDescription = if (isAdaptersPanelExpanded) "Collapse" else "Expand",
                        tint = colors.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation - 90f)
                    )
                }

                // Expanded content
                AnimatedVisibility(visible = isAdaptersPanelExpanded) {
                    Column {
                        HorizontalDivider(color = colors.border, modifier = Modifier.padding(vertical = 8.dp))

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
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        androidx.compose.material3.Slider(
                            value = adapterScale,
                            onValueChange = { adapterScale = it },
                            valueRange = 0f..2f,
                            steps = 39,
                            modifier = Modifier.height(32.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accent,
                                activeTrackColor = colors.accent,
                                inactiveTrackColor = colors.border
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Adapter rows - compact
                        ModelService.LORA_ADAPTERS.forEach { adapterDef ->
                            val isDownloading = downloadingAdapters[adapterDef.id] == true
                            val progress = downloadProgress[adapterDef.id] ?: 0f
                            val isDownloaded = downloadedAdapters[adapterDef.id] == true
                            val isLoaded = loadedAdapters.any {
                                it.path.substringAfterLast("/") == adapterDef.filename
                            }
                            val color = adapterColor(adapterDef.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    adapterIcon(adapterDef.id),
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = adapterDef.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary
                                    )
                                    // Download progress inline
                                    if (isDownloading) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(2.dp)
                                                .clip(RoundedCornerShape(1.dp)),
                                            color = color,
                                            trackColor = colors.border,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))

                                if (isLoaded) {
                                    // Active badge + remove button
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.success
                                    )
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val adapterPath = loadedAdapters.find {
                                                        it.path.substringAfterLast("/") == adapterDef.filename
                                                    }?.path
                                                    if (adapterPath != null) {
                                                        RunAnywhere.removeLoraAdapter(adapterPath)
                                                        refreshAdapters()
                                                    }
                                                } catch (e: Exception) {
                                                    loraError = "Remove failed: ${e.message}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            TablerIcons.X,
                                            contentDescription = "Remove",
                                            tint = colors.tintPink,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    // Load/Download button (compact)
                                    AppButton(
                                        onClick = { downloadAndLoadAdapter(adapterDef) },
                                        enabled = !isLoadingAdapter && !isDownloading,
                                        color = color,
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                if (isDownloaded) TablerIcons.Tune else TablerIcons.Download,
                                                null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                if (isDownloaded) "Load" else "Get",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Custom adapter
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            AppOutlinedButton(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                enabled = !isLoadingAdapter,
                            ) {
                                Icon(TablerIcons.Plus, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Custom .gguf", style = MaterialTheme.typography.labelSmall)
                            }
                        }
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
                                    imageVector = if (loadedAdapters.isNotEmpty()) TablerIcons.Sparkles else TablerIcons.Tune,
                                    contentDescription = null,
                                    tint = if (loadedAdapters.isNotEmpty()) colors.tintPurple else colors.tintCyan,
                                    modifier = Modifier.size(48.dp)
                                )
                            },
                            title = if (loadedAdapters.isNotEmpty()) "LoRA adapter active" else "No adapters loaded",
                            subtitle = if (loadedAdapters.isNotEmpty())
                                "Try an example prompt or type your own!"
                            else
                                "Expand the panel above to load a LoRA adapter"
                        )

                        // Example prompt chips in the chat area
                        if (currentExamplePrompts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                currentExamplePrompts.forEach { prompt ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.tintCyan.copy(alpha = 0.1f),
                                        modifier = Modifier.clickable { sendMessage(prompt) }
                                    ) {
                                        Text(
                                            text = prompt,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.tintCyan,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
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
