package com.runanywhere.kotlin_starter_example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.screens.*
import com.runanywhere.kotlin_starter_example.ui.theme.KotlinStarterTheme
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.SDKEnvironment

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize RunAnywhere SDK
        RunAnywhere.initialize(environment = SDKEnvironment.DEVELOPMENT)
        
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
                onNavigateToVoicePipeline = { navController.navigate("voice_pipeline") }
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
    }
}
