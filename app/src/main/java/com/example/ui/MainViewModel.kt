package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.LiteRtModelPool
import com.example.ai.OnDeviceInpaintingEngine
import com.example.ai.OnDeviceSegmentationEngine
import com.example.ai.VideoCutoutProcessor
import com.example.ai.VideoExportFormat
import com.example.data.local.AppDatabase
import com.example.data.local.AppSettings
import com.example.data.local.DownloadEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.SettingsStore
import com.example.data.model.AdjustmentParams
import com.example.data.model.BackgroundSpec
import com.example.data.model.BackgroundType
import com.example.data.model.BatchItem
import com.example.data.model.BatchStatus
import com.example.data.model.BgBrushTool
import com.example.data.model.BothTab
import com.example.data.model.CanvasPreset
import com.example.data.model.EdgeSettings
import com.example.data.model.EditMode
import com.example.data.model.ExportFormat
import com.example.data.model.ExportOption
import com.example.data.model.ExportSizePreset
import com.example.data.model.LayerTransform
import com.example.data.model.ObjectTool
import com.example.data.model.QualityMode
import com.example.data.model.SamplePhotoOption
import com.example.data.model.StickerOverlay
import com.example.data.model.SubjectType
import com.example.data.model.TextOverlay
import com.example.data.repository.DownloadRepository
import com.example.data.repository.PersistedProjectState
import com.example.data.repository.ProjectRepository
import com.example.export.BitmapUtils
import com.example.export.ExportCheckResult
import com.example.export.ExportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    val settingsStore = SettingsStore(appContext)
    private val projectRepo = ProjectRepository(appContext, db.projectDao())
    private val downloadRepo = DownloadRepository(appContext, db.downloadDao())
    private val modelPool = LiteRtModelPool(appContext)
    private val segEngine = OnDeviceSegmentationEngine(modelPool)
    private val inpaintEngine = OnDeviceInpaintingEngine(segEngine)
    private val exportEngine = ExportEngine(appContext, downloadRepo)
    private val videoProcessor = VideoCutoutProcessor(appContext, segEngine)

    val settings: StateFlow<AppSettings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val allProjects: StateFlow<List<ProjectEntity>> = projectRepo.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentProjects: StateFlow<List<ProjectEntity>> = projectRepo.recentProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadRepo.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _editor = MutableStateFlow(EditorUiState())
    val editor: StateFlow<EditorUiState> = _editor.asStateFlow()

    private val _export = MutableStateFlow(ExportUiState())
    val export: StateFlow<ExportUiState> = _export.asStateFlow()

    private val _batch = MutableStateFlow(BatchUiState())
    val batch: StateFlow<BatchUiState> = _batch.asStateFlow()

    private val _video = MutableStateFlow(VideoUiState())
    val video: StateFlow<VideoUiState> = _video.asStateFlow()

    private val _clipboardSubject = MutableStateFlow<Bitmap?>(null)
    val clipboardSubject: StateFlow<Bitmap?> = _clipboardSubject.asStateFlow()

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private var batchJob: Job? = null
    private var activeProcessJob: Job? = null

    init {
        refreshCacheSize()
        viewModelScope.launch {
            val s = settingsStore.settingsFlow.first()
            _export.update {
                it.copy(
                    config = it.config.copy(
                        option = s.defaultExportOption,
                        format = s.defaultExportFormat,
                        quality = s.defaultExportQuality,
                        sizePreset = s.defaultExportSize,
                        useCustomFolder = s.defaultSaveFolderUri.isNotBlank(),
                        customFolderUri = s.defaultSaveFolderUri.ifBlank { null }
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelPool.close()
    }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheBytes.value = projectRepo.getCacheSizeBytes()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _cacheBytes.value = projectRepo.clearCache()
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsStore.setOnboardingDone(true) }
    }

    // === PHOTO LOADING ===

    fun loadPickedPhoto(uri: Uri, onReady: () -> Unit) {
        activeProcessJob?.cancel()
        activeProcessJob = viewModelScope.launch {
            val s = settings.value
            val maxDim = s.segmentationQuality.workingMaxDim
            _editor.update { it.copy(isProcessing = true, progress = 0.2f, statusText = "Photo khul raha hai...", bannerMessageHinglish = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Copy source URI into local cache so re-opening or full-res export always works offline
                    val cacheFile = File(appContext.cacheDir, "src_${System.currentTimeMillis()}.jpg")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                    }
                    val working = BitmapUtils.decodeUriSafe(appContext, Uri.fromFile(cacheFile), maxDim)
                        ?: throw IllegalStateException("Photo decode nahi ho saka.")
                    cacheFile.absolutePath to working
                }
            }
            result.onSuccess { (path, working) ->
                resetEditorForNewBitmap(uri, path, working, "Photo_${shortTimestamp()}")
                onReady()
            }.onFailure { err ->
                val msg = if (err is OutOfMemoryError) {
                    "Phone ki memory kam hai — thoda chhota photo chunein."
                } else {
                    "Photo load nahi ho saka. Dobara koshish karein."
                }
                _editor.update { it.copy(isProcessing = false, bannerMessageHinglish = msg) }
            }
        }
    }

    fun loadSamplePhoto(sample: SamplePhotoOption, onReady: () -> Unit) {
        viewModelScope.launch {
            val s = settings.value
            val maxDim = s.segmentationQuality.workingMaxDim
            _editor.update { it.copy(isProcessing = true, progress = 0.25f, statusText = "Sample photo taiyaar ho raha hai...") }
            val result = withContext(Dispatchers.IO) {
                val full = BitmapUtils.decodeDrawableSafe(appContext, sample.drawableRes, null)
                    ?: return@withContext null
                val cacheFile = File(appContext.cacheDir, "sample_${sample.id}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(cacheFile).use { full.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                val working = BitmapUtils.scaleDownToMax(full, maxDim)
                if (working !== full) full.recycle()
                cacheFile.absolutePath to working
            }
            if (result != null) {
                resetEditorForNewBitmap(null, result.first, result.second, sample.titleHinglish)
                onReady()
            } else {
                _editor.update { it.copy(isProcessing = false, bannerMessageHinglish = "Sample photo nahi khula.") }
            }
        }
    }

    private fun resetEditorForNewBitmap(uri: Uri?, fullPath: String, working: Bitmap, title: String) {
        undoStack.clear()
        redoStack.clear()
        val s = settings.value
        val bgSpec = when (s.defaultBgKey) {
            "WHITE" -> BackgroundSpec(type = BackgroundType.SOLID, solidColorArgb = Color.WHITE)
            "STUDIO" -> BackgroundSpec(type = BackgroundType.BUILTIN_STUDIO, builtinId = "studio_glow")
            else -> BackgroundSpec(type = BackgroundType.TRANSPARENT)
        }
        _editor.value = EditorUiState(
            projectId = null,
            projectName = title,
            sourceUri = uri,
            originalWorking = working,
            originalFullPath = fullPath,
            activeMode = s.lastUsedMode,
            backgroundSpec = bgSpec,
            isProcessing = false
        )
    }

    fun openSavedProject(project: ProjectEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            _editor.update { it.copy(isProcessing = true, progress = 0.3f, statusText = "Project khul raha hai...") }
            val loaded = withContext(Dispatchers.IO) {
                val origFull = projectRepo.loadBitmapFromPath(project.originalPath) ?: return@withContext null
                val working = BitmapUtils.scaleDownToMax(origFull, settings.value.segmentationQuality.workingMaxDim)
                if (working !== origFull) origFull.recycle()
                val mask = projectRepo.loadBitmapFromPath(project.maskPath)?.let {
                    Bitmap.createScaledBitmap(it, working.width, working.height, true)
                }
                val cleanBg = projectRepo.loadBitmapFromPath(project.cleanBgPath)?.let {
                    Bitmap.createScaledBitmap(it, working.width, working.height, true)
                }
                val st = projectRepo.decodeState(project.stateJson)
                Triple(working, mask, cleanBg) to st
            }
            if (loaded == null) {
                _editor.update { it.copy(isProcessing = false, bannerMessageHinglish = "Project file nahi mili.") }
                return@launch
            }
            val (bitmaps, st) = loaded
            val (working, mask, cleanBg) = bitmaps
            val mode = runCatching { EditMode.valueOf(project.mode) }.getOrDefault(EditMode.BG_REMOVE)
            undoStack.clear()
            redoStack.clear()
            _editor.value = EditorUiState(
                projectId = project.id,
                projectName = project.name,
                originalWorking = working,
                originalFullPath = project.originalPath,
                rawMask = mask,
                cleanBgBitmap = cleanBg,
                activeMode = mode,
                subjectType = st.subjectType,
                edgeSettings = st.edgeSettings,
                backgroundSpec = st.backgroundSpec,
                subjectTransform = st.subjectTransform,
                bgTransform = st.bgTransform,
                adjustments = st.adjustments,
                canvasPreset = st.canvasPreset,
                customCanvasW = st.customCanvasW,
                customCanvasH = st.customCanvasH,
                shadowEnabled = st.shadowEnabled,
                shadowOpacity = st.shadowOpacity,
                outlineEnabled = st.outlineEnabled,
                outlineThickness = st.outlineThickness,
                outlineColorArgb = st.outlineColorArgb,
                mixKaroEnabled = st.mixKaroEnabled,
                removeShadowReflection = st.removeShadowReflection,
                textOverlays = st.textOverlays,
                stickerOverlays = st.stickerOverlays,
                isProcessing = false
            )
            recomputePreviews(autoSave = false)
            onReady()
        }
    }

    // === MODE SELECTION & AUTO PROCESSING ===

    fun startMode(mode: EditMode) {
        viewModelScope.launch { settingsStore.setLastUsedMode(mode) }
        _editor.update { it.copy(activeMode = mode, bannerMessageHinglish = null) }
        val st = _editor.value
        val src = st.originalWorking ?: return
        when (mode) {
            EditMode.BG_REMOVE -> {
                if (st.rawMask == null) runAutoCutout() else recomputePreviews()
            }
            EditMode.OBJECT_REMOVE -> {
                if (st.cleanBgBitmap == null && st.objSelectionMask == null) {
                    _editor.update { it.copy(cleanBgBitmap = src.copy(Bitmap.Config.ARGB_8888, true)) }
                    detectObjectToRemoveAuto()
                } else recomputePreviews()
            }
            EditMode.BOTH -> {
                runBothModeAuto()
            }
        }
    }

    fun switchActiveModeInEditor(newMode: EditMode) {
        _editor.value.projectId?.let { id ->
            viewModelScope.launch { projectRepo.switchProjectMode(id, newMode) }
        }
        startMode(newMode)
    }

    fun switchProjectModeFromList(projectId: String, newMode: EditMode) {
        viewModelScope.launch { projectRepo.switchProjectMode(projectId, newMode) }
    }

    fun setSubjectType(type: SubjectType) {
        _editor.update { it.copy(subjectType = type) }
        runAutoCutout()
    }

    fun runAutoCutout() {
        val src = _editor.value.originalWorking ?: return
        activeProcessJob?.cancel()
        activeProcessJob = viewModelScope.launch {
            _editor.update { it.copy(isProcessing = true, progress = 0.08f, statusText = "AI cutout shuru ho raha hai...", bannerMessageHinglish = null) }
            val res = segEngine.detectSubjectMask(
                source = src,
                subjectType = _editor.value.subjectType,
                hairDetail = _editor.value.edgeSettings.hairDetailMode,
                quality = settings.value.segmentationQuality
            ) { p, msg ->
                _editor.update { it.copy(progress = p, statusText = msg) }
            }
            pushUndoSnapshot()
            _editor.update {
                it.copy(
                    rawMask = res.mask,
                    isProcessing = false,
                    bannerMessageHinglish = if (!res.subjectFound) {
                        "Koi saaf subject nahi mila — 'Wapas lao' / 'Mitao' brush ya Lasso se set karein."
                    } else null
                )
            }
            recomputePreviews()
        }
    }

    fun runBothModeAuto() {
        val src = _editor.value.originalWorking ?: return
        activeProcessJob?.cancel()
        activeProcessJob = viewModelScope.launch {
            _editor.update { it.copy(isProcessing = true, progress = 0.08f, statusText = "Dono layers taiyaar ho rahi hain...", bannerMessageHinglish = null) }
            val seg = segEngine.detectSubjectMask(
                source = src,
                subjectType = _editor.value.subjectType,
                hairDetail = _editor.value.edgeSettings.hairDetailMode,
                quality = settings.value.segmentationQuality
            ) { p, msg ->
                _editor.update { it.copy(progress = p * 0.45f, statusText = msg) }
            }
            val grownForClean = inpaintEngine.growSelectionMask(seg.mask, 6)
            val inpaintRes = inpaintEngine.inpaintWithVariations(
                source = src,
                selectionMask = grownForClean,
                quality = settings.value.fillQuality,
                generateVariations = false
            ) { p, msg ->
                _editor.update { it.copy(progress = 0.45f + p * 0.50f, statusText = msg) }
            }
            grownForClean.recycle()
            pushUndoSnapshot()
            _editor.update {
                it.copy(
                    rawMask = seg.mask,
                    cleanBgBitmap = inpaintRes.resultBitmap,
                    isProcessing = false,
                    bannerMessageHinglish = if (!seg.subjectFound) "Subject halka dikha — brush se refine karein." else null
                )
            }
            recomputePreviews()
        }
    }

    // === MODE 1: EDGE & BACKGROUND TOOLS ===

    fun setBgTool(tool: BgBrushTool) {
        _editor.update { it.copy(bgTool = tool) }
    }

    fun setBrushParams(sizeNorm: Float, hardness: Float) {
        _editor.update { it.copy(brushSizeNorm = sizeNorm, brushHardness = hardness) }
    }

    fun updateEdgeSettings(update: (EdgeSettings) -> EdgeSettings, rerunHair: Boolean = false) {
        val next = update(_editor.value.edgeSettings)
        _editor.update { it.copy(edgeSettings = next) }
        if (rerunHair) {
            runAutoCutout()
        } else {
            recomputePreviews()
        }
    }

    fun beginBrushStroke() {
        pushUndoSnapshot()
    }

    fun paintBgBrushAt(normX: Float, normY: Float) {
        val st = _editor.value
        val src = st.originalWorking ?: return
        val mask = st.rawMask ?: Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val isRestore = st.bgTool == BgBrushTool.RESTORE || st.bgTool == BgBrushTool.SMART_BRUSH
        val smart = st.bgTool == BgBrushTool.SMART_BRUSH
        segEngine.applyBrushStroke(
            source = src,
            mask = mask,
            normX = normX,
            normY = normY,
            radiusNorm = st.brushSizeNorm,
            hardness = st.brushHardness,
            isRestore = isRestore,
            smartEdgeSnap = smart
        )
        _editor.update { it.copy(rawMask = mask) }
        recomputePreviews(autoSave = false)
    }

    fun finishBrushStroke() {
        recomputePreviews(autoSave = true)
    }

    fun tapSelectBgMaskAt(normX: Float, normY: Float) {
        val st = _editor.value
        val src = st.originalWorking ?: return
        val mask = st.rawMask ?: Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val keep = st.bgTool == BgBrushTool.TAP_KEEP
        viewModelScope.launch {
            pushUndoSnapshot()
            segEngine.applyTapSelectRegion(src, mask, normX, normY, keepRegion = keep)
            _editor.update { it.copy(rawMask = mask) }
            recomputePreviews()
        }
    }

    fun applyBgLasso(points: List<Pair<Float, Float>>, keepInside: Boolean) {
        val st = _editor.value
        val src = st.originalWorking ?: return
        val mask = st.rawMask ?: Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        pushUndoSnapshot()
        segEngine.applyLassoPolygon(mask, points, keepInside)
        _editor.update { it.copy(rawMask = mask, bgTool = BgBrushTool.NONE) }
        recomputePreviews()
    }

    fun updateBackgroundSpec(spec: BackgroundSpec) {
        pushUndoSnapshot()
        _editor.update { it.copy(backgroundSpec = spec) }
        recomputePreviews()
    }

    fun setCustomBackgroundImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = BitmapUtils.decodeUriSafe(appContext, uri, 1440) ?: return@launch
            val file = File(appContext.cacheDir, "bg_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bmp.recycle()
            updateBackgroundSpec(
                _editor.value.backgroundSpec.copy(
                    type = BackgroundType.GALLERY_IMAGE,
                    customImagePath = file.absolutePath
                )
            )
        }
    }

    fun updateSubjectTransform(transform: LayerTransform, recordUndo: Boolean = false) {
        if (recordUndo) pushUndoSnapshot()
        _editor.update { it.copy(subjectTransform = transform) }
        recomputePreviews(autoSave = recordUndo)
    }

    fun toggleMixKaro() {
        pushUndoSnapshot()
        _editor.update { it.copy(mixKaroEnabled = !it.mixKaroEnabled) }
        recomputePreviews()
    }

    fun updateShadow(enabled: Boolean, opacity: Float) {
        _editor.update { it.copy(shadowEnabled = enabled, shadowOpacity = opacity) }
        recomputePreviews()
    }

    fun updateOutline(enabled: Boolean, thickness: Float, colorArgb: Int) {
        _editor.update { it.copy(outlineEnabled = enabled, outlineThickness = thickness, outlineColorArgb = colorArgb) }
        recomputePreviews()
    }

    // === MODE 2: SUBJECT HATAO ===

    fun setObjectTool(tool: ObjectTool) {
        _editor.update { it.copy(objTool = tool) }
    }

    fun setRemoveShadowReflection(enabled: Boolean) {
        _editor.update { it.copy(removeShadowReflection = enabled) }
    }

    fun setObjMaskGrowPx(px: Int) {
        _editor.update { it.copy(objMaskGrowPx = px) }
    }

    fun detectObjectToRemoveAuto() {
        val currentBase = _editor.value.cleanBgBitmap ?: _editor.value.originalWorking ?: return
        activeProcessJob?.cancel()
        activeProcessJob = viewModelScope.launch {
            _editor.update { it.copy(isProcessing = true, progress = 0.1f, statusText = "Main subject dhundha ja raha hai...") }
            val seg = inpaintEngine.detectSubjectForRemoval(
                source = currentBase,
                includeShadowAndReflection = _editor.value.removeShadowReflection,
                quality = settings.value.segmentationQuality
            ) { p, msg ->
                _editor.update { it.copy(progress = p, statusText = msg) }
            }
            pushUndoSnapshot()
            _editor.update {
                it.copy(
                    objSelectionMask = seg.mask,
                    isProcessing = false,
                    bannerMessageHinglish = if (!seg.subjectFound) "Subject auto-detect nahi hua — brush ya tap se chunein." else null
                )
            }
        }
    }

    fun tapSelectObjectToRemove(normX: Float, normY: Float) {
        val base = _editor.value.cleanBgBitmap ?: _editor.value.originalWorking ?: return
        val mask = _editor.value.objSelectionMask ?: Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        viewModelScope.launch {
            pushUndoSnapshot()
            segEngine.applyTapSelectRegion(base, mask, normX, normY, keepRegion = true, tolerance = 44)
            _editor.update { it.copy(objSelectionMask = mask) }
        }
    }

    fun paintObjectSelectionAt(normX: Float, normY: Float, add: Boolean) {
        val base = _editor.value.cleanBgBitmap ?: _editor.value.originalWorking ?: return
        val mask = _editor.value.objSelectionMask ?: Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        inpaintEngine.paintSelectionBrush(
            mask = mask,
            normX = normX,
            normY = normY,
            radiusNorm = _editor.value.brushSizeNorm,
            softness = 1f - _editor.value.brushHardness,
            addMode = add
        )
        _editor.update { it.copy(objSelectionMask = mask) }
    }

    fun applyObjectLasso(points: List<Pair<Float, Float>>) {
        val base = _editor.value.cleanBgBitmap ?: _editor.value.originalWorking ?: return
        val mask = _editor.value.objSelectionMask ?: Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        pushUndoSnapshot()
        segEngine.applyLassoPolygon(mask, points, keepInside = true)
        _editor.update { it.copy(objSelectionMask = mask) }
    }

    fun setCloneSourcePoint(normX: Float, normY: Float) {
        _editor.update { it.copy(cloneSourcePoint = normX to normY) }
    }

    fun applyRepairBrushAt(normX: Float, normY: Float, healing: Boolean) {
        val base = _editor.value.cleanBgBitmap ?: _editor.value.originalWorking ?: return
        val mutable = if (base.isMutable) base else base.copy(Bitmap.Config.ARGB_8888, true)
        val srcPt = _editor.value.cloneSourcePoint ?: ((normX - 0.12f).coerceIn(0f, 1f) to normY)
        inpaintEngine.applyManualRepairBrush(
            bitmap = mutable,
            srcNormX = srcPt.first,
            srcNormY = srcPt.second,
            dstNormX = normX,
            dstNormY = normY,
            radiusNorm = _editor.value.brushSizeNorm,
            softness = 1f - _editor.value.brushHardness,
            healingMode = healing
        )
        _editor.update { it.copy(cleanBgBitmap = mutable) }
    }

    fun applyPatchPolygon(points: List<Pair<Float, Float>>) {
        val base = _editor.value.cleanBgBitmap ?: _editor.value.originalWorking ?: return
        val mutable = if (base.isMutable) base else base.copy(Bitmap.Config.ARGB_8888, true)
        pushUndoSnapshot()
        inpaintEngine.applyPatchTool(mutable, points, shiftNormX = 0.12f, shiftNormY = 0.06f)
        _editor.update { it.copy(cleanBgBitmap = mutable) }
        recomputePreviews()
    }

    fun executeObjectFill(generateVariations: Boolean = false) {
        val base = _editor.value.cleanBgBitmap ?: _editor.value.originalWorking ?: return
        val selMask = _editor.value.objSelectionMask ?: run {
            _editor.update { it.copy(bannerMessageHinglish = "Pehle hatane ke liye area select karein.") }
            return
        }
        activeProcessJob?.cancel()
        activeProcessJob = viewModelScope.launch {
            _editor.update { it.copy(isProcessing = true, progress = 0.1f, statusText = "Object hata kar background bhar raha hai...", bannerMessageHinglish = null) }
            val grown = inpaintEngine.growSelectionMask(selMask, _editor.value.objMaskGrowPx)
            val outcome = inpaintEngine.inpaintWithVariations(
                source = base,
                selectionMask = grown,
                quality = settings.value.fillQuality,
                generateVariations = generateVariations
            ) { p, msg ->
                _editor.update { it.copy(progress = p, statusText = msg) }
            }
            grown.recycle()
            pushUndoSnapshot()
            _editor.update {
                it.copy(
                    cleanBgBitmap = outcome.resultBitmap,
                    objSelectionMask = null,
                    fillVariations = outcome.variations,
                    activeVariationIndex = 0,
                    isProcessing = false,
                    bannerMessageHinglish = if (outcome.areaTooLargeWarning) {
                        "Chuna hua hissa kaafi bada hai — behtar nateeje ke liye chhota area chunein ya Clone/Heal tools use karein."
                    } else null
                )
            }
            recomputePreviews()
        }
    }

    fun selectFillVariation(index: Int) {
        val vars = _editor.value.fillVariations
        if (index !in vars.indices) return
        pushUndoSnapshot()
        _editor.update {
            it.copy(
                cleanBgBitmap = vars[index].bitmap.copy(Bitmap.Config.ARGB_8888, true),
                activeVariationIndex = index
            )
        }
        recomputePreviews()
    }

    // === MODE 3: DONO (BOTH) & COPY / PASTE SUBJECT ===

    fun setBothTab(tab: BothTab) {
        _editor.update { it.copy(bothTab = tab) }
    }

    fun copySubjectToClipboard() {
        val cutout = _editor.value.refinedCutout ?: return
        _clipboardSubject.value = cutout.copy(Bitmap.Config.ARGB_8888, true)
        _editor.update { it.copy(bannerMessageHinglish = "Subject copy ho gaya! Ab kisi bhi photo mein Paste karein.") }
    }

    fun pasteSubjectFromClipboard() {
        val clip = _clipboardSubject.value ?: return
        pushUndoSnapshot()
        _editor.update {
            it.copy(
                pastedSubjectBitmap = clip.copy(Bitmap.Config.ARGB_8888, true),
                bothTab = BothTab.COMBINED,
                bannerMessageHinglish = "Doosri photo ka subject paste ho gaya! Khiska kar set karein."
            )
        }
        recomputePreviews()
    }

    // === COMMON EDITS ===

    fun updateCanvasPreset(preset: CanvasPreset, customW: Int = 1080, customH: Int = 1080) {
        pushUndoSnapshot()
        _editor.update { it.copy(canvasPreset = preset, customCanvasW = customW, customCanvasH = customH) }
        recomputePreviews()
    }

    fun updateAdjustments(update: (AdjustmentParams) -> AdjustmentParams) {
        pushUndoSnapshot()
        _editor.update { it.copy(adjustments = update(it.adjustments)) }
        recomputePreviews()
    }

    fun addTextOverlay(text: String, fontId: String, colorArgb: Int, sizeSp: Float) {
        if (text.isBlank()) return
        pushUndoSnapshot()
        val item = TextOverlay(UUID.randomUUID().toString(), text.trim(), fontId, colorArgb, sizeSp)
        _editor.update { it.copy(textOverlays = it.textOverlays + item) }
        recomputePreviews()
    }

    fun addStickerOverlay(symbol: String) {
        pushUndoSnapshot()
        val item = StickerOverlay(UUID.randomUUID().toString(), symbol)
        _editor.update { it.copy(stickerOverlays = it.stickerOverlays + item) }
        recomputePreviews()
    }

    fun moveLastOverlay(dx: Float, dy: Float) {
        val texts = _editor.value.textOverlays
        val stickers = _editor.value.stickerOverlays
        if (texts.isNotEmpty()) {
            val last = texts.last()
            val updated = last.copy(
                normX = (last.normX + dx).coerceIn(0.05f, 0.95f),
                normY = (last.normY + dy).coerceIn(0.05f, 0.95f)
            )
            _editor.update { it.copy(textOverlays = texts.dropLast(1) + updated) }
            recomputePreviews(autoSave = false)
        } else if (stickers.isNotEmpty()) {
            val last = stickers.last()
            val updated = last.copy(
                normX = (last.normX + dx).coerceIn(0.05f, 0.95f),
                normY = (last.normY + dy).coerceIn(0.05f, 0.95f)
            )
            _editor.update { it.copy(stickerOverlays = stickers.dropLast(1) + updated) }
            recomputePreviews(autoSave = false)
        }
    }

    fun clearOverlays() {
        pushUndoSnapshot()
        _editor.update { it.copy(textOverlays = emptyList(), stickerOverlays = emptyList()) }
        recomputePreviews()
    }

    fun toggleBeforeAfter(show: Boolean) {
        _editor.update { it.copy(showBeforeAfter = show) }
    }

    // === UNDO / REDO ===

    private fun pushUndoSnapshot() {
        val st = _editor.value
        val snap = EditorSnapshot(
            maskBitmap = st.rawMask?.copy(Bitmap.Config.ARGB_8888, true),
            objSelectionMask = st.objSelectionMask?.copy(Bitmap.Config.ARGB_8888, true),
            cleanBgBitmap = st.cleanBgBitmap?.copy(Bitmap.Config.ARGB_8888, true),
            edgeSettings = st.edgeSettings,
            backgroundSpec = st.backgroundSpec,
            subjectTransform = st.subjectTransform,
            bgTransform = st.bgTransform,
            adjustments = st.adjustments,
            canvasPreset = st.canvasPreset,
            customCanvasW = st.customCanvasW,
            customCanvasH = st.customCanvasH,
            shadowEnabled = st.shadowEnabled,
            shadowOpacity = st.shadowOpacity,
            outlineEnabled = st.outlineEnabled,
            outlineThickness = st.outlineThickness,
            outlineColorArgb = st.outlineColorArgb,
            mixKaroEnabled = st.mixKaroEnabled,
            textOverlays = st.textOverlays,
            stickerOverlays = st.stickerOverlays
        )
        if (undoStack.size >= 15) undoStack.removeFirst()
        undoStack.addLast(snap)
        redoStack.clear()
        _editor.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val cur = captureCurrentSnapshot()
        redoStack.addLast(cur)
        restoreSnapshot(undoStack.removeLast())
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val cur = captureCurrentSnapshot()
        undoStack.addLast(cur)
        restoreSnapshot(redoStack.removeLast())
    }

    private fun captureCurrentSnapshot(): EditorSnapshot {
        val st = _editor.value
        return EditorSnapshot(
            maskBitmap = st.rawMask?.copy(Bitmap.Config.ARGB_8888, true),
            objSelectionMask = st.objSelectionMask?.copy(Bitmap.Config.ARGB_8888, true),
            cleanBgBitmap = st.cleanBgBitmap?.copy(Bitmap.Config.ARGB_8888, true),
            edgeSettings = st.edgeSettings,
            backgroundSpec = st.backgroundSpec,
            subjectTransform = st.subjectTransform,
            bgTransform = st.bgTransform,
            adjustments = st.adjustments,
            canvasPreset = st.canvasPreset,
            customCanvasW = st.customCanvasW,
            customCanvasH = st.customCanvasH,
            shadowEnabled = st.shadowEnabled,
            shadowOpacity = st.shadowOpacity,
            outlineEnabled = st.outlineEnabled,
            outlineThickness = st.outlineThickness,
            outlineColorArgb = st.outlineColorArgb,
            mixKaroEnabled = st.mixKaroEnabled,
            textOverlays = st.textOverlays,
            stickerOverlays = st.stickerOverlays
        )
    }

    private fun restoreSnapshot(snap: EditorSnapshot) {
        _editor.update {
            it.copy(
                rawMask = snap.maskBitmap,
                objSelectionMask = snap.objSelectionMask,
                cleanBgBitmap = snap.cleanBgBitmap,
                edgeSettings = snap.edgeSettings,
                backgroundSpec = snap.backgroundSpec,
                subjectTransform = snap.subjectTransform,
                bgTransform = snap.bgTransform,
                adjustments = snap.adjustments,
                canvasPreset = snap.canvasPreset,
                customCanvasW = snap.customCanvasW,
                customCanvasH = snap.customCanvasH,
                shadowEnabled = snap.shadowEnabled,
                shadowOpacity = snap.shadowOpacity,
                outlineEnabled = snap.outlineEnabled,
                outlineThickness = snap.outlineThickness,
                outlineColorArgb = snap.outlineColorArgb,
                mixKaroEnabled = snap.mixKaroEnabled,
                textOverlays = snap.textOverlays,
                stickerOverlays = snap.stickerOverlays,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        recomputePreviews()
    }

    // === PREVIEW RENDERING & AUTO-SAVE ===

    private fun recomputePreviews(autoSave: Boolean = true) {
        val st = _editor.value
        val working = st.originalWorking ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val adjustedSrc = BitmapUtils.applyAdjustmentsAndFilter(working, st.adjustments)
            val cutout = st.rawMask?.let { mask ->
                val adjMask = BitmapUtils.applyAdjustmentsAndFilter(mask, st.adjustments.copy(brightness = 0f, contrast = 1f, saturation = 1f, sharpness = 0f, filter = com.example.data.model.ImageFilter.NONE))
                val c = segEngine.applyCutoutWithRefinement(adjustedSrc, adjMask, st.edgeSettings)
                if (adjMask !== mask) adjMask.recycle()
                c
            }

            val subjectForComposite = st.pastedSubjectBitmap ?: cutout
            val bgPreview = subjectForComposite?.let { sub ->
                BitmapUtils.composeCutoutWithBackground(
                    context = appContext,
                    original = adjustedSrc,
                    cutout = sub,
                    bgSpec = st.backgroundSpec,
                    subjectTransform = st.subjectTransform,
                    bgTransform = st.bgTransform,
                    shadowEnabled = st.shadowEnabled,
                    shadowOpacity = st.shadowOpacity,
                    outlineEnabled = st.outlineEnabled,
                    outlineThickness = st.outlineThickness,
                    outlineColorArgb = st.outlineColorArgb,
                    mixKaroEnabled = st.mixKaroEnabled,
                    canvasPreset = st.canvasPreset,
                    customW = st.customCanvasW,
                    customH = st.customCanvasH,
                    textOverlays = st.textOverlays,
                    stickerOverlays = st.stickerOverlays
                )
            }

            val adjustedCleanBg = st.cleanBgBitmap?.let { BitmapUtils.applyAdjustmentsAndFilter(it, st.adjustments) }
            val bothCombined = if (subjectForComposite != null && adjustedCleanBg != null) {
                BitmapUtils.composeCutoutWithBackground(
                    context = appContext,
                    original = adjustedSrc,
                    cutout = subjectForComposite,
                    bgSpec = BackgroundSpec(type = BackgroundType.TRANSPARENT),
                    subjectTransform = st.subjectTransform,
                    bgTransform = st.bgTransform,
                    cleanBgOverride = adjustedCleanBg,
                    shadowEnabled = true,
                    shadowOpacity = 0.45f,
                    outlineEnabled = st.outlineEnabled,
                    outlineThickness = st.outlineThickness,
                    outlineColorArgb = st.outlineColorArgb,
                    mixKaroEnabled = st.mixKaroEnabled,
                    canvasPreset = st.canvasPreset,
                    customW = st.customCanvasW,
                    customH = st.customCanvasH,
                    textOverlays = st.textOverlays,
                    stickerOverlays = st.stickerOverlays
                )
            } else null

            _editor.update {
                it.copy(
                    refinedCutout = cutout,
                    composedPreview = bgPreview,
                    bothCombinedPreview = bothCombined
                )
            }

            if (autoSave && settings.value.autoSave) {
                val thumb = when (st.activeMode) {
                    EditMode.BG_REMOVE -> bgPreview ?: cutout ?: adjustedSrc
                    EditMode.OBJECT_REMOVE -> adjustedCleanBg ?: adjustedSrc
                    EditMode.BOTH -> bothCombined ?: bgPreview ?: adjustedSrc
                }
                val savedEntity = projectRepo.saveProject(
                    existingId = st.projectId,
                    name = st.projectName,
                    mode = st.activeMode,
                    originalFull = st.originalFullPath?.let { projectRepo.loadBitmapFromPath(it) } ?: working,
                    maskBitmap = st.rawMask,
                    cleanBgBitmap = st.cleanBgBitmap,
                    previewThumb = thumb,
                    state = PersistedProjectState(
                        subjectType = st.subjectType,
                        edgeSettings = st.edgeSettings,
                        backgroundSpec = st.backgroundSpec,
                        subjectTransform = st.subjectTransform,
                        bgTransform = st.bgTransform,
                        adjustments = st.adjustments,
                        canvasPreset = st.canvasPreset,
                        customCanvasW = st.customCanvasW,
                        customCanvasH = st.customCanvasH,
                        shadowEnabled = st.shadowEnabled,
                        shadowOpacity = st.shadowOpacity,
                        outlineEnabled = st.outlineEnabled,
                        outlineThickness = st.outlineThickness,
                        outlineColorArgb = st.outlineColorArgb,
                        mixKaroEnabled = st.mixKaroEnabled,
                        removeShadowReflection = st.removeShadowReflection,
                        textOverlays = st.textOverlays,
                        stickerOverlays = st.stickerOverlays
                    )
                )
                _editor.update { it.copy(projectId = savedEntity.id) }
            }
            if (adjustedSrc !== working) adjustedSrc.recycle()
        }
    }

    // === EXPORT SCREEN PREP & EXECUTION ===

    fun prepareExportScreen() {
        val st = _editor.value
        val smartName = generateSmartFileName(st.projectName, st.activeMode)
        val cutoutPreview = st.composedPreview ?: st.refinedCutout
        val cleanPreview = st.cleanBgBitmap
        val combinedPreview = st.bothCombinedPreview ?: cutoutPreview
        _export.update {
            it.copy(
                config = it.config.copy(fileName = smartName),
                cutoutThumb = cutoutPreview,
                cleanBgThumb = cleanPreview,
                combinedThumb = combinedPreview,
                resultInfo = null,
                errorHinglish = null,
                collisionFileName = null
            )
        }
    }

    fun updateExportConfig(update: (com.example.data.model.ExportConfig) -> com.example.data.model.ExportConfig) {
        _export.update { it.copy(config = update(it.config), errorHinglish = null) }
    }

    fun generatePreviewForOption(option: ExportOption, onReady: () -> Unit) {
        viewModelScope.launch {
            _export.update { it.copy(isExporting = true, progress = 0.2f, statusText = "Preview taiyaar ho raha hai...") }
            ensureMissingResults(option)
            val st = _editor.value
            val preview = when (option) {
                ExportOption.BG_REMOVE -> st.composedPreview ?: st.refinedCutout
                ExportOption.OBJECT_REMOVE -> st.cleanBgBitmap ?: st.originalWorking
                ExportOption.BOTH -> st.bothCombinedPreview ?: st.composedPreview ?: st.refinedCutout
            }
            _export.update { it.copy(isExporting = false, fullPreviewBitmap = preview) }
            onReady()
        }
    }

    fun triggerExportOption(
        option: ExportOption,
        overwriteExisting: Boolean = false,
        autoNumberOnConflict: Boolean = false
    ) {
        val cfg = _export.value.config.copy(option = option)
        _export.update { it.copy(config = cfg, errorHinglish = null, collisionFileName = null) }

        if (!overwriteExisting && !autoNumberOnConflict) {
            when (val check = exportEngine.checkExportPreconditions(cfg)) {
                is ExportCheckResult.ErrorHinglish -> {
                    _export.update { it.copy(errorHinglish = check.message) }
                    return
                }
                is ExportCheckResult.NameCollision -> {
                    if (settings.value.askBeforeSaving) {
                        _export.update {
                            it.copy(
                                collisionFileName = check.existingName,
                                pendingCollisionOption = option
                            )
                        }
                        return
                    }
                }
                ExportCheckResult.Ok -> {}
            }
        }

        viewModelScope.launch {
            _export.update { it.copy(isExporting = true, progress = 0.08f, statusText = "Result check ho raha hai...") }
            ensureMissingResults(option)

            val st = _editor.value
            // Full resolution rendering from originalFullPath when available!
            val (fullCutout, fullCleanBg, fullCombined) = renderFullResolutionOutputs(option, st)

            val res = exportEngine.performExport(
                config = cfg,
                cutoutFull = fullCutout,
                cleanBgFull = fullCleanBg,
                combinedFull = fullCombined,
                projectId = st.projectId,
                overwriteExisting = overwriteExisting,
                autoNumberOnConflict = autoNumberOnConflict || !settings.value.askBeforeSaving
            ) { p, msg ->
                _export.update { it.copy(progress = 0.5f + p * 0.5f, statusText = msg) }
            }

            res.onSuccess { info ->
                _export.update {
                    it.copy(
                        isExporting = false,
                        resultInfo = info,
                        cutoutThumb = st.composedPreview ?: st.refinedCutout,
                        cleanBgThumb = st.cleanBgBitmap,
                        combinedThumb = st.bothCombinedPreview
                    )
                }
            }.onFailure { err ->
                _export.update {
                    it.copy(
                        isExporting = false,
                        errorHinglish = err.message ?: "Save karne mein dikkat aayi. Storage aur permission check karein."
                    )
                }
            }
        }
    }

    private suspend fun ensureMissingResults(option: ExportOption) {
        val st = _editor.value
        val working = st.originalWorking ?: return
        val needsCutout = option == ExportOption.BG_REMOVE || option == ExportOption.BOTH
        val needsCleanBg = option == ExportOption.OBJECT_REMOVE || option == ExportOption.BOTH

        if (needsCutout && st.rawMask == null) {
            _export.update { it.copy(progress = 0.2f, statusText = "Cutout automatically ban raha hai...") }
            val seg = segEngine.detectSubjectMask(working, st.subjectType, st.edgeSettings.hairDetailMode, settings.value.segmentationQuality)
            _editor.update { it.copy(rawMask = seg.mask) }
        }
        if (needsCleanBg && st.cleanBgBitmap == null) {
            _export.update { it.copy(progress = 0.35f, statusText = "Subject hata kar saaf background ban raha hai...") }
            val maskForInpaint = _editor.value.rawMask?.let { inpaintEngine.growSelectionMask(it, 6) }
                ?: inpaintEngine.detectSubjectForRemoval(working, st.removeShadowReflection, settings.value.segmentationQuality).mask
            val out = inpaintEngine.inpaintWithVariations(working, maskForInpaint, settings.value.fillQuality, false)
            _editor.update { it.copy(cleanBgBitmap = out.resultBitmap) }
        }
        recomputePreviews(autoSave = false)
    }

    private suspend fun renderFullResolutionOutputs(
        option: ExportOption,
        st: EditorUiState
    ): Triple<Bitmap?, Bitmap?, Bitmap?> = withContext(Dispatchers.Default) {
        val fullOrig = st.originalFullPath?.let { projectRepo.loadBitmapFromPath(it) }
            ?: st.originalWorking
            ?: return@withContext Triple(null, null, null)

        val maxExportLimit = 3840
        val safeFull = BitmapUtils.scaleDownToMax(fullOrig, maxExportLimit)
        val adjustedFull = BitmapUtils.applyAdjustmentsAndFilter(safeFull, st.adjustments)

        val needCutout = option == ExportOption.BG_REMOVE || option == ExportOption.BOTH
        val needClean = option == ExportOption.OBJECT_REMOVE || option == ExportOption.BOTH

        val fullCutout = if (needCutout) {
            val subCut = if (st.pastedSubjectBitmap != null) {
                st.pastedSubjectBitmap
            } else {
                val mask = st.rawMask
                if (mask != null) {
                    val upscaledMask = Bitmap.createScaledBitmap(mask, adjustedFull.width, adjustedFull.height, true)
                    segEngine.applyCutoutWithRefinement(adjustedFull, upscaledMask, st.edgeSettings).also {
                        upscaledMask.recycle()
                    }
                } else null
            }
            if (subCut != null && st.backgroundSpec.type != BackgroundType.TRANSPARENT) {
                BitmapUtils.composeCutoutWithBackground(
                    context = appContext,
                    original = adjustedFull,
                    cutout = subCut,
                    bgSpec = st.backgroundSpec,
                    subjectTransform = st.subjectTransform,
                    bgTransform = st.bgTransform,
                    shadowEnabled = st.shadowEnabled,
                    shadowOpacity = st.shadowOpacity,
                    outlineEnabled = st.outlineEnabled,
                    outlineThickness = st.outlineThickness,
                    outlineColorArgb = st.outlineColorArgb,
                    mixKaroEnabled = st.mixKaroEnabled,
                    canvasPreset = st.canvasPreset,
                    customW = st.customCanvasW,
                    customH = st.customCanvasH,
                    textOverlays = st.textOverlays,
                    stickerOverlays = st.stickerOverlays
                )
            } else if (subCut != null && (st.outlineEnabled || st.shadowEnabled || st.textOverlays.isNotEmpty() || st.stickerOverlays.isNotEmpty() || st.canvasPreset != CanvasPreset.ORIGINAL)) {
                BitmapUtils.composeCutoutWithBackground(
                    context = appContext,
                    original = adjustedFull,
                    cutout = subCut,
                    bgSpec = BackgroundSpec(type = BackgroundType.TRANSPARENT),
                    subjectTransform = st.subjectTransform,
                    shadowEnabled = st.shadowEnabled,
                    shadowOpacity = st.shadowOpacity,
                    outlineEnabled = st.outlineEnabled,
                    outlineThickness = st.outlineThickness,
                    outlineColorArgb = st.outlineColorArgb,
                    canvasPreset = st.canvasPreset,
                    customW = st.customCanvasW,
                    customH = st.customCanvasH,
                    textOverlays = st.textOverlays,
                    stickerOverlays = st.stickerOverlays
                )
            } else subCut
        } else null

        val fullCleanBg = if (needClean) {
            val clean = st.cleanBgBitmap
            if (clean != null) {
                val upClean = Bitmap.createScaledBitmap(clean, adjustedFull.width, adjustedFull.height, true)
                BitmapUtils.applyAdjustmentsAndFilter(upClean, st.adjustments)
            } else adjustedFull
        } else null

        val fullCombined = if (option == ExportOption.BOTH && fullCutout != null && fullCleanBg != null) {
            BitmapUtils.composeCutoutWithBackground(
                context = appContext,
                original = adjustedFull,
                cutout = st.pastedSubjectBitmap ?: st.refinedCutout ?: fullCutout,
                bgSpec = BackgroundSpec(type = BackgroundType.TRANSPARENT),
                subjectTransform = st.subjectTransform,
                cleanBgOverride = fullCleanBg,
                shadowEnabled = true,
                shadowOpacity = 0.45f,
                outlineEnabled = st.outlineEnabled,
                outlineThickness = st.outlineThickness,
                outlineColorArgb = st.outlineColorArgb,
                mixKaroEnabled = st.mixKaroEnabled,
                canvasPreset = st.canvasPreset,
                customW = st.customCanvasW,
                customH = st.customCanvasH,
                textOverlays = st.textOverlays,
                stickerOverlays = st.stickerOverlays
            )
        } else null

        Triple(fullCutout, fullCleanBg, fullCombined)
    }

    fun exportWhatsAppStickerShortcut() {
        viewModelScope.launch {
            _export.update { it.copy(isExporting = true, progress = 0.4f, statusText = "WhatsApp sticker (512x512) ban raha hai...") }
            ensureMissingResults(ExportOption.BG_REMOVE)
            val cut = _editor.value.refinedCutout ?: run {
                _export.update { it.copy(isExporting = false, errorHinglish = "Sticker ke liye cutout nahi mila.") }
                return@launch
            }
            exportEngine.exportWhatsAppSticker(cut, _export.value.config.fileName, _editor.value.outlineColorArgb, _editor.value.projectId)
                .onSuccess { saved ->
                    _export.update {
                        it.copy(isExporting = false, resultInfo = com.example.data.model.ExportResultInfo(listOf(saved), saved.sizeBytes))
                    }
                }.onFailure { err ->
                    _export.update { it.copy(isExporting = false, errorHinglish = err.message ?: "Sticker export fail hua.") }
                }
        }
    }

    fun addCurrentToStickerPack() {
        viewModelScope.launch {
            ensureMissingResults(ExportOption.BG_REMOVE)
            val cut = _editor.value.refinedCutout ?: return@launch
            _export.update { it.copy(stickerPackItems = it.stickerPackItems + cut.copy(Bitmap.Config.ARGB_8888, true)) }
        }
    }

    fun exportStickerPack() {
        val list = _export.value.stickerPackItems
        if (list.isEmpty()) return
        viewModelScope.launch {
            _export.update { it.copy(isExporting = true, progress = 0.5f, statusText = "Sticker pack ZIP ban raha hai...") }
            exportEngine.exportStickerPackZip(list, _export.value.config.fileName)
                .onSuccess { saved ->
                    _export.update {
                        it.copy(isExporting = false, resultInfo = com.example.data.model.ExportResultInfo(listOf(saved), saved.sizeBytes))
                    }
                }.onFailure { err ->
                    _export.update { it.copy(isExporting = false, errorHinglish = err.message ?: "Sticker pack export fail hua.") }
                }
        }
    }

    fun exportPassportSheetShortcut(asPdf: Boolean) {
        viewModelScope.launch {
            _export.update { it.copy(isExporting = true, progress = 0.45f, statusText = "Passport sheet ban rahi hai...") }
            ensureMissingResults(ExportOption.BG_REMOVE)
            val cut = _editor.value.composedPreview ?: _editor.value.refinedCutout ?: _editor.value.originalWorking ?: return@launch
            exportEngine.exportPassportSheet(cut, _export.value.config.fileName, asPdf = asPdf, copiesCount = 8, projectId = _editor.value.projectId)
                .onSuccess { saved ->
                    _export.update { it.copy(isExporting = false, resultInfo = com.example.data.model.ExportResultInfo(listOf(saved), saved.sizeBytes)) }
                }.onFailure { err ->
                    _export.update { it.copy(isExporting = false, errorHinglish = err.message ?: "Passport sheet fail hui.") }
                }
        }
    }

    fun exportPrintReadyA4Shortcut() {
        viewModelScope.launch {
            _export.update { it.copy(isExporting = true, progress = 0.45f, statusText = "A4 Print sheet ban rahi hai...") }
            val bmp = _editor.value.composedPreview ?: _editor.value.cleanBgBitmap ?: _editor.value.refinedCutout ?: _editor.value.originalWorking ?: return@launch
            exportEngine.exportPrintReadyA4(bmp, _export.value.config.fileName, _editor.value.projectId)
                .onSuccess { saved ->
                    _export.update { it.copy(isExporting = false, resultInfo = com.example.data.model.ExportResultInfo(listOf(saved), saved.sizeBytes)) }
                }.onFailure { err ->
                    _export.update { it.copy(isExporting = false, errorHinglish = err.message ?: "A4 Print sheet fail hui.") }
                }
        }
    }

    fun clearExportResult() {
        _export.update { it.copy(resultInfo = null, errorHinglish = null, collisionFileName = null) }
    }

    // === BATCH MODE ===

    fun setBatchUris(uris: List<Uri>) {
        val items = uris.mapIndexed { idx, uri ->
            BatchItem(
                id = UUID.randomUUID().toString(),
                sourceUri = uri,
                fileName = "batch_photo_${idx + 1}"
            )
        }
        _batch.update { it.copy(items = items, completedCount = 0, resultMessage = null) }
    }

    fun setBatchMode(mode: EditMode) {
        _batch.update { it.copy(mode = mode) }
    }

    fun setBatchBg(spec: BackgroundSpec) {
        _batch.update { it.copy(sharedBgSpec = spec) }
    }

    fun setBatchExportOption(option: ExportOption, saveAsZip: Boolean) {
        _batch.update { it.copy(exportOption = option, saveAsZip = saveAsZip) }
    }

    fun startBatchProcessing() {
        val list = _batch.value.items
        if (list.isEmpty() || _batch.value.isRunning) return
        batchJob = viewModelScope.launch(Dispatchers.Default) {
            _batch.update { it.copy(isRunning = true, completedCount = 0, resultMessage = null) }
            val mode = _batch.value.mode
            val sharedBg = _batch.value.sharedBgSpec
            val opt = _batch.value.exportOption
            var doneCount = 0

            exportEngine.showBatchProgressNotification(0, list.size, false)

            for (i in list.indices) {
                val item = list[i]
                updateBatchItem(item.id) { it.copy(status = BatchStatus.PROCESSING, progress = 0.2f) }
                val res = runCatching {
                    val bmp = BitmapUtils.decodeUriSafe(appContext, item.sourceUri, settings.value.segmentationQuality.workingMaxDim)
                        ?: throw IllegalStateException("Decode fail")
                    val seg = segEngine.detectSubjectMask(bmp, SubjectType.AUTO, hairDetail = false, quality = settings.value.segmentationQuality)
                    val cutout = segEngine.applyCutoutWithRefinement(bmp, seg.mask, EdgeSettings())
                    val composedCutout = if (sharedBg.type != BackgroundType.TRANSPARENT) {
                        BitmapUtils.composeCutoutWithBackground(appContext, bmp, cutout, sharedBg)
                    } else cutout

                    val cleanBg = if (mode != EditMode.BG_REMOVE || opt != ExportOption.BG_REMOVE) {
                        val grown = inpaintEngine.growSelectionMask(seg.mask, 6)
                        inpaintEngine.inpaintWithVariations(bmp, grown, QualityMode.FAST, false).resultBitmap.also { grown.recycle() }
                    } else null

                    val cfg = _export.value.config.copy(
                        option = opt,
                        fileName = item.fileName,
                        saveAsZipForBoth = _batch.value.saveAsZip
                    )
                    exportEngine.performExport(cfg, composedCutout, cleanBg, null, null, overwriteExisting = false, autoNumberOnConflict = true) { _, _ -> }
                    bmp.recycle()
                    seg.mask.recycle()
                    if (composedCutout !== cutout) composedCutout.recycle()
                    cutout.recycle()
                    cleanBg?.recycle()
                }

                doneCount++
                updateBatchItem(item.id) {
                    if (res.isSuccess) it.copy(status = BatchStatus.DONE, progress = 1f)
                    else it.copy(status = BatchStatus.FAILED, progress = 0f, errorMsg = res.exceptionOrNull()?.message)
                }
                _batch.update { it.copy(completedCount = doneCount) }
                exportEngine.showBatchProgressNotification(doneCount, list.size, false)
            }
            exportEngine.showBatchProgressNotification(doneCount, list.size, true)
            _batch.update { it.copy(isRunning = false, resultMessage = "$doneCount / ${list.size} photos export ho gayi hain!") }
        }
    }

    fun cancelBatchProcessing() {
        batchJob?.cancel()
        _batch.update { it.copy(isRunning = false, resultMessage = "Batch export roka gaya.") }
    }

    private fun updateBatchItem(id: String, transform: (BatchItem) -> BatchItem) {
        _batch.update { state ->
            state.copy(items = state.items.map { if (it.id == id) transform(it) else it })
        }
    }

    // === VIDEO BETA ===

    fun loadVideoClip(uri: Uri) {
        viewModelScope.launch {
            _video.update { it.copy(isProcessing = true, statusText = "Video check ho raha hai...", errorHinglish = null, exportedUri = null) }
            val info = runCatching { videoProcessor.inspectVideo(uri) }.getOrNull()
            if (info == null || info.originalPreviewSafe()) {
                _video.update { it.copy(isProcessing = false, errorHinglish = "Video load nahi ho saka.") }
                return@launch
            }
            if (info.durationMs > 16_000L) {
                _video.update {
                    it.copy(
                        isProcessing = false,
                        errorHinglish = "Beta mode mein sirf 15 second tak ka video chalta hai (${info.durationMs / 1000}s mila)."
                    )
                }
                return@launch
            }
            val prev = info.previewFrame?.let { videoProcessor.renderPreviewFrame(it, _video.value.bgSpec) }
            _video.update {
                it.copy(
                    selectedUri = uri,
                    durationMs = info.durationMs,
                    originalFrame = info.previewFrame,
                    previewFrame = prev,
                    isProcessing = false
                )
            }
        }
    }

    private fun com.example.ai.VideoMetadataInfo.originalPreviewSafe(): Boolean = previewFrame == null

    fun updateVideoBg(spec: BackgroundSpec) {
        _video.update { it.copy(bgSpec = spec) }
        val orig = _video.value.originalFrame ?: return
        viewModelScope.launch {
            val prev = videoProcessor.renderPreviewFrame(orig, spec)
            _video.update { it.copy(previewFrame = prev) }
        }
    }

    fun setVideoFormat(fmt: VideoExportFormat) {
        _video.update { it.copy(format = fmt) }
    }

    fun exportVideoBeta() {
        val uri = _video.value.selectedUri ?: return
        viewModelScope.launch {
            _video.update { it.copy(isProcessing = true, progress = 0.05f, statusText = "Video process ho raha hai...", errorHinglish = null) }
            val ext = _video.value.format.ext
            val dir = File(appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appContext.filesDir, "CutoutProVideo").apply { mkdirs() }
            val outFile = File(dir, "cutout_video_${System.currentTimeMillis()}.$ext")
            runCatching {
                videoProcessor.exportVideo(uri, _video.value.bgSpec, _video.value.format, outFile) { p, msg ->
                    _video.update { it.copy(progress = p, statusText = msg) }
                }
            }.onSuccess { file ->
                val contentUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
                _video.update { it.copy(isProcessing = false, exportedUri = contentUri, statusText = "Video save ho gaya: ${file.name}") }
            }.onFailure { err ->
                _video.update { it.copy(isProcessing = false, errorHinglish = err.message ?: "Video export fail hua.") }
            }
        }
    }

    // === PROJECTS & DOWNLOADS MANAGEMENT ===

    fun renameProject(id: String, name: String) {
        viewModelScope.launch { projectRepo.renameProject(id, name) }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch { projectRepo.duplicateProject(id) }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch { projectRepo.deleteProject(id) }
    }

    fun renameDownload(item: DownloadEntity, newName: String) {
        viewModelScope.launch { downloadRepo.renameDownload(item, newName) }
    }

    fun deleteDownload(item: DownloadEntity) {
        viewModelScope.launch { downloadRepo.deleteDownload(item) }
    }

    fun reExportDownload(item: DownloadEntity, onOpenExport: () -> Unit) {
        val pid = item.projectId ?: return
        viewModelScope.launch {
            val proj = projectRepo.getProject(pid) ?: return@launch
            openSavedProject(proj) {
                prepareExportScreen()
                onOpenExport()
            }
        }
    }

    fun createCameraOutputUri(): Uri {
        val dir = File(appContext.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    }

    private fun generateSmartFileName(projectName: String, mode: EditMode): String {
        val pattern = settings.value.fileNamePattern
        val date = SimpleDateFormat("ddMM_HHmm", Locale.US).format(Date())
        val modeSlug = when (mode) {
            EditMode.BG_REMOVE -> "bg"
            EditMode.OBJECT_REMOVE -> "clean"
            EditMode.BOTH -> "dono"
        }
        val cleanProj = projectName.trim().replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifEmpty { "photo" }
        return pattern
            .replace("{date}", date)
            .replace("{mode}", modeSlug)
            .replace("{name}", cleanProj)
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
    }

    private fun shortTimestamp(): String = SimpleDateFormat("ddMMM_HHmm", Locale.US).format(Date())
}
