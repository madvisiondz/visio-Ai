package com.oasismall.oasisai.domain.paray

import org.json.JSONObject
import java.io.File

/** Persists Scan & shoot lock state across unlock, screen off, and process restart. */
class ParaySessionStore(home: ParayHome) {
    private val file = home.checkShootSessionFile

    fun save(session: CheckShootPersistedSession) {
        file.writeText(
            JSONObject()
                .put("locked", session.locked)
                .put("barcode", session.barcode)
                .put("articleId", session.articleId ?: JSONObject.NULL)
                .put("designation", session.designation ?: "")
                .put("inGestiumCatalog", session.inGestiumCatalog)
                .put("linkedViaAlternate", session.linkedViaAlternate)
                .put("existingImagePath", session.existingImagePath ?: "")
                .put("savedAt", System.currentTimeMillis())
                .toString(),
        )
    }

    fun load(): CheckShootPersistedSession? {
        if (!file.exists()) return null
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        if (!o.optBoolean("locked", false)) return null
        val articleId = if (o.isNull("articleId")) null else o.getLong("articleId")
        return CheckShootPersistedSession(
            locked = true,
            barcode = o.getString("barcode"),
            articleId = articleId,
            designation = o.optString("designation").ifBlank { null },
            inGestiumCatalog = o.optBoolean("inGestiumCatalog", false),
            linkedViaAlternate = o.optBoolean("linkedViaAlternate", false),
            existingImagePath = o.optString("existingImagePath").ifBlank { null },
        )
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}

data class CheckShootPersistedSession(
    val locked: Boolean,
    val barcode: String,
    val articleId: Long?,
    val designation: String?,
    val inGestiumCatalog: Boolean,
    val linkedViaAlternate: Boolean,
    val existingImagePath: String?,
)
