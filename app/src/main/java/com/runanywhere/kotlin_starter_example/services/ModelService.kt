package com.runanywhere.kotlin_starter_example.services

import ai.runanywhere.proto.v1.ArchiveStructure
import ai.runanywhere.proto.v1.ArchiveType
import ai.runanywhere.proto.v1.CurrentModelRequest
import ai.runanywhere.proto.v1.InferenceFramework
import ai.runanywhere.proto.v1.ModelCategory
import ai.runanywhere.proto.v1.ModelFileDescriptor
import ai.runanywhere.proto.v1.ModelFileRole
import ai.runanywhere.proto.v1.ModelGetRequest
import ai.runanywhere.proto.v1.ModelUnloadRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.currentModel
import com.runanywhere.sdk.public.extensions.downloadModel
import com.runanywhere.sdk.public.extensions.getModel
import com.runanywhere.sdk.public.extensions.loadModel
import com.runanywhere.sdk.public.extensions.registerModel
import com.runanywhere.sdk.public.extensions.unloadModel
import com.runanywhere.sdk.public.types.RAModelLoadRequest
import kotlinx.coroutines.launch

/**
 * Service for managing AI models - handles registration, downloading, and loading
 * Similar to the Flutter ModelService for consistent behavior across platforms
 */
class ModelService : ViewModel() {

    // LLM state
    var isLLMDownloading by mutableStateOf(false)
        private set
    var llmDownloadProgress by mutableStateOf(0f)
        private set
    var isLLMLoading by mutableStateOf(false)
        private set
    var isLLMLoaded by mutableStateOf(false)
        private set

    // STT state
    var isSTTDownloading by mutableStateOf(false)
        private set
    var sttDownloadProgress by mutableStateOf(0f)
        private set
    var isSTTLoading by mutableStateOf(false)
        private set
    var isSTTLoaded by mutableStateOf(false)
        private set

    // TTS state
    var isTTSDownloading by mutableStateOf(false)
        private set
    var ttsDownloadProgress by mutableStateOf(0f)
        private set
    var isTTSLoading by mutableStateOf(false)
        private set
    var isTTSLoaded by mutableStateOf(false)
        private set

    // VLM state
    var isVLMDownloading by mutableStateOf(false)
        private set
    var vlmDownloadProgress by mutableStateOf(0f)
        private set
    var isVLMLoading by mutableStateOf(false)
        private set
    var isVLMLoaded by mutableStateOf(false)
        private set

    // VAD state
    var isVADDownloading by mutableStateOf(false)
        private set
    var vadDownloadProgress by mutableStateOf(0f)
        private set
    var isVADLoading by mutableStateOf(false)
        private set
    var isVADLoaded by mutableStateOf(false)
        private set

    // Embedding state (also used as the retriever for RAG)
    var isEmbeddingDownloading by mutableStateOf(false)
        private set
    var embeddingDownloadProgress by mutableStateOf(0f)
        private set
    var isEmbeddingLoading by mutableStateOf(false)
        private set
    var isEmbeddingLoaded by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    companion object {
        // Model IDs - using officially supported models
        const val LLM_MODEL_ID = "smollm2-360m-instruct-q8_0"
        const val STT_MODEL_ID = "sherpa-onnx-whisper-tiny.en"
        const val TTS_MODEL_ID = "vits-piper-en_US-lessac-medium"
        const val VLM_MODEL_ID = "smolvlm-256m-instruct"
        const val VAD_MODEL_ID = "silero-vad"
        const val EMBEDDING_MODEL_ID = "all-minilm-l6-v2"

        /**
         * Register default models with the SDK.
         * Includes LLM, STT, TTS (Sherpa-ONNX archives), and VLM (multi-file model with mmproj).
         */
        suspend fun registerDefaultModels() {
            // LLM Model - SmolLM2 360M (small, fast, good for demos)
            RunAnywhere.registerModel(
                id = LLM_MODEL_ID,
                name = "SmolLM2 360M Instruct Q8_0",
                url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
                framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
                modality = ModelCategory.MODEL_CATEGORY_LANGUAGE,
                memoryRequirement = 400_000_000
            )

            // STT Model - Whisper Tiny English (Sherpa-ONNX archive, fast transcription)
            RunAnywhere.registerModel(
                archiveUrl = "https://github.com/RunanywhereAI/sherpa-onnx/releases/download/runanywhere-models-v1/sherpa-onnx-whisper-tiny.en.tar.gz",
                structure = ArchiveStructure.ARCHIVE_STRUCTURE_NESTED_DIRECTORY,
                id = STT_MODEL_ID,
                name = "Sherpa Whisper Tiny (ONNX)",
                framework = InferenceFramework.INFERENCE_FRAMEWORK_SHERPA,
                modality = ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION,
                archiveType = ArchiveType.ARCHIVE_TYPE_TAR_GZ,
                memoryRequirement = 75_000_000
            )

            // TTS Model - Piper TTS (US English - Medium quality, Sherpa-ONNX archive)
            RunAnywhere.registerModel(
                archiveUrl = "https://github.com/RunanywhereAI/sherpa-onnx/releases/download/runanywhere-models-v1/vits-piper-en_US-lessac-medium.tar.gz",
                structure = ArchiveStructure.ARCHIVE_STRUCTURE_NESTED_DIRECTORY,
                id = TTS_MODEL_ID,
                name = "Piper TTS (US English - Medium)",
                framework = InferenceFramework.INFERENCE_FRAMEWORK_SHERPA,
                modality = ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS,
                archiveType = ArchiveType.ARCHIVE_TYPE_TAR_GZ,
                memoryRequirement = 65_000_000
            )

            // VLM Model - SmolVLM 256M (tiny multimodal model, GGUF + mmproj)
            // Mirrors iOS Swift starter exactly: two-file download (main model + vision projector)
            RunAnywhere.registerModel(
                multiFile = listOf(
                    ModelFileDescriptor(
                        url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf",
                        filename = "SmolVLM-256M-Instruct-Q8_0.gguf",
                        is_required = true,
                        role = ModelFileRole.MODEL_FILE_ROLE_PRIMARY_MODEL,
                    ),
                    ModelFileDescriptor(
                        url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-256M-Instruct-f16.gguf",
                        filename = "mmproj-SmolVLM-256M-Instruct-f16.gguf",
                        is_required = true,
                        role = ModelFileRole.MODEL_FILE_ROLE_COMPANION,
                    ),
                ),
                id = VLM_MODEL_ID,
                name = "SmolVLM 256M Instruct (Q8)",
                framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
                modality = ModelCategory.MODEL_CATEGORY_MULTIMODAL,
                memoryRequirement = 365_000_000
            )

            // VAD Model - Silero VAD (ONNX single-file, streaming voice-activity detection)
            RunAnywhere.registerModel(
                id = VAD_MODEL_ID,
                name = "Silero VAD",
                url = "https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx",
                framework = InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
                modality = ModelCategory.MODEL_CATEGORY_VOICE_ACTIVITY_DETECTION,
                memoryRequirement = 2_327_524
            )

            // Embedding Model - All MiniLM L6 v2 (ONNX multi-file: model + vocab).
            // Powers the standalone Embeddings demo and acts as the retriever in RAG.
            RunAnywhere.registerModel(
                multiFile = listOf(
                    ModelFileDescriptor(
                        url = "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
                        filename = "model.onnx",
                        is_required = true,
                        role = ModelFileRole.MODEL_FILE_ROLE_PRIMARY_MODEL,
                    ),
                    ModelFileDescriptor(
                        url = "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/vocab.txt",
                        filename = "vocab.txt",
                        is_required = true,
                        role = ModelFileRole.MODEL_FILE_ROLE_COMPANION,
                    ),
                ),
                id = EMBEDDING_MODEL_ID,
                name = "All MiniLM L6 v2 (Embedding)",
                framework = InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
                modality = ModelCategory.MODEL_CATEGORY_EMBEDDING,
                memoryRequirement = 25_500_000
            )
        }
    }

    init {
        viewModelScope.launch {
            refreshModelState()
        }
    }

    /**
     * Refresh model loaded states from SDK's canonical lifecycle.
     */
    private suspend fun refreshModelState() {
        isLLMLoaded = RunAnywhere.currentModel(
            CurrentModelRequest(category = ModelCategory.MODEL_CATEGORY_LANGUAGE)
        ).found
        isSTTLoaded = RunAnywhere.currentModel(
            CurrentModelRequest(category = ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION)
        ).found
        isTTSLoaded = RunAnywhere.currentModel(
            CurrentModelRequest(category = ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS)
        ).found
        isVLMLoaded = RunAnywhere.currentModel(
            CurrentModelRequest(category = ModelCategory.MODEL_CATEGORY_MULTIMODAL)
        ).found
        isVADLoaded = RunAnywhere.currentModel(
            CurrentModelRequest(category = ModelCategory.MODEL_CATEGORY_VOICE_ACTIVITY_DETECTION)
        ).found
        isEmbeddingLoaded = RunAnywhere.currentModel(
            CurrentModelRequest(category = ModelCategory.MODEL_CATEGORY_EMBEDDING)
        ).found
    }

    /**
     * Check if a model is downloaded
     */
    private suspend fun isModelDownloaded(modelId: String): Boolean {
        val result = RunAnywhere.getModel(ModelGetRequest(model_id = modelId))
        return result.found && !result.model?.local_path.isNullOrBlank()
    }

    /**
     * Download and load LLM model
     */
    fun downloadAndLoadLLM() {
        if (isLLMDownloading || isLLMLoading) return

        viewModelScope.launch {
            try {
                errorMessage = null

                // Check if already downloaded
                if (!isModelDownloaded(LLM_MODEL_ID)) {
                    isLLMDownloading = true
                    llmDownloadProgress = 0f

                    val model = RunAnywhere.getModel(ModelGetRequest(model_id = LLM_MODEL_ID)).model
                        ?: error("LLM model is not registered")
                    RunAnywhere.downloadModel(model) { progress ->
                        llmDownloadProgress = progress.overall_progress
                    }

                    isLLMDownloading = false
                }

                // Load the model
                isLLMLoading = true
                val result = RunAnywhere.loadModel(
                    RAModelLoadRequest(
                        model_id = LLM_MODEL_ID,
                        category = ModelCategory.MODEL_CATEGORY_LANGUAGE,
                        framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
                    )
                )
                if (!result.success) {
                    error(result.error_message.ifBlank { "LLM load failed" })
                }
                isLLMLoading = false

                refreshModelState()
            } catch (e: Exception) {
                errorMessage = "LLM load failed: ${e.message}"
                isLLMDownloading = false
                isLLMLoading = false
            }
        }
    }

    /**
     * Download and load STT model
     */
    fun downloadAndLoadSTT() {
        if (isSTTDownloading || isSTTLoading) return

        viewModelScope.launch {
            try {
                errorMessage = null

                // Check if already downloaded
                if (!isModelDownloaded(STT_MODEL_ID)) {
                    isSTTDownloading = true
                    sttDownloadProgress = 0f

                    val model = RunAnywhere.getModel(ModelGetRequest(model_id = STT_MODEL_ID)).model
                        ?: error("STT model is not registered")
                    RunAnywhere.downloadModel(model) { progress ->
                        sttDownloadProgress = progress.overall_progress
                    }

                    isSTTDownloading = false
                }

                // Load the model
                isSTTLoading = true
                val result = RunAnywhere.loadModel(
                    RAModelLoadRequest(
                        model_id = STT_MODEL_ID,
                        category = ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION,
                        framework = InferenceFramework.INFERENCE_FRAMEWORK_SHERPA,
                    )
                )
                if (!result.success) {
                    error(result.error_message.ifBlank { "STT load failed" })
                }
                isSTTLoading = false

                refreshModelState()
            } catch (e: Exception) {
                errorMessage = "STT load failed: ${e.message}"
                isSTTDownloading = false
                isSTTLoading = false
            }
        }
    }

    /**
     * Download and load TTS model
     */
    fun downloadAndLoadTTS() {
        if (isTTSDownloading || isTTSLoading) return

        viewModelScope.launch {
            try {
                errorMessage = null

                // Check if already downloaded
                if (!isModelDownloaded(TTS_MODEL_ID)) {
                    isTTSDownloading = true
                    ttsDownloadProgress = 0f

                    val model = RunAnywhere.getModel(ModelGetRequest(model_id = TTS_MODEL_ID)).model
                        ?: error("TTS model is not registered")
                    RunAnywhere.downloadModel(model) { progress ->
                        ttsDownloadProgress = progress.overall_progress
                    }

                    isTTSDownloading = false
                }

                // Load the model
                isTTSLoading = true
                val result = RunAnywhere.loadModel(
                    RAModelLoadRequest(
                        model_id = TTS_MODEL_ID,
                        category = ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS,
                        framework = InferenceFramework.INFERENCE_FRAMEWORK_SHERPA,
                    )
                )
                if (!result.success) {
                    error(result.error_message.ifBlank { "TTS load failed" })
                }
                isTTSLoading = false

                refreshModelState()
            } catch (e: Exception) {
                errorMessage = "TTS load failed: ${e.message}"
                isTTSDownloading = false
                isTTSLoading = false
            }
        }
    }

    /**
     * Download and load VLM model (SmolVLM 256M - multimodal with mmproj)
     */
    fun downloadAndLoadVLM() {
        if (isVLMDownloading || isVLMLoading) return

        viewModelScope.launch {
            try {
                errorMessage = null

                if (!isModelDownloaded(VLM_MODEL_ID)) {
                    isVLMDownloading = true
                    vlmDownloadProgress = 0f

                    val model = RunAnywhere.getModel(ModelGetRequest(model_id = VLM_MODEL_ID)).model
                        ?: error("VLM model is not registered")
                    RunAnywhere.downloadModel(model) { progress ->
                        vlmDownloadProgress = progress.overall_progress
                    }

                    isVLMDownloading = false
                }

                // Load the VLM model by ID -- C++ resolves the model folder,
                // finds main .gguf and mmproj .gguf automatically
                isVLMLoading = true
                val result = RunAnywhere.loadModel(
                    RAModelLoadRequest(
                        model_id = VLM_MODEL_ID,
                        category = ModelCategory.MODEL_CATEGORY_MULTIMODAL,
                        framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
                    )
                )
                if (!result.success) {
                    error(result.error_message.ifBlank { "VLM load failed" })
                }
                isVLMLoading = false

                refreshModelState()
            } catch (e: Exception) {
                errorMessage = "VLM load failed: ${e.message}"
                isVLMDownloading = false
                isVLMLoading = false
            }
        }
    }

    /**
     * Download and load VAD model (Silero VAD - streaming voice-activity detection)
     */
    fun downloadAndLoadVAD() {
        if (isVADDownloading || isVADLoading) return

        viewModelScope.launch {
            try {
                errorMessage = null

                if (!isModelDownloaded(VAD_MODEL_ID)) {
                    isVADDownloading = true
                    vadDownloadProgress = 0f

                    val model = RunAnywhere.getModel(ModelGetRequest(model_id = VAD_MODEL_ID)).model
                        ?: error("VAD model is not registered")
                    RunAnywhere.downloadModel(model) { progress ->
                        vadDownloadProgress = progress.overall_progress
                    }

                    isVADDownloading = false
                }

                isVADLoading = true
                val result = RunAnywhere.loadModel(
                    RAModelLoadRequest(
                        model_id = VAD_MODEL_ID,
                        category = ModelCategory.MODEL_CATEGORY_VOICE_ACTIVITY_DETECTION,
                        framework = InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
                    )
                )
                if (!result.success) {
                    error(result.error_message.ifBlank { "VAD load failed" })
                }
                isVADLoading = false

                refreshModelState()
            } catch (e: Exception) {
                errorMessage = "VAD load failed: ${e.message}"
                isVADDownloading = false
                isVADLoading = false
            }
        }
    }

    /**
     * Download and load the embedding model (All MiniLM L6 v2).
     * The embed API also lazy-loads on first call, but loading here surfaces a
     * "Ready" state in the UI before the first embedding request.
     */
    fun downloadAndLoadEmbedding() {
        if (isEmbeddingDownloading || isEmbeddingLoading) return

        viewModelScope.launch {
            try {
                errorMessage = null

                if (!isModelDownloaded(EMBEDDING_MODEL_ID)) {
                    isEmbeddingDownloading = true
                    embeddingDownloadProgress = 0f

                    val model = RunAnywhere.getModel(ModelGetRequest(model_id = EMBEDDING_MODEL_ID)).model
                        ?: error("Embedding model is not registered")
                    RunAnywhere.downloadModel(model) { progress ->
                        embeddingDownloadProgress = progress.overall_progress
                    }

                    isEmbeddingDownloading = false
                }

                isEmbeddingLoading = true
                val result = RunAnywhere.loadModel(
                    RAModelLoadRequest(
                        model_id = EMBEDDING_MODEL_ID,
                        category = ModelCategory.MODEL_CATEGORY_EMBEDDING,
                        framework = InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
                    )
                )
                if (!result.success) {
                    error(result.error_message.ifBlank { "Embedding load failed" })
                }
                isEmbeddingLoading = false

                refreshModelState()
            } catch (e: Exception) {
                errorMessage = "Embedding load failed: ${e.message}"
                isEmbeddingDownloading = false
                isEmbeddingLoading = false
            }
        }
    }

    /**
     * Ensure a registered model is downloaded to local storage without loading
     * it into any component. Used by the RAG pipeline, which resolves its
     * embedding + LLM artifacts from the registry by id.
     *
     * @return true when the model is available locally after the call.
     */
    suspend fun downloadModelIfNeeded(
        modelId: String,
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        if (isModelDownloaded(modelId)) return true
        val model = RunAnywhere.getModel(ModelGetRequest(model_id = modelId)).model
            ?: return false
        RunAnywhere.downloadModel(model) { progress -> onProgress(progress.overall_progress) }
        return isModelDownloaded(modelId)
    }

    /**
     * Download and load all models for voice agent
     */
    fun downloadAndLoadAllModels() {
        viewModelScope.launch {
            if (!isLLMLoaded) downloadAndLoadLLM()
            if (!isSTTLoaded) downloadAndLoadSTT()
            if (!isTTSLoaded) downloadAndLoadTTS()
        }
    }

    /**
     * Unload all models
     */
    fun unloadAllModels() {
        viewModelScope.launch {
            try {
                RunAnywhere.unloadModel(
                    ModelUnloadRequest(category = ModelCategory.MODEL_CATEGORY_LANGUAGE, unload_all = true)
                )
                RunAnywhere.unloadModel(
                    ModelUnloadRequest(category = ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION, unload_all = true)
                )
                RunAnywhere.unloadModel(
                    ModelUnloadRequest(category = ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS, unload_all = true)
                )
                try {
                    RunAnywhere.unloadModel(
                        ModelUnloadRequest(category = ModelCategory.MODEL_CATEGORY_MULTIMODAL, unload_all = true)
                    )
                } catch (_: Exception) {}
                refreshModelState()
            } catch (e: Exception) {
                errorMessage = "Failed to unload models: ${e.message}"
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        errorMessage = null
    }
}
