package it.turni.app

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.util.UUID

/** Synthetic people and clock records; no private PDF is packaged in the APK or repository. */
object PayrollFixture {
    val minutes =
        listOf(
            180,
            180,
            300,
            180,
            180,
            300,
            180,
            0,
            360,
            240,
            180,
            360,
            300,
            300,
            0,
            180,
            360,
            180,
            0,
            180,
            180,
            0,
            360,
            375,
            240,
            180,
            0,
            360,
            360,
            360,
            360,
        )
    private val starts =
        listOf(
            1110,
            720,
            1140,
            660,
            720,
            1320,
            1200,
            0,
            1425,
            1140,
            1140,
            1260,
            1110,
            720,
            0,
            780,
            1140,
            1320,
            0,
            780,
            720,
            0,
            1425,
            1320,
            1200,
            1200,
            0,
            1020,
            720,
            600,
            1020,
        )

    private fun clock(value: Int) = "%02d:%02d".format(value / 60, value % 60)

    fun pdf(context: Context): File {
        val output = File(context.cacheDir, "payroll-fixture-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val ink =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    typeface = Typeface.MONOSPACE
                    textSize = 6f
                }
            fun text(value: String, x: Float, y: Float, size: Float = 6f) {
                ink.textSize = size
                canvas.drawText(value, x, y, ink)
            }
            text("AZIENDA DIMOSTRATIVA", 30f, 90f, 8f)
            text("MESE", 30f, 118f, 7f)
            text("ANNO", 153f, 118f, 7f)
            text("Ottobre", 30f, 142f, 8f)
            text("2026", 153f, 142f, 8f)
            text("COGNOME", 26f, 156f, 7f)
            text("NOME", 153f, 156f, 7f)
            text("ROSSI", 31f, 180f, 8f)
            text("LUCA", 153f, 180f, 8f)
            text("GIORNO", 26f, 233f, 4f)
            text("TOTALE ORE", 330f, 234f, 9f)
            val rule =
                Paint().apply {
                    color = Color.BLACK
                    strokeWidth = .6f
                }
            val edges = listOf(25f) + (0..10).map { 40f + 21.5f * it } + 565f
            edges.forEach { x -> canvas.drawLine(x, 219f, x, 619.1f, rule) }
            canvas.drawLine(25f, 219f, 565f, 219f, rule)
            (0..31).forEach { i ->
                canvas.drawLine(25f, 244f + 12.1f * i, 565f, 244f + 12.1f * i, rule)
            }
            (0..9).forEach { column ->
                text(if (column % 2 == 0) "E" else "U", 47f + 21.5f * column, 233f, 6f)
            }
            val stamps = List(31) { arrayOfNulls<Int>(10) }
            for (index in minutes.indices) {
                if (minutes[index] == 0) continue
                val first = if (stamps[index][1] != null) 2 else 0
                stamps[index][first] = starts[index]
                val end = starts[index] + minutes[index]
                if (end >= 1440 && index < 30) stamps[index + 1][1] = end % 1440
                else stamps[index][first + 1] = end % 1440
            }
            stamps[0][0] = 1109
            stamps[0][1] = 1292
            minutes.forEachIndexed { index, value ->
                val y = 252.5f + 12.1f * index
                text("${index+1}", 30f, y)
                stamps[index].forEachIndexed { column, time ->
                    if (time != null) text(clock(time), 41.5f + 21.5f * column, y)
                }
                if (value > 0) {
                    val extra =
                        if (index == 23) "04:15 M25 01:00 502 "
                        else if (index == 17) "03:00 MDom 10:00 MORE+ " else ""
                    text("$extra${clock(value)} HH_LAV ${clock(value)} TOTLAV", 263f, y)
                }
            }
            text("TOTALE GENERALE", 28f, 631f, 8f)
            text("102:15 HH_LAV Ore Lavorate", 28f, 645f)
            text("115:15 TOTLAV Totali Ore Lavorat", 190f, 645f)
            text("23:45 M25 Maggiorazione notturna", 28f, 659f)
            text("01:00 502 ORA SOLARE", 28f, 673f)
            canvas.drawLine(25f, 686f, 565f, 686f, rule)
            document.finishPage(page)
            output.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return output
    }
}
