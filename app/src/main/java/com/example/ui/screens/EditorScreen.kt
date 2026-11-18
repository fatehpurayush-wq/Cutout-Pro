package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BgBrushTool
import com.example.data.model.BothTab
import com.example.data.model.EditMode
import com.example.data.model.ObjectTool
import com.example.data.model.QualityMode
import com.example.ui.EditorUiState
import com.example.ui.MainViewModel
import com.example.ui.components.CanvasInteractionMode
import com.example.ui.components.InteractivePhotoCanvas
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    fillQuality: QualityMode,
    clipboardHasSubject: Boolean,
    vm: MainViewModel,
    onBack: () -> Unit,
    onPickBgGallery: () -> Unit,
    onSetFillQuality: (QualityMode) -> Unit,
    onOpenExport: () -> Unit
) {
    BackHandler(onBack = onBack)

    var showCommonEdits by remember { mutableStateOf(false) }
    var pendingLassoPoints by remember { mutableStateOf<List<Pair<Float, Float>>?>(null) }

    val displayBmp = when (state.activeMode) {
        EditMode.BG_REMOVE -> state.composedPreview ?: state.refinedCutout ?: state.originalWorking
        EditMode.OBJECT_REMOVE -> state.cleanBgBitmap ?: state.originalWorking
        EditMode.BOTH -> when (state.bothTab) {
            BothTab.SUBJECT -> state.composedPreview ?: state.refinedCutout ?: state.originalWorking
            BothTab.BACKGROUND -> state.cleanBgBitmap ?: state.originalWorking
            BothTab.COMBINED -> state.bothCombinedPreview ?: state.composedPreview ?: state.originalWorking
        }
    }

    val overlayMask = if (state.activeMode == EditMode.OBJECT_REMOVE) state.objSelectionMask else null

    val interactionMode = when (state.activeMode) {
        EditMode.BG_REMOVE -> when (state.bgTool) {
            BgBrushTool.NONE -> CanvasInteractionMode.VIEW_ZOOM_PAN
            BgBrushTool.ERASE, BgBrushTool.RESTORE, BgBrushTool.SMART_BRUSH -> CanvasInteractionMode.BRUSH_STROKE
            BgBrushTool.TAP_KEEP, BgBrushTool.TAP_REMOVE -> CanvasInteractionMode.TAP_POINT
            BgBrushTool.LASSO -> CanvasInteractionMode.LASSO_DRAW
        }
        EditMode.OBJECT_REMOVE -> when (state.objTool) {
            ObjectTool.AUTO -> CanvasInteractionMode.VIEW_ZOOM_PAN
            ObjectTool.TAP_SELECT -> CanvasInteractionMode.TAP_POINT
            ObjectTool.ADD_BRUSH, ObjectTool.ERASE_BRUSH, ObjectTool.CLONE_STAMP, ObjectTool.HEALING_BRUSH -> CanvasInteractionMode.BRUSH_STROKE
            ObjectTool.LASSO, ObjectTool.PATCH_TOOL -> CanvasInteractionMode.LASSO_DRAW
        }
        EditMode.BOTH -> if (state.bothTab == BothTab.COMBINED) CanvasInteractionMode.MOVE_LAYER else CanvasInteractionMode.VIEW_ZOOM_PAN
    }

    Scaffold(
        containerColor = StudioBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        EditMode.entries.forEach { m ->
                            FilterChip(
                                selected = state.activeMode == m,
                                onClick = { vm.switchActiveModeInEditor(m) },
                                label = { Text(m.shortTag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wapas")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.undo() }, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { vm.redo() }, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { vm.toggleBeforeAfter(!state.showBeforeAfter) }) {
                        Icon(
                            Icons.Default.Compare,
                            contentDescription = "Before After",
                            tint = if (state.showBeforeAfter) CyanPrimary else Color.White
                        )
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
        ) {
            // Both mode tabs
            if (state.activeMode == EditMode.BOTH) {
                TabRow(
                    selectedTabIndex = state.bothTab.ordinal,
                    containerColor = StudioSurface
                ) {
                    BothTab.entries.forEach { t ->
                        Tab(
                            selected = state.bothTab == t,
                            onClick = { vm.setBothTab(t) },
                            text = { Text(t.labelHinglish) }
                        )
                    }
                }
            }

            // Friendly Hinglish Alert Banner
            state.bannerMessageHinglish?.let { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = WarningAmber.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                }
            }

            // Main Canvas Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                InteractivePhotoCanvas(
                    displayBitmap = displayBmp,
                    beforeBitmap = state.originalWorking,
                    overlayMaskBitmap = overlayMask,
                    showBeforeAfter = state.showBeforeAfter,
                    interactionMode = interactionMode,
                    brushRadiusNorm = state.brushSizeNorm,
                    cloneSourceNorm = if (state.activeMode == EditMode.OBJECT_REMOVE &&
                        (state.objTool == ObjectTool.CLONE_STAMP || state.objTool == ObjectTool.HEALING_BRUSH)
                    ) state.cloneSourcePoint else null,
                    onBrushStart = {
                        vm.beginBrushStroke()
                    },
                    onBrushMove = { nx, ny ->
                        when (state.activeMode) {
                            EditMode.BG_REMOVE -> vm.paintBgBrushAt(nx, ny)
                            EditMode.OBJECT_REMOVE -> when (state.objTool) {
                                ObjectTool.ADD_BRUSH -> vm.paintObjectSelectionAt(nx, ny, true)
                                ObjectTool.ERASE_BRUSH -> vm.paintObjectSelectionAt(nx, ny, false)
                                ObjectTool.CLONE_STAMP -> vm.applyRepairBrushAt(nx, ny, healing = false)
                                ObjectTool.HEALING_BRUSH -> vm.applyRepairBrushAt(nx, ny, healing = true)
                                else -> {}
                            }
                            EditMode.BOTH -> {}
                        }
                    },
                    onBrushEnd = {
                        if (state.activeMode == EditMode.BG_REMOVE) vm.finishBrushStroke()
                    },
                    onTapNormalized = { nx, ny ->
                        when (state.activeMode) {
                            EditMode.BG_REMOVE -> vm.tapSelectBgMaskAt(nx, ny)
                            EditMode.OBJECT_REMOVE -> {
                                if (state.objTool == ObjectTool.CLONE_STAMP || state.objTool == ObjectTool.HEALING_BRUSH) {
                                    vm.setCloneSourcePoint(nx, ny)
                                } else {
                                    vm.tapSelectObjectToRemove(nx, ny)
                                }
                            }
                            EditMode.BOTH -> {}
                        }
                    },
                    onLassoFinished = { pts ->
                        when (state.activeMode) {
                            EditMode.BG_REMOVE -> pendingLassoPoints = pts
                            EditMode.OBJECT_REMOVE -> {
                                if (state.objTool == ObjectTool.PATCH_TOOL) vm.applyPatchPolygon(pts)
                                else vm.applyObjectLasso(pts)
                            }
                            EditMode.BOTH -> {}
                        }
                    },
                    onLayerDelta = { dx, dy, scaleFactor, rot ->
                        val cur = state.subjectTransform
                        vm.updateSubjectTransform(
                            cur.copy(
                                offsetX = (cur.offsetX + dx).coerceIn(-0.9f, 0.9f),
                                offsetY = (cur.offsetY + dy).coerceIn(-0.9f, 0.9f),
                                scale = (cur.scale * scaleFactor).coerceIn(0.25f, 3.0f),
                                rotationDeg = cur.rotationDeg + rot
                            )
                        )
                    }
                )

                // Clone Stamp Source Set Chip
                if (state.activeMode == EditMode.OBJECT_REMOVE &&
                    (state.objTool == ObjectTool.CLONE_STAMP || state.objTool == ObjectTool.HEALING_BRUSH)
                ) {
                    Surface(
                        onClick = { vm.setCloneSourcePoint(0.25f, 0.25f) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = StudioSurfaceElevated.copy(alpha = 0.9f)
                    ) {
                        Text(
                            "Source Center Reset",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary
                        )
                    }
                }

                // Lasso Keep/Remove Popup for Mode 1
                pendingLassoPoints?.let { pts ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = StudioSurfaceElevated,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = {
                                vm.applyBgLasso(pts, keepInside = true)
                                pendingLassoPoints = null
                            }) { Text("Andar Rakho") }
                            OutlinedButton(onClick = {
                                vm.applyBgLasso(pts, keepInside = false)
                                pendingLassoPoints = null
                            }) { Text("Andar Hatao") }
                        }
                    }
                }
            }

            // Bottom Control Deck + Big Download Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(StudioSurface)
                    .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (state.activeMode) {
                        EditMode.BG_REMOVE -> BgRemoveControlPanel(
                            state = state,
                            vm = vm,
                            onPickBgGallery = onPickBgGallery,
                            onOpenCommonEdits = { showCommonEdits = true }
                        )
                        EditMode.OBJECT_REMOVE -> ObjectRemoveControlPanel(
                            state = state,
                            fillQuality = fillQuality,
                            vm = vm,
                            onSetQuality = onSetFillQuality,
                            onOpenCommonEdits = { showCommonEdits = true }
                        )
                        EditMode.BOTH -> BothModeControlPanel(
                            state = state,
                            clipboardHasSubject = clipboardHasSubject,
                            vm = vm,
                            onPickBgGallery = onPickBgGallery,
                            onOpenCommonEdits = { showCommonEdits = true }
                        )
                    }
                }

                // Always-visible big Download button
                Button(
                    onClick = onOpenExport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("editor_download_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF001C24)
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCommonEdits) {
        CommonEditsSheet(
            state = state,
            vm = vm,
            onDismiss = { showCommonEdits = false }
        )
    }
}
