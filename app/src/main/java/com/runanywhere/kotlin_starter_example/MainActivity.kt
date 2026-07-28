package com.runanywhere.kotlin_starter_example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.runanywhere.kotlin_starter_example.BuildConfig
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.screens.ChatScreen
import com.runanywhere.kotlin_starter_example.ui.screens.EmbeddingsScreen
import com.runanywhere.kotlin_starter_example.ui.screens.HomeScreen
import com.runanywhere.kotlin_starter_example.ui.screens.QHexRTLabScreen
import com.runanywhere.kotlin_starter_example.ui.screens.RagScreen
import com.runanywhere.kotlin_starter_example.ui.screens.SpeechToTextScreen
import com.runanywhere.kotlin_starter_example.ui.screens.StructuredOutputScreen
import com.runanywhere.kotlin_starter_example.ui.screens.TextToSpeechScreen
import com.runanywhere.kotlin_starter_example.ui.screens.ToolCallingScreen
import com.runanywhere.kotlin_starter_example.ui.screens.VadScreen
import com.runanywhere.kotlin_starter_example.ui.screens.VisionScreen
import com.runanywhere.kotlin_starter_example.ui.screens.VoicePipelineScreen
import com.runanywhere.kotlin_starter_example.ui.theme.KotlinStarterTheme
import com.runanywhere.sdk.core.onnx.ONNX
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.npu.qhexrt.QHexRT
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.configuration.SDKEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KotlinStarterTheme {
                RunAnywhereApp(activity = this)
            }
        }
    }
}

@Composable
fun RunAnywhereApp(activity: ComponentActivity) {
    val navController = rememberNavController()
    val modelService: ModelService = viewModel()

    // Backend registration is suspend (0.20.x). Register backends before
    // RunAnywhere.initialize() so the plugin registry is never briefly empty
    // for a concurrent loadModel() caller. QHexRT comes after initialize()
    // because its skel installer needs the application Context.
    LaunchedEffect(Unit) {
        if (modelService.isSdkReady) return@LaunchedEffect
        // Native registration + HF catalog probes must not run on the main thread
        // (OkHttp throws NetworkOnMainThreadException during registerModelForDevice).
        withContext(Dispatchers.IO) {
            try {
                try {
                    LlamaCPP.register() // LLM + VLM (GGUF)
                } catch (e: Throwable) {
                    Log.w(TAG, "LlamaCPP.register partial failure (VLM may be unavailable): ${e.message}")
                }
                ONNX.register() // STT/TTS/VAD/embeddings CPU path

                RunAnywhere.initialize(
                    context = activity,
                    environment = SDKEnvironment.SDK_ENVIRONMENT_DEVELOPMENT,
                )

                // Optional HF token from local.properties → BuildConfig (gated HNPU repos).
                val hfToken = BuildConfig.HF_TOKEN
                if (hfToken.isNotBlank()) {
                    RunAnywhere.setHfToken(hfToken)
                    Log.i(TAG, "Hugging Face token applied for HNPU downloads")
                }

                // Safe on non-NPU devices — registration is a no-op when unsupported.
                QHexRT.register()
                modelService.bootstrapModels()

                val npu = QHexRT.probeNpu()
                Log.i(
                    TAG,
                    "SDK ready · npu_supported=${npu.qhexrt_supported} arch=${npu.arch_name} soc=${npu.soc_model} " +
                        "npu_models=${modelService.registeredNpuCount} " +
                        "llm=${modelService.llmModelId} stt=${modelService.sttModelId} " +
                        "tts=${modelService.ttsModelId} embed=${modelService.embeddingModelId} " +
                        "vlm=${modelService.vlmModelId}",
                )
            } catch (e: Throwable) {
                Log.e(TAG, "SDK bootstrap failed", e)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                modelService = modelService,
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToSTT = { navController.navigate("stt") },
                onNavigateToTTS = { navController.navigate("tts") },
                onNavigateToVoicePipeline = { navController.navigate("voice_pipeline") },
                onNavigateToToolCalling = { navController.navigate("tool_calling") },
                onNavigateToVision = { navController.navigate("vision") },
                onNavigateToVad = { navController.navigate("vad") },
                onNavigateToRag = { navController.navigate("rag") },
                onNavigateToEmbeddings = { navController.navigate("embeddings") },
                onNavigateToStructuredOutput = { navController.navigate("structured_output") },
                onNavigateToQHexRTLab = { navController.navigate("qhexrt_lab") },
            )
        }

        composable("qhexrt_lab") {
            QHexRTLabScreen(
                modelService = modelService,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable("chat") {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("stt") {
            SpeechToTextScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("tts") {
            TextToSpeechScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("voice_pipeline") {
            VoicePipelineScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("tool_calling") {
            ToolCallingScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("vision") {
            VisionScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("vad") {
            VadScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("rag") {
            RagScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("embeddings") {
            EmbeddingsScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }

        composable("structured_output") {
            StructuredOutputScreen(
                onNavigateBack = { navController.popBackStack() },
                modelService = modelService
            )
        }
    }
}
