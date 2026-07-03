package com.litert.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litert.server.BuildConfig
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
    onSamplerChanged: (temperature: Float, topK: Int, topP: Float, maxTokens: Int) -> Unit,
    onChangeModel: () -> Unit,
    onUnloadModel: () -> Unit,
    onDeleteModel: () -> Unit,
    isModelLoaded: Boolean = true,
    onBack: (() -> Unit)? = null
) {
    var temperature by remember { mutableFloatStateOf(settings.temperature) }
    var topK by remember { mutableIntStateOf(settings.topK) }
    var topP by remember { mutableFloatStateOf(settings.topP) }
    var maxTokens by remember { mutableIntStateOf(settings.maxTokens) }
    var portText by remember { mutableStateOf(settings.serverPort.toString()) }
    var tokenText by remember { mutableStateOf(settings.hfToken) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editingParam by remember { mutableStateOf<SamplerParam?>(null) }

    val commitSampler = { onSamplerChanged(temperature, topK, topP, maxTokens) }

    val portValid = portText.toIntOrNull()?.let { it in 1024..65535 } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text("Settings", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (isModelLoaded) {
            SettingsCard {
                Text("Model", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(modelPath, color = Color.White, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onChangeModel, colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenPrimary)) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change model")
                    }
                    OutlinedButton(onClick = onUnloadModel, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D))) {
                        Icon(Icons.Default.Eject, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unload")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Unload stops the engine and server and frees the model's memory.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        SettingsCard {
            Text("Backend", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                if (isModelLoaded) "Active: $activeBackend · applied on next model load"
                else "Applied when a model is loaded",
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
                if (!portValid) "Port must be 1024–65535."
                else if (isModelLoaded) "Applying restarts the server (model stays loaded)."
                else "Used when the server starts.",
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

        SamplerSliderCard(
            title = "Temperature",
            valueText = "%.2f".format(temperature),
            sliderValue = temperature,
            valueRange = 0f..2f,
            onSliderChange = { temperature = it },
            onSliderFinished = commitSampler,
            onValueClick = { editingParam = SamplerParam.TEMPERATURE }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SamplerSliderCard(
            title = "Top K",
            valueText = "$topK",
            sliderValue = topK.toFloat().coerceIn(1f, 128f),
            valueRange = 1f..128f,
            onSliderChange = { topK = it.toInt() },
            onSliderFinished = commitSampler,
            onValueClick = { editingParam = SamplerParam.TOP_K }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SamplerSliderCard(
            title = "Top P",
            valueText = "%.2f".format(topP),
            sliderValue = topP,
            valueRange = 0f..1f,
            onSliderChange = { topP = it },
            onSliderFinished = commitSampler,
            onValueClick = { editingParam = SamplerParam.TOP_P }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SamplerSliderCard(
            title = "Max Tokens",
            valueText = "$maxTokens",
            sliderValue = maxTokens.toFloat().coerceIn(128f, 8192f),
            valueRange = 128f..8192f,
            onSliderChange = { maxTokens = it.toInt() },
            onSliderFinished = commitSampler,
            onValueClick = { editingParam = SamplerParam.MAX_TOKENS }
        ) {
            if (maxTokens > MAX_TOKENS_SAFE_LIMIT) {
                Text(
                    "⚠ Above $MAX_TOKENS_SAFE_LIMIT the app may freeze or crash due to memory usage.",
                    color = Color(0xFFFFB74D),
                    fontSize = 11.sp
                )
            }
            Text(
                "Tap any green value to type a custom one. Sampler changes apply on next model load.",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isModelLoaded) OutlinedButton(
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
        Text("LiteRT Server v${BuildConfig.VERSION_NAME} · LiteRT-LM SDK 0.13.1", color = Color.Gray, fontSize = 12.sp)
    }

    editingParam?.let { param ->
        val close = { editingParam = null }
        when (param) {
            SamplerParam.TEMPERATURE -> EditValueDialog(
                title = "Temperature",
                initial = "%.2f".format(temperature),
                keyboardType = KeyboardType.Decimal,
                validate = { it.replace(',', '.').toFloatOrNull()?.let { v -> v in 0f..2f } == true },
                hint = "0.00 – 2.00",
                onConfirm = { text ->
                    temperature = text.replace(',', '.').toFloat()
                    commitSampler(); close()
                },
                onDismiss = close
            )
            SamplerParam.TOP_K -> EditValueDialog(
                title = "Top K",
                initial = "$topK",
                keyboardType = KeyboardType.Number,
                validate = { it.toIntOrNull()?.let { v -> v >= 1 } == true },
                hint = "Any integer ≥ 1",
                onConfirm = { text -> topK = text.toInt(); commitSampler(); close() },
                onDismiss = close
            )
            SamplerParam.TOP_P -> EditValueDialog(
                title = "Top P",
                initial = "%.2f".format(topP),
                keyboardType = KeyboardType.Decimal,
                validate = { it.replace(',', '.').toFloatOrNull()?.let { v -> v in 0f..1f } == true },
                hint = "0.00 – 1.00",
                onConfirm = { text ->
                    topP = text.replace(',', '.').toFloat()
                    commitSampler(); close()
                },
                onDismiss = close
            )
            SamplerParam.MAX_TOKENS -> EditValueDialog(
                title = "Max Tokens",
                initial = "$maxTokens",
                keyboardType = KeyboardType.Number,
                validate = { it.toIntOrNull()?.let { v -> v >= 1 } == true },
                hint = "Any integer ≥ 1",
                warningFor = { text ->
                    text.toIntOrNull()?.takeIf { it > MAX_TOKENS_SAFE_LIMIT }?.let {
                        "Above $MAX_TOKENS_SAFE_LIMIT the app may freeze or crash due to memory usage."
                    }
                },
                onConfirm = { text -> maxTokens = text.toInt(); commitSampler(); close() },
                onDismiss = close
            )
        }
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

const val MAX_TOKENS_SAFE_LIMIT = 8192

enum class SamplerParam { TEMPERATURE, TOP_K, TOP_P, MAX_TOKENS }

@Composable
private fun SamplerSliderCard(
    title: String,
    valueText: String,
    sliderValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onValueClick: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    SettingsCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(
                valueText,
                color = GreenPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onValueClick)
                    .padding(horizontal = 8.dp)
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onSliderChange,
            onValueChangeFinished = onSliderFinished,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = GreenPrimary,
                activeTrackColor = GreenPrimary,
                inactiveTrackColor = Color(0xFF333333)
            )
        )
        footer?.invoke(this)
    }
}

@Composable
private fun EditValueDialog(
    title: String,
    initial: String,
    keyboardType: KeyboardType,
    validate: (String) -> Boolean,
    hint: String,
    warningFor: (String) -> String? = { null },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    val valid = validate(text)
    val warning = if (valid) warningFor(text) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    supportingText = { Text(if (valid) hint else "Invalid value — $hint") }
                )
                if (warning != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(warning, color = Color(0xFFFFB74D), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(text.trim()) }) {
                Text("Apply", color = GreenPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
