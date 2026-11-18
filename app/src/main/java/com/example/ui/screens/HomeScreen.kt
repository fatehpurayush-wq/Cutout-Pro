package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ProjectEntity
import com.example.data.model.EditMode
import com.example.data.model.SamplePhotoOption
import com.example.ui.components.TestPhotoHintDialog
import com.example.ui.theme.CoralSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VioletTertiary

@Composable
fun HomeScreen(
    recentProjects: List<ProjectEntity>,
    videoBetaEnabled: Boolean,
    onPickPhoto: () -> Unit,
    onTakeCamera: () -> Unit,
    onOpenBatch: () -> Unit,
    onOpenVideo: () -> Unit,
    onPickSample: (SamplePhotoOption) -> Unit,
    onOpenProject: (ProjectEntity) -> Unit,
    onSeeAllProjects: () -> Unit
) {
    var showTestHint by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBackground),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero Studio Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(26.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_studio),
                    contentDescription = "Cutout Pro Offline Studio",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    StudioBackground.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SuccessGreen.copy(alpha = 0.18f),
                            modifier = Modifier.border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("100% Offline • No Internet", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                            }
                        }
                        IconButton(
                            onClick = { showTestHint = true },
                            modifier = Modifier.testTag("home_help_button")
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "Test photo tips", tint = Color.White)
                        }
                    }

                    Column {
                        Text(
                            text = "Cutout Pro",
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White
                        )
                        Text(
                            text = "Background hatao, object mitao — sab kuch phone ke andar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.84f)
                        )
                    }
                }
            }
        }

        // Big Primary Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPickPhoto,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("pick_photo_button"),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color(0xFF001A22)
                    )
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Photo chuno", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Gallery se photo select karein", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onTakeCamera,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("camera_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = StudioSurfaceElevated,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = CoralSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Camera se lo", style = MaterialTheme.typography.labelLarge)
                    }

                    FilledTonalButton(
                        onClick = onOpenBatch,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("batch_mode_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = StudioSurfaceElevated,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.BurstMode, contentDescription = null, tint = VioletTertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("Batch mode", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedCard(
                        onClick = { showTestHint = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sample_photo_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = StudioSurface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                            Text("Test photo try karo", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    if (videoBetaEnabled) {
                        OutlinedCard(
                            onClick = onOpenVideo,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_beta_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = StudioSurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = CoralSecondary, modifier = Modifier.size(20.dp))
                                Text("Video (Beta)", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        // Recent Projects Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haal ke Projects", style = MaterialTheme.typography.titleMedium)
                    if (recentProjects.isNotEmpty()) {
                        TextButton(onClick = onSeeAllProjects) {
                            Text("Sabhi dekho", color = CyanPrimary)
                        }
                    }
                }

                if (recentProjects.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = StudioSurface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(StudioSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = CyanPrimary)
                            }
                            Column {
                                Text("Abhi koi project nahi hai", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Upar 'Photo chuno' dabakar pehla cutout ya object removal shuru karein.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recentProjects, key = { it.id }) { proj ->
                            RecentProjectCard(proj = proj, onClick = { onOpenProject(proj) })
                        }
                    }
                }
            }
        }

        // Short "Kaise kaam karta hai" Hint
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = StudioSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Kaise kaam karta hai", style = MaterialTheme.typography.titleMedium, color = CyanPrimary)
                    HintLine("1.", "Photo chuno ya kisi bhi app se 'Share to Cutout Pro' karein.")
                    HintLine("2.", "Mode chuno: Background hatao, Subject hatao, ya Dono.")
                    HintLine("3.", "1-Tap Download screen par teeno options hamesha taiyaar milenge.")
                }
            }
        }
    }

    if (showTestHint) {
        TestPhotoHintDialog(
            onPickSample = { sample ->
                showTestHint = false
                onPickSample(sample)
            },
            onDismiss = { showTestHint = false }
        )
    }
}

@Composable
private fun RecentProjectCard(proj: ProjectEntity, onClick: () -> Unit) {
    val thumb = remember(proj.thumbPath) { BitmapFactory.decodeFile(proj.thumbPath) }
    val modeLabel = remember(proj.mode) {
        runCatching { EditMode.valueOf(proj.mode).shortTag }.getOrDefault("Edit")
    }
    Card(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick)
            .border(1.dp, StudioBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color(0xFF141824))
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = proj.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = modeLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary
                    )
                }
            }
            Text(
                text = proj.name,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HintLine(num: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(num, color = CoralSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
