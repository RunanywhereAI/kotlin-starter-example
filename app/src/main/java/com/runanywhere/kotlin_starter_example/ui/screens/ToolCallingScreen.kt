package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.LLM.*
import com.runanywhere.sdk.public.extensions.generateWithTools
import com.runanywhere.sdk.public.extensions.registerTool
import com.runanywhere.sdk.public.types.RALLMGenerationOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tool Call Info for UI display
 */
data class ToolCallInfo(
    val toolName: String,
    val arguments: String,
    val result: String? = null,
    val error: String? = null,
    val success: Boolean = true
)

/**
 * Chat message that may contain tool calls
 */
data class ToolChatMessage(
    val text: String,
    val isUser: Boolean,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolCallingScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf(listOf<ToolChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var toolsRegistered by remember { mutableStateOf(false) }
    var selectedToolCall by remember { mutableStateOf<ToolCallInfo?>(null) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Suggestion prompts grouped by tool
    val suggestionPrompts = remember {
        listOf(
            // Weather tool
            "What's the weather in Tokyo?",
            "How's the weather in London?",
            "Is it raining in New York?",
            // Time tool
            "What time is it?",
            "What's the current date and time?",
            // Calculator tool
            "Calculate 15 * 7 + 23",
            "What is (100 - 37) / 9?",
            "Compute 2.5 * 4 + 10",
        )
    }
    
    // Send message handler shared by input field and suggestion chips
    val sendMessage: (String) -> Unit = { text ->
        if (text.isNotBlank() && !isGenerating) {
            messages = messages + ToolChatMessage(text, isUser = true)
            inputText = ""
            
            scope.launch {
                isGenerating = true
                listState.animateScrollToItem(messages.size)
                
                try {
                    val result = RunAnywhere.generateWithTools(
                        prompt = text,
                        options = RALLMGenerationOptions(
                            temperature = 0.7f,
                            max_tokens = 512,
                        ),
                        toolOptions = RAToolCallingOptions(
                            max_tool_calls = 3,
                            auto_execute = true,
                        ),
                        toolChoice = null,
                        forcedToolName = null,
                    )

                    val toolCallInfos = result.tool_calls.mapIndexed { index, call ->
                        val toolResult = result.tool_results.getOrNull(index)
                        val argumentsMap = runCatching { ToolValue.parseObjectJSON(call.arguments_json) }
                            .getOrDefault(emptyMap())
                        val resultMap = toolResult?.result_json
                            ?.let { json -> runCatching { ToolValue.parseObjectJSON(json) }.getOrNull() }
                        ToolCallInfo(
                            toolName = call.name,
                            arguments = argumentsMap.entries.joinToString(", ") {
                                "${it.key}: ${formatToolValue(it.value)}"
                            },
                            result = resultMap?.let { formatToolResult(it) },
                            error = toolResult?.error,
                            success = toolResult?.success ?: false
                        )
                    }

                    messages = messages + ToolChatMessage(
                        text = result.text,
                        isUser = false,
                        toolCalls = toolCallInfos
                    )
                    listState.animateScrollToItem(messages.size)
                } catch (e: Exception) {
                    messages = messages + ToolChatMessage(
                        text = "Error: ${e.message}",
                        isUser = false
                    )
                } finally {
                    isGenerating = false
                }
            }
        }
    }
    
    // Register tools on first composition
    LaunchedEffect(Unit) {
        if (!toolsRegistered) {
            registerDemoTools()
            toolsRegistered = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Tool Calling")
                        Text(
                            text = "LLM + Function Execution",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentCyan
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryDark
                )
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
            if (!modelService.isLLMLoaded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    ModelLoaderWidget(
                        modelName = "SmolLM2 360M",
                        isDownloading = modelService.isLLMDownloading,
                        isLoading = modelService.isLLMLoading,
                        isLoaded = modelService.isLLMLoaded,
                        downloadProgress = modelService.llmDownloadProgress,
                        onLoadClick = { modelService.downloadAndLoadLLM() }
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
            }
            
            // Tools info card
            if (modelService.isLLMLoaded && toolsRegistered) {
                ToolsInfoCard()
            }
            
            // Chat messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (messages.isEmpty() && modelService.isLLMLoaded) {
                    item {
                        ToolCallingEmptyState(
                            suggestions = suggestionPrompts,
                            enabled = !isGenerating,
                            onSuggestionClick = sendMessage
                        )
                    }
                }
                
                items(messages) { message ->
                    ToolChatMessageBubble(
                        message = message,
                        onToolCallClick = { selectedToolCall = it }
                    )
                }
            }
            
            // Input section with suggestion chips
            if (modelService.isLLMLoaded) {
                // Suggestion chips row (scrollable, always visible when not generating)
                if (!isGenerating && messages.isNotEmpty()) {
                    SuggestionChipsRow(
                        suggestions = suggestionPrompts,
                        onSuggestionClick = sendMessage
                    )
                }
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard.copy(alpha = 0.8f),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Try: What's the weather in Tokyo?") },
                            readOnly = isGenerating,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = PrimaryMid,
                                unfocusedContainerColor = PrimaryMid,
                                disabledContainerColor = PrimaryMid,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 4
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        FloatingActionButton(
                            onClick = { sendMessage(inputText) },
                            containerColor = if (isGenerating) AccentViolet else if (inputText.isBlank()) TextMuted else AccentOrange
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.Send, "Send")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Tool call detail sheet
    selectedToolCall?.let { toolCall ->
        ToolCallDetailSheet(
            toolCallInfo = toolCall,
            onDismiss = { selectedToolCall = null }
        )
    }
}

@Composable
private fun ToolsInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentOrange.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Build,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "3 Tools Available",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Weather • Time • Calculator",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ToolCallingEmptyState(
    suggestions: List<String>,
    enabled: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Build,
            contentDescription = null,
            tint = AccentOrange,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tool Calling Demo",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap a suggestion or type your own prompt",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // Weather suggestions
        SuggestionCategory(
            icon = Icons.Rounded.Cloud,
            label = "Weather",
            color = AccentCyan,
            prompts = suggestions.filter { it.lowercase().contains("weather") || it.lowercase().contains("rain") },
            enabled = enabled,
            onSuggestionClick = onSuggestionClick
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Time suggestions
        SuggestionCategory(
            icon = Icons.Rounded.Schedule,
            label = "Time",
            color = AccentViolet,
            prompts = suggestions.filter { it.lowercase().contains("time") || it.lowercase().contains("date") },
            enabled = enabled,
            onSuggestionClick = onSuggestionClick
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Calculator suggestions
        SuggestionCategory(
            icon = Icons.Rounded.Calculate,
            label = "Calculator",
            color = AccentGreen,
            prompts = suggestions.filter { it.lowercase().contains("calc") || it.lowercase().contains("compute") || it.lowercase().contains("what is") },
            enabled = enabled,
            onSuggestionClick = onSuggestionClick
        )
    }
}

@Composable
private fun SuggestionCategory(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    prompts: List<String>,
    enabled: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.forEach { prompt ->
                SuggestionChip(
                    text = prompt,
                    accentColor = color,
                    enabled = enabled,
                    onClick = { onSuggestionClick(prompt) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { onClick() },
        color = SurfaceCard,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) TextPrimary else TextMuted,
            maxLines = 1,
        )
    }
}

/**
 * Horizontally scrollable suggestion chips shown above the input bar
 * after the user has sent at least one message.
 */
@Composable
private fun SuggestionChipsRow(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { prompt ->
            SuggestionChip(
                text = prompt,
                accentColor = AccentOrange,
                enabled = true,
                onClick = { onSuggestionClick(prompt) }
            )
        }
    }
}

@Composable
private fun ToolChatMessageBubble(
    message: ToolChatMessage,
    onToolCallClick: (ToolCallInfo) -> Unit
) {
    Column {
        // Tool call indicators
        if (message.toolCalls.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                message.toolCalls.forEach { toolCall ->
                    ToolCallIndicator(
                        toolCallInfo = toolCall,
                        onTap = { onToolCallClick(toolCall) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
        
        // Message bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isUser) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Card(
                modifier = Modifier.widthIn(max = 280.dp),
                shape = RoundedCornerShape(
                    topStart = if (message.isUser) 16.dp else 4.dp,
                    topEnd = if (message.isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isUser) AccentCyan else SurfaceCard
                )
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else TextPrimary
                )
            }
            
            if (message.isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = AccentViolet,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolCallIndicator(
    toolCallInfo: ToolCallInfo,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (toolCallInfo.success) {
        AccentGreen.copy(alpha = 0.1f)
    } else {
        AccentPink.copy(alpha = 0.1f)
    }
    
    val borderColor = if (toolCallInfo.success) {
        AccentGreen.copy(alpha = 0.3f)
    } else {
        AccentPink.copy(alpha = 0.3f)
    }
    
    val iconTint = if (toolCallInfo.success) AccentGreen else AccentPink

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onTap() },
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (toolCallInfo.success) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = iconTint,
            )
            Text(
                text = toolCallInfo.toolName,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCallDetailSheet(
    toolCallInfo: ToolCallInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Text(
                text = "Tool Call Details",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            
            // Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (toolCallInfo.success) AccentGreen.copy(alpha = 0.1f)
                        else AccentPink.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (toolCallInfo.success) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (toolCallInfo.success) AccentGreen else AccentPink,
                )
                Text(
                    text = if (toolCallInfo.success) "Success" else "Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
            }
            
            // Tool name
            DetailRow(title = "Tool", content = toolCallInfo.toolName)
            
            // Arguments
            CodeBlock(title = "Arguments", code = toolCallInfo.arguments)
            
            // Result
            toolCallInfo.result?.let { result ->
                CodeBlock(title = "Result", code = result)
            }
            
            // Error
            toolCallInfo.error?.let { error ->
                DetailRow(title = "Error", content = error, isError = true)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(title: String, content: String, isError: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) AccentPink else TextPrimary,
        )
    }
}

@Composable
private fun CodeBlock(title: String, code: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryMid, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = AccentCyan,
            )
        }
    }
}

// =============================================================================
// TOOL REGISTRATION & HELPERS
// =============================================================================

private suspend fun registerDemoTools() {
    // Weather Tool
    RunAnywhere.registerTool(
        definition = ToolDefinition(
            name = "get_weather",
            description = "Gets the current weather for a given location using Open-Meteo API",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = ToolParameterType.TOOL_PARAMETER_TYPE_STRING,
                    description = "City name (e.g., 'San Francisco', 'London', 'Tokyo')",
                    required = true
                )
            ),
            category = "Utility"
        ),
        executor = { args ->
            val location = args["location"]?.string ?: "San Francisco"
            fetchWeather(location)
        }
    )

    // Time Tool
    RunAnywhere.registerTool(
        definition = ToolDefinition(
            name = "get_current_time",
            description = "Gets the current date, time, and timezone information",
            parameters = emptyList(),
            category = "Utility"
        ),
        executor = {
            val now = Date()
            val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm:ss a", Locale.getDefault())
            val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val tz = TimeZone.getDefault()

            mapOf(
                "datetime" to ToolValue.string(dateFormatter.format(now)),
                "time" to ToolValue.string(timeFormatter.format(now)),
                "timezone" to ToolValue.string(tz.id),
                "utc_offset" to ToolValue.string(tz.getDisplayName(false, TimeZone.SHORT))
            )
        }
    )

    // Calculator Tool
    RunAnywhere.registerTool(
        definition = ToolDefinition(
            name = "calculate",
            description = "Performs math calculations. Supports +, -, *, /, and parentheses",
            parameters = listOf(
                ToolParameter(
                    name = "expression",
                    type = ToolParameterType.TOOL_PARAMETER_TYPE_STRING,
                    description = "Math expression (e.g., '2 + 2 * 3', '(10 + 5) / 3')",
                    required = true
                )
            ),
            category = "Utility"
        ),
        executor = { args ->
            val expression = args["expression"]?.string ?: "0"
            evaluateMathExpression(expression)
        }
    )
}

private suspend fun fetchWeather(location: String): Map<String, ToolValue> {
    return withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000L) {
                // Geocode the location
                val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(location, "UTF-8")}&count=1"
                val geocodeResponse = fetchUrl(geocodeUrl)
                
                val latMatch = Regex("\"latitude\":\\s*(-?\\d+\\.?\\d*)").find(geocodeResponse)
                val lonMatch = Regex("\"longitude\":\\s*(-?\\d+\\.?\\d*)").find(geocodeResponse)
                val nameMatch = Regex("\"name\":\\s*\"([^\"]+)\"").find(geocodeResponse)
                
                if (latMatch == null || lonMatch == null) {
                    return@withTimeout mapOf(
                        "error" to ToolValue.string("Location not found: $location")
                    )
                }
                
                val lat = latMatch.groupValues[1]
                val lon = lonMatch.groupValues[1]
                val resolvedName = nameMatch?.groupValues?.get(1) ?: location
                
                // Fetch weather
                val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                val weatherResponse = fetchUrl(weatherUrl)
                
                val tempMatch = Regex("\"temperature_2m\":\\s*(-?\\d+\\.?\\d*)").find(weatherResponse)
                val humidityMatch = Regex("\"relative_humidity_2m\":\\s*(\\d+)").find(weatherResponse)
                val windMatch = Regex("\"wind_speed_10m\":\\s*(-?\\d+\\.?\\d*)").find(weatherResponse)
                val codeMatch = Regex("\"weather_code\":\\s*(\\d+)").find(weatherResponse)
                
                val temperature = tempMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val humidity = humidityMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val windSpeed = windMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val weatherCode = codeMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                
                val condition = when (weatherCode) {
                    0 -> "Clear sky"
                    1, 2, 3 -> "Partly cloudy"
                    45, 48 -> "Foggy"
                    51, 53, 55 -> "Drizzle"
                    61, 63, 65 -> "Rain"
                    71, 73, 75 -> "Snow"
                    80, 81, 82 -> "Rain showers"
                    95, 96, 99 -> "Thunderstorm"
                    else -> "Unknown"
                }
                
                mapOf(
                    "location" to ToolValue.string(resolvedName),
                    "temperature_celsius" to ToolValue.double(temperature),
                    "temperature_fahrenheit" to ToolValue.double(temperature * 9/5 + 32),
                    "humidity_percent" to ToolValue.int(humidity),
                    "wind_speed_kmh" to ToolValue.double(windSpeed),
                    "condition" to ToolValue.string(condition)
                )
            }
        } catch (e: Exception) {
            mapOf("error" to ToolValue.string("Weather fetch failed: ${e.message}"))
        }
    }
}

private fun fetchUrl(urlString: String): String {
    val url = URL(urlString)
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10000
    connection.readTimeout = 10000
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun evaluateMathExpression(expression: String): Map<String, ToolValue> {
    return try {
        val cleaned = expression
            .replace("=", "")
            .replace("x", "*")
            .replace("×", "*")
            .replace("÷", "/")
            .trim()
        
        val result = evaluateSimpleExpression(cleaned)
        mapOf(
            "result" to ToolValue.double(result),
            "expression" to ToolValue.string(expression)
        )
    } catch (e: Exception) {
        mapOf("error" to ToolValue.string("Could not evaluate: $expression"))
    }
}

private fun evaluateSimpleExpression(expr: String): Double {
    val tokens = tokenize(expr)
    val parser = TokenParser(tokens)
    return parseExpression(parser)
}

private fun tokenize(expr: String): List<String> {
    val tokens = mutableListOf<String>()
    var current = StringBuilder()
    
    for (char in expr) {
        when {
            char.isDigit() || char == '.' -> current.append(char)
            char in "+-*/()" -> {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current = StringBuilder()
                }
                tokens.add(char.toString())
            }
            char.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current = StringBuilder()
                }
            }
        }
    }
    if (current.isNotEmpty()) tokens.add(current.toString())
    return tokens
}

private class TokenParser(private val tokens: List<String>) {
    private var index = 0
    fun hasNext(): Boolean = index < tokens.size
    fun next(): String = if (hasNext()) tokens[index++] else throw NoSuchElementException()
    fun peek(): String? = if (hasNext()) tokens[index] else null
}

private fun parseExpression(parser: TokenParser): Double {
    var left = parseTerm(parser)
    while (parser.hasNext()) {
        val op = parser.peek() ?: break
        if (op != "+" && op != "-") break
        parser.next()
        val right = parseTerm(parser)
        left = if (op == "+") left + right else left - right
    }
    return left
}

private fun parseTerm(parser: TokenParser): Double {
    var left = parseFactor(parser)
    while (parser.hasNext()) {
        val op = parser.peek() ?: break
        if (op != "*" && op != "/") break
        parser.next()
        val right = parseFactor(parser)
        left = if (op == "*") left * right else left / right
    }
    return left
}

private fun parseFactor(parser: TokenParser): Double {
    if (!parser.hasNext()) return 0.0
    val token = parser.next()
    return when {
        token == "(" -> {
            val result = parseExpression(parser)
            if (parser.hasNext()) parser.next() // consume ')'
            result
        }
        token == "-" -> -parseFactor(parser)
        else -> token.toDoubleOrNull() ?: 0.0
    }
}

private fun formatToolValue(value: ToolValue): String {
    return when {
        value.string_value != null -> "\"${value.string_value}\""
        value.number_value != null -> value.number_value.toString()
        value.bool_value != null -> value.bool_value.toString()
        value.array_value != null -> "[...]"
        value.object_value != null -> "{...}"
        else -> "null"
    }
}

private fun formatToolResult(result: Map<String, ToolValue>): String {
    return result.entries.joinToString("\n") { (key, value) ->
        "$key: ${formatToolValue(value)}"
    }
}
