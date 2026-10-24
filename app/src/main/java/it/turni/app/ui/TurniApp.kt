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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    var calendarForSettings by rememberSaveable { mutableStateOf(false) }
    fun continueWithCalendar(forSettings: Boolean) {
        if (forSettings) vm.loadCalendars { nav.navigate("calendar-settings") }
        else
            vm.continueImport(
                onPrepared = { nav.navigate("sync") },
                onChooseCalendar = { nav.navigate("calendars") },
            )
    }
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            result ->
            if (
                listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
                    result[it] == true
                }
            )
                continueWithCalendar(calendarForSettings)
            else vm.reportError(context.getString(R.string.permission_denied))
        }
    fun requestCalendar(forSettings: Boolean) {
        calendarForSettings = forSettings
        val requested =
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        if (
            requested.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
            continueWithCalendar(forSettings)
        else permissions.launch(requested)
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
    ShiftrailTheme {
        if (!state.busy && state.updater.release != null) {
            Surface(Modifier.fillMaxSize()) {
                RequiredUpdateScreen(state.updater, vm::downloadUpdate, vm::installUpdate) {
                    vm.checkUpdates()
                }
            }
            BackHandler {}
            return@ShiftrailTheme
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    navigationIcon = {
                        if (route != "home")
                            IconButton(
                                onClick = {
                                    if (route == "crop") {
                                        vm.clearImages()
                                        nav.popBackStack("home", false)
                                    } else nav.popBackStack()
                                },
                                enabled = !state.busy,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_back),
                                    stringResource(R.string.back),
                                )
                            }
                    },
                    actions = {
                        if (route in listOf("crop", "review", "calendars", "sync"))
                            TextButton(
                                onClick = {
                                    vm.clearImages()
                                    nav.popBackStack("home", false)
                                },
                                enabled = !state.busy,
                            ) {
                                Text(stringResource(R.string.cancel_short))
                            }
                    },
                )
            },
            bottomBar = {
                if (route in listOf("home", "hours", "settings")) {
                    NavigationBar {
                        listOf(
                                Triple("home", R.string.home, R.drawable.ic_calendar),
                                Triple("hours", R.string.hours, R.drawable.ic_hours),
                                Triple("settings", R.string.settings, R.drawable.ic_profile),
                            )
                            .forEach { (destination, label, icon) ->
                                NavigationBarItem(
                                    selected = route == destination,
                                    enabled = !state.busy,
                                    onClick = {
                                        if (route != destination)
                                            nav.navigate(destination) {
                                                popUpTo("home") { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                    },
                                    icon = { Icon(painterResource(icon), null) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                    }
                }
            },
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
                            )
                        }
                        composable("settings") {
                            SettingsScreen(state, vm, onCalendar = { requestCalendar(true) }) {
                                nav.popBackStack()
                            }
                        }
                        composable("hours") {
                            HoursScreen(state, vm, onSettings = { nav.navigate("settings") })
                        }
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
                                onCalendar = { requestCalendar(false) },
                            )
                        }
                        composable("calendars") {
                            CalendarScreen(
                                state,
                                onChoose = { id, enable ->
                                    vm.prepare(id, enable) { nav.navigate("sync") }
                                },
                                onRetry = { vm.loadCalendars {} },
                            )
                        }
                        composable("calendar-settings") {
                            CalendarScreen(
                                state,
                                onChoose = { id, enable ->
                                    vm.saveCalendar(id, enable) { nav.popBackStack() }
                                },
                                onRetry = { vm.loadCalendars {} },
                                importing = false,
                            )
                        }
                        composable("sync") {
                            SyncScreen(state, vm) {
                                vm.synchronize { nav.navigate("result") { popUpTo("home") } }
                            }
                        }
                        composable("result") {
                            ResultScreen(state, vm::reportError) { nav.popBackStack("home", false) }
                        }
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
