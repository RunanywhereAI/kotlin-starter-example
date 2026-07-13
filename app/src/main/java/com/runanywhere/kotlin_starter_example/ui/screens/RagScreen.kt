package com.runanywhere.kotlin_starter_example.ui.screens

import ai.runanywhere.proto.v1.RAGConfiguration
import ai.runanywhere.proto.v1.RAGDocument
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.theme.*
import com.runanywhere.sdk.generated.convenience.defaults
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.ragCreatePipeline
import com.runanywhere.sdk.public.extensions.ragDestroyPipeline
import com.runanywhere.sdk.public.extensions.ragGetStatistics
import com.runanywhere.sdk.public.extensions.ragIngest
import com.runanywhere.sdk.public.extensions.ragQuery
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

data class RagSource(val text: String, val score: Float, val document: String)

data class RagMessage(
    val text: String,
    val isUser: Boolean,
    val sources: List<RagSource> = emptyList(),
)

/**
 * Retrieval-Augmented Generation demo. Wires the ONNX embedding retriever to
 * the on-device LLM through the SDK's RAG pipeline: ingest text, then ask
 * questions grounded in it. Mirrors the monorepo Android RagViewModel, reduced
 * to pasted-text documents for the starter.
 */
class RagViewModel : ViewModel() {

    var isPreparing by mutableStateOf(false)
        private set
    var isReady by mutableStateOf(false)
        private set
    var prepareProgress by mutableStateOf(0f)
        private set
    var prepareStatus by mutableStateOf("")
        private set

    val documents = mutableStateListOf<String>()
    var chunkCount by mutableStateOf(0)
        private set
    var isIngesting by mutableStateOf(false)
        private set

    val messages = mutableStateListOf<RagMessage>()
    var isQuerying by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun prepare(modelService: ModelService) {
        if (isPreparing || isReady) return
        isPreparing = true
        error = null
        prepareProgress = 0f
        viewModelScope.launch {
            try {
                prepareStatus = "Downloading embedding model..."
                val embeddingOk = modelService.downloadModelIfNeeded(ModelService.EMBEDDING_MODEL_ID) {
                    prepareProgress = it * 0.5f
                }
                if (!embeddingOk) error("Embedding model unavailable")

                prepareStatus = "Downloading language model..."
                val llmOk = modelService.downloadModelIfNeeded(ModelService.LLM_MODEL_ID) {
                    prepareProgress = 0.5f + it * 0.5f
                }
                if (!llmOk) error("Language model unavailable")

                prepareStatus = "Building RAG pipeline..."
                RunAnywhere.ragCreatePipeline(
                    RAGConfiguration.defaults().copy(
                        embedding_model_id = ModelService.EMBEDDING_MODEL_ID,
                        llm_model_id = ModelService.LLM_MODEL_ID,
                    ),
                )
                isReady = true
                prepareStatus = ""
            } catch (e: Exception) {
                error = "Could not prepare RAG: ${e.message}"
            } finally {
                isPreparing = false
            }
        }
    }

    fun addDocument(name: String, text: String) {
        val body = text.trim()
        if (!isReady || isIngesting || body.isEmpty()) return
        isIngesting = true
        error = null
        viewModelScope.launch {
            try {
                RunAnywhere.ragIngest(
                    RAGDocument(text = body, metadata = mapOf("name" to name)),
                )
                documents += name
                chunkCount = runCatching { RunAnywhere.ragGetStatistics().indexed_chunks.toInt() }
                    .getOrDefault(chunkCount)
            } catch (e: Exception) {
                error = "Could not add the document: ${e.message}"
            } finally {
                isIngesting = false
            }
        }
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || !isReady || isQuerying || documents.isEmpty()) return
        messages += RagMessage(q, isUser = true)
        isQuerying = true
        error = null
        viewModelScope.launch {
            try {
                val result = RunAnywhere.ragQuery(q)
                val sources = result.retrieved_chunks.map {
                    RagSource(
                        text = it.text.trim(),
                        score = it.similarity_score,
                        document = it.source_document.orEmpty(),
                    )
                }
                val answer = result.answer.trim().ifBlank {
                    "I couldn't find an answer in the added documents."
                }
                messages += RagMessage(answer, isUser = false, sources = sources)
            } catch (e: Exception) {
                error = "The query failed: ${e.message}"
            } finally {
                isQuerying = false
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        // viewModelScope is already cancelled here, so tear the process-wide
        // native pipeline down on a scope that outlives this ViewModel.
        if (isReady) GlobalScope.launch { runCatching { RunAnywhere.ragDestroyPipeline() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RagScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier,
) {
    val ragViewModel: RagViewModel = viewModel()
    var documentText by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents - RAG") },
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
            if (!ragViewModel.isReady) {
                PreparePanel(vm = ragViewModel, modelService = modelService)
            } else {
                // Ingest section
                Text(
                    text = "Knowledge base",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = documentText,
                    onValueChange = { documentText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("Paste text to add to the knowledge base...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PrimaryMid,
                        unfocusedContainerColor = PrimaryMid,
                    ),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val idx = ragViewModel.documents.size + 1
                        ragViewModel.addDocument("Document $idx", documentText)
                        documentText = ""
                    },
                    enabled = documentText.isNotBlank() && !ragViewModel.isIngesting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (ragViewModel.isIngesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Indexing...")
                    } else {
                        Text("Add to knowledge base")
                    }
                }

                if (ragViewModel.documents.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DocumentsSummary(
                        count = ragViewModel.documents.size,
                        chunks = ragViewModel.chunkCount,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = SurfaceCard)
                Spacer(modifier = Modifier.height(20.dp))

                // Messages
                if (ragViewModel.messages.isEmpty()) {
                    EmptyRagState(hasDocs = ragViewModel.documents.isNotEmpty())
                } else {
                    ragViewModel.messages.forEach { message ->
                        RagMessageBubble(message)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ask
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about your documents...") },
                        enabled = ragViewModel.documents.isNotEmpty() && !ragViewModel.isQuerying,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PrimaryMid,
                            unfocusedContainerColor = PrimaryMid,
                        ),
                        maxLines = 3,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (question.isNotBlank()) {
                                ragViewModel.ask(question)
                                question = ""
                            }
                        },
                        containerColor = if (ragViewModel.isQuerying) AccentViolet else AccentCyan,
                    ) {
                        if (ragViewModel.isQuerying) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Send, "Ask")
                        }
                    }
                }
            }

            ragViewModel.error?.let { err ->
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
private fun PreparePanel(vm: RagViewModel, modelService: ModelService) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryBooks,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Retrieval-Augmented Generation",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Grounds the on-device LLM in text you provide, using the " +
                    "MiniLM embedding model to retrieve the most relevant passages.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (vm.isPreparing) {
                Text(
                    text = vm.prepareStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { vm.prepareProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            } else {
                Button(
                    onClick = { vm.prepare(modelService) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Prepare RAG (Embedding + LLM)")
                }
            }
        }
    }
}

@Composable
private fun DocumentsSummary(count: Int, chunks: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryMid),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Article,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$count document${if (count == 1) "" else "s"} · $chunks chunk${if (chunks == 1) "" else "s"} indexed",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
        }
    }
}

@Composable
private fun EmptyRagState(hasDocs: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.LibraryBooks,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (hasDocs) "Ask a question about your documents" else "Add a document to get started",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
        )
    }
}

@Composable
private fun RagMessageBubble(message: RagMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isUser) AccentCyan else SurfaceCard,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else TextPrimary,
                )
                if (message.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sources",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentCyan,
                        fontWeight = FontWeight.SemiBold,
                    )
                    message.sources.take(3).forEach { source ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• (${String.format("%.2f", source.score)}) ${source.text.take(120)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }
            }
        }
    }
}
