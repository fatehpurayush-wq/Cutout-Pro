package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.BuiltinSamplePhotos
import com.example.data.model.SamplePhotoOption
import com.example.ui.theme.CheckerDark
import com.example.ui.theme.CheckerLight
import com.example.ui.theme.CoralSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.VioletTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CheckerboardBackground(modifier: Modifier = Modifier, cellDp: Float = 12f) {
    val cellPx = with(LocalDensity.current) { cellDp.dp.toPx() }
    Canvas(modifier = modifier) {
        drawRect(CheckerDark)
        val cols = (size.width / cellPx).toInt() + 1
        val rows = (size.height / cellPx).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if ((r + c) % 2 == 0) {
                    drawRect(
                        color = CheckerLight,
                        topLeft = Offset(c * cellPx, r * cellPx),
                        size = Size(cellPx, cellPx)
                    )
                }
            }
        }
    }
}

enum class CanvasInteractionMode {
    VIEW_ZOOM_PAN,
    BRUSH_STROKE,
    TAP_POINT,
    LASSO_DRAW,
    MOVE_LAYER
}

@Composable
fun InteractivePhotoCanvas(
    displayBitmap: Bitmap?,
    beforeBitmap: Bitmap?,
    overlayMaskBitmap: Bitmap?,
    showBeforeAfter: Boolean,
    interactionMode: CanvasInteractionMode,
    brushRadiusNorm: Float,
    cloneSourceNorm: Pair<Float, Float>?,
    onBrushStart: () -> Unit,
    onBrushMove: (Float, Float) -> Unit,
    onBrushEnd: () -> Unit,
    onTapNormalized: (Float, Float) -> Unit,
    onLassoFinished: (List<Pair<Float, Float>>) -> Unit,
    onLayerDelta: (dxNorm: Float, dyNorm: Float, scaleFactor: Float, rotateDelta: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    var activeTouchNorm by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val lassoPoints = remember { mutableStateListOf<Pair<Float, Float>>() }

    val bmpW = displayBitmap?.width ?: 1
    val bmpH = displayBitmap?.height ?: 1

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0E111A)),
        contentAlignment = Alignment.Center
    ) {
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val fitScale = min(boxW / bmpW, boxH / bmpH)
        val fittedW = bmpW * fitScale
        val fittedH = bmpH * fitScale
        val imgLeft = (boxW - fittedW) / 2f
        val imgTop = (boxH - fittedH) / 2f

        fun screenToNorm(pos: Offset): Pair<Float, Float> {
            // Invert graphicsLayer transform centered at box center
            val cx = boxW / 2f
            val cy = boxH / 2f
            val unpannedX = (pos.x - cx - panX) / zoom + cx
            val unpannedY = (pos.y - cy - panY) / zoom + cy
            val nx = ((unpannedX - imgLeft) / fittedW).coerceIn(0f, 1f)
            val ny = ((unpannedY - imgTop) / fittedH).coerceIn(0f, 1f)
            return nx to ny
        }

        // Checkerboard + Display Bitmap + Optional Mode 2 Mask Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = panX
                    translationY = panY
                }
        ) {
            CheckerboardBackground(modifier = Modifier.fillMaxSize())

            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Edited photo canvas",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            if (overlayMaskBitmap != null && displayBitmap != null) {
                Image(
                    bitmap = overlayMaskBitmap.asImageBitmap(),
                    contentDescription = "Selection mask overlay",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alpha = 0.55f,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(CoralSecondary)
                )
            }

            // Before / After vertical wipe
            if (showBeforeAfter && beforeBitmap != null && displayBitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val splitX = imgLeft + fittedW * splitFraction
                    val leftW = (splitX - imgLeft).coerceIn(0f, fittedW)
                    val srcW = ((leftW / fittedW) * beforeBitmap.width).roundToInt().coerceIn(1, beforeBitmap.width)
                    if (leftW > 2f) {
                        drawImage(
                            image = beforeBitmap.asImageBitmap(),
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(srcW, beforeBitmap.height),
                            dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                            dstSize = IntSize(leftW.roundToInt(), fittedH.roundToInt())
                        )
                    }
                    drawLine(
                        color = CyanPrimary,
                        start = Offset(splitX, imgTop),
                        end = Offset(splitX, imgTop + fittedH),
                        strokeWidth = 4f
                    )
                    drawCircle(
                        color = CyanPrimary,
                        radius = 22f,
                        center = Offset(splitX, imgTop + fittedH / 2f)
                    )
                }
            }

            // Clone Source crosshair + Lasso + Brush cursor
            Canvas(modifier = Modifier.fillMaxSize()) {
                cloneSourceNorm?.let { (cx, cy) ->
                    val px = imgLeft + cx * fittedW
                    val py = imgTop + cy * fittedH
                    drawCircle(CyanPrimary, radius = 16f, center = Offset(px, py), style = Stroke(3f))
                    drawLine(CyanPrimary, Offset(px - 22f, py), Offset(px + 22f, py), strokeWidth = 2.5f)
                    drawLine(CyanPrimary, Offset(px, py - 22f), Offset(px, py + 22f), strokeWidth = 2.5f)
                }
                if (lassoPoints.size > 1) {
                    val path = Path().apply {
                        val f = lassoPoints.first()
                        moveTo(imgLeft + f.first * fittedW, imgTop + f.second * fittedH)
                        for (i in 1 until lassoPoints.size) {
                            val p = lassoPoints[i]
                            lineTo(imgLeft + p.first * fittedW, imgTop + p.second * fittedH)
                        }
                    }
                    drawPath(
                        path = path,
                        color = CyanPrimary,
                        style = Stroke(
                            width = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    )
                }
                activeTouchNorm?.let { (nx, ny) ->
                    if (interactionMode == CanvasInteractionMode.BRUSH_STROKE) {
                        val px = imgLeft + nx * fittedW
                        val py = imgTop + ny * fittedH
                        val rad = brushRadiusNorm * min(fittedW, fittedH)
                        drawCircle(Color.White, radius = rad, center = Offset(px, py), style = Stroke(3f))
                    }
                }
            }
        }

        // Gesture Interceptor Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showBeforeAfter, interactionMode, zoom, panX, panY) {
                    if (showBeforeAfter) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val nx = ((change.position.x - imgLeft) / fittedW).coerceIn(0.05f, 0.95f)
                            splitFraction = nx
                        }
                    } else when (interactionMode) {
                        CanvasInteractionMode.VIEW_ZOOM_PAN -> {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                if (zoom > 1.02f) {
                                    panX = (panX + pan.x).coerceIn(-boxW * 0.6f, boxW * 0.6f)
                                    panY = (panY + pan.y).coerceIn(-boxH * 0.6f, boxH * 0.6f)
                                } else {
                                    panX = 0f; panY = 0f
                                }
                            }
                        }
                        CanvasInteractionMode.MOVE_LAYER -> {
                            detectTransformGestures { _, pan, zoomChange, rot ->
                                onLayerDelta(pan.x / fittedW, pan.y / fittedH, zoomChange, rot)
                            }
                        }
                        CanvasInteractionMode.TAP_POINT -> {
                            detectTapGestures { offset ->
                                val (nx, ny) = screenToNorm(offset)
                                onTapNormalized(nx, ny)
                            }
                        }
                        CanvasInteractionMode.BRUSH_STROKE -> {
                            detectDragGestures(
                                onDragStart = { start ->
                                    onBrushStart()
                                    val (nx, ny) = screenToNorm(start)
                                    activeTouchNorm = nx to ny
                                    onBrushMove(nx, ny)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val (nx, ny) = screenToNorm(change.position)
                                    activeTouchNorm = nx to ny
                                    onBrushMove(nx, ny)
                                },
                                onDragEnd = {
                                    activeTouchNorm = null
                                    onBrushEnd()
                                },
                                onDragCancel = {
                                    activeTouchNorm = null
                                    onBrushEnd()
                                }
                            )
                        }
                        CanvasInteractionMode.LASSO_DRAW -> {
                            detectDragGestures(
                                onDragStart = { start ->
                                    lassoPoints.clear()
                                    val pt = screenToNorm(start)
                                    lassoPoints.add(pt)
                                    activeTouchNorm = pt
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pt = screenToNorm(change.position)
                                    lassoPoints.add(pt)
                                    activeTouchNorm = pt
                                },
                                onDragEnd = {
                                    activeTouchNorm = null
                                    if (lassoPoints.size >= 3) {
                                        onLassoFinished(lassoPoints.toList())
                                    }
                                    lassoPoints.clear()
                                },
                                onDragCancel = {
                                    activeTouchNorm = null
                                    lassoPoints.clear()
                                }
                            )
                        }
                    }
                }
        )

        // Magnifier Loupe when painting/drawing fine edges!
        val touch = activeTouchNorm
        if (touch != null && displayBitmap != null &&
            (interactionMode == CanvasInteractionMode.BRUSH_STROKE || interactionMode == CanvasInteractionMode.LASSO_DRAW)
        ) {
            val alignRight = touch.first < 0.45f
            Box(
                modifier = Modifier
                    .align(if (alignRight) Alignment.TopEnd else Alignment.TopStart)
                    .padding(12.dp)
                    .size(104.dp)
                    .clip(CircleShape)
                    .border(2.5.dp, CyanPrimary, CircleShape)
                    .background(StudioSurface)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cropHalf = (min(displayBitmap.width, displayBitmap.height) * 0.14f).roundToInt().coerceAtLeast(24)
                    val cx = (touch.first * displayBitmap.width).roundToInt()
                    val cy = (touch.second * displayBitmap.height).roundToInt()
                    val x0 = (cx - cropHalf).coerceIn(0, (displayBitmap.width - 1).coerceAtLeast(0))
                    val y0 = (cy - cropHalf).coerceIn(0, (displayBitmap.height - 1).coerceAtLeast(0))
                    val x1 = (cx + cropHalf).coerceIn(x0 + 1, displayBitmap.width)
                    val y1 = (cy + cropHalf).coerceIn(y0 + 1, displayBitmap.height)
                    drawImage(
                        image = displayBitmap.asImageBitmap(),
                        srcOffset = IntOffset(x0, y0),
                        srcSize = IntSize((x1 - x0).coerceAtLeast(1), (y1 - y0).coerceAtLeast(1)),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                    drawCircle(CyanPrimary, radius = 10f, center = center, style = Stroke(2.5f))
                }
            }
        }

        // Before/After badge or Zoom Reset chip
        if (showBeforeAfter) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(StudioSurface.copy(alpha = 0.88f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Pehle (Original)", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
                Icon(Icons.Default.Compare, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Baad (Edited)", style = MaterialTheme.typography.labelSmall, color = CoralSecondary)
            }
        } else if (zoom > 1.05f) {
            Surface(
                onClick = { zoom = 1f; panX = 0f; panY = 0f },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(16.dp),
                color = StudioSurface.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset zoom", tint = CyanPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${(zoom * 100).roundToInt()}% • Reset", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ProcessingOverlay(
    visible: Boolean,
    progress: Float,
    statusText: String
) {
    AnimatedVisibility(visible = visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .pointerInput(Unit) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .border(1.dp, StudioBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = statusText.ifBlank { "Kaam chal raha hai..." },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = CyanPrimary,
                        trackColor = StudioBorder
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${(progress * 100).roundToInt()}% • Offline AI on phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FirstLaunchGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        title = {
            Text("Cutout Pro mein Swagat hai!", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GuideStepRow("1", "Photo chuno ya Camera se lo", "Gallery se ya kisi aur app se Share karke photo kholo.")
                GuideStepRow("2", "3 Modes mein se chuno", "Background hatao, Subject hatao, ya Dono ek saath pao.")
                GuideStepRow("3", "1-Tap Download", "PNG, WEBP, JPG, WhatsApp sticker ya Passport sheet save karo. 100% offline!")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("onboarding_done_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Samajh aa gaya!")
            }
        }
    )
}

@Composable
private fun GuideStepRow(step: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(step, color = CyanPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TestPhotoHintDialog(
    onPickSample: (SamplePhotoOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        title = { Text("Kaunsi photo best kaam karti hai?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "• Background hatao: Saaf roshni mein insaan, pet, product ya gaadi.\n" +
                        "• Subject hatao: Beach, road, wall ya grass ke saamne khade log/cheezein.\n" +
                        "• Dono: Portrait aur product photos jahan aap subject ko kahin aur rakhna chahein.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text("Turant try karne ke liye sample photo chuno:", style = MaterialTheme.typography.labelLarge, color = CyanPrimary)
                BuiltinSamplePhotos.forEach { sample ->
                    OutlinedButton(
                        onClick = { onPickSample(sample) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(sample.titleHinglish, fontWeight = FontWeight.SemiBold)
                            Text(sample.hintHinglish, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Band karo") }
        }
    )
}

fun formatBytesHinglish(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

fun formatDateHinglish(millis: Long): String {
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(millis))
}
