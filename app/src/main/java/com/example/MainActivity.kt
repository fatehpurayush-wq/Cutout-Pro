package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.AppRoutes
import com.example.ui.MainViewModel
import com.example.ui.components.FirstLaunchGuideDialog
import com.example.ui.components.ProcessingOverlay
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.BatchScreen
import com.example.ui.screens.DownloadsHistoryScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ModeSelectScreen
import com.example.ui.screens.ProjectsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoBetaScreen
import com.example.ui.theme.CutoutProTheme
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StudioBackground
import com.example.ui.theme.StudioSurface
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val pendingSharedUri = mutableStateOf<Uri?>(null)
    private val pendingSharedMultiple = mutableStateOf<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            CutoutProTheme(amoledBlack = settings.amoledTheme) {
                CutoutProRoot(
                    vm = viewModel,
                    sharedSingleUri = pendingSharedUri.value,
                    sharedMultipleUris = pendingSharedMultiple.value,
                    onConsumeShared = {
                        pendingSharedUri.value = null
                        pendingSharedMultiple.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) pendingSharedUri.value = uri
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    if (!uris.isNullOrEmpty()) pendingSharedMultiple.value = uris
                }
            }
        }
    }
}

@Composable
private fun CutoutProRoot(
    vm: MainViewModel,
    sharedSingleUri: Uri?,
    sharedMultipleUris: List<Uri>?,
    onConsumeShared: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: AppRoutes.HOME

    val settings by vm.settings.collectAsStateWithLifecycle()
    val recentProjects by vm.recentProjects.collectAsStateWithLifecycle()
    val allProjects by vm.allProjects.collectAsStateWithLifecycle()
    val allDownloads by vm.allDownloads.collectAsStateWithLifecycle()
    val editorState by vm.editor.collectAsStateWithLifecycle()
    val exportState by vm.export.collectAsStateWithLifecycle()
    val batchState by vm.batch.collectAsStateWithLifecycle()
    val videoState by vm.video.collectAsStateWithLifecycle()
    val clipboardSubject by vm.clipboardSubject.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val singlePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.loadPickedPhoto(uri) {
                navController.navigate(AppRoutes.MODE_SELECT)
            }
        }
    }

    val bgGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.setCustomBackgroundImage(uri)
    }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) vm.setBatchUris(uris)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.loadVideoClip(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            vm.loadPickedPhoto(uri) {
                navController.navigate(AppRoutes.MODE_SELECT)
            }
        }
    }

    val exportFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.updateExportConfig {
                it.copy(useCustomFolder = true, customFolderUri = treeUri.toString())
            }
        }
    }

    val defaultFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            scope.launch { vm.settingsStore.setDefaultSaveFolderUri(treeUri.toString()) }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    // Handle Share-to-Cutout-Pro incoming URIs
    LaunchedEffect(sharedSingleUri, sharedMultipleUris) {
        if (sharedSingleUri != null) {
            onConsumeShared()
            vm.loadPickedPhoto(sharedSingleUri) {
                navController.navigate(AppRoutes.MODE_SELECT)
            }
        } else if (!sharedMultipleUris.isNullOrEmpty()) {
            onConsumeShared()
            vm.setBatchUris(sharedMultipleUris)
            navController.navigate(AppRoutes.BATCH)
        }
    }

    val showBottomBar = currentRoute in setOf(
        AppRoutes.HOME,
        AppRoutes.PROJECTS,
        AppRoutes.DOWNLOADS,
        AppRoutes.SETTINGS
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = StudioBackground,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = StudioSurface) {
                        NavigationBarItem(
                            selected = currentRoute == AppRoutes.HOME,
                            onClick = { navController.navigate(AppRoutes.HOME) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_home"),
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = CyanPrimary, selectedTextColor = CyanPrimary)
                        )
                        NavigationBarItem(
                            selected = currentRoute == AppRoutes.PROJECTS,
                            onClick = { navController.navigate(AppRoutes.PROJECTS) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Folder, contentDescription = "Projects") },
                            label = { Text("Projects") },
                            modifier = Modifier.testTag("nav_projects"),
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = CyanPrimary, selectedTextColor = CyanPrimary)
                        )
                        NavigationBarItem(
                            selected = currentRoute == AppRoutes.DOWNLOADS,
                            onClick = { navController.navigate(AppRoutes.DOWNLOADS) { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Download, contentDescription = "Downloads") },
                            label = { Text("Downloads") },
                            modifier = Modifier.testTag("nav_downloads"),
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = CyanPrimary, selectedTextColor = CyanPrimary)
                        )
                        NavigationBarItem(
                            selected = currentRoute == AppRoutes.SETTINGS,
                            onClick = {
                                vm.refreshCacheSize()
                                navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true }
                            },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") },
                            modifier = Modifier.testTag("nav_settings"),
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = CyanPrimary, selectedTextColor = CyanPrimary)
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppRoutes.HOME,
                modifier = if (showBottomBar) Modifier.padding(innerPadding) else Modifier
            ) {
                composable(AppRoutes.HOME) {
                    HomeScreen(
                        recentProjects = recentProjects,
                        videoBetaEnabled = settings.videoBetaEnabled,
                        onPickPhoto = {
                            singlePhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onTakeCamera = {
                            val outUri = vm.createCameraOutputUri()
                            pendingCameraUri = outUri
                            cameraLauncher.launch(outUri)
                        },
                        onOpenBatch = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            navController.navigate(AppRoutes.BATCH)
                        },
                        onOpenVideo = { navController.navigate(AppRoutes.VIDEO) },
                        onPickSample = { sample ->
                            vm.loadSamplePhoto(sample) {
                                navController.navigate(AppRoutes.MODE_SELECT)
                            }
                        },
                        onOpenProject = { proj ->
                            vm.openSavedProject(proj) {
                                navController.navigate(AppRoutes.EDITOR)
                            }
                        },
                        onSeeAllProjects = { navController.navigate(AppRoutes.PROJECTS) }
                    )
                }

                composable(AppRoutes.MODE_SELECT) {
                    ModeSelectScreen(
                        previewBitmap = editorState.originalWorking,
                        lastUsedMode = settings.lastUsedMode,
                        onBack = { navController.popBackStack() },
                        onSelectMode = { mode ->
                            vm.startMode(mode)
                            navController.navigate(AppRoutes.EDITOR) {
                                popUpTo(AppRoutes.HOME) { inclusive = false }
                            }
                        }
                    )
                }

                composable(AppRoutes.EDITOR) {
                    EditorScreen(
                        state = editorState,
                        fillQuality = settings.fillQuality,
                        clipboardHasSubject = clipboardSubject != null,
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onPickBgGallery = {
                            bgGalleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSetFillQuality = { q ->
                            scope.launch { vm.settingsStore.setFillQuality(q) }
                        },
                        onOpenExport = {
                            vm.prepareExportScreen()
                            navController.navigate(AppRoutes.EXPORT)
                        }
                    )
                }

                composable(AppRoutes.EXPORT) {
                    ExportScreen(
                        state = exportState,
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onPickCustomFolder = { exportFolderPicker.launch(null) },
                        onDoneHome = {
                            navController.popBackStack(AppRoutes.HOME, inclusive = false)
                        }
                    )
                }

                composable(AppRoutes.PROJECTS) {
                    ProjectsScreen(
                        projects = allProjects,
                        vm = vm,
                        onOpenProject = { proj ->
                            vm.openSavedProject(proj) {
                                navController.navigate(AppRoutes.EDITOR)
                            }
                        }
                    )
                }

                composable(AppRoutes.DOWNLOADS) {
                    DownloadsHistoryScreen(
                        downloads = allDownloads,
                        vm = vm,
                        onReExportToExportScreen = {
                            navController.navigate(AppRoutes.EXPORT)
                        }
                    )
                }

                composable(AppRoutes.BATCH) {
                    BatchScreen(
                        state = batchState,
                        vm = vm,
                        onPickMultiplePhotos = {
                            multiPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppRoutes.VIDEO) {
                    VideoBetaScreen(
                        state = videoState,
                        vm = vm,
                        onPickVideo = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(AppRoutes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        cacheBytes = cacheBytes,
                        vm = vm,
                        onPickDefaultFolder = { defaultFolderPicker.launch(null) },
                        onOpenVideoBeta = { navController.navigate(AppRoutes.VIDEO) },
                        onOpenAbout = { navController.navigate(AppRoutes.ABOUT) }
                    )
                }

                composable(AppRoutes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }

        // First launch 3-step guide
        if (!settings.onboardingDone) {
            FirstLaunchGuideDialog(onDismiss = { vm.completeOnboarding() })
        }

        // Global smooth processing progress overlay
        val overlayVisible = editorState.isProcessing || exportState.isExporting || videoState.isProcessing
        val overlayProgress = when {
            exportState.isExporting -> exportState.progress
            videoState.isProcessing -> videoState.progress
            else -> editorState.progress
        }
        val overlayText = when {
            exportState.isExporting -> exportState.statusText
            videoState.isProcessing -> videoState.statusText
            else -> editorState.statusText
        }
        ProcessingOverlay(
            visible = overlayVisible,
            progress = overlayProgress,
            statusText = overlayText
        )
    }
}
