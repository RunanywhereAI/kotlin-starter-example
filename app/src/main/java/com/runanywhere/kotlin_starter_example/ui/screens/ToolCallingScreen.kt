package com.runanywhere.kotlin_starter_example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.ui.components.AppScaffold
import com.runanywhere.kotlin_starter_example.ui.components.ChatInputBar
import com.runanywhere.kotlin_starter_example.ui.components.MessageBubble
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme
import com.runanywhere.sdk.public.extensions.LLM.RunAnywhereToolCalling
import com.runanywhere.sdk.public.extensions.LLM.ToolCallingOptions
import com.runanywhere.sdk.public.extensions.LLM.ToolDefinition
import com.runanywhere.sdk.public.extensions.LLM.ToolParameter
import com.runanywhere.sdk.public.extensions.LLM.ToolParameterType
import com.runanywhere.sdk.public.extensions.LLM.ToolValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ToolCallInfo(
    val toolName: String,
    val arguments: String,
    val result: String? = null,
    val error: String? = null,
    val success: Boolean = true
)

data class ToolChatMessage(
    val text: String,
    val isUser: Boolean,
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ToolCallingScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var messages by remember { mutableStateOf(listOf<ToolChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var toolsRegistered by remember { mutableStateOf(false) }
    var selectedToolCall by remember { mutableStateOf<ToolCallInfo?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val suggestionPrompts = remember {
        listOf(
            "What's the weather in Tokyo?",
            "How's the weather in London?",
            "Is it raining in New York?",
            "What time is it?",
            "What's the current date and time?",
            "Calculate 15 * 7 + 23",
            "What is (100 - 37) / 9?",
            "Compute 2.5 * 4 + 10",
        )
    }

    val sendMessage: (String) -> Unit = { text ->
        if (text.isNotBlank() && !isGenerating) {
            messages = messages + ToolChatMessage(text, isUser = true)
            inputText = ""

            scope.launch {
                isGenerating = true
                listState.animateScrollToItem(messages.size)

                try {
                    val result = RunAnywhereToolCalling.generateWithTools(
                        prompt = text,
                        options = ToolCallingOptions(
                            maxToolCalls = 3,
                            autoExecute = true,
                            temperature = 0.7f,
                            maxTokens = 512
                        )
                    )

                    val toolCallInfos = result.toolCalls.mapIndexed { index, call ->
                        val toolResult = result.toolResults.getOrNull(index)
                        ToolCallInfo(
                            toolName = call.toolName,
                            arguments = call.arguments.entries.joinToString(", ") {
                                "${it.key}: ${formatToolValue(it.value)}"
                            },
                            result = toolResult?.result?.let { formatToolResult(it) },
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

    LaunchedEffect(Unit) {
        if (!toolsRegistered) {
            registerDemoTools()
            toolsRegistered = true
        }
    }

    AppScaffold(
        title = "Tool Calling",
        subtitle = "LLM + Function Execution",
        onBack = onNavigateBack,
        bottomBar = {
            if (modelService.isLLMLoaded) {
                Column {
                    // Suggestion chips
                    if (!isGenerating && messages.isNotEmpty()) {
                        ToolSuggestionChipsRow(
                            suggestions = suggestionPrompts,
                            onSuggestionClick = sendMessage
                        )
                    }

                    ChatInputBar(
                        value = inputText,
                        onValueChange = { inputText = it },
                        onSend = { sendMessage(inputText) },
                        isGenerating = isGenerating,
                        placeholder = "Try: What's the weather in Tokyo?",
                        accentColor = colors.tintOrange
                    )
                }
            }
        }
    ) {
        // Model loader
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
                        color = colors.error,
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
    val colors = AppTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colors.tintOrange.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(0.5.dp, colors.tintOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = TablerIcons.Tool,
            contentDescription = null,
            tint = colors.tintOrange,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "3 Tools Available",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary
            )
            Text(
                text = "Weather · Time · Calculator",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun ToolCallingEmptyState(
    suggestions: List<String>,
    enabled: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = TablerIcons.Tool,
            contentDescription = null,
            tint = colors.tintOrange,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tool Calling Demo",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tap a suggestion or type your own prompt",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))

        ToolSuggestionCategory(
            icon = TablerIcons.Cloud, label = "Weather", color = colors.tintCyan,
            prompts = suggestions.filter { it.lowercase().contains("weather") || it.lowercase().contains("rain") },
            enabled = enabled, onSuggestionClick = onSuggestionClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        ToolSuggestionCategory(
            icon = TablerIcons.Clock, label = "Time", color = colors.tintPurple,
            prompts = suggestions.filter { it.lowercase().contains("time") || it.lowercase().contains("date") },
            enabled = enabled, onSuggestionClick = onSuggestionClick
        )
        Spacer(modifier = Modifier.height(12.dp))
        ToolSuggestionCategory(
            icon = TablerIcons.Calculator, label = "Calculator", color = colors.tintGreen,
            prompts = suggestions.filter { it.lowercase().contains("calc") || it.lowercase().contains("compute") || it.lowercase().contains("what is") },
            enabled = enabled, onSuggestionClick = onSuggestionClick
        )
    }
}

@Composable
private fun ToolSuggestionCategory(
    icon: ImageVector,
    label: String,
    color: Color,
    prompts: List<String>,
    enabled: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    val colors = AppTheme.colors

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            prompts.forEach { prompt ->
                ToolSuggestionChip(text = prompt, accentColor = color, enabled = enabled, onClick = { onSuggestionClick(prompt) })
            }
        }
    }
}

@Composable
private fun ToolSuggestionChip(text: String, accentColor: Color, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { onClick() },
        color = colors.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .border(0.5.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ToolSuggestionChipsRow(suggestions: List<String>, onSuggestionClick: (String) -> Unit) {
    val colors = AppTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { prompt ->
            ToolSuggestionChip(text = prompt, accentColor = colors.tintOrange, enabled = true, onClick = { onSuggestionClick(prompt) })
        }
    }
}

@Composable
private fun ToolChatMessageBubble(
    message: ToolChatMessage,
    onToolCallClick: (ToolCallInfo) -> Unit
) {
    val colors = AppTheme.colors

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
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(colors.tintOrange.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(TablerIcons.Robot, null, tint = colors.tintOrange, modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Card(
                modifier = Modifier.widthIn(max = 290.dp),
                shape = RoundedCornerShape(
                    topStart = if (message.isUser) 14.dp else 4.dp,
                    topEnd = if (message.isUser) 4.dp else 14.dp,
                    bottomStart = 14.dp,
                    bottomEnd = 14.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isUser) colors.tintOrange else colors.surfaceElevated
                ),
                border = if (message.isUser) null else androidx.compose.foundation.BorderStroke(0.5.dp, colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else colors.textPrimary
                )
            }

            if (message.isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(colors.tintPurple.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(TablerIcons.User, null, tint = colors.tintPurple, modifier = Modifier.size(14.dp))
                }
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
    val colors = AppTheme.colors
    val bgColor = if (toolCallInfo.success) colors.success.copy(alpha = 0.08f) else colors.tintPink.copy(alpha = 0.08f)
    val borderColor = if (toolCallInfo.success) colors.success.copy(alpha = 0.2f) else colors.tintPink.copy(alpha = 0.2f)
    val iconTint = if (toolCallInfo.success) colors.success else colors.tintPink

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onTap() },
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = if (toolCallInfo.success) TablerIcons.CircleCheck else TablerIcons.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = iconTint,
            )
            Text(
                text = toolCallInfo.toolName,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
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
    val colors = AppTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Tool Call Details",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (toolCallInfo.success) colors.success.copy(alpha = 0.08f) else colors.tintPink.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (toolCallInfo.success) TablerIcons.CircleCheck else TablerIcons.CircleX,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (toolCallInfo.success) colors.success else colors.tintPink,
                )
                Text(
                    text = if (toolCallInfo.success) "Success" else "Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }

            ToolDetailRow(title = "Tool", content = toolCallInfo.toolName)
            ToolCodeBlock(title = "Arguments", code = toolCallInfo.arguments)
            toolCallInfo.result?.let { result -> ToolCodeBlock(title = "Result", code = result) }
            toolCallInfo.error?.let { error -> ToolDetailRow(title = "Error", content = error, isError = true) }
        }
    }
}

@Composable
private fun ToolDetailRow(title: String, content: String, isError: Boolean = false) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Text(text = content, style = MaterialTheme.typography.bodyMedium, color = if (isError) colors.tintPink else colors.textPrimary)
    }
}

@Composable
private fun ToolCodeBlock(title: String, code: String) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainer, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.tintBlue,
            )
        }
    }
}

// =============================================================================
// TOOL REGISTRATION & HELPERS
// =============================================================================

private suspend fun registerDemoTools() {
    RunAnywhereToolCalling.registerTool(
        definition = ToolDefinition(
            name = "get_weather",
            description = "Gets the current weather for a given location using Open-Meteo API",
            parameters = listOf(
                ToolParameter(
                    name = "location",
                    type = ToolParameterType.STRING,
                    description = "City name (e.g., 'San Francisco', 'London', 'Tokyo')",
                    required = true
                )
            ),
            category = "Utility"
        ),
        executor = { args ->
            val location = args["location"]?.stringValue ?: "San Francisco"
            fetchWeather(location)
        }
    )

    RunAnywhereToolCalling.registerTool(
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

    RunAnywhereToolCalling.registerTool(
        definition = ToolDefinition(
            name = "calculate",
            description = "Performs math calculations. Supports +, -, *, /, and parentheses",
            parameters = listOf(
                ToolParameter(
                    name = "expression",
                    type = ToolParameterType.STRING,
                    description = "Math expression (e.g., '2 + 2 * 3', '(10 + 5) / 3')",
                    required = true
                )
            ),
            category = "Utility"
        ),
        executor = { args ->
            val expression = args["expression"]?.stringValue ?: "0"
            evaluateMathExpression(expression)
        }
    )
}

private suspend fun fetchWeather(location: String): Map<String, ToolValue> {
    return withContext(Dispatchers.IO) {
        try {
            withTimeout(15_000L) {
                val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(location, "UTF-8")}&count=1"
                val geocodeResponse = fetchUrl(geocodeUrl)

                val latMatch = Regex("\"latitude\":\\s*(-?\\d+\\.?\\d*)").find(geocodeResponse)
                val lonMatch = Regex("\"longitude\":\\s*(-?\\d+\\.?\\d*)").find(geocodeResponse)
                val nameMatch = Regex("\"name\":\\s*\"([^\"]+)\"").find(geocodeResponse)

                if (latMatch == null || lonMatch == null) {
                    return@withTimeout mapOf("error" to ToolValue.string("Location not found: $location"))
                }

                val lat = latMatch.groupValues[1]
                val lon = lonMatch.groupValues[1]
                val resolvedName = nameMatch?.groupValues?.get(1) ?: location

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
                    "temperature_celsius" to ToolValue.number(temperature),
                    "temperature_fahrenheit" to ToolValue.number(temperature * 9/5 + 32),
                    "humidity_percent" to ToolValue.number(humidity),
                    "wind_speed_kmh" to ToolValue.number(windSpeed),
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
            .replace("\u00d7", "*")
            .replace("\u00f7", "/")
            .trim()

        val result = evaluateSimpleExpression(cleaned)
        mapOf(
            "result" to ToolValue.number(result),
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
            if (parser.hasNext()) parser.next()
            result
        }
        token == "-" -> -parseFactor(parser)
        else -> token.toDoubleOrNull() ?: 0.0
    }
}

private fun formatToolValue(value: ToolValue): String {
    return when (value) {
        is ToolValue.StringValue -> "\"${value.value}\""
        is ToolValue.NumberValue -> value.value.toString()
        is ToolValue.BoolValue -> value.value.toString()
        is ToolValue.NullValue -> "null"
        is ToolValue.ArrayValue -> "[...]"
        is ToolValue.ObjectValue -> "{...}"
    }
}

private fun formatToolResult(result: Map<String, ToolValue>): String {
    return result.entries.joinToString("\n") { (key, value) ->
        "$key: ${formatToolValue(value)}"
    }
}
