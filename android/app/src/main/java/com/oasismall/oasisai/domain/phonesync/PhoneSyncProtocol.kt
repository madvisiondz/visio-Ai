package com.oasismall.oasisai.domain.phonesync

import org.json.JSONArray
import org.json.JSONObject

object PhoneSyncProtocol {
    const val PORT = 8776
    const val PATH_PING = "/sync/phone/ping"
    const val PATH_CATALOG = "/sync/phone/catalog"
    const val PATH_CATALOG_TEXT = "/sync/phone/catalog.txt"
    const val PATH_PUSH = "/sync/phone/push"
    const val HEADER_PIN = "X-Oasis-Pin"
    const val PREFS = "oasis_phone_sync"
}

data class PhoneSyncCatalog(
    val exportedAt: String,
    val deviceName: String,
    val articleCount: Int,
    val imageCount: Int,
    val catalogText: String,
    val entries: List<PhoneSyncCatalogEntry>,
    val alternateBarcodes: List<PhoneSyncAlternateEntry>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("exported_at", exportedAt)
        put("device_name", deviceName)
        put("article_count", articleCount)
        put("image_count", imageCount)
        put("catalog_text", catalogText)
        put("entries", JSONArray().apply {
            entries.forEach { put(it.toJson()) }
        })
        put("alternate_barcodes", JSONArray().apply {
            alternateBarcodes.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(raw: JSONObject): PhoneSyncCatalog = PhoneSyncCatalog(
            exportedAt = raw.optString("exported_at"),
            deviceName = raw.optString("device_name"),
            articleCount = raw.optInt("article_count"),
            imageCount = raw.optInt("image_count"),
            catalogText = raw.optString("catalog_text"),
            entries = raw.optJSONArray("entries")?.let { arr ->
                (0 until arr.length()).map { i -> PhoneSyncCatalogEntry.fromJson(arr.getJSONObject(i)) }
            }.orEmpty(),
            alternateBarcodes = raw.optJSONArray("alternate_barcodes")?.let { arr ->
                (0 until arr.length()).map { i -> PhoneSyncAlternateEntry.fromJson(arr.getJSONObject(i)) }
            }.orEmpty(),
        )
    }
}

data class PhoneSyncCatalogEntry(
    val barcode: String,
    val codeart: String?,
    val designation: String,
    val hasImage: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("barcode", barcode)
        put("codeart", codeart.orEmpty())
        put("designation", designation)
        put("has_image", hasImage)
    }

    companion object {
        fun fromJson(o: JSONObject) = PhoneSyncCatalogEntry(
            barcode = o.optString("barcode"),
            codeart = o.optString("codeart").ifBlank { null },
            designation = o.optString("designation"),
            hasImage = o.optBoolean("has_image"),
        )
    }
}

data class PhoneSyncAlternateEntry(
    val barcode: String,
    val primaryBarcode: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("barcode", barcode)
        put("primary_barcode", primaryBarcode)
    }

    companion object {
        fun fromJson(o: JSONObject) = PhoneSyncAlternateEntry(
            barcode = o.optString("barcode"),
            primaryBarcode = o.optString("primary_barcode"),
        )
    }
}

data class PhoneSyncPushProduct(
    val barcode: String,
    val codeart: String?,
    val designation: String,
    val price: Double,
    val imageFilename: String,
    val alternateBarcodes: List<String>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("barcode", barcode)
        put("codeart", codeart.orEmpty())
        put("designation", designation)
        put("price", price)
        put("image_filename", imageFilename)
        put("alternate_barcodes", JSONArray(alternateBarcodes))
    }

    companion object {
        fun fromJson(o: JSONObject) = PhoneSyncPushProduct(
            barcode = o.optString("barcode"),
            codeart = o.optString("codeart").ifBlank { null },
            designation = o.optString("designation"),
            price = o.optDouble("price"),
            imageFilename = o.optString("image_filename"),
            alternateBarcodes = o.optJSONArray("alternate_barcodes")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }.orEmpty(),
        )
    }
}

data class PhoneSyncPushManifest(
    val deviceName: String,
    val sentAt: String,
    val products: List<PhoneSyncPushProduct>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("device_name", deviceName)
        put("sent_at", sentAt)
        put("products", JSONArray().apply { products.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(raw: JSONObject) = PhoneSyncPushManifest(
            deviceName = raw.optString("device_name"),
            sentAt = raw.optString("sent_at"),
            products = raw.optJSONArray("products")?.let { arr ->
                (0 until arr.length()).map { i -> PhoneSyncPushProduct.fromJson(arr.getJSONObject(i)) }
            }.orEmpty(),
        )
    }
}

data class PhoneSyncApplyResult(
    val imagesApplied: Int,
    val alternatesLinked: Int,
    val skipped: Int,
    val messages: List<String>,
)
