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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.AppButton
import com.runanywhere.kotlin_starter_example.ui.components.AppCard
import com.runanywhere.kotlin_starter_example.ui.components.AppOutlinedButton
import com.runanywhere.kotlin_starter_example.ui.components.AppScaffold
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme
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

@Composable
fun VisionScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
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

    AppScaffold(
        title = "Vision",
        subtitle = "Image Understanding",
        onBack = onNavigateBack,
        bottomBar = {
            if (modelService.isVLMLoaded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppOutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(TablerIcons.PhotoPlus, contentDescription = "Pick image", modifier = Modifier.size(18.dp))
                    }

                    AppButton(
                        onClick = {
                            if (isProcessing) {
                                RunAnywhere.cancelVLMGeneration()
                            } else {
                                val path = imageFilePath ?: return@AppButton
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
                            }
                        },
                        enabled = selectedBitmap != null || isProcessing,
                        color = if (isProcessing) colors.error else colors.tintPink,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isProcessing) TablerIcons.PlayerStop else TablerIcons.Eye,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(if (isProcessing) "Stop" else "Analyze Image")
                    }
                }
            }
        }
    ) {
        // Model loader
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
                        color = colors.error,
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image area
                VisionImageArea(
                    bitmap = selectedBitmap,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                // Prompt input
                VisionPromptInput(
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    onQuickPrompt = { prompt = it }
                )

                // Description output
                if (description.isNotEmpty() || isProcessing) {
                    VisionDescriptionArea(
                        description = description,
                        isProcessing = isProcessing,
                        tokensPerSecond = tokensPerSecond
                    )
                }

                // Error
                errorMessage?.let { VisionErrorView(it) }
            }
        }
    }
}

@Composable
private fun VisionImageArea(bitmap: Bitmap?, onPickImage: () -> Unit) {
    val colors = AppTheme.colors

    AppCard {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 300.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = TablerIcons.Photo,
                    contentDescription = null,
                    tint = colors.tintPink.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "Select an image to analyze",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                AppButton(onClick = onPickImage, color = colors.tintPink) {
                    Icon(TablerIcons.PhotoPlus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Choose Photo")
                }
            }
        }
    }
}

@Composable
private fun VisionPromptInput(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onQuickPrompt: (String) -> Unit
) {
    val colors = AppTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Prompt",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary
        )

        TextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ask about the image...", color = colors.textTertiary) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainer,
                unfocusedContainerColor = colors.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.tintPink
            ),
            shape = RoundedCornerShape(12.dp),
            maxLines = 3,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary)
        )

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
                    color = colors.tintPink.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.tintPink
                    )
                }
            }
        }
    }
}

@Composable
private fun VisionDescriptionArea(
    description: String,
    isProcessing: Boolean,
    tokensPerSecond: Float
) {
    val colors = AppTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Description",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tokensPerSecond > 0) {
                    Text(
                        text = String.format("%.1f tok/s", tokensPerSecond),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.tintPink
                    )
                }
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.tintPink
                    )
                }
            }
        }

        AppCard {
            Text(
                text = description.ifEmpty { "Analyzing..." },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary
            )
        }
    }
}

@Composable
private fun VisionErrorView(message: String) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.error.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.error,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.error
        )
    }
}

private suspend fun loadImageFromUri(context: Context, uri: Uri): Pair<Bitmap?, String?> {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null to null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

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
