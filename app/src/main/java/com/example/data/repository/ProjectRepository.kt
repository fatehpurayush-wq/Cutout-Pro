package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.model.AdjustmentParams
import com.example.data.model.BackgroundSpec
import com.example.data.model.BackgroundType
import com.example.data.model.CanvasPreset
import com.example.data.model.EdgeSettings
import com.example.data.model.EditMode
import com.example.data.model.ImageFilter
import com.example.data.model.LayerTransform
import com.example.data.model.StickerOverlay
import com.example.data.model.SubjectType
import com.example.data.model.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PersistedProjectState(
    val subjectType: SubjectType = SubjectType.AUTO,
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
    val removeShadowReflection: Boolean = true,
    val textOverlays: List<TextOverlay> = emptyList(),
    val stickerOverlays: List<StickerOverlay> = emptyList()
)

class ProjectRepository(
    private val context: Context,
    private val dao: ProjectDao
) {
    private val projectsRoot: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    val allProjects: Flow<List<ProjectEntity>> = dao.observeAll()
    val recentProjects: Flow<List<ProjectEntity>> = dao.observeRecent(10)

    suspend fun getProject(id: String): ProjectEntity? = dao.getById(id)

    suspend fun saveProject(
        existingId: String?,
        name: String,
        mode: EditMode,
        originalFull: Bitmap,
        maskBitmap: Bitmap?,
        cleanBgBitmap: Bitmap?,
        previewThumb: Bitmap,
        state: PersistedProjectState
    ): ProjectEntity = withContext(Dispatchers.IO) {
        val id = existingId ?: UUID.randomUUID().toString()
        val dir = File(projectsRoot, id).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val existing = existingId?.let { dao.getById(it) }

        val origFile = File(dir, "original.jpg")
        if (!origFile.exists() || existingId == null) {
            saveJpg(originalFull, origFile, 95)
        }
        val maskFile = File(dir, "mask.png")
        val maskPath = if (maskBitmap != null) {
            savePng(maskBitmap, maskFile)
            maskFile.absolutePath
        } else existing?.maskPath

        val cleanBgFile = File(dir, "clean_bg.jpg")
        val cleanBgPath = if (cleanBgBitmap != null) {
            saveJpg(cleanBgBitmap, cleanBgFile, 94)
            cleanBgFile.absolutePath
        } else existing?.cleanBgPath

        val thumbFile = File(dir, "thumb.jpg")
        val scaledThumb = scaleDownMax(previewThumb, 420)
        saveJpg(scaledThumb, thumbFile, 85)
        if (scaledThumb !== previewThumb) scaledThumb.recycle()

        val entity = ProjectEntity(
            id = id,
            name = name,
            mode = mode.name,
            originalPath = origFile.absolutePath,
            maskPath = maskPath,
            cleanBgPath = cleanBgPath,
            thumbPath = thumbFile.absolutePath,
            stateJson = encodeState(state),
            width = originalFull.width,
            height = originalFull.height,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        dao.upsert(entity)
        entity
    }

    suspend fun renameProject(id: String, newName: String) {
        dao.rename(id, newName.trim().ifEmpty { "Cutout Project" }, System.currentTimeMillis())
    }

    suspend fun switchProjectMode(id: String, newMode: EditMode) {
        dao.updateMode(id, newMode.name, System.currentTimeMillis())
    }

    suspend fun duplicateProject(id: String): ProjectEntity? = withContext(Dispatchers.IO) {
        val src = dao.getById(id) ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val srcDir = File(projectsRoot, id)
        val dstDir = File(projectsRoot, newId).apply { mkdirs() }
        srcDir.listFiles()?.forEach { file ->
            file.copyTo(File(dstDir, file.name), overwrite = true)
        }
        val now = System.currentTimeMillis()
        val copy = src.copy(
            id = newId,
            name = "${src.name} (Copy)",
            originalPath = File(dstDir, "original.jpg").absolutePath,
            maskPath = src.maskPath?.let { File(dstDir, "mask.png").absolutePath },
            cleanBgPath = src.cleanBgPath?.let { File(dstDir, "clean_bg.jpg").absolutePath },
            thumbPath = File(dstDir, "thumb.jpg").absolutePath,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(copy)
        copy
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
        File(projectsRoot, id).deleteRecursively()
    }

    fun loadBitmapFromPath(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }

    fun getCacheSizeBytes(): Long {
        val cacheBytes = dirSize(context.cacheDir)
        val clipboardBytes = dirSize(File(context.filesDir, "clipboard"))
        return cacheBytes + clipboardBytes
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        getCacheSizeBytes()
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun saveJpg(bmp: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }

    private fun savePng(bmp: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun scaleDownMax(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    }

    fun encodeState(s: PersistedProjectState): String {
        val root = JSONObject()
        root.put("subjectType", s.subjectType.name)
        root.put("edge", JSONObject().apply {
            put("feather", s.edgeSettings.feather.toDouble())
            put("smooth", s.edgeSettings.smooth.toDouble())
            put("growShrink", s.edgeSettings.growShrink.toDouble())
            put("defringe", s.edgeSettings.defringeStrength.toDouble())
            put("hair", s.edgeSettings.hairDetailMode)
        })
        root.put("bg", JSONObject().apply {
            put("type", s.backgroundSpec.type.name)
            put("solid", s.backgroundSpec.solidColorArgb)
            put("gStart", s.backgroundSpec.gradientStartArgb)
            put("gEnd", s.backgroundSpec.gradientEndArgb)
            put("builtin", s.backgroundSpec.builtinId)
            put("blur", s.backgroundSpec.blurRadius.toDouble())
            put("custom", s.backgroundSpec.customImagePath ?: "")
        })
        root.put("subjT", encodeTransform(s.subjectTransform))
        root.put("bgT", encodeTransform(s.bgTransform))
        root.put("adj", JSONObject().apply {
            put("b", s.adjustments.brightness.toDouble())
            put("c", s.adjustments.contrast.toDouble())
            put("sat", s.adjustments.saturation.toDouble())
            put("sh", s.adjustments.sharpness.toDouble())
            put("flt", s.adjustments.filter.name)
            put("cl", s.adjustments.cropLeft.toDouble())
            put("ct", s.adjustments.cropTop.toDouble())
            put("cr", s.adjustments.cropRight.toDouble())
            put("cb", s.adjustments.cropBottom.toDouble())
            put("rot", s.adjustments.canvasRotation)
        })
        root.put("canvas", s.canvasPreset.name)
        root.put("cw", s.customCanvasW)
        root.put("ch", s.customCanvasH)
        root.put("shadow", s.shadowEnabled)
        root.put("shadowOp", s.shadowOpacity.toDouble())
        root.put("outline", s.outlineEnabled)
        root.put("outlineT", s.outlineThickness.toDouble())
        root.put("outlineC", s.outlineColorArgb)
        root.put("mixKaro", s.mixKaroEnabled)
        root.put("remShadow", s.removeShadowReflection)
        root.put("texts", JSONArray().apply {
            s.textOverlays.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("text", t.text)
                    put("font", t.fontId)
                    put("color", t.colorArgb)
                    put("size", t.sizeSp.toDouble())
                    put("x", t.normX.toDouble())
                    put("y", t.normY.toDouble())
                    put("sh", t.hasShadow)
                })
            }
        })
        root.put("stickers", JSONArray().apply {
            s.stickerOverlays.forEach { st ->
                put(JSONObject().apply {
                    put("id", st.id)
                    put("sym", st.symbol)
                    put("x", st.normX.toDouble())
                    put("y", st.normY.toDouble())
                    put("sc", st.scale.toDouble())
                })
            }
        })
        return root.toString()
    }

    fun decodeState(json: String): PersistedProjectState {
        return runCatching {
            val r = JSONObject(json)
            val edge = r.optJSONObject("edge")
            val bg = r.optJSONObject("bg")
            val adj = r.optJSONObject("adj")
            val textsArr = r.optJSONArray("texts")
            val stickersArr = r.optJSONArray("stickers")
            PersistedProjectState(
                subjectType = enumOr(r.optString("subjectType"), SubjectType.AUTO),
                edgeSettings = EdgeSettings(
                    feather = edge?.optDouble("feather", 2.0)?.toFloat() ?: 2f,
                    smooth = edge?.optDouble("smooth", 1.5)?.toFloat() ?: 1.5f,
                    growShrink = edge?.optDouble("growShrink", 0.0)?.toFloat() ?: 0f,
                    defringeStrength = edge?.optDouble("defringe", 0.4)?.toFloat() ?: 0.4f,
                    hairDetailMode = edge?.optBoolean("hair", false) ?: false
                ),
                backgroundSpec = BackgroundSpec(
                    type = enumOr(bg?.optString("type"), BackgroundType.TRANSPARENT),
                    solidColorArgb = bg?.optInt("solid", 0xFFFFFFFF.toInt()) ?: 0xFFFFFFFF.toInt(),
                    gradientStartArgb = bg?.optInt("gStart", 0xFF0F2027.toInt()) ?: 0xFF0F2027.toInt(),
                    gradientEndArgb = bg?.optInt("gEnd", 0xFF2C5364.toInt()) ?: 0xFF2C5364.toInt(),
                    builtinId = bg?.optString("builtin", "studio_glow") ?: "studio_glow",
                    blurRadius = bg?.optDouble("blur", 24.0)?.toFloat() ?: 24f,
                    customImagePath = bg?.optString("custom", "")?.ifBlank { null }
                ),
                subjectTransform = decodeTransform(r.optJSONObject("subjT")),
                bgTransform = decodeTransform(r.optJSONObject("bgT")),
                adjustments = AdjustmentParams(
                    brightness = adj?.optDouble("b", 0.0)?.toFloat() ?: 0f,
                    contrast = adj?.optDouble("c", 1.0)?.toFloat() ?: 1f,
                    saturation = adj?.optDouble("sat", 1.0)?.toFloat() ?: 1f,
                    sharpness = adj?.optDouble("sh", 0.0)?.toFloat() ?: 0f,
                    filter = enumOr(adj?.optString("flt"), ImageFilter.NONE),
                    cropLeft = adj?.optDouble("cl", 0.0)?.toFloat() ?: 0f,
                    cropTop = adj?.optDouble("ct", 0.0)?.toFloat() ?: 0f,
                    cropRight = adj?.optDouble("cr", 1.0)?.toFloat() ?: 1f,
                    cropBottom = adj?.optDouble("cb", 1.0)?.toFloat() ?: 1f,
                    canvasRotation = adj?.optInt("rot", 0) ?: 0
                ),
                canvasPreset = enumOr(r.optString("canvas"), CanvasPreset.ORIGINAL),
                customCanvasW = r.optInt("cw", 1080),
                customCanvasH = r.optInt("ch", 1080),
                shadowEnabled = r.optBoolean("shadow", false),
                shadowOpacity = r.optDouble("shadowOp", 0.45).toFloat(),
                outlineEnabled = r.optBoolean("outline", false),
                outlineThickness = r.optDouble("outlineT", 10.0).toFloat(),
                outlineColorArgb = r.optInt("outlineC", 0xFFFFFFFF.toInt()),
                mixKaroEnabled = r.optBoolean("mixKaro", false),
                removeShadowReflection = r.optBoolean("remShadow", true),
                textOverlays = buildList {
                    if (textsArr != null) {
                        for (i in 0 until textsArr.length()) {
                            val o = textsArr.getJSONObject(i)
                            add(
                                TextOverlay(
                                    id = o.getString("id"),
                                    text = o.getString("text"),
                                    fontId = o.optString("font", "jakarta"),
                                    colorArgb = o.optInt("color", 0xFFFFFFFF.toInt()),
                                    sizeSp = o.optDouble("size", 28.0).toFloat(),
                                    normX = o.optDouble("x", 0.5).toFloat(),
                                    normY = o.optDouble("y", 0.25).toFloat(),
                                    hasShadow = o.optBoolean("sh", true)
                                )
                            )
                        }
                    }
                },
                stickerOverlays = buildList {
                    if (stickersArr != null) {
                        for (i in 0 until stickersArr.length()) {
                            val o = stickersArr.getJSONObject(i)
                            add(
                                StickerOverlay(
                                    id = o.getString("id"),
                                    symbol = o.getString("sym"),
                                    normX = o.optDouble("x", 0.5).toFloat(),
                                    normY = o.optDouble("y", 0.5).toFloat(),
                                    scale = o.optDouble("sc", 1.0).toFloat()
                                )
                            )
                        }
                    }
                }
            )
        }.getOrDefault(PersistedProjectState())
    }

    private fun encodeTransform(t: LayerTransform) = JSONObject().apply {
        put("x", t.offsetX.toDouble())
        put("y", t.offsetY.toDouble())
        put("s", t.scale.toDouble())
        put("r", t.rotationDeg.toDouble())
        put("fh", t.flipH)
        put("fv", t.flipV)
    }

    private fun decodeTransform(o: JSONObject?): LayerTransform {
        if (o == null) return LayerTransform()
        return LayerTransform(
            offsetX = o.optDouble("x", 0.0).toFloat(),
            offsetY = o.optDouble("y", 0.0).toFloat(),
            scale = o.optDouble("s", 1.0).toFloat(),
            rotationDeg = o.optDouble("r", 0.0).toFloat(),
            flipH = o.optBoolean("fh", false),
            flipV = o.optBoolean("fv", false)
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, default: T): T {
        if (raw.isNullOrBlank()) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }
}
