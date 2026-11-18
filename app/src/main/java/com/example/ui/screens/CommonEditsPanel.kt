package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.CanvasPreset
import com.example.data.model.ImageFilter
import com.example.ui.EditorUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.OverlayFontOptions
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonEditsSheet(
    state: EditorUiState,
    vm: MainViewModel,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) } // 0=Canvas, 1=Color/Crop, 2=Text & Stickers
    var textInput by remember { mutableStateOf("") }
    var fontId by remember { mutableStateOf("jakarta") }
    var textColor by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var textSize by remember { mutableFloatStateOf(30f) }
    var customW by remember { mutableStateOf(state.customCanvasW.toString()) }
    var customH by remember { mutableStateOf(state.customCanvasH.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Extra Edits", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text("Done", color = CyanPrimary) }
            }

            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Canvas Size") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Color & Crop") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Text & Stickers") })
            }

            when (tab) {
                0 -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CanvasPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = state.canvasPreset == preset,
                                onClick = {
                                    if (preset == CanvasPreset.CUSTOM) {
                                        vm.updateCanvasPreset(preset, customW.toIntOrNull() ?: 1080, customH.toIntOrNull() ?: 1080)
                                    } else {
                                        vm.updateCanvasPreset(preset)
                                    }
                                },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    if (state.canvasPreset == CanvasPreset.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = customW,
                                onValueChange = { customW = it.filter(Char::isDigit) },
                                label = { Text("Width px") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customH,
                                onValueChange = { customH = it.filter(Char::isDigit) },
                                label = { Text("Height px") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                vm.updateCanvasPreset(
                                    CanvasPreset.CUSTOM,
                                    customW.toIntOrNull() ?: 1080,
                                    customH.toIntOrNull() ?: 1080
                                )
                            }) { Text("Set") }
                        }
                    }
                }

                1 -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = {
                            vm.updateAdjustments { it.copy(canvasRotation = (it.canvasRotation + 90) % 360) }
                        }) { Text("Rotate 90°") }
                        OutlinedButton(onClick = {
                            vm.updateAdjustments { it.copy(cropLeft = 0.08f, cropTop = 0.08f, cropRight = 0.92f, cropBottom = 0.92f) }
                        }) { Text("Zoom Crop") }
                        OutlinedButton(onClick = {
                            vm.updateAdjustments { com.example.data.model.AdjustmentParams() }
                        }) { Text("Reset Edits") }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageFilter.entries.forEach { flt ->
                            FilterChip(
                                selected = state.adjustments.filter == flt,
                                onClick = { vm.updateAdjustments { it.copy(filter = flt) } },
                                label = { Text(flt.label) }
                            )
                        }
                    }
                    CompactSliderRow("Brightness", state.adjustments.brightness, -80f..80f) { v ->
                        vm.updateAdjustments { it.copy(brightness = v) }
                    }
                    CompactSliderRow("Contrast", state.adjustments.contrast, 0.5f..1.8f) { v ->
                        vm.updateAdjustments { it.copy(contrast = v) }
                    }
                    CompactSliderRow("Saturation", state.adjustments.saturation, 0f..2f) { v ->
                        vm.updateAdjustments { it.copy(saturation = v) }
                    }
                    CompactSliderRow("Sharpness", state.adjustments.sharpness, 0f..1f) { v ->
                        vm.updateAdjustments { it.copy(sharpness = v) }
                    }
                }

                2 -> {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Hindi ya English text likho") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlayFontOptions.forEach { f ->
                            FilterChip(
                                selected = fontId == f.id,
                                onClick = { fontId = f.id },
                                label = { Text(f.label, fontFamily = f.composeFontFamily) }
                            )
                        }
                    }
                    ColorDotsRow(selectedArgb = textColor, onSelect = { textColor = it })
                    CompactSliderRow("Text Size", textSize, 18f..54f) { textSize = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                vm.addTextOverlay(textInput, fontId, textColor, textSize)
                                textInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                        ) { Text("Text Jodo") }
                        if (state.textOverlays.isNotEmpty() || state.stickerOverlays.isNotEmpty()) {
                            OutlinedButton(onClick = { vm.clearOverlays() }) { Text("Sab Hatao") }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Stickers jodo:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("✨", "🔥", "❤️", "🎉", "😎", "🌸", "⭐", "🇮🇳", "🛍️", "👑").forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF101522))
                                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                                    .clickable { vm.addStickerOverlay(emoji) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(emoji, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
