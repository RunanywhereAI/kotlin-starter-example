package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.embeddings
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private data class EmbeddingComparison(
    val dimension: Int,
    val similarity: Float,
    val previewA: List<Float>,
    val previewB: List<Float>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddingsScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier,
) {
    var textA by remember { mutableStateOf("The cat sat on the warm windowsill.") }
    var textB by remember { mutableStateOf("A feline rested by the sunny window.") }
    var isComputing by remember { mutableStateOf(false) }
    var comparison by remember { mutableStateOf<EmbeddingComparison?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Embeddings") },
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
            if (!modelService.isEmbeddingLoaded) {
                ModelLoaderWidget(
                    modelName = "All MiniLM L6 v2",
                    isDownloading = modelService.isEmbeddingDownloading,
                    isLoading = modelService.isEmbeddingLoading,
                    isLoaded = modelService.isEmbeddingLoaded,
                    downloadProgress = modelService.embeddingDownloadProgress,
                    onLoadClick = { modelService.downloadAndLoadEmbedding() },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            Text(
                text = "Compare two texts by their semantic embedding vectors.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = textA,
                onValueChange = { textA = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Text A") },
                enabled = modelService.isEmbeddingLoaded && !isComputing,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PrimaryMid,
                    unfocusedContainerColor = PrimaryMid,
                ),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = textB,
                onValueChange = { textB = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Text B") },
                enabled = modelService.isEmbeddingLoaded && !isComputing,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PrimaryMid,
                    unfocusedContainerColor = PrimaryMid,
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (textA.isNotBlank() && textB.isNotBlank()) {
                        isComputing = true
                        errorMessage = null
                        comparison = null
                        scope.launch {
                            try {
                                val a = RunAnywhere.embeddings
                                    .embed(textA, ModelService.EMBEDDING_MODEL_ID)
                                    .vectors.firstOrNull()?.values.orEmpty()
                                val b = RunAnywhere.embeddings
                                    .embed(textB, ModelService.EMBEDDING_MODEL_ID)
                                    .vectors.firstOrNull()?.values.orEmpty()
                                if (a.isEmpty() || b.isEmpty()) {
                                    errorMessage = "Embedding returned no vector."
                                } else {
                                    comparison = EmbeddingComparison(
                                        dimension = a.size,
                                        similarity = cosineSimilarity(a, b),
                                        previewA = a.take(6),
                                        previewB = b.take(6),
                                    )
                                }
                            } catch (e: Exception) {
                                errorMessage = "Embedding failed: ${e.message}"
                            } finally {
                                isComputing = false
                            }
                        }
                    }
                },
                enabled = modelService.isEmbeddingLoaded && !isComputing &&
                    textA.isNotBlank() && textB.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isComputing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Embedding...")
                } else {
                    Text("Compute similarity")
                }
            }

            comparison?.let { result ->
                Spacer(modifier = Modifier.height(20.dp))
                SimilarityCard(result)
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
private fun SimilarityCard(result: EmbeddingComparison) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Hub,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Cosine similarity",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = String.format("%.4f", result.similarity),
                style = MaterialTheme.typography.headlineMedium,
                color = AccentGreen,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { result.similarity.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = AccentGreen,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Vector dimension: ${result.dimension}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(8.dp))
            VectorPreview("A", result.previewA)
            Spacer(modifier = Modifier.height(4.dp))
            VectorPreview("B", result.previewB)
        }
    }
}

@Composable
private fun VectorPreview(label: String, values: List<Float>) {
    Text(
        text = "$label: [${values.joinToString(", ") { String.format("%.3f", it) }}, ...]",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        fontFamily = FontFamily.Monospace,
    )
}

private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
    val n = minOf(a.size, b.size)
    if (n == 0) return 0f
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in 0 until n) {
        dot += (a[i] * b[i]).toDouble()
        normA += (a[i] * a[i]).toDouble()
        normB += (b[i] * b[i]).toDouble()
    }
    if (normA == 0.0 || normB == 0.0) return 0f
    return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
}
