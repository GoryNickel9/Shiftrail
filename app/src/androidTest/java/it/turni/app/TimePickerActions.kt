package it.turni.app

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

fun ComposeContentTestRule.selectTime(tag: String, time: String) {
    val parts = time.split(":").map(String::toInt)
    require(parts[0] in 0..23 && parts[1] in listOf(0, 15, 30, 45))
    onNodeWithTag(tag).performScrollTo().performClick()
    onNodeWithTag("time_hour").performSemanticsAction(SemanticsActions.SetProgress) {
        it(parts[0].toFloat())
    }
    onNodeWithTag("time_minute").performSemanticsAction(SemanticsActions.SetProgress) {
        it((parts[1] / 15).toFloat())
    }
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    try {
        val directory =
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-1.0.1").apply {
                mkdirs()
            }
        File(directory, "time-picker.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    } finally {
        screenshot.recycle()
    }
    onNodeWithTag("confirm_time").performClick()
}
