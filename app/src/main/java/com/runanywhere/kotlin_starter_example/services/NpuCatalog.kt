package com.runanywhere.kotlin_starter_example.services

import ai.runanywhere.proto.v1.InferenceFramework
import ai.runanywhere.proto.v1.ModelCategory
import ai.runanywhere.proto.v1.ModelSource
import ai.runanywhere.proto.v1.RegisterModelFromUrlRequest

/**
 * Curated QHexRT / HNPU catalog for the starter.
 *
 * Native [com.runanywhere.sdk.npu.qhexrt.QHexRT.registerModelForDevice] filters by
 * Hexagon arch / auth policy — ineligible rows simply do not appear in the picker.
 */
data class ModelOption(
    val id: String,
    val name: String,
    val framework: InferenceFramework,
    val category: ModelCategory,
    val usesNpu: Boolean,
    /** Approximate download size for UI hints. */
    val sizeBytes: Long = 0L,
) {
    val backendLabel: String
        get() = when {
            usesNpu -> "QHexRT · Hexagon NPU"
            framework == InferenceFramework.INFERENCE_FRAMEWORK_LLAMA_CPP -> "llama.cpp"
            framework == InferenceFramework.INFERENCE_FRAMEWORK_SHERPA -> "Sherpa-ONNX"
            framework == InferenceFramework.INFERENCE_FRAMEWORK_ONNX -> "ONNX"
            else -> framework.name.removePrefix("INFERENCE_FRAMEWORK_")
        }

    val sizeLabel: String
        get() = when {
            sizeBytes <= 0L -> ""
            sizeBytes < 1_000_000L -> "${sizeBytes / 1_000} KB"
            sizeBytes < 1_000_000_000L -> "${sizeBytes / 1_000_000} MB"
            else -> String.format("%.1f GB", sizeBytes / 1_000_000_000.0)
        }
}

internal data class NpuCatalogEntry(
    val id: String,
    val name: String,
    val url: String,
    val category: ModelCategory,
    val memoryBytes: Long,
    val contextLength: Int? = null,
    val supportsThinking: Boolean = false,
    /** Prefer as the default active model for its modality when registration succeeds. */
    val preferredDefault: Boolean = false,
) {
    fun toRegistrationRequest(): RegisterModelFromUrlRequest =
        RegisterModelFromUrlRequest(
            id = id,
            name = name,
            url = url,
            framework = InferenceFramework.INFERENCE_FRAMEWORK_QHEXRT,
            category = category,
            source = ModelSource.MODEL_SOURCE_REMOTE,
            memory_required_bytes = memoryBytes,
            download_size_bytes = memoryBytes,
            context_length = contextLength,
            supports_thinking = supportsThinking,
            description = "Qualcomm Hexagon NPU (QHexRT) model bundle.",
        )

    fun toOption(): ModelOption =
        ModelOption(
            id = id,
            name = name,
            framework = InferenceFramework.INFERENCE_FRAMEWORK_QHEXRT,
            category = category,
            usesNpu = true,
            sizeBytes = memoryBytes,
        )
}

internal object NpuCatalog {
    private val LANGUAGE = ModelCategory.MODEL_CATEGORY_LANGUAGE
    private val STT = ModelCategory.MODEL_CATEGORY_SPEECH_RECOGNITION
    private val TTS = ModelCategory.MODEL_CATEGORY_SPEECH_SYNTHESIS
    private val EMBEDDING = ModelCategory.MODEL_CATEGORY_EMBEDDING
    private val MULTIMODAL = ModelCategory.MODEL_CATEGORY_MULTIMODAL

    /**
     * Starter-focused HNPU set: covers LLM / STT / TTS / embed / VLM-OCR with
     * models that have device suites on V75/V79/V81. Keep preferred defaults first.
     */
    val entries: List<NpuCatalogEntry> = listOf(
        // --- Language ---
        NpuCatalogEntry(
            "lfm2_5_230m", "LFM2.5 230M (HNPU)",
            "https://huggingface.co/runanywhere/lfm2_5_230m_HNPU/lfm2-5-230m.json",
            LANGUAGE, 538_771_163L, contextLength = 512, preferredDefault = true,
        ),
        NpuCatalogEntry(
            "lfm2_5_350m", "LFM2.5 350M (HNPU)",
            "https://huggingface.co/runanywhere/lfm2_5_350m_HNPU/lfm2-5-350m-2048.json",
            LANGUAGE, 1_441_493_515L, contextLength = 2_048,
        ),
        NpuCatalogEntry(
            "qwen3_0_6b", "Qwen3 0.6B (HNPU)",
            "https://huggingface.co/runanywhere/qwen3_0_6b_HNPU/qwen3-0.6b-1024final.json",
            LANGUAGE, 1_823_248_798L, contextLength = 1_024,
        ),
        NpuCatalogEntry(
            "qwen3_5_0_8b", "Qwen3.5 0.8B (HNPU)",
            "https://huggingface.co/runanywhere/qwen3_5_0_8b_HNPU/qwen3.5-0.8b-1024.json",
            LANGUAGE, 2_046_527_510L, contextLength = 1_024, supportsThinking = true,
        ),
        NpuCatalogEntry(
            "llama3_2_1b", "Llama 3.2 1B (HNPU)",
            "https://huggingface.co/runanywhere/llama3_2_1b_HNPU/llama-3.2-1b.json",
            LANGUAGE, 3_023_821_212L, contextLength = 512,
        ),
        NpuCatalogEntry(
            "bonsai_4b_1bit", "Bonsai-4B 1-bit (HNPU)",
            "https://huggingface.co/runanywhere/bonsai_4b_1bit_HNPU/bonsai-4b-1024.json",
            LANGUAGE, 1_358_352_318L, contextLength = 1_024, supportsThinking = true,
        ),
        NpuCatalogEntry(
            "bonsai_8b_1bit", "Bonsai-8B 1-bit (HNPU)",
            "https://huggingface.co/runanywhere/bonsai_8b_1bit_HNPU/bonsai-8b-1024.json",
            LANGUAGE, 2_323_975_102L, contextLength = 1_024, supportsThinking = true,
        ),
        NpuCatalogEntry(
            "qwen3_vl_2b_text", "Qwen3-VL 2B Text (HNPU)",
            "https://huggingface.co/runanywhere/qwen3_vl_HNPU/qwen3vl-2b-text-512.json",
            LANGUAGE, 2_364_667_194L, contextLength = 512,
        ),

        // --- STT ---
        NpuCatalogEntry(
            "whisper_base", "Whisper Base (HNPU)",
            "https://huggingface.co/runanywhere/whisper_base_HNPU/whisper-base.json",
            STT, 221_522_616L, preferredDefault = true,
        ),
        NpuCatalogEntry(
            "moonshine_base", "Moonshine Base (HNPU)",
            "https://huggingface.co/runanywhere/moonshine_base_HNPU/moonshine-base.json",
            STT, 167_310_675L,
        ),
        NpuCatalogEntry(
            "canary_180m_flash", "Canary 180M Flash (HNPU)",
            "https://huggingface.co/runanywhere/canary_180m_flash_HNPU/canary-180m-flash.json",
            STT, 401_629_133L,
        ),
        NpuCatalogEntry(
            "canary_1b_flash", "Canary-1B-flash (HNPU)",
            "https://huggingface.co/runanywhere/canary_1b_flash_HNPU/canary-1b-flash.json",
            STT, 1_835_592_227L,
        ),
        NpuCatalogEntry(
            "parakeet_tdt_0_6b_v2", "Parakeet TDT 0.6B v2 (HNPU)",
            "https://huggingface.co/runanywhere/parakeet_tdt_0.6b_v2_HNPU/parakeet-tdt-0.6b-v2.json",
            STT, 1_280_063_837L,
        ),
        NpuCatalogEntry(
            "parakeet_tdt_0_6b_v3", "Parakeet TDT 0.6B v3 (HNPU)",
            "https://huggingface.co/runanywhere/parakeet_tdt_0.6b_v3_HNPU/parakeet-tdt-0.6b.json",
            STT, 1_317_902_802L,
        ),
        NpuCatalogEntry(
            "nemotron_asr_streaming", "Nemotron ASR Streaming 0.6B (HNPU)",
            "https://huggingface.co/runanywhere/nemotron_asr_streaming_HNPU/nemotron-3.5-asr-streaming-0.6b.json",
            STT, 1_361_283_432L,
        ),

        // --- TTS ---
        // Varlen L=128 Graph A (private HF). Preferred for local-engine Kitten validation.
        NpuCatalogEntry(
            "kitten_nano_0_8_varlen", "Kitten-nano-0.8 varlen L128 (HNPU)",
            "https://huggingface.co/runanywhere/kitten_nano_0_8_varlen_HNPU/kitten_nano08_v81.json",
            TTS, 45_000_000L, preferredDefault = true,
        ),
        NpuCatalogEntry(
            "magpie_tts_357m", "Magpie-TTS Multilingual 357M (HNPU)",
            "https://huggingface.co/runanywhere/magpie_tts_357m_HNPU",
            TTS, 749_093_186L,
        ),
        NpuCatalogEntry(
            "kitten_nano_0_8", "Kitten-nano-0.8 (HNPU)",
            "https://huggingface.co/runanywhere/kitten_nano_0_8_HNPU/kitten_nano08_v81.json",
            TTS, 44_135_896L,
        ),
        NpuCatalogEntry(
            "kitten_micro_0_8", "Kitten-micro-0.8 (HNPU)",
            "https://huggingface.co/runanywhere/kitten_micro_0_8_HNPU/kitten_micro08_v81.json",
            TTS, 103_930_338L,
        ),
        NpuCatalogEntry(
            "kitten_mini_0_8", "Kitten-mini-0.8 (HNPU)",
            "https://huggingface.co/runanywhere/kitten_mini_0_8_HNPU/kitten_mini08_v81.json",
            TTS, 184_334_815L,
        ),
        NpuCatalogEntry(
            "melotts_en", "MeloTTS EN (HNPU)",
            "https://huggingface.co/runanywhere/melotts_en_HNPU/melotts-en.json",
            TTS, 120_439_053L,
        ),
        NpuCatalogEntry(
            "kokoro_en", "Kokoro-82M EN (HNPU)",
            "https://huggingface.co/runanywhere/kokoro_en_HNPU/kokoro-en.json",
            TTS, 470_739_484L,
        ),

        // --- Embeddings ---
        NpuCatalogEntry(
            "nemotron_3_embed_1b", "Nemotron-3-Embed 1B (HNPU)",
            "https://huggingface.co/runanywhere/nemotron_3_embed_1b_HNPU/nemotron-3-embed-1b.json",
            EMBEDDING, 2_302_290_226L, preferredDefault = true,
        ),
        NpuCatalogEntry(
            "nv_embedqa_1b", "NV-EmbedQA 1B (HNPU)",
            "https://huggingface.co/runanywhere/nv_embedqa_1b_HNPU",
            EMBEDDING, 2_493_026_133L,
        ),
        NpuCatalogEntry(
            "embeddinggemma_300m", "EmbeddingGemma 300M (HNPU)",
            "https://huggingface.co/runanywhere/embeddinggemma_300m_HNPU",
            EMBEDDING, 566_263_339L,
        ),

        // --- Multimodal / OCR ---
        // Prefer small OCR as HNPU default: InternVL/Qwen3-VL are ~3GB and the
        // starter RAM gate (model + 1.5GB headroom) rejects them on ~8GB phones.
        NpuCatalogEntry(
            "nemotron_ocr", "Nemotron OCR (HNPU)",
            "https://huggingface.co/runanywhere/nemotron_ocr_HNPU",
            MULTIMODAL, 121_193_004L, preferredDefault = true,
        ),
        NpuCatalogEntry(
            "nemotron_ocr_v1", "Nemotron OCR v1 (HNPU)",
            "https://huggingface.co/runanywhere/nemotron_ocr_v1_HNPU",
            MULTIMODAL, 121_406_323L,
        ),
        NpuCatalogEntry(
            "internvl3_5_1b", "InternVL3.5 1B (HNPU)",
            "https://huggingface.co/runanywhere/internvl3_5_1b_HNPU",
            MULTIMODAL, 3_067_933_894L, contextLength = 512,
        ),
        NpuCatalogEntry(
            "qwen3_vl", "Qwen3-VL 2B (HNPU)",
            // Repo root — registerModelForDevice resolves arch-specific json.
            "https://huggingface.co/runanywhere/qwen3_vl_HNPU",
            MULTIMODAL, 3_220_398_168L, contextLength = 512,
        ),
        NpuCatalogEntry(
            "nemotron_parse", "Nemotron Parse (HNPU)",
            "https://huggingface.co/runanywhere/nemotron_parse_HNPU",
            MULTIMODAL, 1_995_206_253L,
        ),
    )
}
