package it.turni.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import it.turni.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurniApp(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            result ->
            if (result.values.all { it }) vm.loadCalendars { nav.navigate("calendars") }
            else vm.reportError(context.getString(R.string.permission_denied))
        }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { vm.loadImage(it) { nav.navigate("crop") } }
        }
    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
            if (allowed) nav.navigate("camera")
            else vm.reportError(context.getString(R.string.permission_denied))
        }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val colors =
        lightColorScheme(
            primary = Color(0xFF006C67),
            secondary = Color(0xFF4A635F),
            background = Color(0xFFF6FAF8),
            surface = Color(0xFFF6FAF8),
        )
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        if (route != "home")
                            TextButton(
                                onClick = {
                                    if (route == "crop") {
                                        vm.clearImages()
                                        nav.popBackStack("home", false)
                                    } else nav.popBackStack()
                                },
                                enabled = !state.busy,
                            ) {
                                Text(stringResource(R.string.back))
                            }
                    },
                    actions = {
                        if (route in listOf("crop", "review", "calendars", "sync", "debug"))
                            TextButton(
                                onClick = {
                                    vm.clearImages()
                                    nav.popBackStack("home", false)
                                },
                                enabled = !state.busy,
                            ) {
                                Text(stringResource(R.string.cancel_import))
                            }
                    },
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (state.loaded)
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                state,
                                camera = {
                                    if (state.profile == null) nav.navigate("settings")
                                    else if (
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA,
                                        ) == PackageManager.PERMISSION_GRANTED
                                    )
                                        nav.navigate("camera")
                                    else cameraPermission.launch(Manifest.permission.CAMERA)
                                },
                                gallery = {
                                    if (state.profile == null) nav.navigate("settings")
                                    else
                                        gallery.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                },
                                settings = { nav.navigate("settings") },
                                history = { nav.navigate("history") },
                            )
                        }
                        composable("settings") { SettingsScreen(state, vm) { nav.popBackStack() } }
                        composable("camera") {
                            CameraScreen(
                                onCaptured = { file ->
                                    vm.loadImage(android.net.Uri.fromFile(file), file) {
                                        nav.navigate("crop") {
                                            popUpTo("camera") { inclusive = true }
                                        }
                                    }
                                },
                                onError = vm::reportError,
                            )
                        }
                        composable("crop") {
                            CropScreen(
                                state.image,
                                onAnalyze = { corners ->
                                    vm.analyze(corners) { nav.navigate("review") }
                                },
                                onError = vm::reportError,
                            )
                        }
                        composable("review") {
                            ReviewScreen(
                                state,
                                vm,
                                onCalendar = {
                                    val allowed =
                                        listOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR,
                                            )
                                            .all {
                                                ContextCompat.checkSelfPermission(context, it) ==
                                                    PackageManager.PERMISSION_GRANTED
                                            }
                                    if (allowed) vm.loadCalendars { nav.navigate("calendars") }
                                    else
                                        permissions.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALENDAR,
                                                Manifest.permission.WRITE_CALENDAR,
                                            )
                                        )
                                },
                                onDebug = { nav.navigate("debug") },
                            )
                        }
                        composable("calendars") {
                            CalendarScreen(
                                state,
                                onChoose = { id -> vm.prepare(id) { nav.navigate("sync") } },
                                onRetry = { vm.loadCalendars {} },
                            )
                        }
                        composable("sync") {
                            SyncScreen(state, vm) {
                                vm.synchronize { nav.navigate("result") { popUpTo("home") } }
                            }
                        }
                        composable("result") {
                            ResultScreen(state.syncCount ?: 0) { nav.popBackStack("home", false) }
                        }
                        composable("history") {
                            HistoryScreen(state.weeks) { week ->
                                vm.history(week) { nav.navigate("week") }
                            }
                        }
                        composable("week") { SavedWeekScreen(state.historyShifts) }
                        composable("debug") { DebugScreen(state) }
                    }
                if (state.busy || !state.loaded)
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Consume touches while work runs so the user cannot navigate away from an
                        // active import.
                        Surface(onClick = {}, Modifier.fillMaxSize(), color = Color.Transparent) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.processing), Modifier.padding(16.dp))
                            }
                        }
                    }
                state.error?.let { message ->
                    AlertDialog(
                        onDismissRequest = vm::dismissError,
                        text = { Text(message) },
                        confirmButton = {
                            TextButton(onClick = vm::dismissError) {
                                Text(stringResource(R.string.continue_label))
                            }
                        },
                    )
                }
            }
        }
    }
    // Register after NavHost so work cannot be interrupted by system Back navigation.
    BackHandler(enabled = state.busy) {}
    BackHandler(enabled = !state.busy && route == "crop") {
        vm.clearImages()
        nav.popBackStack("home", false)
    }
}

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
