package com.runanywhere.kotlin_starter_example.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.core.types.InferenceFramework
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.Models.ModelCategory
import com.runanywhere.sdk.public.extensions.Models.ModelFileDescriptor
import com.runanywhere.sdk.public.extensions.registerModel
import com.runanywhere.sdk.public.extensions.registerMultiFileModel
import com.runanywhere.sdk.public.extensions.downloadModel
import com.runanywhere.sdk.public.extensions.loadLLMModel
import com.runanywhere.sdk.public.extensions.loadSTTModel
import com.runanywhere.sdk.public.extensions.loadTTSVoice
import com.runanywhere.sdk.public.extensions.loadVLMModel
import com.runanywhere.sdk.public.extensions.unloadLLMModel
import com.runanywhere.sdk.public.extensions.unloadSTTModel
import com.runanywhere.sdk.public.extensions.unloadTTSVoice
import com.runanywhere.sdk.public.extensions.unloadVLMModel
import com.runanywhere.sdk.public.extensions.isLLMModelLoaded
import com.runanywhere.sdk.public.extensions.isSTTModelLoaded
import com.runanywhere.sdk.public.extensions.isTTSVoiceLoaded
import com.runanywhere.sdk.public.extensions.isVLMModelLoaded
import com.runanywhere.sdk.public.extensions.isVoiceAgentReady
import com.runanywhere.sdk.public.extensions.availableModels
import kotlinx.coroutines.flow.catch
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
    
    var isVoiceAgentReady by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    companion object {
        // Model IDs - using officially supported models
        const val LLM_MODEL_ID = "smollm2-360m-instruct-q8_0"
        const val STT_MODEL_ID = "sherpa-onnx-whisper-tiny.en"
        const val TTS_MODEL_ID = "vits-piper-en_US-lessac-medium"
        const val VLM_MODEL_ID = "smolvlm-256m-instruct"
        
        /**
         * Register default models with the SDK.
         * Includes LLM, STT, TTS, and VLM (multi-file model with mmproj).
         */
        fun registerDefaultModels() {
            // LLM Model - SmolLM2 360M (small, fast, good for demos)
            RunAnywhere.registerModel(
                id = LLM_MODEL_ID,
                name = "SmolLM2 360M Instruct Q8_0",
                url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
                framework = InferenceFramework.LLAMA_CPP,
                modality = ModelCategory.LANGUAGE,
                memoryRequirement = 400_000_000
            )
            
            // STT Model - Whisper Tiny English (fast transcription)
            RunAnywhere.registerModel(
                id = STT_MODEL_ID,
                name = "Sherpa Whisper Tiny (ONNX)",
                url = "https://github.com/RunanywhereAI/sherpa-onnx/releases/download/runanywhere-models-v1/sherpa-onnx-whisper-tiny.en.tar.gz",
                framework = InferenceFramework.ONNX,
                modality = ModelCategory.SPEECH_RECOGNITION
            )
            
            // TTS Model - Piper TTS (US English - Medium quality)
            RunAnywhere.registerModel(
                id = TTS_MODEL_ID,
                name = "Piper TTS (US English - Medium)",
                url = "https://github.com/RunanywhereAI/sherpa-onnx/releases/download/runanywhere-models-v1/vits-piper-en_US-lessac-medium.tar.gz",
                framework = InferenceFramework.ONNX,
                modality = ModelCategory.SPEECH_SYNTHESIS
            )
            
            // VLM Model - SmolVLM 256M (tiny multimodal model, GGUF + mmproj)
            // Mirrors iOS Swift starter exactly: two-file download (main model + vision projector)
            RunAnywhere.registerMultiFileModel(
                id = VLM_MODEL_ID,
                name = "SmolVLM 256M Instruct (Q8)",
                files = listOf(
                    ModelFileDescriptor(
                        url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/SmolVLM-256M-Instruct-Q8_0.gguf",
                        filename = "SmolVLM-256M-Instruct-Q8_0.gguf"
                    ),
                    ModelFileDescriptor(
                        url = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/mmproj-SmolVLM-256M-Instruct-f16.gguf",
                        filename = "mmproj-SmolVLM-256M-Instruct-f16.gguf"
                    ),
                ),
                framework = InferenceFramework.LLAMA_CPP,
                modality = ModelCategory.MULTIMODAL,
                memoryRequirement = 365_000_000
            )
        }
    }
    
    init {
        viewModelScope.launch {
            refreshModelState()
        }
    }
    
    /**
     * Refresh model loaded states from SDK
     */
    private suspend fun refreshModelState() {
        isLLMLoaded = RunAnywhere.isLLMModelLoaded()
        isSTTLoaded = RunAnywhere.isSTTModelLoaded()
        isTTSLoaded = RunAnywhere.isTTSVoiceLoaded()
        isVLMLoaded = RunAnywhere.isVLMModelLoaded
        isVoiceAgentReady = RunAnywhere.isVoiceAgentReady()
    }
    
    /**
     * Check if a model is downloaded
     */
    private suspend fun isModelDownloaded(modelId: String): Boolean {
        val models = RunAnywhere.availableModels()
        val model = models.find { it.id == modelId }
        return model?.localPath != null
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
                    
                    RunAnywhere.downloadModel(LLM_MODEL_ID)
                        .catch { e ->
                            errorMessage = "LLM download failed: ${e.message}"
                        }
                        .collect { progress ->
                            llmDownloadProgress = progress.progress
                        }
                    
                    isLLMDownloading = false
                }
                
                // Load the model
                isLLMLoading = true
                RunAnywhere.loadLLMModel(LLM_MODEL_ID)
                isLLMLoaded = true
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
                    
                    RunAnywhere.downloadModel(STT_MODEL_ID)
                        .catch { e ->
                            errorMessage = "STT download failed: ${e.message}"
                        }
                        .collect { progress ->
                            sttDownloadProgress = progress.progress
                        }
                    
                    isSTTDownloading = false
                }
                
                // Load the model
                isSTTLoading = true
                RunAnywhere.loadSTTModel(STT_MODEL_ID)
                isSTTLoaded = true
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
                    
                    RunAnywhere.downloadModel(TTS_MODEL_ID)
                        .catch { e ->
                            errorMessage = "TTS download failed: ${e.message}"
                        }
                        .collect { progress ->
                            ttsDownloadProgress = progress.progress
                        }
                    
                    isTTSDownloading = false
                }
                
                // Load the model
                isTTSLoading = true
                RunAnywhere.loadTTSVoice(TTS_MODEL_ID)
                isTTSLoaded = true
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
                    
                    RunAnywhere.downloadModel(VLM_MODEL_ID)
                        .catch { e ->
                            errorMessage = "VLM download failed: ${e.message}"
                        }
                        .collect { progress ->
                            vlmDownloadProgress = progress.progress
                        }
                    
                    isVLMDownloading = false
                }
                
                // Load the VLM model by ID -- C++ resolves the model folder,
                // finds main .gguf and mmproj .gguf automatically
                isVLMLoading = true
                RunAnywhere.loadVLMModel(VLM_MODEL_ID)
                isVLMLoaded = true
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
                RunAnywhere.unloadLLMModel()
                RunAnywhere.unloadSTTModel()
                RunAnywhere.unloadTTSVoice()
                try { RunAnywhere.unloadVLMModel() } catch (_: Exception) {}
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
