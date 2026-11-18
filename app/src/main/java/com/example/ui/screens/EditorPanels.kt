package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BackgroundSpec
import com.example.data.model.BackgroundType
import com.example.data.model.BgBrushTool
import com.example.data.model.BuiltinBackgrounds
import com.example.data.model.LayerTransform
import com.example.data.model.ObjectTool
import com.example.data.model.QualityMode
import com.example.data.model.SubjectType
import com.example.ui.EditorUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.CoralSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.VioletTertiary

@Composable
fun BgRemoveControlPanel(
    state: EditorUiState,
    vm: MainViewModel,
    onPickBgGallery: () -> Unit,
    onOpenCommonEdits: () -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) } // 0=Subject/Edge, 1=Edge Sudharo, 2=Background, 3=Style/Mix

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Person / Object / Auto switch + Subtabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubjectType.entries.forEach { st ->
                FilterChip(
                    selected = state.subjectType == st,
                    onClick = { vm.setSubjectType(st) },
                    label = { Text(st.labelHinglish) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary.copy(alpha = 0.22f),
                        selectedLabelColor = CyanPrimary
                    )
                )
            }
            Spacer(Modifier.width(6.dp))
            listOf("Brush & Lasso", "Edge Sudharo", "Background", "Mix & Shadow", "Extra Edits").forEachIndexed { idx, label ->
                FilterChip(
                    selected = idx != 4 && subTab == idx,
                    onClick = {
                        if (idx == 4) onOpenCommonEdits() else subTab = idx
                    },
                    label = { Text(label) }
                )
            }
        }

        when (subTab) {
            0 -> {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ToolChip("Pan / Zoom", state.bgTool == BgBrushTool.NONE) { vm.setBgTool(BgBrushTool.NONE) }
                    ToolChip("Mitao", state.bgTool == BgBrushTool.ERASE) { vm.setBgTool(BgBrushTool.ERASE) }
                    ToolChip("Wapas lao", state.bgTool == BgBrushTool.RESTORE) { vm.setBgTool(BgBrushTool.RESTORE) }
                    ToolChip("Smart Brush", state.bgTool == BgBrushTool.SMART_BRUSH) { vm.setBgTool(BgBrushTool.SMART_BRUSH) }
                    ToolChip("Tap Keep", state.bgTool == BgBrushTool.TAP_KEEP) { vm.setBgTool(BgBrushTool.TAP_KEEP) }
                    ToolChip("Tap Remove", state.bgTool == BgBrushTool.TAP_REMOVE) { vm.setBgTool(BgBrushTool.TAP_REMOVE) }
                    ToolChip("Lasso", state.bgTool == BgBrushTool.LASSO) { vm.setBgTool(BgBrushTool.LASSO) }
                }

                if (state.bgTool in listOf(BgBrushTool.ERASE, BgBrushTool.RESTORE, BgBrushTool.SMART_BRUSH)) {
                    CompactSliderRow("Brush Size", state.brushSizeNorm, 0.015f..0.14f) {
                        vm.setBrushParams(it, state.brushHardness)
                    }
                    CompactSliderRow("Hardness", state.brushHardness, 0.1f..1f) {
                        vm.setBrushParams(state.brushSizeNorm, it)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Baal / hair detail mode", style = MaterialTheme.typography.labelLarge)
                        Text("Fine hair strands ke liye 512px AI model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.edgeSettings.hairDetailMode,
                        onCheckedChange = { checked ->
                            vm.updateEdgeSettings({ it.copy(hairDetailMode = checked) }, rerunHair = true)
                        }
                    )
                }
            }

            1 -> {
                CompactSliderRow("Feather", state.edgeSettings.feather, 0f..16f) { v ->
                    vm.updateEdgeSettings({ it.copy(feather = v) })
                }
                CompactSliderRow("Smooth", state.edgeSettings.smooth, 0f..10f) { v ->
                    vm.updateEdgeSettings({ it.copy(smooth = v) })
                }
                CompactSliderRow("Grow / Shrink", state.edgeSettings.growShrink, -10f..10f) { v ->
                    vm.updateEdgeSettings({ it.copy(growShrink = v) })
                }
                CompactSliderRow("Color Fringe Hatao", state.edgeSettings.defringeStrength, 0f..1f) { v ->
                    vm.updateEdgeSettings({ it.copy(defringeStrength = v) })
                }
            }

            2 -> {
                BackgroundPickerStrip(
                    current = state.backgroundSpec,
                    onChange = { vm.updateBackgroundSpec(it) },
                    onPickGallery = onPickBgGallery
                )
                if (state.backgroundSpec.type == BackgroundType.BLURRED_ORIGINAL) {
                    CompactSliderRow("Blur Amount", state.backgroundSpec.blurRadius, 4f..44f) { v ->
                        vm.updateBackgroundSpec(state.backgroundSpec.copy(blurRadius = v))
                    }
                }
            }

            3 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.toggleMixKaro() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.mixKaroEnabled) CyanPrimary else StudioSurfaceElevated,
                            contentColor = if (state.mixKaroEnabled) Color.Black else Color.White
                        )
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mix karo")
                    }
                    FilterChip(
                        selected = state.shadowEnabled,
                        onClick = { vm.updateShadow(!state.shadowEnabled, state.shadowOpacity) },
                        label = { Text("Shadow") }
                    )
                    FilterChip(
                        selected = state.outlineEnabled,
                        onClick = { vm.updateOutline(!state.outlineEnabled, state.outlineThickness, state.outlineColorArgb) },
                        label = { Text("Sticker Outline") }
                    )
                    OutlinedButton(onClick = { vm.updateSubjectTransform(state.subjectTransform.copy(flipH = !state.subjectTransform.flipH), true) }) {
                        Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Flip")
                    }
                    OutlinedButton(onClick = { vm.updateSubjectTransform(LayerTransform(), true) }) {
                        Text("Reset Pos")
                    }
                }
                if (state.shadowEnabled) {
                    CompactSliderRow("Shadow Softness", state.shadowOpacity, 0.1f..0.9f) {
                        vm.updateShadow(true, it)
                    }
                }
                if (state.outlineEnabled) {
                    CompactSliderRow("Outline Thickness", state.outlineThickness, 4f..28f) {
                        vm.updateOutline(true, it, state.outlineColorArgb)
                    }
                    ColorDotsRow(
                        selectedArgb = state.outlineColorArgb,
                        onSelect = { vm.updateOutline(true, state.outlineThickness, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ObjectRemoveControlPanel(
    state: EditorUiState,
    fillQuality: QualityMode,
    vm: MainViewModel,
    onSetQuality: (QualityMode) -> Unit,
    onOpenCommonEdits: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolChip("Auto Subject", state.objTool == ObjectTool.AUTO) {
                vm.setObjectTool(ObjectTool.AUTO)
                vm.detectObjectToRemoveAuto()
            }
            ToolChip("Tap Select", state.objTool == ObjectTool.TAP_SELECT) { vm.setObjectTool(ObjectTool.TAP_SELECT) }
            ToolChip("Add Brush", state.objTool == ObjectTool.ADD_BRUSH) { vm.setObjectTool(ObjectTool.ADD_BRUSH) }
            ToolChip("Erase Brush", state.objTool == ObjectTool.ERASE_BRUSH) { vm.setObjectTool(ObjectTool.ERASE_BRUSH) }
            ToolChip("Lasso", state.objTool == ObjectTool.LASSO) { vm.setObjectTool(ObjectTool.LASSO) }
            ToolChip("Clone Stamp", state.objTool == ObjectTool.CLONE_STAMP) { vm.setObjectTool(ObjectTool.CLONE_STAMP) }
            ToolChip("Healing", state.objTool == ObjectTool.HEALING_BRUSH) { vm.setObjectTool(ObjectTool.HEALING_BRUSH) }
            ToolChip("Patch Tool", state.objTool == ObjectTool.PATCH_TOOL) { vm.setObjectTool(ObjectTool.PATCH_TOOL) }
            ToolChip("Extra Edits", false) { onOpenCommonEdits() }
        }

        if (state.objTool in listOf(ObjectTool.ADD_BRUSH, ObjectTool.ERASE_BRUSH, ObjectTool.CLONE_STAMP, ObjectTool.HEALING_BRUSH)) {
            if (state.objTool == ObjectTool.CLONE_STAMP || state.objTool == ObjectTool.HEALING_BRUSH) {
                Text(
                    "Tip: Pehle saaf jagah par tap karein (source set), phir daag par paint karein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyanPrimary
                )
            }
            CompactSliderRow("Brush Size", state.brushSizeNorm, 0.015f..0.14f) {
                vm.setBrushParams(it, state.brushHardness)
            }
        }

        CompactSliderRow("Mask thoda bada karo (${state.objMaskGrowPx}px)", state.objMaskGrowPx.toFloat(), 0f..20f) {
            vm.setObjMaskGrowPx(it.toInt())
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Shadow/Reflection hatao", style = MaterialTheme.typography.labelMedium)
                Switch(
                    checked = state.removeShadowReflection,
                    onCheckedChange = { vm.setRemoveShadowReflection(it) }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = fillQuality == QualityMode.FAST,
                    onClick = { onSetQuality(QualityMode.FAST) },
                    label = { Text("Fast") }
                )
                FilterChip(
                    selected = fillQuality == QualityMode.HIGH,
                    onClick = { onSetQuality(QualityMode.HIGH) },
                    label = { Text("High") }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { vm.executeObjectFill(generateVariations = false) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("fill_object_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CoralSecondary, contentColor = Color.White)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hatao & Bharo", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { vm.executeObjectFill(generateVariations = true) },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("dobara_bharo_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Dobara bharo")
            }
        }

        if (state.fillVariations.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.fillVariations.forEachIndexed { idx, v ->
                    val selected = idx == state.activeVariationIndex
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(if (selected) 2.dp else 1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(12.dp))
                            .clickable { vm.selectFillVariation(idx) }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = v.bitmap.asImageBitmap(),
                            contentDescription = v.label,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(v.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun BothModeControlPanel(
    state: EditorUiState,
    clipboardHasSubject: Boolean,
    vm: MainViewModel,
    onPickBgGallery: () -> Unit,
    onOpenCommonEdits: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Combined preview mein subject ko drag/pinch karke kahin bhi khiskayein!",
            style = MaterialTheme.typography.bodySmall,
            color = VioletTertiary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { vm.copySubjectToClipboard() },
                colors = ButtonDefaults.buttonColors(containerColor = VioletTertiary, contentColor = Color(0xFF190E38))
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy Subject")
            }
            OutlinedButton(
                onClick = { vm.pasteSubjectFromClipboard() },
                enabled = clipboardHasSubject
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste Subject")
            }
            OutlinedButton(onClick = { vm.updateSubjectTransform(state.subjectTransform.copy(flipH = !state.subjectTransform.flipH), true) }) {
                Text("Flip Subject")
            }
            OutlinedButton(onClick = { vm.updateSubjectTransform(LayerTransform(), true) }) {
                Text("Center Reset")
            }
            OutlinedButton(onClick = onOpenCommonEdits) {
                Text("Extra Edits")
            }
        }

        if (state.bothTab == com.example.data.model.BothTab.SUBJECT) {
            BackgroundPickerStrip(
                current = state.backgroundSpec,
                onChange = { vm.updateBackgroundSpec(it) },
                onPickGallery = onPickBgGallery
            )
        }
    }
}

@Composable
fun BackgroundPickerStrip(
    current: BackgroundSpec,
    onChange: (BackgroundSpec) -> Unit,
    onPickGallery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolChip("Transparent", current.type == BackgroundType.TRANSPARENT) {
                onChange(current.copy(type = BackgroundType.TRANSPARENT))
            }
            ToolChip("Blur Original", current.type == BackgroundType.BLURRED_ORIGINAL) {
                onChange(current.copy(type = BackgroundType.BLURRED_ORIGINAL))
            }
            OutlinedButton(onClick = onPickGallery, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Gallery BG")
            }
        }

        // Solid Colors + Gradients + Built-in Studio presets
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val solids = listOf(0xFFFFFFFF.toInt(), 0xFF0A0C12.toInt(), 0xFF00E5FF.toInt(), 0xFFFF6B4A.toInt(), 0xFF22C55E.toInt(), 0xFFFACC15.toInt(), 0xFF1E3A8A.toInt())
            solids.forEach { argb ->
                val selected = current.type == BackgroundType.SOLID && current.solidColorArgb == argb
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (selected) 2.5.dp else 1.dp, if (selected) CyanPrimary else StudioBorder, CircleShape)
                        .clickable { onChange(current.copy(type = BackgroundType.SOLID, solidColorArgb = argb)) }
                )
            }

            val gradients = listOf(
                0xFF0F2027.toInt() to 0xFF2C5364.toInt(),
                0xFFFF512F.toInt() to 0xFFDD2476.toInt(),
                0xFF4776E6.toInt() to 0xFF8E54E9.toInt(),
                0xFF11998E.toInt() to 0xFF38EF7D.toInt()
            )
            gradients.forEach { (s, e) ->
                val selected = current.type == BackgroundType.GRADIENT && current.gradientStartArgb == s
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Color(s), Color(e))))
                        .border(if (selected) 2.5.dp else 1.dp, if (selected) CyanPrimary else StudioBorder, RoundedCornerShape(10.dp))
                        .clickable { onChange(current.copy(type = BackgroundType.GRADIENT, gradientStartArgb = s, gradientEndArgb = e)) }
                )
            }

            BuiltinBackgrounds.forEach { b ->
                val selected = current.type == BackgroundType.BUILTIN_STUDIO && current.builtinId == b.id
                FilterChip(
                    selected = selected,
                    onClick = { onChange(current.copy(type = BackgroundType.BUILTIN_STUDIO, builtinId = b.id)) },
                    label = { Text(b.label) }
                )
            }
        }
    }
}

@Composable
fun ColorDotsRow(selectedArgb: Int, onSelect: (Int) -> Unit) {
    val colors = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF00E5FF.toInt(), 0xFFFF6B4A.toInt(), 0xFFFACC15.toInt(), 0xFFA78BFA.toInt())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (selectedArgb == c) 2.5.dp else 1.dp, if (selectedArgb == c) CyanPrimary else StudioBorder, CircleShape)
                    .clickable { onSelect(c) }
            )
        }
    }
}

@Composable
fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CyanPrimary.copy(alpha = 0.22f),
            selectedLabelColor = CyanPrimary
        )
    )
}

@Composable
fun CompactSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(116.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = CyanPrimary, activeTrackColor = CyanPrimary)
        )
    }
}
