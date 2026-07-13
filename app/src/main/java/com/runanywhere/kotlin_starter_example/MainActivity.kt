package com.runanywhere.kotlin_starter_example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.screens.ChatScreen
import com.runanywhere.kotlin_starter_example.ui.screens.HomeScreen
import com.runanywhere.kotlin_starter_example.ui.screens.SpeechToTextScreen
import com.runanywhere.kotlin_starter_example.ui.screens.TextToSpeechScreen
import com.runanywhere.kotlin_starter_example.ui.screens.ToolCallingScreen
import com.runanywhere.kotlin_starter_example.ui.screens.VisionScreen
import com.runanywhere.kotlin_starter_example.ui.screens.VoicePipelineScreen
import com.runanywhere.kotlin_starter_example.ui.theme.KotlinStarterTheme
import com.runanywhere.sdk.core.onnx.ONNX
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.npu.qhexrt.QHexRT
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.configuration.SDKEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    // Backend registration is suspend (0.20.9), so bootstrap runs on its own
    // scope rather than blocking onCreate. Backends are registered before
    // RunAnywhere.initialize() so the plugin registry is never briefly empty
    // for a concurrent loadModel() caller.
    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bootstrapScope.launch {
            try {
                LlamaCPP.register() // For LLM + VLM (GGUF models)
            } catch (e: Throwable) {
                // VLM native registration may fail if .so doesn't include nativeRegisterVlm;
                // LLM text generation still works since it was registered before VLM in register()
                Log.w(TAG, "LlamaCPP.register partial failure (VLM may be unavailable): ${e.message}")
            }
            ONNX.register() // For STT/TTS (Sherpa-ONNX models)

            // Single bootstrap call: this also wires up the Android platform
            // context (storage paths, secure storage) that the SDK needs.
            RunAnywhere.initialize(
                context = this@MainActivity,
                environment = SDKEnvironment.SDK_ENVIRONMENT_DEVELOPMENT,
            )

            // QHexRT (Qualcomm Hexagon NPU). Registered after initialize() because its
            // skel installer needs the application Context installed by RunAnywhere.initialize()
            // above. Registration is rejected internally (no-op) on devices without a supported
            // Hexagon NPU, so this is safe to call unconditionally.
            QHexRT.register()

            ModelService.registerDefaultModels()
        }

        setContent {
            KotlinStarterTheme {
                RunAnywhereApp()
            }
        }
    }
}

@Composable
fun RunAnywhereApp() {
    val navController = rememberNavController()
    val modelService: ModelService = viewModel()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToChat = { navController.navigate("chat") },
                onNavigateToSTT = { navController.navigate("stt") },
                onNavigateToTTS = { navController.navigate("tts") },
                onNavigateToVoicePipeline = { navController.navigate("voice_pipeline") },
                onNavigateToToolCalling = { navController.navigate("tool_calling") },
                onNavigateToVision = { navController.navigate("vision") }
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
    }
}
