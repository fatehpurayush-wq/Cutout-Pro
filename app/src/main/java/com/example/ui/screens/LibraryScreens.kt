package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DownloadEntity
import com.example.data.local.ProjectEntity
import com.example.data.model.EditMode
import com.example.data.model.ExportOption
import com.example.ui.MainViewModel
import com.example.ui.components.formatBytesHinglish
import com.example.ui.components.formatDateHinglish
import com.example.ui.theme.CoralSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.VioletTertiary

@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    vm: MainViewModel,
    onOpenProject: (ProjectEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var renamingProj by remember { mutableStateOf<ProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var switchingProj by remember { mutableStateOf<ProjectEntity?>(null) }

    val filtered = remember(projects, query) {
        if (query.isBlank()) projects else projects.filter { it.name.contains(query, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Saved Projects", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Project dhundhein...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface)
                ) {
                    Text(
                        "Koi saved project nahi mila. Photo edit karte hi project automatic save ho jaata hai.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { p ->
                val thumb = remember(p.thumbPath) { BitmapFactory.decodeFile(p.thumbPath) }
                val modeEnum = runCatching { EditMode.valueOf(p.mode) }.getOrDefault(EditMode.BG_REMOVE)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
                        .clickable { onOpenProject(p) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF10131C))
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = p.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CyanPrimary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    modeEnum.labelHinglish,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPrimary
                                )
                            }
                            Text(
                                "${p.width}x${p.height} • ${formatDateHinglish(p.updatedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column {
                            Row {
                                IconButton(onClick = { switchingProj = p }) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = "Mode badlo", tint = VioletTertiary)
                                }
                                IconButton(onClick = {
                                    renamingProj = p
                                    renameText = p.name
                                }) {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename", tint = CyanPrimary)
                                }
                            }
                            Row {
                                IconButton(onClick = { vm.duplicateProject(p.id) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color.White)
                                }
                                IconButton(onClick = { vm.deleteProject(p.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renamingProj?.let { proj ->
        AlertDialog(
            onDismissRequest = { renamingProj = null },
            containerColor = StudioSurfaceElevated,
            title = { Text("Project ka naam badlo") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    vm.renameProject(proj.id, renameText)
                    renamingProj = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingProj = null }) { Text("Cancel") }
            }
        )
    }

    switchingProj?.let { proj ->
        AlertDialog(
            onDismissRequest = { switchingProj = null },
            containerColor = StudioSurfaceElevated,
            title = { Text("Mode badlo (bina photo dobara chune)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditMode.entries.forEach { m ->
                        Button(
                            onClick = {
                                vm.switchProjectModeFromList(proj.id, m)
                                switchingProj = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(m.labelHinglish) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { switchingProj = null }) { Text("Band karo") }
            }
        )
    }
}

@Composable
fun DownloadsHistoryScreen(
    downloads: List<DownloadEntity>,
    vm: MainViewModel,
    onReExportToExportScreen: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filterOption by remember { mutableStateOf<ExportOption?>(null) }
    var renamingItem by remember { mutableStateOf<DownloadEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filtered = remember(downloads, query, filterOption) {
        downloads.filter { d ->
            val matchQ = query.isBlank() || d.fileName.contains(query, ignoreCase = true)
            val matchOpt = filterOption == null || d.optionType == filterOption?.name
            matchQ && matchOpt
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Meri downloads", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("File dhundhein...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filterOption == null, onClick = { filterOption = null }, label = { Text("Sabhi") })
                FilterChip(selected = filterOption == ExportOption.BG_REMOVE, onClick = { filterOption = ExportOption.BG_REMOVE }, label = { Text("Background hatao") })
                FilterChip(selected = filterOption == ExportOption.OBJECT_REMOVE, onClick = { filterOption = ExportOption.OBJECT_REMOVE }, label = { Text("Subject hatao") })
                FilterChip(selected = filterOption == ExportOption.BOTH, onClick = { filterOption = ExportOption.BOTH }, label = { Text("Dono") })
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface)
                ) {
                    Text(
                        "Abhi tak koi download nahi hai. Export screen se save karne par yahan dikhega.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                val thumb = remember(item.thumbPath) { BitmapFactory.decodeFile(item.thumbPath) }
                val opt = runCatching { ExportOption.valueOf(item.optionType) }.getOrDefault(ExportOption.BG_REMOVE)
                val mime = when (item.format.uppercase()) {
                    "PNG" -> "image/png"
                    "JPG" -> "image/jpeg"
                    "ZIP", "STICKER PACK" -> "application/zip"
                    "PDF" -> "application/pdf"
                    else -> "image/webp"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF10131C))
                            ) {
                                if (thumb != null) {
                                    Image(bitmap = thumb.asImageBitmap(), contentDescription = item.fileName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${opt.titleHinglish.removeSuffix(" download")} • ${item.format} • ${formatBytesHinglish(item.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyanPrimary
                                )
                                Text(formatDateHinglish(item.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = { openUriExternal(context, item.uriString, mime) }) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = CyanPrimary)
                            }
                            IconButton(onClick = { shareUriExternal(context, item.uriString, mime, null) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = CoralSecondary)
                            }
                            IconButton(
                                onClick = { vm.reExportDownload(item, onReExportToExportScreen) },
                                enabled = item.projectId != null
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Re-export", tint = VioletTertiary)
                            }
                            IconButton(onClick = {
                                renamingItem = item
                                renameText = item.fileName
                            }) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename")
                            }
                            IconButton(onClick = { vm.deleteDownload(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }

    renamingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            containerColor = StudioSurfaceElevated,
            title = { Text("File rename karein") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    vm.renameDownload(item, renameText)
                    renamingItem = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingItem = null }) { Text("Cancel") }
            }
        )
    }
}
