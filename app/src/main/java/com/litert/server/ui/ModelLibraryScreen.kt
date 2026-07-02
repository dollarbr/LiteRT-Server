package com.litert.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litert.server.download.LocalModel

@Composable
fun ModelLibraryScreen(
    models: List<LocalModel>,
    lastModelPath: String,
    onSelect: (LocalModel) -> Unit,
    onBrowseHuggingFace: () -> Unit,
    onImportFile: () -> Unit,
    onDelete: (LocalModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("LiteRT Server", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Select a model to load",
            color = Color.Gray,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (models.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No models installed yet.\nSearch HuggingFace or import a .litertlm file.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(models) { model ->
                    val isLast = model.path == lastModelPath
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(
                                width = if (isLast) 1.5.dp else 1.dp,
                                color = if (isLast) GreenPrimary else Color(0xFF333333),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .background(
                                color = if (isLast) Color(0xFF0D2D0D) else Color(0xFF111111),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(model) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                model.name,
                                color = if (isLast) GreenPrimary else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${"%.2f".format(model.sizeGb)} GB" + if (isLast) " · last used" else "",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { onDelete(model) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF666666), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onBrowseHuggingFace,
            colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Search HuggingFace", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onImportFile,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import .litertlm file", fontSize = 14.sp)
        }
    }
}
