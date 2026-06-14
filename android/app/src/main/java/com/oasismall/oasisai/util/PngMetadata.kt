package com.oasismall.oasisai.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Reads/writes barcode in PNG text chunks (visible in file Properties → Details on Windows).
 * - [KEY_BARCODE] — machine-readable for the app
 * - [KEY_DESCRIPTION] — human-readable file description
 */
object PngMetadata {
    const val KEY_BARCODE = "Barcode"
    const val KEY_DESCRIPTION = "Description"
    const val KEY_DESIGNATION = "Designation"
    const val KEY_PRICE_NOW = "PriceNow"
    const val KEY_PRICE_BEFORE = "PriceBefore"
    const val KEY_RAYON = "Rayon"
    const val KEY_CODEART = "Codeart"

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )
    private val BARCODE_FILENAME_SUFFIX = Regex("_([0-9]{8,18})$")
    private val BARCODE_FILENAME_ONLY = Regex("^[0-9]{8,18}$")
    private val BARCODE_IN_DESCRIPTION = Regex("""(?i)barcode\s*[=:]\s*([0-9]{8,18})""")

    data class PngArticleDetails(
        val barcode: String? = null,
        val codeart: String? = null,
        val designation: String? = null,
        val priceNow: Double? = null,
        val priceBefore: Double? = null,
        val rayon: String? = null,
        val description: String? = null,
    )


    fun readAllTextChunks(file: File): Map<String, String> {
        if (!file.isFile || file.extension.lowercase() != "png") return emptyMap()
        val out = mutableMapOf<String, String>()
        RandomAccessFile(file, "r").use { raf ->
            val sig = ByteArray(8)
            if (raf.read(sig) != 8 || !sig.contentEquals(PNG_SIGNATURE)) return emptyMap()
            while (raf.filePointer < raf.length()) {
                val lenBuf = ByteArray(4)
                if (raf.read(lenBuf) != 4) break
                val length = ByteBuffer.wrap(lenBuf).order(ByteOrder.BIG_ENDIAN).int
                if (length < 0 || length > 1_000_000) break
                val type = ByteArray(4)
                if (raf.read(type) != 4) break
                val chunkType = String(type, Charsets.US_ASCII)
                val data = ByteArray(length)
                if (length > 0 && raf.read(data) != length) break
                raf.skipBytes(4)
                if (chunkType == "tEXt" || chunkType == "iTXt") {
                    parseTextChunk(data, chunkType)?.let { (kw, text) ->
                        out[kw] = text
                    }
                }
                if (chunkType == "IEND") break
            }
        }
        return out
    }

    fun readArticleDetails(file: File): PngArticleDetails {
        val chunks = readAllTextChunks(file)
        val barcode = chunks[KEY_BARCODE]?.trim()?.takeIf { it.isNotBlank() }
            ?: chunks[KEY_DESCRIPTION]?.let { desc ->
                BARCODE_IN_DESCRIPTION.find(desc)?.groupValues?.get(1)
            }
            ?: extractBarcodeFromFilename(file.nameWithoutExtension)
        return PngArticleDetails(
            barcode = barcode,
            codeart = chunks[KEY_CODEART]?.trim()?.takeIf { it.isNotBlank() },
            designation = chunks[KEY_DESIGNATION]?.trim()?.takeIf { it.isNotBlank() },
            priceNow = chunks[KEY_PRICE_NOW]?.let { parsePriceText(it) },
            priceBefore = chunks[KEY_PRICE_BEFORE]?.let { parsePriceText(it) },
            rayon = chunks[KEY_RAYON]?.trim()?.takeIf { it.isNotBlank() },
            description = chunks[KEY_DESCRIPTION]?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    /** Parses values like `90,00 DA` from PNG tEXt (matches PC script + [PriceFormatter]). */
    fun parsePriceText(raw: String): Double? {
        val cleaned = raw.trim()
            .removeSuffix("DA")
            .trim()
            .replace(" ", "")
            .replace(",", ".")
        return cleaned.toDoubleOrNull()
    }

    fun readBarcode(file: File): String? =
        readArticleDetails(file).barcode

    /** Safe filename stem for barcode-only PNGs (matches [extractBarcodeFromFilename] rules). */
    fun barcodeFileStem(barcode: String): String {
        val trimmed = barcode.trim()
        if (BARCODE_FILENAME_ONLY.matches(trimmed)) return trimmed
        return trimmed.replace(Regex("[^A-Za-z0-9]"), "").take(32).ifBlank { "barcode" }
    }

    fun buildDescription(barcode: String, designation: String?): String {
        val des = designation?.trim().orEmpty()
        return if (des.isNotEmpty()) {
            "Barcode: $barcode | $des"
        } else {
            "Barcode: $barcode"
        }
    }

    fun writeBarcode(file: File, barcode: String, designation: String?) {
        if (!file.isFile) return
        val description = buildDescription(barcode, designation)
        writeTextChunks(
            file,
            mapOf(
                KEY_BARCODE to barcode.trim(),
                KEY_DESCRIPTION to description,
            ),
        )
    }

    fun writeArticleDetails(
        file: File,
        barcode: String,
        designation: String?,
        priceNow: Double?,
        priceBefore: Double?,
        rayon: String?,
        codeart: String? = null,
    ) {
        if (!file.isFile) return
        val details = buildList {
            designation?.trim()?.takeIf { it.isNotBlank() }?.let { add("Designation: $it") }
            priceNow?.let { add("Price now: ${PriceFormatter.format(it)}") }
            priceBefore?.let { add("Price before: ${PriceFormatter.format(it)}") }
            add("Barcode: ${barcode.trim()}")
            codeart?.trim()?.takeIf { it.isNotBlank() }?.let { add("Code: $it") }
            rayon?.trim()?.takeIf { it.isNotBlank() }?.let { add("Rayon: $it") }
        }
        val chunks = buildMap {
            put(KEY_BARCODE, barcode.trim())
            put(KEY_DESCRIPTION, details.joinToString(" | "))
            designation?.trim()?.takeIf { it.isNotBlank() }?.let { put(KEY_DESIGNATION, it) }
            priceNow?.let { put(KEY_PRICE_NOW, PriceFormatter.format(it)) }
            priceBefore?.let { put(KEY_PRICE_BEFORE, PriceFormatter.format(it)) }
            codeart?.trim()?.takeIf { it.isNotBlank() }?.let { put(KEY_CODEART, it) }
            rayon?.trim()?.takeIf { it.isNotBlank() }?.let { put(KEY_RAYON, it) }
        }
        writeTextChunks(file, chunks)
    }

    fun extractBarcodeFromFilename(stem: String): String? =
        if (BARCODE_FILENAME_ONLY.matches(stem)) {
            stem
        } else {
            BARCODE_FILENAME_SUFFIX.find(stem)?.groupValues?.get(1)
        }

    fun stemWithoutBarcodeSuffix(stem: String): String =
        stem.replace(BARCODE_FILENAME_SUFFIX, "")

    private fun readTextChunk(file: File, keyword: String): String? {
        RandomAccessFile(file, "r").use { raf ->
            val sig = ByteArray(8)
            if (raf.read(sig) != 8 || !sig.contentEquals(PNG_SIGNATURE)) return null
            while (raf.filePointer < raf.length()) {
                val lenBuf = ByteArray(4)
                if (raf.read(lenBuf) != 4) break
                val length = ByteBuffer.wrap(lenBuf).order(ByteOrder.BIG_ENDIAN).int
                if (length < 0 || length > 1_000_000) break
                val type = ByteArray(4)
                if (raf.read(type) != 4) break
                val chunkType = String(type, Charsets.US_ASCII)
                val data = ByteArray(length)
                if (length > 0 && raf.read(data) != length) break
                raf.skipBytes(4) // CRC
                if (chunkType == "tEXt" || chunkType == "iTXt") {
                    parseTextChunk(data, chunkType)?.let { (kw, text) ->
                        if (kw == keyword) return text
                    }
                }
                if (chunkType == "IEND") break
            }
        }
        return null
    }

    private fun parseTextChunk(data: ByteArray, type: String): Pair<String, String>? {
        if (type == "tEXt") {
            val zero = data.indexOf(0)
            if (zero < 0) return null
            val keyword = String(data, 0, zero, Charsets.US_ASCII)
            val text = String(data, zero + 1, data.size - zero - 1, Charsets.ISO_8859_1)
            return keyword to text
        }
        // iTXt: keyword\0 compression\0 method\0 language\0 translated\0 text
        val zero = data.indexOf(0)
        if (zero < 0) return null
        val keyword = String(data, 0, zero, Charsets.US_ASCII)
        if (zero + 1 >= data.size) return null
        val compression = data[zero + 1]
        var pos = zero + 2
        while (pos < data.size && data[pos] != 0.toByte()) pos++
        if (pos >= data.size) return null
        pos++
        while (pos < data.size && data[pos] != 0.toByte()) pos++
        if (pos >= data.size) return null
        pos++
        while (pos < data.size && data[pos] != 0.toByte()) pos++
        if (pos >= data.size) return null
        pos++
        while (pos < data.size && data[pos] != 0.toByte()) pos++
        if (pos >= data.size) return null
        pos++
        if (pos > data.size) return null
        val textBytes = data.copyOfRange(pos, data.size)
        val text = if (compression.toInt() == 0) {
            String(textBytes, Charsets.UTF_8)
        } else {
            return null
        }
        return keyword to text
    }

    private fun writeTextChunks(file: File, chunks: Map<String, String>) {
        val original = file.readBytes()
        if (original.size < 12 || !original.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return
        val rebuilt = stripTextChunks(original, chunks.keys)
        val iendIndex = findIendIndex(rebuilt) ?: return
        val before = rebuilt.copyOfRange(0, iendIndex)
        val after = rebuilt.copyOfRange(iendIndex, rebuilt.size)
        val newChunks = chunks.map { (keyword, text) -> buildTextChunk(keyword, text) }
        val combined = before + newChunks.fold(ByteArray(0)) { acc, c -> acc + c } + after
        file.writeBytes(combined)
    }

    private fun stripTextChunks(png: ByteArray, keywords: Set<String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var offset = 8
        out.write(png, 0, 8)
        while (offset + 12 <= png.size) {
            val length = ByteBuffer.wrap(png, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            if (length < 0) break
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            val total = 12 + length
            if (offset + total > png.size) break
            val drop = if (type == "tEXt" || type == "iTXt") {
                val data = png.copyOfRange(offset + 8, offset + 8 + length)
                val parsed = parseTextChunk(data, type)
                parsed != null && parsed.first in keywords
            } else {
                false
            }
            if (!drop) {
                out.write(png, offset, total)
            }
            offset += total
            if (type == "IEND") break
        }
        return out.toByteArray()
    }

    private fun findIendIndex(png: ByteArray): Int? {
        var offset = 8
        while (offset + 12 <= png.size) {
            val length = ByteBuffer.wrap(png, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            if (length < 0 || offset + 12 + length > png.size) return null
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            if (type == "IEND") return offset
            offset += 12 + length
        }
        return null
    }

    private fun buildTextChunk(keyword: String, text: String): ByteArray {
        val data = keyword.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0) +
            text.toByteArray(Charsets.ISO_8859_1)
        val type = "tEXt".toByteArray(Charsets.US_ASCII)
        val length = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array()
        val crcInput = type + data
        val crc = CRC32().apply { update(crcInput) }.value.toInt()
        val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc).array()
        return length + type + data + crcBytes
    }
}
