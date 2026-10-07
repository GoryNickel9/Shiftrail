package it.turni.core

import java.time.LocalDate
import kotlin.system.measureNanoTime

/** Reproducible smoke benchmark; not a replacement for JMH or measurements on real photos. */
object ParserBenchmark {
    @JvmStatic
    fun main(args: Array<String>) {
        val date = LocalDate.of(2026, 10, 5)
        fun token(text: String, x: Float, y: Float, w: Float = 0.06f) =
            OcrToken(text, Box(x - w / 2, y - 0.006f, x + w / 2, y + 0.006f), 0.95f)
        val tokens = buildList {
            listOf("LUNEDI", "MARTEDI", "MERCOLEDI", "GIOVEDI", "VENERDI", "SABATO", "DOMENICA")
                .forEachIndexed { i, day ->
                    add(token(day, 0.29f + i * 0.105f, 0.05f))
                    add(token("${5+i}-ott-2026", 0.29f + i * 0.105f, 0.075f))
                }
            repeat(30) { row ->
                val y = 0.15f + row * 0.025f
                add(token(if (row == 12) "BALDEDDU LUCA" else "DIPENDENTE $row", 0.1f, y, 0.17f))
                repeat(7) { day ->
                    add(token(if (day == 1) "X" else "19-01", 0.29f + day * 0.105f, y))
                }
            }
        }
        val parser = TableParser()
        val profile = EmployeeProfile("Luca Baldeddu")
        repeat(100) { check(parser.extract(tokens, profile, date).cells.size == 7) }
        val iterations = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 100000) ?: 1000
        var checksum = 0
        val nanos = measureNanoTime {
            repeat(iterations) { checksum += parser.extract(tokens, profile, date).cells.size }
        }
        println("tokens,iterations,total_ms,mean_ms,checksum")
        println("${tokens.size},$iterations,${nanos / 1e6},${nanos / 1e6 / iterations},$checksum")
        println(
            "java=${System.getProperty("java.version")},os=${System.getProperty("os.name")},threads=1"
        )
    }
}
