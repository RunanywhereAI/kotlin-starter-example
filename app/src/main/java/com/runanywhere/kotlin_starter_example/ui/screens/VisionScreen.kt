package com.runanywhere.kotlin_starter_example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.VLM.VLMGenerationOptions
import com.runanywhere.sdk.public.extensions.VLM.VLMImage
import com.runanywhere.sdk.public.extensions.cancelVLMGeneration
import com.runanywhere.sdk.public.extensions.processImageStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageFilePath by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf("Describe this image in detail.") }
    var description by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var tokensPerSecond by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            description = ""
            errorMessage = null
            tokensPerSecond = 0f
            scope.launch {
                val (bitmap, path) = loadImageFromUri(context, it)
                selectedBitmap = bitmap
                imageFilePath = path
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Vision (VLM)")
                        Text(
                            text = "Image Understanding",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentPink
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryDark)
            )
        },
        containerColor = PrimaryDark
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Model loader section
            if (!modelService.isVLMLoaded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    ModelLoaderWidget(
                        modelName = "SmolVLM 256M (~365 MB)",
                        isDownloading = modelService.isVLMDownloading,
                        isLoading = modelService.isVLMLoading,
                        isLoaded = modelService.isVLMLoaded,
                        downloadProgress = modelService.vlmDownloadProgress,
                        onLoadClick = { modelService.downloadAndLoadVLM() }
                    )

                    modelService.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            } else {
                // Main VLM content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Image area
                    ImageArea(
                        bitmap = selectedBitmap,
                        onPickImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    // Prompt input
                    PromptInput(
                        prompt = prompt,
                        onPromptChange = { prompt = it },
                        onQuickPrompt = { prompt = it }
                    )

                    // Description output
                    if (description.isNotEmpty() || isProcessing) {
                        DescriptionArea(
                            description = description,
                            isProcessing = isProcessing,
                            tokensPerSecond = tokensPerSecond
                        )
                    }

                    // Error
                    errorMessage?.let { VisionErrorView(it) }
                }

                // Bottom action bar
                ActionBar(
                    hasImage = selectedBitmap != null,
                    isProcessing = isProcessing,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAnalyze = {
                        val path = imageFilePath ?: return@ActionBar
                        scope.launch {
                            isProcessing = true
                            description = ""
                            errorMessage = null
                            tokensPerSecond = 0f

                            try {
                                val vlmImage = VLMImage.fromFilePath(path)
                                val options = VLMGenerationOptions(maxTokens = 300)
                                val startTime = System.currentTimeMillis()
                                var tokenCount = 0

                                RunAnywhere.processImageStream(vlmImage, prompt, options)
                                    .collect { token ->
                                        description += token
                                        tokenCount++
                                        val elapsed = System.currentTimeMillis() - startTime
                                        if (elapsed > 0) {
                                            tokensPerSecond = tokenCount * 1000f / elapsed
                                        }
                                    }
                            } catch (e: Exception) {
                                errorMessage = "VLM Error: ${e.message}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    onStop = {
                        RunAnywhere.cancelVLMGeneration()
                    }
                )
            }
        }
    }
}

@Composable
private fun ImageArea(bitmap: Bitmap?, onPickImage: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 350.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected image",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = AccentPink.copy(alpha = 0.5f),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Select an image to analyze",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Button(
                    onClick = onPickImage,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink)
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Photo")
                }
            }
        }
    }
}

@Composable
private fun PromptInput(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Prompt",
            style = MaterialTheme.typography.labelLarge,
            color = TextMuted
        )

        TextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ask about the image...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3
        )

        // Quick prompts
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Describe this image",
                "What objects are in this?",
                "What colors do you see?",
                "Is there text in this image?"
            ).forEach { text ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onQuickPrompt(text) },
                    color = AccentPink.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentPink
                    )
                }
            }
        }
    }
}

@Composable
private fun DescriptionArea(
    description: String,
    isProcessing: Boolean,
    tokensPerSecond: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Description",
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tokensPerSecond > 0) {
                    Text(
                        text = String.format("%.1f tok/s", tokensPerSecond),
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentPink
                    )
                }
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AccentPink
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Text(
                text = description.ifEmpty { "Analyzing..." },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun VisionErrorView(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AEF4444))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ActionBar(
    hasImage: Boolean,
    isProcessing: Boolean,
    onPickImage: () -> Unit,
    onAnalyze: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PrimaryDark.copy(alpha = 0.9f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pick image button
            OutlinedButton(
                onClick = onPickImage,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPink),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Pick image")
            }

            // Analyze / Stop button
            Button(
                onClick = { if (isProcessing) onStop() else onAnalyze() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = hasImage || isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isProcessing) MaterialTheme.colorScheme.error else AccentPink
                )
            ) {
                Icon(
                    imageVector = if (isProcessing) Icons.Rounded.Stop else Icons.Rounded.RemoveRedEye,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isProcessing) "Stop" else "Analyze Image")
            }
        }
    }
}

/**
 * Load an image from a content URI, decode it, and save to a temp file
 * so the VLM can access it via file path.
 */
private suspend fun loadImageFromUri(context: Context, uri: Uri): Pair<Bitmap?, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null to null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Save to a temp file for the VLM to read
            val tempFile = File(context.cacheDir, "vlm_input_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            bitmap to tempFile.absolutePath
        } catch (e: Exception) {
            null to null
        }
    }
}
