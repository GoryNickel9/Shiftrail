package it.turni.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF2255C7),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4ECFF),
        onPrimaryContainer = Color(0xFF17386F),
        secondary = Color(0xFF386452),
        secondaryContainer = Color(0xFFD9F2E8),
        onSecondaryContainer = Color(0xFF183D30),
        background = Color(0xFFF4F7F6),
        onBackground = Color(0xFF162A35),
        surface = Color.White,
        onSurface = Color(0xFF162A35),
        surfaceVariant = Color(0xFFE8EEEC),
        onSurfaceVariant = Color(0xFF4A5D65),
        surfaceContainer = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainerHigh = Color(0xFFE8EEEC),
        surfaceContainerHighest = Color.White,
        surfaceContainerLowest = Color.White,
        outline = Color(0xFF73848B),
        outlineVariant = Color(0xFFD8E2DF),
        error = Color(0xFF9F3326),
        errorContainer = Color(0xFFFFE9E3),
        onErrorContainer = Color(0xFF6F2018),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB3C7FF),
        onPrimary = Color(0xFF0D2E70),
        primaryContainer = Color(0xFF203E76),
        onPrimaryContainer = Color(0xFFDEE8FF),
        secondary = Color(0xFFADD8C5),
        secondaryContainer = Color(0xFF234C3E),
        onSecondaryContainer = Color(0xFFCFEDDF),
        background = Color(0xFF101B22),
        surface = Color(0xFF19282F),
        onSurface = Color(0xFFE2ECE9),
        onBackground = Color(0xFFE2ECE9),
        surfaceContainer = Color(0xFF19282F),
        surfaceContainerLow = Color(0xFF19282F),
        surfaceContainerHighest = Color(0xFF2E4149),
        surfaceContainerLowest = Color(0xFF101B22),
        surfaceVariant = Color(0xFF2E4149),
        onSurfaceVariant = Color(0xFFB8CBCF),
        outlineVariant = Color(0xFF3B4F57),
    )

@Composable
fun ShiftrailTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes =
            Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
        content = content,
    )
}

@Composable
fun ImportProgress(step: Int) {
    val labels = listOf(R.string.step_photo, R.string.step_review, R.string.step_calendar)
    Text(
        stringResource(R.string.import_progress, step, stringResource(labels[step - 1])),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Keep the primary action visible while the body scrolls, including with larger fonts. */
@Composable
fun ActionFooter(label: String, enabled: Boolean = true, note: String? = null, action: () -> Unit) {
    Surface(shadowElevation = 4.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = action,
                shape = MaterialTheme.shapes.medium,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(label)
            }
        }
    }
}
