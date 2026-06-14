package com.oasismall.oasisai.domain.phonesync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

class PhoneSyncClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.MINUTES)
        .writeTimeout(20, TimeUnit.MINUTES)
        .build()

    suspend fun ping(host: String, port: Int, pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(base(host, port) + PhoneSyncProtocol.PATH_PING)
                .header(PhoneSyncProtocol.HEADER_PIN, pin)
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Ping failed: ${response.code}")
            }
        }
    }

    suspend fun fetchCatalog(host: String, port: Int, pin: String): Result<PhoneSyncCatalog> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(base(host, port) + PhoneSyncProtocol.PATH_CATALOG)
                    .header(PhoneSyncProtocol.HEADER_PIN, pin)
                    .get()
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Catalog failed: ${response.code}")
                    val body = response.body?.string() ?: error("Empty catalog")
                    PhoneSyncCatalog.fromJson(JSONObject(body))
                }
            }
        }

    suspend fun pushOutbound(
        host: String,
        port: Int,
        pin: String,
        deviceName: String,
        items: List<PhoneSyncOutboundItem>,
    ): Result<PhoneSyncApplyResult> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext Result.failure(IllegalStateException("Nothing new to send"))
        runCatching {
            val products = items.map { item ->
                PhoneSyncPushProduct(
                    barcode = item.barcode,
                    codeart = item.codeart,
                    designation = item.designation,
                    price = item.price,
                    imageFilename = item.imageFile.name,
                    alternateBarcodes = item.alternateBarcodes,
                )
            }
            val manifest = PhoneSyncPushManifest(
                deviceName = deviceName,
                sentAt = Instant.now().toString(),
                products = products,
            )
            val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("manifest", manifest.toJson().toString())
            items.forEach { item ->
                bodyBuilder.addFormDataPart(
                    "images",
                    item.imageFile.name,
                    item.imageFile.asRequestBody("image/png".toMediaType()),
                )
            }
            val request = Request.Builder()
                .url(base(host, port) + PhoneSyncProtocol.PATH_PUSH)
                .header(PhoneSyncProtocol.HEADER_PIN, pin)
                .post(bodyBuilder.build())
                .build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Push failed: ${response.code} $raw")
                val json = JSONObject(raw)
                PhoneSyncApplyResult(
                    imagesApplied = json.optInt("images_applied"),
                    alternatesLinked = json.optInt("alternates_linked"),
                    skipped = json.optInt("skipped"),
                    messages = json.optString("messages").split("; ").filter { it.isNotBlank() },
                )
            }
        }
    }

    private fun base(host: String, port: Int): String = "http://${host.trim()}:$port"
}
