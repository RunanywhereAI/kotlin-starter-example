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
import android.os.Process
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runanywhere.sdk.npu.qhexrt.QHexRT
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.currentModel
import com.runanywhere.sdk.public.extensions.downloadModel
import com.runanywhere.sdk.public.extensions.getModel
import com.runanywhere.sdk.public.extensions.loadModel
import com.runanywhere.sdk.public.extensions.registerModel
import com.runanywhere.sdk.public.extensions.unloadModel
import com.runanywhere.sdk.public.types.RAModelLoadRequest
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ModelService"

/**
 * Manages model registration, download, and load for the starter demos.
 *
 * On Snapdragon devices with a supported Hexagon NPU, registers the curated
 * QHexRT catalog ([NpuCatalog]) and prefers HNPU defaults. CPU models stay
 * available as fallbacks and can be selected from each screen's picker.
 *
 * Loads are exclusive by default: every download/load unloads all other
 * resident models first. Stacking multiple HNPU graphs on ~8GB phones has
 * caused kernel-panic reboots; the voice pipeline keeps at most one HNPU
 * model and forces STT/TTS onto CPU when needed.
 */
class ModelService : ViewModel() {

    var isSdkReady by mutableStateOf(false)
        private set
    var isCatalogRegistering by mutableStateOf(false)
        private set
    var registeredNpuCount by mutableStateOf(0)
        private set

    var npuSupported by mutableStateOf(false)
        private set
    var npuArchName by mutableStateOf<String?>(null)
        private set
    var npuSocModel by mutableStateOf<String?>(null)
        private set

    var llmOptions by mutableStateOf<List<ModelOption>>(emptyList())
        private set
    var sttOptions by mutableStateOf<List<ModelOption>>(emptyList())
        private set
    var ttsOptions by mutableStateOf<List<ModelOption>>(emptyList())
        private set
    var embeddingOptions by mutableStateOf<List<ModelOption>>(emptyList())
        private set
    var vlmOptions by mutableStateOf<List<ModelOption>>(emptyList())
        private set

    /** Flat list of every successfully registered HNPU option (for the QHexRT Lab). */
    var npuOptions by mutableStateOf<List<ModelOption>>(emptyList())
        private set

    var llmModelId by mutableStateOf(CPU_LLM_MODEL_ID)
        private set
    var llmModelName by mutableStateOf("SmolLM2 360M Instruct Q8_0")
        private set
    var llmFramework by mutableStateOf(InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP)
        private set
    var llmUsesNpu by mutableStateOf(false)
        private set

    var sttModelId by mutableStateOf(CPU_STT_MODEL_ID)
        private set
    var sttModelName by mutableStateOf("Sherpa Whisper Tiny (ONNX)")
        private set
    var sttFramework by mutableStateOf(InferenceFramework.INFERENCE_FRAMEWORK_SHERPA)
        private set
    var sttUsesNpu by mutableStateOf(false)
        private set

    var ttsModelId by mutableStateOf(CPU_TTS_MODEL_ID)
        private set
    var ttsModelName by mutableStateOf("Piper TTS (US English - Medium)")
        private set
    var ttsFramework by mutableStateOf(InferenceFramework.INFERENCE_FRAMEWORK_SHERPA)
        private set
    var ttsUsesNpu by mutableStateOf(false)
        private set

    var embeddingModelId by mutableStateOf(CPU_EMBEDDING_MODEL_ID)
        private set
    var embeddingModelName by mutableStateOf("All MiniLM L6 v2 (Embedding)")
        private set
    var embeddingFramework by mutableStateOf(InferenceFramework.INFERENCE_FRAMEWORK_ONNX)
        private set
    var embeddingUsesNpu by mutableStateOf(false)
        private set

    var vlmModelId by mutableStateOf(CPU_VLM_MODEL_ID)
        private set
    var vlmModelName by mutableStateOf("SmolVLM 256M Instruct (Q8)")
        private set
    var vlmFramework by mutableStateOf(InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP)
        private set
    var vlmUsesNpu by mutableStateOf(false)
        private set

    var vadModelName by mutableStateOf("Silero VAD")
        private set

    var isLLMDownloading by mutableStateOf(false)
        private set
    var llmDownloadProgress by mutableStateOf(0f)
        private set
    var isLLMLoading by mutableStateOf(false)
        private set
    var isLLMLoaded by mutableStateOf(false)
        private set

    var isSTTDownloading by mutableStateOf(false)
        private set
    var sttDownloadProgress by mutableStateOf(0f)
        private set
    var isSTTLoading by mutableStateOf(false)
        private set
    var isSTTLoaded by mutableStateOf(false)
        private set

    var isTTSDownloading by mutableStateOf(false)
        private set
    var ttsDownloadProgress by mutableStateOf(0f)
        private set
    var isTTSLoading by mutableStateOf(false)
        private set
    var isTTSLoaded by mutableStateOf(false)
        private set

    var isVLMDownloading by mutableStateOf(false)
        private set
    var vlmDownloadProgress by mutableStateOf(0f)
        private set
    var isVLMLoading by mutableStateOf(false)
        private set
    var isVLMLoaded by mutableStateOf(false)
        private set

    var isVADDownloading by mutableStateOf(false)
        private set
    var vadDownloadProgress by mutableStateOf(0f)
        private set
    var isVADLoading by mutableStateOf(false)
        private set
    var isVADLoaded by mutableStateOf(false)
        private set

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

    /** Non-fatal lifecycle notes (e.g. "unloaded other models to free RAM"). */
    var statusMessage by mutableStateOf<String?>(null)
        private set

    private val selectMutex = Mutex()

    companion object {
        const val CPU_LLM_MODEL_ID = "smollm2-360m-instruct-q8_0"
        const val CPU_STT_MODEL_ID = "sherpa-onnx-whisper-tiny.en"
        const val CPU_TTS_MODEL_ID = "vits-piper-en_US-lessac-medium"
        const val CPU_VLM_MODEL_ID = "smolvlm-256m-instruct"
        const val VAD_MODEL_ID = "silero-vad"
        const val CPU_EMBEDDING_MODEL_ID = "all-minilm-l6-v2"

        /** Keep ~1.5 GiB free for Android / Hexagon / UI to avoid KP under pressure. */
        private const val RAM_HEADROOM_BYTES = 1_500L * 1024L * 1024L

        /** @deprecated Prefer instance [llmModelId]. */
        const val LLM_MODEL_ID = CPU_LLM_MODEL_ID
        /** @deprecated Prefer instance [sttModelId]. */
        const val STT_MODEL_ID = CPU_STT_MODEL_ID
        /** @deprecated Prefer instance [ttsModelId]. */
        const val TTS_MODEL_ID = CPU_TTS_MODEL_ID
        /** @deprecated Prefer instance [embeddingModelId]. */
        const val EMBEDDING_MODEL_ID = CPU_EMBEDDING_MODEL_ID
        /** @deprecated Prefer instance [vlmModelId]. */
        const val VLM_MODEL_ID = CPU_VLM_MODEL_ID
    }

    /**
     * Register CPU models always, then device-aware HNPU rows when QHexRT is live.
     * Call after [RunAnywhere.initialize] + [QHexRT.register].
     */
    suspend fun bootstrapModels() {
        try {
            registerCpuModels()
            seedCpuOptions()

            val npu = QHexRT.probeNpu()
            npuSupported = npu.qhexrt_supported
            npuArchName = npu.arch_name.takeIf { it.isNotBlank() }
            npuSocModel = npu.soc_model.takeIf { it.isNotBlank() }

            if (npu.qhexrt_supported) {
                isCatalogRegistering = true
                registerNpuCatalog()
            }

            refreshModelState()
        } catch (e: Exception) {
            Log.e(TAG, "bootstrapModels failed; CPU catalog may still be usable", e)
            errorMessage = "SDK bootstrap warning: ${e.message}"
        } finally {
            isCatalogRegistering = false
            isSdkReady = true
        }
    }

    private fun seedCpuOptions() {
        llmOptions = listOf(cpuLlmOption())
        sttOptions = listOf(cpuSttOption())
        ttsOptions = listOf(cpuTtsOption())
        embeddingOptions = listOf(cpuEmbeddingOption())
        vlmOptions = listOf(cpuVlmOption())
        applyOption(cpuLlmOption(), Modality.LLM)
        applyOption(cpuSttOption(), Modality.STT)
        applyOption(cpuTtsOption(), Modality.TTS)
        applyOption(cpuEmbeddingOption(), Modality.EMBEDDING)
        applyOption(cpuVlmOption(), Modality.VLM)
    }

    private suspend fun registerNpuCatalog() = coroutineScope {
        val registered = mutableListOf<ModelOption>()
        val lock = Mutex()

        // Bound parallelism so we don't stampede Hugging Face during bootstrap.
        NpuCatalog.entries.chunked(4).forEach { chunk ->
            chunk.map { entry ->
                async {
                    try {
                        val info = QHexRT.registerModelForDevice(entry.toRegistrationRequest())
                            ?: return@async
                        val option = entry.toOption().copy(
                            name = info.name.ifBlank { entry.name },
                        )
                        lock.withLock { registered += option }
                        Log.i(TAG, "Registered HNPU ${entry.id}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping NPU model ${entry.id}: ${e.message}")
                    }
                }
            }.awaitAll()
        }

        // Preserve catalog order for a stable picker.
        val byId = registered.associateBy { it.id }
        val ordered = NpuCatalog.entries.mapNotNull { byId[it.id] }
        npuOptions = ordered
        registeredNpuCount = ordered.size

        // Always keep original CPU/Sherpa/ONNX models alongside every HNPU row.
        fun merge(category: ModelCategory, cpu: ModelOption): List<ModelOption> {
            val npu = ordered.filter { it.category == category }
            return npu + listOf(cpu)
        }

        llmOptions = merge(ModelCategory.MODEL_CATEGORY_LANGUAGE, cpuLlmOption())
        sttOptions = merge(ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION, cpuSttOption())
        ttsOptions = merge(ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS, cpuTtsOption())
        embeddingOptions = merge(ModelCategory.MODEL_CATEGORY_EMBEDDING, cpuEmbeddingOption())
        vlmOptions = merge(ModelCategory.MODEL_CATEGORY_MULTIMODAL, cpuVlmOption())

        require(llmOptions.any { !it.usesNpu }) { "CPU LLM fallback missing" }
        require(sttOptions.any { !it.usesNpu }) { "CPU STT fallback missing" }
        require(ttsOptions.any { !it.usesNpu }) { "CPU TTS fallback missing" }
        require(embeddingOptions.any { !it.usesNpu }) { "CPU embedding fallback missing" }
        require(vlmOptions.any { !it.usesNpu }) { "CPU VLM fallback missing" }

        // Prefer catalog-marked defaults when present; else first HNPU row; else CPU.
        pickDefault(llmOptions, Modality.LLM)
        pickDefault(sttOptions, Modality.STT)
        pickDefault(ttsOptions, Modality.TTS)
        pickDefault(embeddingOptions, Modality.EMBEDDING)
        pickDefault(vlmOptions, Modality.VLM)

        Log.i(
            TAG,
            "QHexRT catalog ready: ${ordered.size}/${NpuCatalog.entries.size} models · " +
                "llm=${llmModelId} stt=${sttModelId} tts=${ttsModelId} " +
                "embed=${embeddingModelId} vlm=${vlmModelId}",
        )
    }

    private fun pickDefault(options: List<ModelOption>, modality: Modality) {
        val preferredIds = NpuCatalog.entries.filter { it.preferredDefault }.map { it.id }.toSet()
        val preferred = options.firstOrNull { it.usesNpu && it.id in preferredIds }
        val firstNpu = options.firstOrNull { it.usesNpu }
        applyOption(preferred ?: firstNpu ?: options.first(), modality)
    }

    private enum class Modality { LLM, STT, TTS, EMBEDDING, VLM }

    private fun applyOption(option: ModelOption, modality: Modality) {
        when (modality) {
            Modality.LLM -> {
                llmModelId = option.id
                llmModelName = option.name
                llmFramework = option.framework
                llmUsesNpu = option.usesNpu
            }
            Modality.STT -> {
                sttModelId = option.id
                sttModelName = option.name
                sttFramework = option.framework
                sttUsesNpu = option.usesNpu
            }
            Modality.TTS -> {
                ttsModelId = option.id
                ttsModelName = option.name
                ttsFramework = option.framework
                ttsUsesNpu = option.usesNpu
            }
            Modality.EMBEDDING -> {
                embeddingModelId = option.id
                embeddingModelName = option.name
                embeddingFramework = option.framework
                embeddingUsesNpu = option.usesNpu
            }
            Modality.VLM -> {
                vlmModelId = option.id
                vlmModelName = option.name
                vlmFramework = option.framework
                vlmUsesNpu = option.usesNpu
            }
        }
    }

    fun selectLlm(option: ModelOption) = select(option, Modality.LLM)

    fun selectStt(option: ModelOption) = select(option, Modality.STT)

    fun selectTts(option: ModelOption) = select(option, Modality.TTS)

    fun selectEmbedding(option: ModelOption) = select(option, Modality.EMBEDDING)

    fun selectVlm(option: ModelOption) = select(option, Modality.VLM)

    private fun select(option: ModelOption, modality: Modality) {
        viewModelScope.launch {
            selectMutex.withLock {
                val currentId = currentIdFor(modality)
                if (option.id == currentId) return@withLock
                if (
                    isBusy(Modality.LLM) || isBusy(Modality.STT) || isBusy(Modality.TTS) ||
                    isBusy(Modality.EMBEDDING) || isBusy(Modality.VLM) ||
                    isVADDownloading || isVADLoading
                ) {
                    statusMessage =
                        "Wait for the current download/load to finish before switching models"
                    return@withLock
                }
                // Drop every resident model when changing selection — stacking
                // HNPU graphs across modalities is what KP'd this 8GB phone.
                if (anyModelLoaded()) {
                    unloadAllSuspend("switch to ${option.id}")
                }
                applyOption(option, modality)
                refreshModelState()
            }
        }
    }

    /** Select [option], free every resident model, then download + load it. */
    fun downloadAndLoadOption(option: ModelOption) {
        viewModelScope.launch {
            val modality = modalityFor(option.category) ?: run {
                errorMessage = "Unsupported modality for ${option.id}"
                return@launch
            }
            selectMutex.withLock {
                if (option.id != currentIdFor(modality)) {
                    applyOption(option, modality)
                }
            }
            downloadAndLoadFor(modality, exclusive = true)
        }
    }

    private fun cpuLlmOption() = ModelOption(
        id = CPU_LLM_MODEL_ID,
        name = "SmolLM2 360M Instruct Q8_0",
        framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
        category = ModelCategory.MODEL_CATEGORY_LANGUAGE,
        usesNpu = false,
        sizeBytes = 400_000_000L,
    )

    private fun cpuSttOption() = ModelOption(
        id = CPU_STT_MODEL_ID,
        name = "Sherpa Whisper Tiny (ONNX)",
        framework = InferenceFramework.INFERENCE_FRAMEWORK_SHERPA,
        category = ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION,
        usesNpu = false,
        sizeBytes = 75_000_000L,
    )

    private fun cpuTtsOption() = ModelOption(
        id = CPU_TTS_MODEL_ID,
        name = "Piper TTS (US English - Medium)",
        framework = InferenceFramework.INFERENCE_FRAMEWORK_SHERPA,
        category = ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS,
        usesNpu = false,
        sizeBytes = 65_000_000L,
    )

    private fun cpuEmbeddingOption() = ModelOption(
        id = CPU_EMBEDDING_MODEL_ID,
        name = "All MiniLM L6 v2 (Embedding)",
        framework = InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
        category = ModelCategory.MODEL_CATEGORY_EMBEDDING,
        usesNpu = false,
        sizeBytes = 25_500_000L,
    )

    private fun cpuVlmOption() = ModelOption(
        id = CPU_VLM_MODEL_ID,
        name = "SmolVLM 256M Instruct (Q8)",
        framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
        category = ModelCategory.MODEL_CATEGORY_MULTIMODAL,
        usesNpu = false,
        sizeBytes = 365_000_000L,
    )

    private suspend fun registerCpuModels() {
        RunAnywhere.registerModel(
            id = CPU_LLM_MODEL_ID,
            name = "SmolLM2 360M Instruct Q8_0",
            url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
            framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
            modality = ModelCategory.MODEL_CATEGORY_LANGUAGE,
            memoryRequirement = 400_000_000
        )

        RunAnywhere.registerModel(
            archiveUrl = "https://github.com/RunanywhereAI/sherpa-onnx/releases/download/runanywhere-models-v1/sherpa-onnx-whisper-tiny.en.tar.gz",
            structure = ArchiveStructure.ARCHIVE_STRUCTURE_NESTED_DIRECTORY,
            id = CPU_STT_MODEL_ID,
            name = "Sherpa Whisper Tiny (ONNX)",
            framework = InferenceFramework.INFERENCE_FRAMEWORK_SHERPA,
            modality = ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION,
            archiveType = ArchiveType.ARCHIVE_TYPE_TAR_GZ,
            memoryRequirement = 75_000_000
        )

        RunAnywhere.registerModel(
            archiveUrl = "https://github.com/RunanywhereAI/sherpa-onnx/releases/download/runanywhere-models-v1/vits-piper-en_US-lessac-medium.tar.gz",
            structure = ArchiveStructure.ARCHIVE_STRUCTURE_NESTED_DIRECTORY,
            id = CPU_TTS_MODEL_ID,
            name = "Piper TTS (US English - Medium)",
            framework = InferenceFramework.INFERENCE_FRAMEWORK_SHERPA,
            modality = ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS,
            archiveType = ArchiveType.ARCHIVE_TYPE_TAR_GZ,
            memoryRequirement = 65_000_000
        )

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
            id = CPU_VLM_MODEL_ID,
            name = "SmolVLM 256M Instruct (Q8)",
            framework = InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP,
            modality = ModelCategory.MODEL_CATEGORY_MULTIMODAL,
            memoryRequirement = 365_000_000
        )

        RunAnywhere.registerModel(
            id = VAD_MODEL_ID,
            name = "Silero VAD",
            url = "https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx",
            framework = InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
            modality = ModelCategory.MODEL_CATEGORY_VOICE_ACTIVITY_DETECTION,
            memoryRequirement = 2_327_524
        )

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
            id = CPU_EMBEDDING_MODEL_ID,
            name = "All MiniLM L6 v2 (Embedding)",
            framework = InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
            modality = ModelCategory.MODEL_CATEGORY_EMBEDDING,
            memoryRequirement = 25_500_000
        )
    }

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

    private suspend fun isModelDownloaded(modelId: String): Boolean {
        val result = RunAnywhere.getModel(ModelGetRequest(model_id = modelId))
        return result.found && !result.model?.local_path.isNullOrBlank()
    }

    private fun currentIdFor(modality: Modality): String = when (modality) {
        Modality.LLM -> llmModelId
        Modality.STT -> sttModelId
        Modality.TTS -> ttsModelId
        Modality.EMBEDDING -> embeddingModelId
        Modality.VLM -> vlmModelId
    }

    private fun modalityFor(category: ModelCategory): Modality? = when (category) {
        ModelCategory.MODEL_CATEGORY_LANGUAGE -> Modality.LLM
        ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION -> Modality.STT
        ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS -> Modality.TTS
        ModelCategory.MODEL_CATEGORY_EMBEDDING -> Modality.EMBEDDING
        ModelCategory.MODEL_CATEGORY_MULTIMODAL -> Modality.VLM
        else -> null
    }

    private fun anyModelLoaded(): Boolean =
        isLLMLoaded || isSTTLoaded || isTTSLoaded || isVLMLoaded ||
            isEmbeddingLoaded || isVADLoaded

    private fun optionFor(modelId: String): ModelOption? =
        (llmOptions + sttOptions + ttsOptions + embeddingOptions + vlmOptions)
            .firstOrNull { it.id == modelId }

    private fun availableRamBytes(): Long = try {
        File("/proc/meminfo").useLines { lines ->
            val line = lines.first { it.startsWith("MemAvailable:") }
            line.split(Regex("\\s+"))[1].toLong() * 1024L
        }
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format("%.1f GB", gb)
        else String.format("%.0f MB", bytes / (1024.0 * 1024.0))
    }

    /**
     * Tear down every resident model (all modalities). Hexagon + host RAM on
     * ~8GB phones cannot safely hold LLM+TTS+STT HNPU graphs at once — that
     * pressure is what has been kernel-panic rebooting the device.
     *
     * Must verify emptiness: a failed/partial unload followed by another HNPU
     * load is the usual reboot path. Callers must abort the next load on false.
     */
    private suspend fun unloadAllSuspend(reason: String): Boolean {
        val before = availableRamBytes()
        Log.i(TAG, "Unloading all models ($reason) avail=${formatBytes(before)}")
        statusMessage = "Freeing memory ($reason)…"
        repeat(2) { attempt ->
            try {
                RunAnywhere.unloadModel(ModelUnloadRequest(unload_all = true))
            } catch (e: Exception) {
                Log.w(TAG, "unloadAll attempt ${attempt + 1} threw: ${e.message}")
            }
            // Clear UI flags immediately; Hexagon reclaim can lag currentModel().
            isLLMLoaded = false
            isSTTLoaded = false
            isTTSLoaded = false
            isVLMLoaded = false
            isVADLoaded = false
            isEmbeddingLoaded = false
            System.gc()
            // DSP / ION reclaim is slow on this device — 300ms was not enough.
            delay(if (attempt == 0) 800 else 1_200)
            refreshModelState()
            val after = availableRamBytes()
            Log.i(
                TAG,
                "unload attempt ${attempt + 1}: " +
                    "llm=$isLLMLoaded stt=$isSTTLoaded tts=$isTTSLoaded " +
                    "vlm=$isVLMLoaded emb=$isEmbeddingLoaded vad=$isVADLoaded " +
                    "avail=${formatBytes(after)} (was ${formatBytes(before)})",
            )
            if (!anyModelLoaded()) {
                statusMessage = "Memory freed ($reason) · ${formatBytes(after)} free"
                return true
            }
        }
        Log.e(TAG, "unloadAll incomplete after retries ($reason) — refusing to stack loads")
        errorMessage =
            "Could not fully unload previous models ($reason). " +
                "Tap “Unload all” in QHexRT Lab, wait a few seconds, then Load again. " +
                "Forcing another HNPU load can reboot this phone."
        return false
    }

    private suspend fun downloadAndLoadFor(modality: Modality, exclusive: Boolean) {
        when (modality) {
            Modality.LLM -> downloadAndLoad(
                modelId = llmModelId,
                category = ModelCategory.MODEL_CATEGORY_LANGUAGE,
                framework = llmFramework,
                setDownloading = { isLLMDownloading = it },
                setProgress = { llmDownloadProgress = it },
                setLoading = { isLLMLoading = it },
                label = "LLM",
                exclusive = exclusive,
                modality = modality,
            )
            Modality.STT -> downloadAndLoad(
                modelId = sttModelId,
                category = ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION,
                framework = sttFramework,
                setDownloading = { isSTTDownloading = it },
                setProgress = { sttDownloadProgress = it },
                setLoading = { isSTTLoading = it },
                label = "STT",
                exclusive = exclusive,
                modality = modality,
            )
            Modality.TTS -> downloadAndLoad(
                modelId = ttsModelId,
                category = ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS,
                framework = ttsFramework,
                setDownloading = { isTTSDownloading = it },
                setProgress = { ttsDownloadProgress = it },
                setLoading = { isTTSLoading = it },
                label = "TTS",
                exclusive = exclusive,
                modality = modality,
            )
            Modality.EMBEDDING -> downloadAndLoad(
                modelId = embeddingModelId,
                category = ModelCategory.MODEL_CATEGORY_EMBEDDING,
                framework = embeddingFramework,
                setDownloading = { isEmbeddingDownloading = it },
                setProgress = { embeddingDownloadProgress = it },
                setLoading = { isEmbeddingLoading = it },
                label = "Embedding",
                exclusive = exclusive,
                modality = modality,
            )
            Modality.VLM -> downloadAndLoad(
                modelId = vlmModelId,
                category = ModelCategory.MODEL_CATEGORY_MULTIMODAL,
                framework = vlmFramework,
                setDownloading = { isVLMDownloading = it },
                setProgress = { vlmDownloadProgress = it },
                setLoading = { isVLMLoading = it },
                label = "VLM",
                exclusive = exclusive,
                modality = modality,
            )
        }
    }

    private fun isBusy(modality: Modality): Boolean = when (modality) {
        Modality.LLM -> isLLMDownloading || isLLMLoading
        Modality.STT -> isSTTDownloading || isSTTLoading
        Modality.TTS -> isTTSDownloading || isTTSLoading
        Modality.EMBEDDING -> isEmbeddingDownloading || isEmbeddingLoading
        Modality.VLM -> isVLMDownloading || isVLMLoading
    }

    private suspend fun downloadAndLoad(
        modelId: String,
        category: ModelCategory,
        framework: InferenceFramework,
        setDownloading: (Boolean) -> Unit,
        setProgress: (Float) -> Unit,
        setLoading: (Boolean) -> Unit,
        label: String,
        exclusive: Boolean = true,
        modality: Modality? = null,
    ) {
        if (!isSdkReady) {
            errorMessage = "SDK is still starting — try again in a moment"
            return
        }
        // Serialize load/unload/download so a second Load tap (or picker change)
        // cannot cancel an in-flight Kitten/Melo download and leave .part junk.
        selectMutex.withLock {
            if (modality != null && isBusy(modality)) {
                statusMessage = "$label is already downloading/loading — wait for it to finish"
                return
            }
            try {
                errorMessage = null
                // Mark busy immediately so UI disables Load and races can't enter.
                setLoading(true)
                statusMessage = "Preparing $label…"

                if (exclusive) {
                    if (!unloadAllSuspend("before $label $modelId")) {
                        setLoading(false)
                        return
                    }
                }

                val needed = optionFor(modelId)?.sizeBytes ?: 0L
                val avail = availableRamBytes()
                if (needed > 0L && avail < needed + RAM_HEADROOM_BYTES) {
                    errorMessage =
                        "$label '${modelId}' is too large for free RAM right now: " +
                            "needs ~${formatBytes(needed)} model + " +
                            "${formatBytes(RAM_HEADROOM_BYTES)} headroom, but only " +
                            "${formatBytes(avail)} is free. " +
                            "This is not a missing-backend issue — pick a smaller model " +
                            "(e.g. SmolVLM CPU or Nemotron OCR), Hard-reset HNPU, " +
                            "or free memory and retry."
                    Log.w(TAG, errorMessage!!)
                    setLoading(false)
                    return
                }

                if (!isModelDownloaded(modelId)) {
                    if (!exclusive && !unloadAllSuspend("before download $modelId")) {
                        setLoading(false)
                        return
                    }
                    setDownloading(true)
                    setProgress(0f)
                    statusMessage = "Downloading $label — keep this screen open…"
                    val model = RunAnywhere.getModel(ModelGetRequest(model_id = modelId)).model
                        ?: error("$label model is not registered")
                    RunAnywhere.downloadModel(model) { progress ->
                        setProgress(progress.overall_progress)
                    }
                    setDownloading(false)
                    if (!isModelDownloaded(modelId)) {
                        error(
                            "$label download did not finish (incomplete files). " +
                                "Tap Load once and wait — don't switch models mid-download."
                        )
                    }
                }

                statusMessage = "Loading $label…"
                val result = RunAnywhere.loadModel(
                    RAModelLoadRequest(
                        model_id = modelId,
                        category = category,
                        framework = framework,
                    )
                )
                if (!result.success) {
                    error(result.error_message.ifBlank { "$label load failed" })
                }
                setLoading(false)
                refreshModelState()
                statusMessage = "$label ready · ${formatBytes(availableRamBytes())} free"
                Log.i(TAG, "Loaded $label modelId=$modelId avail=${formatBytes(availableRamBytes())}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                setDownloading(false)
                setLoading(false)
                statusMessage = null
                errorMessage =
                    "$label was interrupted. Tap Load once and wait until Ready " +
                        "(switching models mid-download cancels it)."
                throw e
            } catch (e: Exception) {
                val raw = e.message.orEmpty()
                errorMessage = when {
                    raw.contains("checksum", ignoreCase = true) ->
                        "$label load failed: checksum mismatch — usually a partial download " +
                            "after a reboot/cancel. Tap Load again and wait for it to finish."
                    raw.contains("cancel", ignoreCase = true) ->
                        "$label download was cancelled. Tap Load once and wait — " +
                            "don't switch models until Ready."
                    else -> "$label load failed: ${e.message}"
                }
                statusMessage = null
                setDownloading(false)
                setLoading(false)
            }
        }
    }

    fun downloadAndLoadLLM() {
        viewModelScope.launch { downloadAndLoadFor(Modality.LLM, exclusive = true) }
    }

    fun downloadAndLoadSTT() {
        viewModelScope.launch { downloadAndLoadFor(Modality.STT, exclusive = true) }
    }

    fun downloadAndLoadTTS() {
        viewModelScope.launch { downloadAndLoadFor(Modality.TTS, exclusive = true) }
    }

    fun downloadAndLoadVLM() {
        viewModelScope.launch { downloadAndLoadFor(Modality.VLM, exclusive = true) }
    }

    fun downloadAndLoadVAD() {
        viewModelScope.launch {
            if (isVADDownloading || isVADLoading) return@launch
            downloadAndLoad(
                modelId = VAD_MODEL_ID,
                category = ModelCategory.MODEL_CATEGORY_VOICE_ACTIVITY_DETECTION,
                framework = InferenceFramework.INFERENCE_FRAMEWORK_ONNX,
                setDownloading = { isVADDownloading = it },
                setProgress = { vadDownloadProgress = it },
                setLoading = { isVADLoading = it },
                label = "VAD",
                exclusive = true,
            )
        }
    }

    fun downloadAndLoadEmbedding() {
        viewModelScope.launch { downloadAndLoadFor(Modality.EMBEDDING, exclusive = true) }
    }

    suspend fun downloadModelIfNeeded(
        modelId: String,
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        if (isModelDownloaded(modelId)) return true
        unloadAllSuspend("before download $modelId")
        val model = RunAnywhere.getModel(ModelGetRequest(model_id = modelId)).model
            ?: return false
        RunAnywhere.downloadModel(model) { progress -> onProgress(progress.overall_progress) }
        return isModelDownloaded(modelId)
    }

    /**
     * Voice pipeline needs LLM+STT+TTS resident together. On HNPU, stacking three
     * NPU graphs OOMs this phone — keep at most one HNPU (prefer LLM) and force
     * STT/TTS onto CPU fallbacks, then load them without exclusive eviction.
     */
    fun downloadAndLoadAllModels() {
        viewModelScope.launch {
            selectMutex.withLock {
                val npuCount = listOf(llmUsesNpu, sttUsesNpu, ttsUsesNpu).count { it }
                if (npuCount >= 2) {
                    sttOptions.firstOrNull { !it.usesNpu }?.let { applyOption(it, Modality.STT) }
                    ttsOptions.firstOrNull { !it.usesNpu }?.let { applyOption(it, Modality.TTS) }
                    statusMessage =
                        "Voice: kept HNPU LLM; STT/TTS use CPU to avoid OOM reboots"
                    Log.i(TAG, statusMessage!!)
                }
            }
            unloadAllSuspend("voice pipeline prepare")
            if (!isLLMLoaded) downloadAndLoadFor(Modality.LLM, exclusive = false)
            if (!isSTTLoaded) downloadAndLoadFor(Modality.STT, exclusive = false)
            if (!isTTSLoaded) downloadAndLoadFor(Modality.TTS, exclusive = false)
        }
    }

    fun unloadAllModels() {
        viewModelScope.launch {
            if (!unloadAllSuspend("manual unload")) {
                errorMessage = "Failed to unload models"
            } else {
                statusMessage = "All models unloaded · ${formatBytes(availableRamBytes())} free"
            }
        }
    }

    /**
     * Safest HNPU reset on this device: kill the process so Hexagon/fastrpc
     * sessions and dma-bufs die with the process. Soft unload goes through
     * fastrpc ioctls that have been kernel-panic'ing (plist_add / pm_qos).
     */
    fun hardKillReset() {
        viewModelScope.launch {
            statusMessage = "Hard-resetting HNPU (killing process)…"
            try {
                RunAnywhere.unloadModel(ModelUnloadRequest(unload_all = true))
            } catch (e: Exception) {
                Log.w(TAG, "hardKillReset unload threw (continuing to kill): ${e.message}")
            }
            delay(300)
            Log.e(TAG, "hardKillReset: Process.killProcess — reopen app after relaunch")
            Process.killProcess(Process.myPid())
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun clearStatus() {
        statusMessage = null
    }
}
