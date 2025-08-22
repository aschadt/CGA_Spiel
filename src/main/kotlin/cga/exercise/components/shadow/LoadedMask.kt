import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths

data class LoadedMask(val width: Int, val height: Int, val data: ByteArray)

/** Lädt unsere RAW-Maskendatei ("MSK1" + width + height + bytes). */
private fun loadMaskRaw(path: String): LoadedMask {
    val bytes = Files.readAllBytes(Paths.get(path))
    require(bytes.size >= 12) { "Datei zu klein" }

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val m = buf.get().toInt().toChar()
    val s = buf.get().toInt().toChar()
    val k = buf.get().toInt().toChar()
    val one = buf.get().toInt().toChar()
    require("$m$s$k$one" == "MSK1") { "Ungültige Signatur" }

    val w = buf.int
    val h = buf.int
    val expected = w * h
    require(buf.remaining() == expected) { "Datenlänge passt nicht zu ${w}x${h}" }

    val data = ByteArray(expected)
    buf.get(data)
    return LoadedMask(w, h, data)
}