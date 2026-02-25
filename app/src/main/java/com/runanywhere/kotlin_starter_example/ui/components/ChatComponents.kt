package com.runanywhere.kotlin_starter_example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.runanywhere.kotlin_starter_example.ui.icons.TablerIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.runanywhere.kotlin_starter_example.ui.theme.AppTheme

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    placeholder: String = "Type a message...",
    accentColor: Color = AppTheme.colors.accent,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val sendScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "send"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(placeholder, color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium)
            },
            readOnly = isGenerating,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainer,
                unfocusedContainerColor = colors.surfaceContainer,
                disabledContainerColor = colors.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = accentColor
            ),
            shape = RoundedCornerShape(12.dp),
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary)
        )

        IconButton(
            onClick = { if (value.isNotBlank() && !isGenerating) onSend() },
            modifier = Modifier
                .size(44.dp)
                .scale(sendScale),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (isGenerating) accentColor.copy(alpha = 0.7f)
                else if (value.isBlank()) colors.surfaceContainer
                else accentColor,
                contentColor = Color.White,
                disabledContainerColor = colors.surfaceContainer,
                disabledContentColor = colors.textTertiary
            ),
            interactionSource = interactionSource
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    TablerIcons.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    text: String,
    isUser: Boolean,
    isStreaming: Boolean = false,
    accentColor: Color = AppTheme.colors.accent,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    if (isUser) {
        // User message: right-aligned bubble
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        accentColor,
                        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    } else {
        // AI message: full-width, ChatGPT style - no bubble, just text
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(end = 32.dp)
        ) {
            if (isStreaming || text.isEmpty()) {
                // During streaming, show plain text for performance
                Text(
                    text = text.ifEmpty { "..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            } else {
                // After streaming completes, render as markdown
                Markdown(
                    content = text,
                    colors = markdownColor(
                        text = colors.textPrimary,
                        codeText = colors.tintCyan,
                        codeBackground = colors.surfaceContainer,
                        dividerColor = colors.border,
                        linkText = colors.accent,
                    ),
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.headlineSmall.copy(color = colors.textPrimary),
                        h2 = MaterialTheme.typography.titleLarge.copy(color = colors.textPrimary),
                        h3 = MaterialTheme.typography.titleMedium.copy(color = colors.textPrimary),
                        text = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                        code = MaterialTheme.typography.bodySmall.copy(color = colors.tintCyan),
                    ),
                )
            }
        }
    }
}

@Composable
fun EmptyChat(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    val colors = AppTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
    }
}
