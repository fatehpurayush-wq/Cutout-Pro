package com.example.data.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.example.R

enum class EditMode(val labelHinglish: String, val shortTag: String, val descriptionHinglish: String) {
    BG_REMOVE(
        labelHinglish = "Background hatao",
        shortTag = "BG Hatao",
        descriptionHinglish = "Subject rakho, peeche ka background saaf karo"
    ),
    OBJECT_REMOVE(
        labelHinglish = "Subject hatao",
        shortTag = "Subject Hatao",
        descriptionHinglish = "Koi bhi cheez ya insaan hatao aur natural bharo"
    ),
    BOTH(
        labelHinglish = "Dono",
        shortTag = "Dono",
        descriptionHinglish = "Subject cutout aur saaf background dono ek saath pao"
    )
}

enum class SubjectType(val labelHinglish: String) {
    AUTO("Auto"),
    PERSON("Person"),
    OBJECT("Object")
}

enum class QualityMode(val labelHinglish: String, val workingMaxDim: Int) {
    FAST("Fast", 640),
    HIGH("High quality", 1024)
}

enum class BothTab(val labelHinglish: String) {
    SUBJECT("Subject"),
    BACKGROUND("Background"),
    COMBINED("Combined")
}

enum class CanvasPreset(
    val label: String,
    val ratioW: Int,
    val ratioH: Int,
    val exactW: Int? = null,
    val exactH: Int? = null
) {
    ORIGINAL("Original", 0, 0),
    RATIO_1_1("1:1 Square", 1, 1),
    RATIO_4_5("4:5 Portrait", 4, 5),
    RATIO_9_16("9:16 Story", 9, 16),
    RATIO_16_9("16:9 Wide", 16, 9),
    PASSPORT_INDIA("Passport (35x45)", 35, 45, 827, 1063),
    ID_PHOTO("ID Card (3:4)", 3, 4, 900, 1200),
    YT_THUMB("YT Thumbnail", 16, 9, 1280, 720),
    IG_POST("IG Post (1080)", 1, 1, 1080, 1080),
    CUSTOM("Custom", 1, 1)
}

enum class BackgroundType {
    TRANSPARENT,
    SOLID,
    GRADIENT,
    BUILTIN_STUDIO,
    BLURRED_ORIGINAL,
    GALLERY_IMAGE
}

data class BackgroundSpec(
    val type: BackgroundType = BackgroundType.TRANSPARENT,
    val solidColorArgb: Int = 0xFFFFFFFF.toInt(),
    val gradientStartArgb: Int = 0xFF0F2027.toInt(),
    val gradientEndArgb: Int = 0xFF2C5364.toInt(),
    val builtinId: String = "studio_glow",
    val blurRadius: Float = 24f,
    val customImagePath: String? = null
)

data class BuiltinBackgroundOption(
    val id: String,
    val label: String,
    val drawableRes: Int? = null,
    val patternType: String? = null,
    val primaryColor: Color = Color(0xFF1A2236),
    val secondaryColor: Color = Color(0xFF0E1320)
)

val BuiltinBackgrounds = listOf(
    BuiltinBackgroundOption("studio_glow", "Studio Glow", drawableRes = R.drawable.img_bg_studio_glow),
    BuiltinBackgroundOption("sunset_arch", "Sunset Wall", drawableRes = R.drawable.img_bg_sunset_minimal),
    BuiltinBackgroundOption("pattern_dots", "Polka Dots", patternType = "dots", primaryColor = Color(0xFF182238), secondaryColor = Color(0xFF00E5FF)),
    BuiltinBackgroundOption("pattern_grid", "Studio Grid", patternType = "grid", primaryColor = Color(0xFF101828), secondaryColor = Color(0xFF2D3E5E)),
    BuiltinBackgroundOption("pattern_diagonal", "Neon Stripes", patternType = "stripes", primaryColor = Color(0xFF201433), secondaryColor = Color(0xFFFF6B4A)),
    BuiltinBackgroundOption("studio_white", "Clean Studio", patternType = "radial", primaryColor = Color(0xFFF6F7FB), secondaryColor = Color(0xFFD6DCE8))
)

data class EdgeSettings(
    val feather: Float = 2.0f,         // 0..20
    val smooth: Float = 1.5f,          // 0..10
    val growShrink: Float = 0f,        // -12..12 px
    val defringeStrength: Float = 0.4f,// 0..1
    val hairDetailMode: Boolean = false
)

data class LayerTransform(
    val offsetX: Float = 0f,           // normalized -1..1 relative to canvas width
    val offsetY: Float = 0f,           // normalized -1..1 relative to canvas height
    val scale: Float = 1f,             // 0.2..3.0
    val rotationDeg: Float = 0f,       // -180..180
    val flipH: Boolean = false,
    val flipV: Boolean = false
)

data class AdjustmentParams(
    val brightness: Float = 0f,        // -100..100
    val contrast: Float = 1f,          // 0.5..2.0
    val saturation: Float = 1f,        // 0..2.0
    val sharpness: Float = 0f,         // 0..1.0
    val filter: ImageFilter = ImageFilter.NONE,
    val cropLeft: Float = 0f,          // 0..1
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val canvasRotation: Int = 0        // 0, 90, 180, 270
)

enum class ImageFilter(val label: String) {
    NONE("Original"),
    VIVID("Vivid"),
    WARM("Warm"),
    COOL("Cool"),
    BW("B&W"),
    CINEMA("Cinema")
}

data class TextOverlay(
    val id: String,
    val text: String,
    val fontId: String = "jakarta",
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val sizeSp: Float = 28f,
    val normX: Float = 0.5f,
    val normY: Float = 0.25f,
    val hasShadow: Boolean = true
)

data class StickerOverlay(
    val id: String,
    val symbol: String,
    val normX: Float = 0.5f,
    val normY: Float = 0.5f,
    val scale: Float = 1f
)

enum class ExportOption(val titleHinglish: String, val subtitleHinglish: String) {
    BG_REMOVE("Background hatao download", "Transparent cutout (subject sirf)"),
    OBJECT_REMOVE("Subject hatao download", "Saaf background (bina subject ke)"),
    BOTH("Dono download", "Cutout + saaf background (aur combined)")
}

enum class ExportFormat(val label: String, val ext: String, val supportsTransparency: Boolean) {
    PNG("PNG", "png", true),
    WEBP("WEBP", "webp", true),
    JPG("JPG", "jpg", false)
}

enum class ExportSizePreset(val labelHinglish: String, val maxDim: Int?) {
    ORIGINAL("Original full size", null),
    UHD_4K("4K (3840px)", 3840),
    QHD_2K("2K (2560px)", 2560),
    FHD_1080P("1080p HD", 1920),
    HD_720P("720p Fast", 1280),
    CUSTOM("Custom size", null)
}

data class ExportConfig(
    val option: ExportOption = ExportOption.BG_REMOVE,
    val format: ExportFormat = ExportFormat.PNG,
    val quality: Int = 92,
    val sizePreset: ExportSizePreset = ExportSizePreset.ORIGINAL,
    val customWidth: Int = 1080,
    val customHeight: Int = 1080,
    val jpgFillColorArgb: Int = 0xFFFFFFFF.toInt(),
    val trimTransparent: Boolean = false,
    val fileName: String = "cutout_pro_edit",
    val saveAsZipForBoth: Boolean = true,
    val useCustomFolder: Boolean = false,
    val customFolderUri: String? = null
)

data class ExportResultInfo(
    val savedFiles: List<SavedOutputFile>,
    val totalSizeBytes: Long,
    val noteHinglish: String? = null
)

data class SavedOutputFile(
    val displayName: String,
    val uriString: String,
    val mimeType: String,
    val sizeBytes: Long,
    val option: ExportOption
)

enum class BgBrushTool {
    NONE,
    ERASE,
    RESTORE,
    SMART_BRUSH,
    TAP_KEEP,
    TAP_REMOVE,
    LASSO
}

enum class ObjectTool {
    AUTO,
    TAP_SELECT,
    ADD_BRUSH,
    ERASE_BRUSH,
    LASSO,
    CLONE_STAMP,
    HEALING_BRUSH,
    PATCH_TOOL
}

data class FillVariation(
    val id: Int,
    val label: String,
    val bitmap: Bitmap
)

enum class BatchStatus(val labelHinglish: String) {
    PENDING("Ruka hua"),
    PROCESSING("Ban raha hai..."),
    DONE("Taiyaar"),
    FAILED("Error")
}

data class BatchItem(
    val id: String,
    val sourceUri: Uri,
    val fileName: String,
    val status: BatchStatus = BatchStatus.PENDING,
    val progress: Float = 0f,
    val cutoutPath: String? = null,
    val cleanBgPath: String? = null,
    val errorMsg: String? = null
)

data class SamplePhotoOption(
    val id: String,
    val titleHinglish: String,
    val hintHinglish: String,
    val drawableRes: Int
)

val BuiltinSamplePhotos = listOf(
    SamplePhotoOption(
        id = "portrait",
        titleHinglish = "Portrait (Person)",
        hintHinglish = "Background hatao aur Baal detail try karo",
        drawableRes = R.drawable.img_sample_portrait
    ),
    SamplePhotoOption(
        id = "product",
        titleHinglish = "Product (Shoe & Cup)",
        hintHinglish = "Subject hatao ya Object cutout try karo",
        drawableRes = R.drawable.img_sample_product
    )
)
