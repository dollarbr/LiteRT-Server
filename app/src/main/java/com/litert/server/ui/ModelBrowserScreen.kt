package com.litert.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litert.server.data.DeviceSpecs
import com.litert.server.data.ModelFit
import com.litert.server.hf.HfModel
import com.litert.server.hf.HfSearchParams
import com.litert.server.hf.HfSibling

private val SORT_OPTIONS = listOf(
    "Downloads" to "downloads",
    "Likes" to "likes",
    "Updated" to "lastModified",
    "Created" to "createdAt",
    "Trending" to "trendingScore"
)

private val AUTHOR_SHORTCUTS = listOf("litert-community", "google")

private val LIMIT_OPTIONS = listOf(25, 50, 100)

@Composable
fun ModelBrowserScreen(
    isLoading: Boolean,
    results: List<HfModel>,
    errorMessage: String?,
    hasToken: Boolean,
    deviceSpecs: DeviceSpecs,
    onSearch: (HfSearchParams) -> Unit,
    onDownload: (HfModel) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("downloads") }
    var descending by remember { mutableStateOf(true) }
    var limit by remember { mutableIntStateOf(50) }
    var fitsOnly by remember { mutableStateOf(false) }

    val doSearch = { onSearch(HfSearchParams(query, author, sort, descending, limit)) }

    // Client-side device filter: keep models that fit or whose size is unknown.
    val shownResults =
        if (fitsOnly) results.filter { ModelFit.fitsDevice(it.id, deviceSpecs) != false }
        else results

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("HuggingFace Models", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (!hasToken) {
            Text(
                "No HuggingFace token set — gated models (Gemma) will fail to download. Add a token in Settings.",
                color = Color(0xFFFFB74D),
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search litert-lm models…", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = browserFieldColors()
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = doSearch,
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                placeholder = { Text("Author (user/org)", color = Color.Gray, fontSize = 12.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                modifier = Modifier.width(180.dp),
                colors = browserFieldColors()
            )
            Spacer(modifier = Modifier.width(6.dp))
            AUTHOR_SHORTCUTS.forEach { shortcut ->
                FilterChip(
                    selected = author == shortcut,
                    onClick = {
                        author = if (author == shortcut) "" else shortcut
                        doSearch()
                    },
                    label = { Text(shortcut, fontSize = 11.sp) },
                    colors = browserChipColors(),
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            Text("Sort", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(40.dp))
            SORT_OPTIONS.forEach { (title, value) ->
                FilterChip(
                    selected = sort == value,
                    onClick = { sort = value; doSearch() },
                    label = { Text(title, fontSize = 11.sp) },
                    colors = browserChipColors(),
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            FilterChip(
                selected = false,
                onClick = { descending = !descending; doSearch() },
                label = { Text(if (descending) "↓ Desc" else "↑ Asc", fontSize = 11.sp) },
                colors = browserChipColors(),
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            Text("Show", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(40.dp))
            LIMIT_OPTIONS.forEach { option ->
                FilterChip(
                    selected = limit == option,
                    onClick = { limit = option; doSearch() },
                    label = { Text("$option", fontSize = 11.sp) },
                    colors = browserChipColors(),
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            FilterChip(
                selected = fitsOnly,
                onClick = { fitsOnly = !fitsOnly },
                label = { Text("Fits device", fontSize = 11.sp) },
                colors = browserChipColors(),
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        Text(
            buildString {
                append("Device: ")
                append(deviceSpecs.socModel.ifBlank { "unknown SoC" })
                append(" · %.1f GB RAM".format(deviceSpecs.totalRamGb))
                if (fitsOnly) append(" · hiding models too large to run")
            },
            color = Color.Gray,
            fontSize = 10.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        when {
            isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
            errorMessage != null -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(errorMessage, color = Color(0xFFEF4444), fontSize = 13.sp)
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(shownResults) { model ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.id, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "⬇ ${model.downloads} · ♥ ${model.likes}" +
                                        (if (model.isGated) " · 🔒 gated" else "") +
                                        fitLabel(model.id, deviceSpecs),
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { onDownload(model) }) {
                                Icon(Icons.Default.Download, contentDescription = "Download", tint = GreenPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fitLabel(modelId: String, specs: DeviceSpecs): String {
    val paramsB = ModelFit.paramsBillion(modelId) ?: return ""
    val fits = ModelFit.fitsDevice(modelId, specs)
    val size = if (paramsB < 1.0) "${(paramsB * 1000).toInt()}M" else "%.1fB".format(paramsB)
    return " · $size " + if (fits == true) "✓" else "⚠ may be too large"
}

/**
 * Lets the user pick which .litertlm file of a multi-variant repo to download.
 * The best candidate for this device is pre-marked: an NPU build matching the
 * SoC wins; otherwise the largest generic build that still fits in RAM.
 */
@Composable
fun VariantPickerDialog(
    modelId: String,
    files: List<HfSibling>,
    deviceSpecs: DeviceSpecs,
    onPick: (HfSibling) -> Unit,
    onDismiss: () -> Unit
) {
    val recommended = remember(files) {
        files.firstOrNull { ModelFit.npuMatchesDevice(it.rfilename, deviceSpecs) }
            ?: files
                .filter { ModelFit.npuSoc(it.rfilename) == null }
                .filter { (it.size ?: Long.MAX_VALUE) <= deviceSpecs.totalRamBytes * 0.6 }
                .maxByOrNull { it.size ?: 0 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a file", fontSize = 16.sp) },
        text = {
            Column {
                Text(modelId, color = GreenPrimary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(files) { file ->
                        val soc = ModelFit.npuSoc(file.rfilename)
                        val socMatch = ModelFit.npuMatchesDevice(file.rfilename, deviceSpecs)
                        val tooLarge = (file.size ?: 0) > deviceSpecs.totalRamBytes * 0.6
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { onPick(file) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (file == recommended) Color(0xFF1A3A1A) else SurfaceColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(file.rfilename, color = Color.White, fontSize = 12.sp)
                                val notes = buildList {
                                    file.size?.let { add("%.2f GB".format(it / 1_073_741_824.0)) }
                                    if (file == recommended) add("★ Recommended for this device")
                                    if (soc != null) {
                                        add(
                                            if (socMatch) "NPU build · matches ${deviceSpecs.socModel}"
                                            else "NPU build for $soc — incompatible with " +
                                                deviceSpecs.socModel.ifBlank { "this device" }
                                        )
                                    }
                                    if (tooLarge) add("⚠ may not fit in RAM")
                                }
                                Text(
                                    notes.joinToString(" · "),
                                    color = when {
                                        soc != null && !socMatch -> Color(0xFFEF4444)
                                        file == recommended -> GreenPrimary
                                        tooLarge -> Color(0xFFFFB74D)
                                        else -> Color.Gray
                                    },
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun browserFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GreenPrimary,
    unfocusedBorderColor = Color(0xFF333333),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

@Composable
private fun browserChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Color(0xFF1A3A1A),
    selectedLabelColor = GreenPrimary
)
