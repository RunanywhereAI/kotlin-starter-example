package com.runanywhere.kotlin_starter_example.ui.screens

import ai.runanywhere.proto.v1.JSONSchema
import ai.runanywhere.proto.v1.JSONSchemaProperty
import ai.runanywhere.proto.v1.JSONSchemaType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.generateStructured
import kotlinx.coroutines.launch

private data class StructuredResult(val json: String, val isValid: Boolean)

// A fixed schema the model must fill: sentiment + one-line summary of the input.
private val reviewSchema = JSONSchema(
    type = JSONSchemaType.JSON_SCHEMA_TYPE_OBJECT,
    properties = mapOf(
        "sentiment" to JSONSchemaProperty(
            type = JSONSchemaType.JSON_SCHEMA_TYPE_STRING,
            description = "Overall sentiment: positive, negative, or neutral",
        ),
        "summary" to JSONSchemaProperty(
            type = JSONSchemaType.JSON_SCHEMA_TYPE_STRING,
            description = "A one-sentence summary of the text",
        ),
        "topic" to JSONSchemaProperty(
            type = JSONSchemaType.JSON_SCHEMA_TYPE_STRING,
            description = "The main subject of the text in one or two words",
        ),
    ),
    required = listOf("sentiment", "summary"),
    additional_properties = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructuredOutputScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier,
) {
    var input by remember {
        mutableStateOf(
            "The delivery arrived two days late, but the headphones sound amazing " +
                "and the battery lasts all week.",
        )
    }
    var isGenerating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<StructuredResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Structured Output") },
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
                .padding(20.dp),
        ) {
            if (!modelService.isLLMLoaded) {
                ModelLoaderWidget(
                    modelName = "SmolLM2 360M",
                    isDownloading = modelService.isLLMDownloading,
                    isLoading = modelService.isLLMLoading,
                    isLoaded = modelService.isLLMLoaded,
                    downloadProgress = modelService.llmDownloadProgress,
                    onLoadClick = { modelService.downloadAndLoadLLM() },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            Text(
                text = "The model is constrained to a JSON schema " +
                    "(sentiment, summary, topic) and its output is parsed back into typed JSON.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                label = { Text("Text to analyze") },
                enabled = modelService.isLLMLoaded && !isGenerating,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PrimaryMid,
                    unfocusedContainerColor = PrimaryMid,
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        isGenerating = true
                        errorMessage = null
                        result = null
                        scope.launch {
                            try {
                                val prompt = "Analyze the following text.\n\n\"$input\""
                                val output = RunAnywhere.generateStructured(prompt, reviewSchema)
                                val json = output.parsed_json.utf8()
                                val text = if (json.isNotBlank()) json else output.raw_text.orEmpty()
                                if (!output.error_message.isNullOrBlank() && text.isBlank()) {
                                    errorMessage = output.error_message
                                } else {
                                    result = StructuredResult(
                                        json = text.ifBlank { "{}" },
                                        isValid = output.validation?.is_valid ?: json.isNotBlank(),
                                    )
                                }
                            } catch (e: Exception) {
                                errorMessage = "Generation failed: ${e.message}"
                            } finally {
                                isGenerating = false
                            }
                        }
                    }
                },
                enabled = modelService.isLLMLoaded && !isGenerating && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating...")
                } else {
                    Text("Extract structured data")
                }
            }

            result?.let { res ->
                Spacer(modifier = Modifier.height(20.dp))
                ResultCard(res)
            }

            errorMessage?.let { err ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: StructuredResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.DataObject,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Parsed JSON",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (result.isValid) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (result.isValid) AccentGreen else AccentOrange,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (result.isValid) "valid" else "unvalidated",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (result.isValid) AccentGreen else AccentOrange,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryMid,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = result.json,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
