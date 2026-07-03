package com.litert.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.litert.server.hf.HfModel

private val SORT_OPTIONS = listOf(
    "Downloads" to "downloads",
    "Likes" to "likes",
    "Updated" to "lastModified"
)

private val AUTHOR_OPTIONS = listOf(
    "All" to "",
    "litert-community" to "litert-community",
    "google" to "google"
)

@Composable
fun ModelBrowserScreen(
    isLoading: Boolean,
    results: List<HfModel>,
    errorMessage: String?,
    hasToken: Boolean,
    onSearch: (query: String, author: String, sort: String) -> Unit,
    onDownload: (HfModel) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("downloads") }

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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = Color(0xFF333333),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onSearch(query, author, sort) },
                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilterChipRow(
            label = "Sort",
            options = SORT_OPTIONS,
            selected = sort,
            onSelect = { sort = it; onSearch(query, author, sort) }
        )
        Spacer(modifier = Modifier.height(4.dp))
        FilterChipRow(
            label = "Author",
            options = AUTHOR_OPTIONS,
            selected = author,
            onSelect = { author = it; onSearch(query, author, sort) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenPrimary)
            }
            errorMessage != null -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(errorMessage, color = Color(0xFFEF4444), fontSize = 13.sp)
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                items(results) { model ->
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
                                    "⬇ ${model.downloads} · ♥ ${model.likes}",
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

@Composable
private fun FilterChipRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.width(48.dp))
        options.forEach { (title, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(title, fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF1A3A1A),
                    selectedLabelColor = GreenPrimary
                ),
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}
