package com.runanywhere.kotlin_starter_example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.services.ModelOption
import com.runanywhere.kotlin_starter_example.ui.theme.AccentCyan
import com.runanywhere.kotlin_starter_example.ui.theme.PrimaryMid
import com.runanywhere.kotlin_starter_example.ui.theme.TextMuted
import com.runanywhere.kotlin_starter_example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPicker(
    label: String,
    options: List<ModelOption>,
    selectedId: String,
    onSelect: (ModelOption) -> Unit,
    enabled: Boolean = true,
) {
    if (options.size <= 1) return

    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId } ?: options.first()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded },
        ) {
            TextField(
                value = buildString {
                    append(selected.name)
                    if (selected.sizeLabel.isNotBlank()) append(" · ${selected.sizeLabel}")
                },
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                supportingText = {
                    Text(
                        text = selected.backendLabel,
                        color = if (selected.usesNpu) AccentCyan else TextMuted,
                    )
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PrimaryMid,
                    unfocusedContainerColor = PrimaryMid,
                    disabledContainerColor = PrimaryMid.copy(alpha = 0.6f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(text = option.name, color = TextPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = buildString {
                                        append(option.backendLabel)
                                        if (option.sizeLabel.isNotBlank()) {
                                            append(" · ")
                                            append(option.sizeLabel)
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (option.usesNpu) AccentCyan else TextMuted,
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}
