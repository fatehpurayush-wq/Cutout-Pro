package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.VideoExportFormat
import com.example.data.local.AppSettings
import com.example.data.model.BatchStatus
import com.example.data.model.EditMode
import com.example.data.model.ExportFormat
import com.example.data.model.ExportOption
import com.example.data.model.ExportSizePreset
import com.example.data.model.QualityMode
import com.example.ui.BatchUiState
import com.example.ui.MainViewModel
import com.example.ui.VideoUiState
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
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(
    state: BatchUiState,
    vm: MainViewModel,
    onPickMultiplePhotos: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("Batch Mode (Ek Saath)", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wapas")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBackground)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = onPickMultiplePhotos,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.BurstMode, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kai Photos Ek Saath Chuno (${state.items.size} chuni hain)")
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Batch Mode & Export Option:", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditMode.entries.forEach { m ->
                                FilterChip(
                                    selected = state.mode == m,
                                    onClick = { vm.setBatchMode(m) },
                                    label = { Text(m.shortTag) }
                                )
                            }
                        }
                        if (state.mode == EditMode.OBJECT_REMOVE) {
                            Text("Note: Batch mode mein Subject hatao automatic AI subject detection use karta hai.", style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                        }
                        BackgroundPickerStrip(
                            current = state.sharedBgSpec,
                            onChange = { vm.setBatchBg(it) },
                            onPickGallery = {}
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ExportOption.entries.forEach { opt ->
                                FilterChip(
                                    selected = state.exportOption == opt,
                                    onClick = { vm.setBatchExportOption(opt, state.saveAsZip) },
                                    label = { Text(opt.titleHinglish.removeSuffix(" download")) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Dono option ke liye ZIP banayein", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = state.saveAsZip,
                                onCheckedChange = { vm.setBatchExportOption(state.exportOption, it) }
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.startBatchProcessing() },
                        enabled = state.items.isNotEmpty() && !state.isRunning,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralSecondary, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Batch Shuru Karein")
                    }
                    if (state.isRunning) {
                        OutlinedButton(
                            onClick = { vm.cancelBatchProcessing() },
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, tint = ErrorRed)
                            Spacer(Modifier.width(4.dp))
                            Text("Roko")
                        }
                    }
                }
            }

            state.resultMessage?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.16f))) {
                        Text(msg, modifier = Modifier.padding(14.dp), color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            items(state.items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.fileName, fontWeight = FontWeight.SemiBold)
                            Text(
                                item.status.labelHinglish,
                                style = MaterialTheme.typography.labelSmall,
                                color = when (item.status) {
                                    BatchStatus.DONE -> SuccessGreen
                                    BatchStatus.FAILED -> ErrorRed
                                    BatchStatus.PROCESSING -> CyanPrimary
                                    BatchStatus.PENDING -> Color.Gray
                                }
                            )
                        }
                        if (item.status == BatchStatus.PROCESSING) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CyanPrimary)
                        }
                        item.errorMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ErrorRed) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoBetaScreen(
    state: VideoUiState,
    vm: MainViewModel,
    onPickVideo: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("Video Background (Beta)", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wapas")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBackground)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                        Text(
                            "Beta Warning: Video cutout (max 15 seconds) phone par heavy hota hai — process dheema ho sakta hai aur phone garam ho sakta hai.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onPickVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Short Video Chuno (Max 15s)")
                }
            }

            state.errorHinglish?.let { err ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.16f))) {
                        Text(err, modifier = Modifier.padding(14.dp), color = ErrorRed)
                    }
                }
            }

            if (state.previewFrame != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
                    ) {
                        CheckerboardBackground(modifier = Modifier.fillMaxSize())
                        Image(
                            bitmap = state.previewFrame.asImageBitmap(),
                            contentDescription = "Video frame preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                item {
                    BackgroundPickerStrip(
                        current = state.bgSpec,
                        onChange = { vm.updateVideoBg(it) },
                        onPickGallery = {}
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoExportFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = state.format == fmt,
                                onClick = { vm.setVideoFormat(fmt) },
                                label = { Text(fmt.label) }
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = { vm.exportVideoBeta() },
                        enabled = !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralSecondary, contentColor = Color.White)
                    ) {
                        Text("Video Export Karein", fontWeight = FontWeight.Bold)
                    }
                }
            }

            state.exportedUri?.let { uri ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.16f))) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                                Text(state.statusText, color = SuccessGreen)
                            }
                            IconButton(onClick = {
                                val mime = if (state.format == VideoExportFormat.MP4) "video/mp4" else "image/webp"
                                shareUriExternal(context, uri.toString(), mime, null)
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share Video", tint = CyanPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    cacheBytes: Long,
    vm: MainViewModel,
    onPickDefaultFolder: () -> Unit,
    onOpenVideoBeta: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        // General & AI Quality
        item {
            SettingsCard("AI & Quality Settings") {
                Text("Default Mode:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditMode.entries.forEach { m ->
                        FilterChip(
                            selected = settings.defaultMode == m,
                            onClick = { scope.launch { vm.settingsStore.setDefaultMode(m) } },
                            label = { Text(m.shortTag) }
                        )
                    }
                }

                Text("Cutout Quality Mode:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityMode.entries.forEach { q ->
                        FilterChip(
                            selected = settings.segmentationQuality == q,
                            onClick = { scope.launch { vm.settingsStore.setSegmentationQuality(q) } },
                            label = { Text(q.labelHinglish) }
                        )
                    }
                }

                Text("Object Fill Quality Mode:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityMode.entries.forEach { q ->
                        FilterChip(
                            selected = settings.fillQuality == q,
                            onClick = { scope.launch { vm.settingsStore.setFillQuality(q) } },
                            label = { Text(q.labelHinglish) }
                        )
                    }
                }

                Text("Default Background:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("TRANSPARENT" to "Transparent", "WHITE" to "White", "STUDIO" to "Studio").forEach { (k, lbl) ->
                        FilterChip(
                            selected = settings.defaultBgKey == k,
                            onClick = { scope.launch { vm.settingsStore.setDefaultBgKey(k) } },
                            label = { Text(lbl) }
                        )
                    }
                }

                SettingsSwitchRow("AMOLED Pure Black Theme", settings.amoledTheme) {
                    scope.launch { vm.settingsStore.setAmoledTheme(it) }
                }
                SettingsSwitchRow("Auto-Save Projects", settings.autoSave) {
                    scope.launch { vm.settingsStore.setAutoSave(it) }
                }
            }
        }

        // Export Settings
        item {
            SettingsCard("Export Settings") {
                Text("Default Download Option:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportOption.entries.forEach { opt ->
                        FilterChip(
                            selected = settings.defaultExportOption == opt,
                            onClick = { scope.launch { vm.settingsStore.setDefaultExportOption(opt) } },
                            label = { Text(opt.titleHinglish.removeSuffix(" download")) }
                        )
                    }
                }

                Text("Default Format:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = settings.defaultExportFormat == fmt,
                            onClick = { scope.launch { vm.settingsStore.setDefaultExportFormat(fmt) } },
                            label = { Text(fmt.label) }
                        )
                    }
                }

                Text("Default Quality (${settings.defaultExportQuality}%):", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = settings.defaultExportQuality.toFloat(),
                    onValueChange = { q -> scope.launch { vm.settingsStore.setDefaultExportQuality(q.toInt()) } },
                    valueRange = 50f..100f
                )

                Text("Default Size:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportSizePreset.entries.forEach { sz ->
                        FilterChip(
                            selected = settings.defaultExportSize == sz,
                            onClick = { scope.launch { vm.settingsStore.setDefaultExportSize(sz) } },
                            label = { Text(sz.labelHinglish) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Save Folder", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (settings.defaultSaveFolderUri.isBlank()) "Pictures/Cutout Pro" else "Custom Folder Set",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanPrimary
                        )
                    }
                    OutlinedButton(onClick = onPickDefaultFolder) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Chuno")
                    }
                }

                SettingsSwitchRow("Save karne se pehle poochhein (file exist hone par)", settings.askBeforeSaving) {
                    scope.launch { vm.settingsStore.setAskBeforeSaving(it) }
                }

                OutlinedTextField(
                    value = settings.fileNamePattern,
                    onValueChange = { p -> scope.launch { vm.settingsStore.setFileNamePattern(p) } },
                    label = { Text("File Name Pattern ({date}, {mode}, {name})") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Video Beta + Cache + Models + About
        item {
            SettingsCard("Beta, Storage & Info") {
                SettingsSwitchRow("Video Background Removal (Beta)", settings.videoBetaEnabled) {
                    scope.launch { vm.settingsStore.setVideoBetaEnabled(it) }
                }
                if (settings.videoBetaEnabled) {
                    OutlinedButton(onClick = onOpenVideoBeta, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Video Cutout Beta Kholo")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Cache Storage", style = MaterialTheme.typography.labelLarge)
                        Text(formatBytesHinglish(cacheBytes), style = MaterialTheme.typography.bodySmall, color = CyanPrimary)
                    }
                    OutlinedButton(onClick = { vm.clearCache() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Cache")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Bundled On-Device AI Models (Apache 2.0):", style = MaterialTheme.typography.labelLarge, color = CyanPrimary)
                    Text(
                        "• MediaPipe Selfie Segmenter (244 KB)\n" +
                            "• MediaPipe DeepLab-v3 21-Class (2.7 MB)\n" +
                            "• MediaPipe Multiclass Portrait (16 MB)\n" +
                            "• MediaPipe Hair Detail Segmenter (764 KB)\n" +
                            "• PatchMatch & Harmonic Inpainter (Built-in)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onOpenAbout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_about_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyanPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("About & Privacy")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = { Text("About Cutout Pro", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wapas")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioBackground)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SuccessGreen.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
                    Column {
                        Text("Privacy Guarantee", style = MaterialTheme.typography.labelLarge, color = SuccessGreen)
                        Text("Aapki photos phone se bahar nahi jaati.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = StudioSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cutout Pro v1.0", style = MaterialTheme.typography.titleLarge, color = CyanPrimary)
                    Text(
                        "Cutout Pro ek 100% offline Android photo cutout aur object removal app hai. " +
                            "Ismein koi internet permission, cloud server, ads, login ya analytics nahi hai.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Included Open-Source Models:", style = MaterialTheme.typography.titleSmall, color = CoralSecondary)
                    Text(
                        "• LiteRT Runtime 2.3.0 (Apache 2.0)\n" +
                            "• MediaPipe Selfie Segmenter (Apache 2.0)\n" +
                            "• MediaPipe DeepLab-v3 (Apache 2.0)\n" +
                            "• MediaPipe Selfie Multiclass (Apache 2.0)\n" +
                            "• MediaPipe Hair Segmenter (Apache 2.0)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = CyanPrimary)
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
