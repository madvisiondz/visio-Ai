package com.oasismall.oasisai.domain.phonesync

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File

class PhoneSyncServer(
    port: Int,
    private val pin: String,
    private val catalogProvider: suspend () -> PhoneSyncCatalog,
    private val onPush: suspend (PhoneSyncPushManifest, Map<String, File>) -> PhoneSyncApplyResult,
) : NanoHTTPD(port) {

    var lastReceiveSummary: String? = null
        private set

    override fun serve(session: IHTTPSession): Response {
        if (!checkPin(session)) {
            return jsonResponse(JSONObject().put("error", "Invalid PIN"), Response.Status.UNAUTHORIZED)
        }
        val uri = session.uri
        return when {
            uri == PhoneSyncProtocol.PATH_PING && session.method == Method.GET -> handlePing()
            uri == PhoneSyncProtocol.PATH_CATALOG && session.method == Method.GET -> handleCatalog()
            uri == PhoneSyncProtocol.PATH_CATALOG_TEXT && session.method == Method.GET -> handleCatalogText()
            uri == PhoneSyncProtocol.PATH_PUSH && session.method == Method.POST -> handlePush(session)
            else -> jsonResponse(JSONObject().put("error", "Not found"), Response.Status.NOT_FOUND)
        }
    }

    private fun checkPin(session: IHTTPSession): Boolean {
        val header = session.headers[PhoneSyncProtocol.HEADER_PIN.lowercase()]
            ?: session.headers[PhoneSyncProtocol.HEADER_PIN]
            ?: session.parms[PhoneSyncProtocol.HEADER_PIN]
        return pin.isNotBlank() && header == pin
    }

    private fun handlePing(): Response {
        val body = JSONObject().apply {
            put("role", "master")
            put("service", "oasis-phone-sync")
            put("port", PhoneSyncProtocol.PORT)
        }
        return jsonResponse(body, Response.Status.OK)
    }

    private fun handleCatalog(): Response = runBlocking {
        val catalog = catalogProvider()
        jsonResponse(catalog.toJson(), Response.Status.OK)
    }

    private fun handleCatalogText(): Response = runBlocking {
        val catalog = catalogProvider()
        newFixedLengthResponse(
            Response.Status.OK,
            "text/plain; charset=utf-8",
            catalog.catalogText,
        )
    }

    private fun handlePush(session: IHTTPSession): Response = runBlocking {
        val files = mutableMapOf<String, String>()
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            return@runBlocking jsonResponse(
                JSONObject().put("error", "Bad multipart: ${e.message}"),
                Response.Status.BAD_REQUEST,
            )
        }
        val manifestRaw = files["manifest"] ?: session.parms["manifest"]
        if (manifestRaw.isNullOrBlank()) {
            return@runBlocking jsonResponse(
                JSONObject().put("error", "Missing manifest"),
                Response.Status.BAD_REQUEST,
            )
        }
        val manifest = PhoneSyncPushManifest.fromJson(JSONObject(manifestRaw))
        val imageMap = mutableMapOf<String, File>()
        val uploaded = files.values.mapNotNull { path ->
            val file = File(path)
            if (file.isFile) file else null
        }
        manifest.products.forEach { product ->
            val match = uploaded.firstOrNull { it.name == product.imageFilename }
                ?: uploaded.firstOrNull { it.name.endsWith(product.imageFilename) }
            if (match != null) imageMap[product.imageFilename] = match
        }
        val result = onPush(manifest, imageMap)
        lastReceiveSummary =
            "From ${manifest.deviceName}: ${result.imagesApplied} PNG(s), ${result.alternatesLinked} alt barcode(s)"
        jsonResponse(
            JSONObject().apply {
                put("images_applied", result.imagesApplied)
                put("alternates_linked", result.alternatesLinked)
                put("skipped", result.skipped)
                put("messages", result.messages.joinToString("; "))
            },
            Response.Status.OK,
        )
    }

    private fun jsonResponse(body: JSONObject, status: Response.Status): Response =
        newFixedLengthResponse(status, "application/json", body.toString())
}
