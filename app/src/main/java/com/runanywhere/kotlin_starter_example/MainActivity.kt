package com.runanywhere.kotlin_starter_example

import android.os.Bundle
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
import android.util.Log
import com.runanywhere.sdk.core.onnx.ONNX
import com.runanywhere.sdk.foundation.bridge.extensions.CppBridgeModelPaths
import com.runanywhere.sdk.llm.llamacpp.LlamaCPP
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.SDKEnvironment
import com.runanywhere.sdk.storage.AndroidPlatformContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Android platform context FIRST - this sets up storage paths
        // The SDK requires this before RunAnywhere.initialize() on Android
        AndroidPlatformContext.initialize(this)
        
        // Initialize RunAnywhere SDK for development
        RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)
        
        // Set the base directory for model storage
        val runanywherePath = java.io.File(filesDir, "runanywhere").absolutePath
        CppBridgeModelPaths.setBaseDirectory(runanywherePath)
        
        // Register backends FIRST - these must be registered before loading any models
        // They provide the inference capabilities (TEXT_GENERATION, STT, TTS, VLM)
        try {
            LlamaCPP.register(priority = 100)  // For LLM + VLM (GGUF models)
        } catch (e: Throwable) {
            // VLM native registration may fail if .so doesn't include nativeRegisterVlm;
            // LLM text generation still works since it was registered before VLM in register()
            Log.w("MainActivity", "LlamaCPP.register partial failure (VLM may be unavailable): ${e.message}")
        }
        ONNX.register(priority = 100)      // For STT/TTS (ONNX models)
        
        // Register default models
        ModelService.registerDefaultModels()
        
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
