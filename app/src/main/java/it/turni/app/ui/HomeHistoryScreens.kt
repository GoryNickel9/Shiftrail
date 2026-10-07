package it.turni.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.turni.app.R
import it.turni.app.data.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    state: AppState,
    camera: () -> Unit,
    gallery: () -> Unit,
    settings: () -> Unit,
) {
    val resources = LocalContext.current.resources
    // Publish a small software sRGB bitmap after decoding to trigger an independent first draw.
    val artwork =
        produceState<Bitmap?>(null, resources) {
            value =
                withContext(Dispatchers.IO) {
                    BitmapFactory.decodeResource(
                        resources,
                        R.drawable.shiftrail_hero,
                        BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                            inSampleSize = 2
                            inScaled = false
                        },
                    )
                }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.brand_tagline),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.home_steps),
                Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Box(
                Modifier.fillMaxWidth()
                    .height(170.dp)
                    .background(Color(0xFFF4F7F6), MaterialTheme.shapes.large)
                    .testTag("home_illustration")
            ) {
                artwork.value?.let { bitmap ->
                    Image(
                        bitmap.asImageBitmap(),
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        item {
            if (state.profile == null) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.first_setup),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(stringResource(R.string.profile_help))
                        Button(onClick = settings, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.setup_button))
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.import_for, state.profile.name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Button(onClick = camera, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.camera))
                }
                OutlinedButton(
                    onClick = gallery,
                    Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp),
                ) {
                    Text(stringResource(R.string.gallery))
                }
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    stringResource(R.string.local_note),
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        item {
            Text(stringResource(R.string.upcoming), style = MaterialTheme.typography.titleLarge)
        }
        if (state.upcoming.isEmpty())
            item {
                Text(
                    stringResource(R.string.empty_upcoming),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        items(state.upcoming, key = { it.id }) { shift -> ShiftSummary(shift) }
    }
}

@Composable
private fun ShiftSummary(shift: ShiftEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                java.time.LocalDate.parse(shift.date)
                    .format(
                        DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.ITALIAN)
                    ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (shift.type == "WORK_SHIFT") "${shift.startTime} → ${shift.endTime}"
                else shift.code.orEmpty()
            )
            if (shift.endNextDay) Text(stringResource(R.string.overnight))
        }
    }
}

@Composable
fun ResultScreen(count: Int, onHome: () -> Unit) {
    ScreenColumn {
        Text(stringResource(R.string.sync_result), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.sync_count, count))
        Text(stringResource(R.string.done_note))
        Button(onClick = onHome) { Text(stringResource(R.string.home)) }
    }
}
