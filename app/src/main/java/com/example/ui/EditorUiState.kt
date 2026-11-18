package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import com.example.ai.VideoExportFormat
import com.example.data.model.AdjustmentParams
import com.example.data.model.BackgroundSpec
import com.example.data.model.BatchItem
import com.example.data.model.BgBrushTool
import com.example.data.model.BothTab
import com.example.data.model.CanvasPreset
import com.example.data.model.EdgeSettings
import com.example.data.model.EditMode
import com.example.data.model.ExportConfig
import com.example.data.model.ExportOption
import com.example.data.model.ExportResultInfo
import com.example.data.model.FillVariation
import com.example.data.model.LayerTransform
import com.example.data.model.ObjectTool
import com.example.data.model.StickerOverlay
import com.example.data.model.SubjectType
import com.example.data.model.TextOverlay

object AppRoutes {
    const val HOME = "home"
    const val MODE_SELECT = "mode_select"
    const val EDITOR = "editor"
    const val EXPORT = "export"
    const val PROJECTS = "projects"
    const val DOWNLOADS = "downloads"
    const val BATCH = "batch"
    const val VIDEO = "video"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

data class EditorSnapshot(
    val maskBitmap: Bitmap? = null,
    val objSelectionMask: Bitmap? = null,
    val cleanBgBitmap: Bitmap? = null,
    val edgeSettings: EdgeSettings = EdgeSettings(),
    val backgroundSpec: BackgroundSpec = BackgroundSpec(),
    val subjectTransform: LayerTransform = LayerTransform(),
    val bgTransform: LayerTransform = LayerTransform(),
    val adjustments: AdjustmentParams = AdjustmentParams(),
    val canvasPreset: CanvasPreset = CanvasPreset.ORIGINAL,
    val customCanvasW: Int = 1080,
    val customCanvasH: Int = 1080,
    val shadowEnabled: Boolean = false,
    val shadowOpacity: Float = 0.45f,
    val outlineEnabled: Boolean = false,
    val outlineThickness: Float = 10f,
    val outlineColorArgb: Int = 0xFFFFFFFF.toInt(),
    val mixKaroEnabled: Boolean = false,
    val textOverlays: List<TextOverlay> = emptyList(),
    val stickerOverlays: List<StickerOverlay> = emptyList()
)

data class EditorUiState(
    val projectId: String? = null,
    val projectName: String = "Naya Cutout",
    val sourceUri: Uri? = null,
    val originalWorking: Bitmap? = null,
    val originalFullPath: String? = null,
    val rawMask: Bitmap? = null,
    val refinedCutout: Bitmap? = null,
    val composedPreview: Bitmap? = null,
    val objSelectionMask: Bitmap? = null,
    val cleanBgBitmap: Bitmap? = null,
    val bothCombinedPreview: Bitmap? = null,
    val activeMode: EditMode = EditMode.BG_REMOVE,
    val subjectType: SubjectType = SubjectType.AUTO,
    val bothTab: BothTab = BothTab.COMBINED,
    val bgTool: BgBrushTool = BgBrushTool.NONE,
    val objTool: ObjectTool = ObjectTool.AUTO,
    val brushSizeNorm: Float = 0.055f,
    val brushHardness: Float = 0.78f,
    val objMaskGrowPx: Int = 4,
    val removeShadowReflection: Boolean = true,
    val edgeSettings: EdgeSettings = EdgeSettings(),
    val backgroundSpec: BackgroundSpec = BackgroundSpec(),
    val subjectTransform: LayerTransform = LayerTransform(),
    val bgTransform: LayerTransform = LayerTransform(),
    val adjustments: AdjustmentParams = AdjustmentParams(),
    val canvasPreset: CanvasPreset = CanvasPreset.ORIGINAL,
    val customCanvasW: Int = 1080,
    val customCanvasH: Int = 1080,
    val shadowEnabled: Boolean = false,
    val shadowOpacity: Float = 0.45f,
    val outlineEnabled: Boolean = false,
    val outlineThickness: Float = 10f,
    val outlineColorArgb: Int = 0xFFFFFFFF.toInt(),
    val mixKaroEnabled: Boolean = false,
    val textOverlays: List<TextOverlay> = emptyList(),
    val stickerOverlays: List<StickerOverlay> = emptyList(),
    val fillVariations: List<FillVariation> = emptyList(),
    val activeVariationIndex: Int = 0,
    val cloneSourcePoint: Pair<Float, Float>? = null,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val bannerMessageHinglish: String? = null,
    val showBeforeAfter: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val pastedSubjectBitmap: Bitmap? = null
)

data class ExportUiState(
    val config: ExportConfig = ExportConfig(),
    val cutoutThumb: Bitmap? = null,
    val cleanBgThumb: Bitmap? = null,
    val combinedThumb: Bitmap? = null,
    val fullPreviewBitmap: Bitmap? = null,
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val collisionFileName: String? = null,
    val pendingCollisionOption: ExportOption? = null,
    val errorHinglish: String? = null,
    val resultInfo: ExportResultInfo? = null,
    val stickerPackItems: List<Bitmap> = emptyList()
)

data class BatchUiState(
    val items: List<BatchItem> = emptyList(),
    val mode: EditMode = EditMode.BG_REMOVE,
    val sharedBgSpec: BackgroundSpec = BackgroundSpec(),
    val isRunning: Boolean = false,
    val completedCount: Int = 0,
    val exportOption: ExportOption = ExportOption.BG_REMOVE,
    val saveAsZip: Boolean = false,
    val resultMessage: String? = null
)

data class VideoUiState(
    val selectedUri: Uri? = null,
    val durationMs: Long = 0L,
    val originalFrame: Bitmap? = null,
    val previewFrame: Bitmap? = null,
    val bgSpec: BackgroundSpec = BackgroundSpec(),
    val format: VideoExportFormat = VideoExportFormat.MP4,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val exportedUri: Uri? = null,
    val errorHinglish: String? = null
)
