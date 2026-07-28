package com.runanywhere.kotlin_starter_example.ui.screens

import ai.runanywhere.proto.v1.ModelCategory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.services.ModelOption
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.theme.AccentCyan
import com.runanywhere.kotlin_starter_example.ui.theme.AccentGreen
import com.runanywhere.kotlin_starter_example.ui.theme.PrimaryDark
import com.runanywhere.kotlin_starter_example.ui.theme.SurfaceCard
import com.runanywhere.kotlin_starter_example.ui.theme.TextMuted
import com.runanywhere.kotlin_starter_example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QHexRTLabScreen(
    modelService: ModelService,
    onNavigateBack: () -> Unit,
) {
    val groups = listOf(
        "Language" to ModelCategory.MODEL_CATEGORY_LANGUAGE,
        "Speech-to-Text" to ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION,
        "Text-to-Speech" to ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS,
        "Embeddings" to ModelCategory.MODEL_CATEGORY_EMBEDDING,
        "Vision / OCR" to ModelCategory.MODEL_CATEGORY_MULTIMODAL,
    )

    fun optionsFor(category: ModelCategory): List<ModelOption> =
        when (category) {
            ModelCategory.MODEL_CATEGORY_LANGUAGE -> modelService.llmOptions
            ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION -> modelService.sttOptions
            ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS -> modelService.ttsOptions
            ModelCategory.MODEL_CATEGORY_EMBEDDING -> modelService.embeddingOptions
            ModelCategory.MODEL_CATEGORY_MULTIMODAL -> modelService.vlmOptions
            else -> emptyList()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Lab") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDark),
            )
        },
        containerColor = PrimaryDark,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.7f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Memory, contentDescription = null, tint = AccentCyan)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    !modelService.isSdkReady || modelService.isCatalogRegistering ->
                                        "Registering model catalogs…"
                                    modelService.npuSupported ->
                                        "Hexagon ${modelService.npuArchName ?: "NPU"} · " +
                                            "${modelService.registeredNpuCount} HNPU + CPU fallbacks"
                                    else -> "CPU backends only (no Hexagon NPU)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Load on any row. Loading one model unloads the others first — " +
                                "stacking HNPU graphs on 8GB phones can kernel-panic the device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { modelService.unloadAllModels() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Unload all models")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { modelService.hardKillReset() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Hard reset HNPU (kill app)")
                        }
                        Text(
                            text = "Prefer Hard reset between HNPU models — soft unload " +
                                "can kernel-panic via Qualcomm fastrpc/pm_qos on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                        if (modelService.isCatalogRegistering) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        modelService.statusMessage?.let { note ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                note,
                                color = AccentGreen,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        modelService.errorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            groups.forEach { (title, category) ->
                val rows = optionsFor(category)
                if (rows.isEmpty()) return@forEach
                val npuCount = rows.count { it.usesNpu }
                val cpuCount = rows.size - npuCount
                item {
                    Text(
                        text = "$title · $npuCount HNPU / $cpuCount CPU",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentCyan,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(rows, key = { it.id }) { option ->
                    ModelLabRow(
                        option = option,
                        selected = isSelected(modelService, option),
                        busy = isBusy(modelService, option),
                        progress = progressFor(modelService, option),
                        loaded = isLoaded(modelService, option),
                        onLoad = { modelService.downloadAndLoadOption(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelLabRow(
    option: ModelOption,
    selected: Boolean,
    busy: Boolean,
    progress: Float,
    loaded: Boolean,
    onLoad: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceCard.copy(alpha = if (selected) 0.85f else 0.55f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append(option.backendLabel)
                            append(" · ")
                            append(option.id)
                            if (option.sizeLabel.isNotBlank()) append(" · ${option.sizeLabel}")
                            if (selected) append(" · selected")
                            if (loaded) append(" · ready")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            loaded -> AccentGreen
                            option.usesNpu -> AccentCyan
                            else -> TextMuted
                        },
                    )
                }
                Button(onClick = onLoad, enabled = !busy) {
                    Text(
                        when {
                            busy && progress > 0f && progress < 1f -> "${(progress * 100).toInt()}%"
                            busy -> "…"
                            loaded -> "Reload"
                            else -> "Load"
                        }
                    )
                }
            }
            if (busy) {
                Spacer(modifier = Modifier.height(10.dp))
                if (progress > 0f && progress < 1f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun isSelected(service: ModelService, option: ModelOption): Boolean =
    when (option.category) {
        ModelCategory.MODEL_CATEGORY_LANGUAGE -> service.llmModelId == option.id
        ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION -> service.sttModelId == option.id
        ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS -> service.ttsModelId == option.id
        ModelCategory.MODEL_CATEGORY_EMBEDDING -> service.embeddingModelId == option.id
        ModelCategory.MODEL_CATEGORY_MULTIMODAL -> service.vlmModelId == option.id
        else -> false
    }

private fun isLoaded(service: ModelService, option: ModelOption): Boolean =
    isSelected(service, option) && when (option.category) {
        ModelCategory.MODEL_CATEGORY_LANGUAGE -> service.isLLMLoaded
        ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION -> service.isSTTLoaded
        ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS -> service.isTTSLoaded
        ModelCategory.MODEL_CATEGORY_EMBEDDING -> service.isEmbeddingLoaded
        ModelCategory.MODEL_CATEGORY_MULTIMODAL -> service.isVLMLoaded
        else -> false
    }

private fun isBusy(service: ModelService, option: ModelOption): Boolean =
    isSelected(service, option) && when (option.category) {
        ModelCategory.MODEL_CATEGORY_LANGUAGE -> service.isLLMDownloading || service.isLLMLoading
        ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION -> service.isSTTDownloading || service.isSTTLoading
        ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS -> service.isTTSDownloading || service.isTTSLoading
        ModelCategory.MODEL_CATEGORY_EMBEDDING ->
            service.isEmbeddingDownloading || service.isEmbeddingLoading
        ModelCategory.MODEL_CATEGORY_MULTIMODAL -> service.isVLMDownloading || service.isVLMLoading
        else -> false
    }

private fun progressFor(service: ModelService, option: ModelOption): Float =
    when (option.category) {
        ModelCategory.MODEL_CATEGORY_LANGUAGE -> service.llmDownloadProgress
        ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION -> service.sttDownloadProgress
        ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS -> service.ttsDownloadProgress
        ModelCategory.MODEL_CATEGORY_EMBEDDING -> service.embeddingDownloadProgress
        ModelCategory.MODEL_CATEGORY_MULTIMODAL -> service.vlmDownloadProgress
        else -> 0f
    }
