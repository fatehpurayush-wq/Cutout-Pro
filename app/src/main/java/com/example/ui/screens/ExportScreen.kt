package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ExportFormat
import com.example.data.model.ExportOption
import com.example.data.model.ExportSizePreset
import com.example.ui.ExportUiState
import com.example.ui.MainViewModel
import com.example.ui.components.CheckerboardBackground
import com.example.ui.components.formatBytesHinglish
import com.example.ui.theme.CoralSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletTertiary
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    vm: MainViewModel,
    onBack: () -> Unit,
    onPickCustomFolder: () -> Unit,
    onDoneHome: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var showFullPreview by remember { mutableStateOf(false) }
    var showPassportChoice by remember { mutableStateOf(false) }
    var customWText by remember { mutableStateOf(state.config.customWidth.toString()) }
    var customHText by remember { mutableStateOf(state.config.customHeight.toString()) }

    val result = state.resultInfo

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("Export & Download", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wapas")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBackground)
            )
        }
    ) { innerPadding ->
        if (result != null) {
            // Export Success Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(42.dp))
                }
                Text("Save ho gaya!", style = MaterialTheme.typography.headlineMedium, color = SuccessGreen)
                Text(
                    "Total size: ${formatBytesHinglish(result.totalSizeBytes)}",
                    style = MaterialTheme.typography.titleMedium
                )
                result.noteHinglish?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        result.savedFiles.forEach { f ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(f.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                    Text(formatBytesHinglish(f.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(onClick = { openUriExternal(context, f.uriString, f.mimeType) }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = CyanPrimary)
                                    }
                                    IconButton(onClick = { shareUriExternal(context, f.uriString, f.mimeType, null) }) {
                                        Icon(Icons.Default.Share, contentDescription = "Share", tint = CoralSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                val primaryFile = result.savedFiles.firstOrNull()
                if (primaryFile != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { shareUriExternal(context, primaryFile.uriString, primaryFile.mimeType, "com.whatsapp") },
                            modifier = Modifier.weight(1f)
                        ) { Text("WhatsApp Share") }
                        OutlinedButton(
                            onClick = { shareUriExternal(context, primaryFile.uriString, primaryFile.mimeType, "com.instagram.android") },
                            modifier = Modifier.weight(1f)
                        ) { Text("Instagram Share") }
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { vm.clearExportResult() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("export_more_button")
                    ) { Text("Aur download karo") }
                    Button(
                        onClick = {
                            vm.clearExportResult()
                            onDoneHome()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("export_done_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                    ) { Text("Done", fontWeight = FontWeight.Bold) }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.errorHinglish?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.16f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(err, modifier = Modifier.padding(14.dp), color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Text("Download Option Chuno", style = MaterialTheme.typography.titleMedium, color = CyanPrimary)
            }

            // OPTION 1: Background hatao download
            item {
                ExportOptionCard(
                    option = ExportOption.BG_REMOVE,
                    thumb = state.cutoutThumb,
                    accent = CyanPrimary,
                    testTag = "export_option_bg_remove",
                    onPreview = { vm.generatePreviewForOption(ExportOption.BG_REMOVE) { showFullPreview = true } },
                    onDownload = { vm.triggerExportOption(ExportOption.BG_REMOVE) }
                )
            }

            // OPTION 2: Subject hatao download
            item {
                ExportOptionCard(
                    option = ExportOption.OBJECT_REMOVE,
                    thumb = state.cleanBgThumb,
                    accent = CoralSecondary,
                    testTag = "export_option_subject_remove",
                    onPreview = { vm.generatePreviewForOption(ExportOption.OBJECT_REMOVE) { showFullPreview = true } },
                    onDownload = { vm.triggerExportOption(ExportOption.OBJECT_REMOVE) }
                )
            }

            // OPTION 3: Dono download
            item {
                ExportOptionCard(
                    option = ExportOption.BOTH,
                    thumb = state.combinedThumb ?: state.cutoutThumb,
                    accent = VioletTertiary,
                    testTag = "export_option_both",
                    onPreview = { vm.generatePreviewForOption(ExportOption.BOTH) { showFullPreview = true } },
                    onDownload = { vm.triggerExportOption(ExportOption.BOTH) }
                )
            }

            // Shared Export Settings
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Shared Settings", style = MaterialTheme.typography.titleMedium)

                        // Format
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExportFormat.entries.forEach { fmt ->
                                FilterChip(
                                    selected = state.config.format == fmt,
                                    onClick = { vm.updateExportConfig { it.copy(format = fmt) } },
                                    label = { Text(fmt.label) }
                                )
                            }
                        }
                        if (state.config.format == ExportFormat.JPG) {
                            Text(
                                "Note: JPG mein transparency nahi hoti — Cutout automatically PNG/WEBP mein save hoga, ya JPG background fill chunein.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber
                            )
                            Text("JPG Background Fill Color:", style = MaterialTheme.typography.labelMedium)
                            ColorDotsRow(selectedArgb = state.config.jpgFillColorArgb) { col ->
                                vm.updateExportConfig { it.copy(jpgFillColorArgb = col) }
                            }
                        }

                        // Quality
                        CompactSliderRow("Quality (${state.config.quality}%)", state.config.quality.toFloat(), 40f..100f) { q ->
                            vm.updateExportConfig { it.copy(quality = q.toInt()) }
                        }

                        // Size choice
                        Text("Size / Resolution:", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExportSizePreset.entries.forEach { sz ->
                                FilterChip(
                                    selected = state.config.sizePreset == sz,
                                    onClick = { vm.updateExportConfig { it.copy(sizePreset = sz) } },
                                    label = { Text(sz.labelHinglish) }
                                )
                            }
                        }
                        if (state.config.sizePreset == ExportSizePreset.CUSTOM) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customWText,
                                    onValueChange = {
                                        customWText = it.filter(Char::isDigit)
                                        customWText.toIntOrNull()?.let { w -> vm.updateExportConfig { cfg -> cfg.copy(customWidth = w) } }
                                    },
                                    label = { Text("Width") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customHText,
                                    onValueChange = {
                                        customHText = it.filter(Char::isDigit)
                                        customHText.toIntOrNull()?.let { h -> vm.updateExportConfig { cfg -> cfg.copy(customHeight = h) } }
                                    },
                                    label = { Text("Height") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Trim transparent edges", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = state.config.trimTransparent,
                                onCheckedChange = { tr -> vm.updateExportConfig { it.copy(trimTransparent = tr) } }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Dono ko ek ZIP file mein save karo", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = state.config.saveAsZipForBoth,
                                onCheckedChange = { z -> vm.updateExportConfig { it.copy(saveAsZipForBoth = z) } }
                            )
                        }

                        OutlinedTextField(
                            value = state.config.fileName,
                            onValueChange = { name -> vm.updateExportConfig { it.copy(fileName = name) } },
                            label = { Text("File ka naam (smart suffix automatic judega)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Save in:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    if (state.config.useCustomFolder && !state.config.customFolderUri.isNullOrBlank())
                                        "Custom Folder chuna hua hai"
                                    else "Gallery -> Pictures/Cutout Pro",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyanPrimary
                                )
                            }
                            OutlinedButton(onClick = onPickCustomFolder) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Folder Chuno")
                            }
                        }
                    }
                }
            }

            // Extra Download Shortcuts
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StudioBorder, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Extra Download Shortcuts", style = MaterialTheme.typography.titleMedium, color = CoralSecondary)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(onClick = { vm.exportWhatsAppStickerShortcut() }) {
                                Icon(Icons.Default.StickyNote2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("WhatsApp Sticker (512x512)")
                            }
                            OutlinedButton(onClick = { vm.addCurrentToStickerPack() }) {
                                Text("Sticker Pack mein Jodo (${state.stickerPackItems.size})")
                            }
                            if (state.stickerPackItems.isNotEmpty()) {
                                Button(onClick = { vm.exportStickerPack() }) {
                                    Text("Pack Export (${state.stickerPackItems.size})")
                                }
                            }
                            OutlinedButton(onClick = { showPassportChoice = true }) {
                                Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Passport Sheet")
                            }
                            OutlinedButton(onClick = { vm.exportPrintReadyA4Shortcut() }) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Print Ready A4")
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Screen Zoomable Preview Dialog ("Pehle preview dekho")
    if (showFullPreview && state.fullPreviewBitmap != null) {
        FullPreviewDialog(
            bitmap = state.fullPreviewBitmap,
            onDismiss = { showFullPreview = false }
        )
    }

    // File Collision Dialog
    state.collisionFileName?.let { existing ->
        AlertDialog(
            onDismissRequest = { vm.clearExportResult() },
            containerColor = StudioSurfaceElevated,
            title = { Text("File pehle se maujood hai") },
            text = { Text("'$existing' naam ki file pehle se hai. Purani replace karein ya naya number lagayein?") },
            confirmButton = {
                Button(onClick = {
                    state.pendingCollisionOption?.let { vm.triggerExportOption(it, autoNumberOnConflict = true) }
                }) { Text("Number lagao") }
            },
            dismissButton = {
                TextButton(onClick = {
                    state.pendingCollisionOption?.let { vm.triggerExportOption(it, overwriteExisting = true) }
                }) { Text("Replace karo") }
            }
        )
    }

    // Passport Sheet JPG/PDF Choice
    if (showPassportChoice) {
        AlertDialog(
            onDismissRequest = { showPassportChoice = false },
            containerColor = StudioSurfaceElevated,
            title = { Text("Passport Size Sheet (8 Copies)") },
            text = { Text("A4 page par 8 passport photos kis format mein save karni hain?") },
            confirmButton = {
                Button(onClick = {
                    showPassportChoice = false
                    vm.exportPassportSheetShortcut(asPdf = true)
                }) { Text("PDF Save karo") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showPassportChoice = false
                    vm.exportPassportSheetShortcut(asPdf = false)
                }) { Text("JPG Save karo") }
            }
        )
    }
}

@Composable
private fun ExportOptionCard(
    option: ExportOption,
    thumb: Bitmap?,
    accent: Color,
    testTag: String,
    onPreview: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                CheckerboardBackground(modifier = Modifier.fillMaxSize(), cellDp = 8f)
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = option.titleHinglish,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(option.titleHinglish, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(option.subtitleHinglish, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPreview,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pehle preview dekho", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Button(
                onClick = onDownload,
                modifier = Modifier
                    .height(50.dp)
                    .testTag(testTag),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF0A0C12))
            ) {
                Icon(Icons.Default.Download, contentDescription = option.titleHinglish, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun FullPreviewDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            CheckerboardBackground(modifier = Modifier.fillMaxSize())
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Full screen export preview",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offX
                        translationY = offY
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1.05f) {
                                offX += pan.x; offY += pan.y
                            } else {
                                offX = 0f; offY = 0f
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close preview", tint = Color.White)
            }
        }
    }
}

fun openUriExternal(context: Context, uriString: String, mimeType: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uriString), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "File kholo"))
    }
}

fun shareUriExternal(context: Context, uriString: String, mimeType: String, targetPackage: String?) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share karo"))
    }.onFailure {
        // Fallback to general chooser if WhatsApp/Instagram not installed
        if (!targetPackage.isNullOrBlank()) {
            shareUriExternal(context, uriString, mimeType, null)
        }
    }
}
