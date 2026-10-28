package it.turni.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.turni.app.R

private val eventColors =
    listOf(
        0xff1a73e8.toInt() to R.string.color_blue,
        0xff0b8043.toInt() to R.string.color_green,
        0xff8e24aa.toInt() to R.string.color_purple,
        0xffe67c00.toInt() to R.string.color_orange,
        0xffd50000.toInt() to R.string.color_red,
        0xff616161.toInt() to R.string.color_gray,
    )

@Composable
private fun colorLabel(color: Int?): String =
    when {
        color == null -> stringResource(R.string.color_calendar)
        else ->
            eventColors.firstOrNull { it.first == color }?.second?.let { stringResource(it) }
                ?: "#%06X".format(color and 0xffffff)
    }

@Composable
fun SettingsScreen(
    state: AppState,
    vm: AppViewModel,
    onCalendar: () -> Unit,
    section: String?,
    onSectionChange: (String?) -> Unit,
    onSaved: () -> Unit,
) {
    BackHandler(enabled = section != null) { onSectionChange(null) }
    val settingsScroll = rememberLazyListState()
    LaunchedEffect(section) { settingsScroll.scrollToItem(0) }
    val focus = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf(state.profile?.name.orEmpty()) }
    val reminder = state.settings.reminderMinutes
    var workColor by rememberSaveable { mutableStateOf(state.settings.eventColor) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var choosingColor by rememberSaveable { mutableIntStateOf(0) }
    var notificationsEnabled by rememberSaveable {
        mutableStateOf(state.settings.notificationsEnabled)
    }
    var notificationSound by rememberSaveable { mutableStateOf(state.settings.notificationSound) }
    var notificationVibration by rememberSaveable {
        mutableStateOf(state.settings.notificationVibration)
    }
    var fullScreenAlarm by rememberSaveable { mutableStateOf(state.settings.fullScreenAlarm) }
    var notificationHours by rememberSaveable {
        mutableStateOf((state.settings.notificationMinutes / 60).toString())
    }
    var notificationMinutes by rememberSaveable {
        mutableStateOf((state.settings.notificationMinutes % 60).toString())
    }
    val hours = notificationHours.toIntOrNull()
    val minutes = notificationMinutes.toIntOrNull()
    val validNotificationTime =
        hours != null &&
            minutes != null &&
            hours in 0..24 &&
            minutes in 0..55 &&
            minutes % 5 == 0 &&
            hours * 60 + minutes <= 1440
    val backgroundPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) vm.setBackground(uri)
        }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(
                    when (section) {
                        "calendar" -> R.string.calendar_preferences
                        "background" -> R.string.background_title
                        "notifications" -> R.string.notifications_title
                        "updates" -> R.string.updates
                        else -> R.string.settings
                    }
                ),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("settings_heading"),
            )
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("settings_list"),
            state = settingsScroll,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (section) {
                null -> {
                    item {
                        OutlinedTextField(
                            name,
                            {
                                name = it
                                invalid = false
                            },
                            label = { Text(stringResource(R.string.name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = invalid,
                        )
                    }
                    item {
                        SettingChoice(
                            stringResource(R.string.calendar_preferences),
                            "",
                            "menu_calendar",
                            icon = R.drawable.ic_calendar,
                        ) {
                            onSectionChange("calendar")
                            focus.clearFocus()
                        }
                    }
                    item {
                        SettingChoice(
                            stringResource(R.string.background_title),
                            "",
                            "menu_background",
                            icon = R.drawable.ic_image,
                        ) {
                            onSectionChange("background")
                            focus.clearFocus()
                        }
                    }
                    item {
                        SettingChoice(
                            stringResource(R.string.notifications_title),
                            "",
                            "menu_notifications",
                            icon = R.drawable.ic_bell,
                        ) {
                            onSectionChange("notifications")
                            focus.clearFocus()
                        }
                    }
                    item {
                        SettingChoice(
                            stringResource(R.string.updates),
                            "",
                            "menu_updates",
                            icon = R.drawable.ic_update,
                        ) {
                            onSectionChange("updates")
                            focus.clearFocus()
                        }
                    }
                }
                "calendar" -> {
                    item {
                        val calendar =
                            state.calendars.firstOrNull { it.id == state.settings.calendarId }
                        SettingChoice(
                            stringResource(R.string.choose_calendar),
                            calendar?.let {
                                "${calendarName(it)}\n${calendarService(it)}${if (it.localOnly || it.account.isBlank()) "" else "\n${it.account}"}"
                            }
                                ?: stringResource(
                                    if (state.settings.calendarId == null)
                                        R.string.calendar_not_selected
                                    else R.string.calendar_preferred_unavailable
                                ),
                            "destination_calendar",
                        ) {
                            focus.clearFocus()
                            onCalendar()
                        }
                        if (calendar?.localOnly == true)
                            Text(
                                stringResource(R.string.calendar_local_only),
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                    item {
                        SettingChoice(
                            stringResource(R.string.work_color),
                            colorLabel(workColor),
                            "work_color",
                            workColor,
                        ) {
                            focus.clearFocus()
                            choosingColor = 1
                        }
                    }
                }
                "background" -> {
                    item {
                        Text(
                            stringResource(R.string.app_theme),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            AppTheme.entries.forEach { theme ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .testTag("theme_${theme.name.lowercase()}")
                                        .selectable(
                                            selected = state.theme == theme,
                                            enabled = !state.busy,
                                            role = Role.RadioButton,
                                            onClick = { vm.setTheme(theme) },
                                        )
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RadioButton(
                                        state.theme == theme,
                                        onClick = null,
                                        enabled = !state.busy,
                                    )
                                    Text(
                                        stringResource(
                                            when (theme) {
                                                AppTheme.SYSTEM -> R.string.theme_system
                                                AppTheme.LIGHT -> R.string.theme_light
                                                AppTheme.DARK -> R.string.theme_dark
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                    item {
                        state.background?.let { bitmap ->
                            androidx.compose.foundation.Image(
                                bitmap.asImageBitmap(),
                                stringResource(R.string.background_title),
                                Modifier.fillMaxWidth().height(160.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                backgroundPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().testTag("choose_background"),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_image),
                                null,
                                Modifier.padding(end = 8.dp),
                            )
                            Text(stringResource(R.string.background_choose))
                        }
                        if (state.background != null)
                            TextButton(
                                onClick = { vm.setBackground(null) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().testTag("reset_background"),
                            ) {
                                Text(stringResource(R.string.background_reset))
                            }
                    }
                }
                "notifications" ->
                    item {
                        NotificationSettings(
                            enabled = notificationsEnabled,
                            onEnabled = { notificationsEnabled = it },
                            fullScreenAlarm = fullScreenAlarm,
                            onFullScreenAlarm = { fullScreenAlarm = it },
                            sound = notificationSound,
                            onSound = { notificationSound = it },
                            vibration = notificationVibration,
                            onVibration = { notificationVibration = it },
                            hours = notificationHours,
                            onHours = { notificationHours = it },
                            minutes = notificationMinutes,
                            onMinutes = { notificationMinutes = it },
                            validTime = validNotificationTime,
                            onError = vm::reportError,
                        )
                    }
                "updates" -> item { UpdateSettings(state.updater, vm) }
            }
        }
        if (section == null || section == "calendar" || section == "notifications")
            ActionFooter(
                stringResource(R.string.save),
                enabled = !state.busy && (!notificationsEnabled || validNotificationTime),
                note = if (invalid) stringResource(R.string.invalid_name) else null,
                action = {
                    invalid = name.isBlank() || name.trim().length > 160
                    if (!invalid)
                        vm.saveSettings(
                            name,
                            state.settings.copy(
                                reminderMinutes = reminder,
                                eventColor = workColor,
                                restColor = state.settings.restColor,
                                saveRestDays = false,
                                notificationsEnabled = notificationsEnabled,
                                notificationMinutes =
                                    if (validNotificationTime)
                                        requireNotNull(hours) * 60 + requireNotNull(minutes)
                                    else state.settings.notificationMinutes,
                                fullScreenAlarm = fullScreenAlarm,
                                notificationSound = notificationSound,
                                notificationVibration = notificationVibration,
                            ),
                            onSaved,
                        )
                },
            )
    }
    if (choosingColor != 0) {
        val selected = workColor
        val choices = (listOf<Int?>(null) + eventColors.map { it.first } + selected).distinct()
        AlertDialog(
            onDismissRequest = { choosingColor = 0 },
            title = {
                Text(
                    stringResource(
                        if (choosingColor == 1) R.string.work_color else R.string.rest_color
                    )
                )
            },
            text = {
                LazyColumn {
                    items(choices) { color ->
                        TextButton(
                            onClick = {
                                workColor = color
                                choosingColor = 0
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(selected == color, onClick = null)
                            color?.let {
                                Box(
                                    Modifier.padding(horizontal = 8.dp)
                                        .size(20.dp)
                                        .background(Color(it), CircleShape)
                                )
                            }
                            Text(colorLabel(color), Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingColor = 0 }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun SettingChoice(
    title: String,
    value: String,
    tag: String,
    color: Int? = null,
    icon: Int? = null,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, Modifier.fillMaxWidth().testTag(tag)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.let {
                Icon(
                    painterResource(it),
                    null,
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (value.isNotBlank())
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (icon == null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            color?.let { Box(Modifier.size(28.dp).background(Color(it), CircleShape)) }
            Icon(
                painterResource(R.drawable.ic_chevron),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
