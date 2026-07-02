package com.litert.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litert.server.data.AppSettings

private val BACKEND_OPTIONS = listOf("AUTO", "NPU", "GPU", "CPU")

@Composable
fun SettingsScreen(
    settings: AppSettings,
    modelPath: String,
    activeBackend: String,
    onBackendSelected: (String) -> Unit,
    onPortChanged: (Int) -> Unit,
    onHfTokenChanged: (String) -> Unit,
    onSamplerChanged: (Float, Int) -> Unit,
    onChangeModel: () -> Unit,
    onDeleteModel: () -> Unit
) {
    var temperature by remember { mutableFloatStateOf(settings.temperature) }
    var maxTokens by remember { mutableFloatStateOf(settings.maxTokens.toFloat()) }
    var portText by remember { mutableStateOf(settings.serverPort.toString()) }
    var tokenText by remember { mutableStateOf(settings.hfToken) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val portValid = portText.toIntOrNull()?.let { it in 1024..65535 } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))

        SettingsCard {
            Text("Model", color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(modelPath, color = Color.White, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onChangeModel, colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPrimary)) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Change model")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Text("Backend", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "Active: $activeBackend · applied on next model load",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BACKEND_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = settings.backendPreference == option,
                        onClick = { onBackendSelected(option) },
                        label = { Text(option, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1A3A1A),
                            selectedLabelColor = GreenPrimary
                        )
                    )
                }
            }
            Text(
                "AUTO tries NPU → GPU → CPU. A forced backend fails instead of falling back.",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Text("Server Port", color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    singleLine = true,
                    isError = !portValid,
                    modifier = Modifier.width(120.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GreenPrimary,
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { portText.toIntOrNull()?.let(onPortChanged) },
                    enabled = portValid && portText.toIntOrNull() != settings.serverPort,
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
                ) { Text("Apply") }
            }
            Text(
                if (portValid) "Applying restarts the server (model stays loaded)." else "Port must be 1024–65535.",
                color = if (portValid) Color.Gray else Color(0xFFEF4444),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Text("HuggingFace Token", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                "Fine-grained token with read access. Needed for gated models (Gemma) and higher API limits.",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = tokenText,
                onValueChange = { tokenText = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("hf_…", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onHfTokenChanged(tokenText.trim()) },
                enabled = tokenText.trim() != settings.hfToken,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) { Text(if (settings.hfToken.isBlank()) "Connect" else "Update") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Temperature", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("%.2f".format(temperature), color = GreenPrimary)
            }
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                onValueChangeFinished = { onSamplerChanged(temperature, maxTokens.toInt()) },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = GreenPrimary,
                    activeTrackColor = GreenPrimary,
                    inactiveTrackColor = Color(0xFF333333)
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Max Tokens", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("${maxTokens.toInt()}", color = GreenPrimary)
            }
            Slider(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                onValueChangeFinished = { onSamplerChanged(temperature, maxTokens.toInt()) },
                valueRange = 128f..2048f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = GreenPrimary,
                    activeTrackColor = GreenPrimary,
                    inactiveTrackColor = Color(0xFF333333)
                )
            )
            Text("Sampler changes apply on next model load.", color = Color.Gray, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete This Model")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("LiteRT Server v1.1 · LiteRT-LM SDK 0.13.1", color = Color.Gray, fontSize = 12.sp)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Model?") },
            text = { Text("This deletes the loaded model file and returns to the model list.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteModel()
                    showDeleteDialog = false
                }) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
