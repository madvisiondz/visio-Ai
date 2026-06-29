package com.oasismall.oasisai.domain.agent

import android.content.Context
import com.oasismall.oasisai.util.writeTextAtomic
import org.json.JSONObject
import java.io.File

/** Persists AGENT lock state across unlock, screen off, and process restart. */
class AgentSessionStore(context: Context) {
    private val file = File(context.filesDir, "agent/check_shoot_session.json")

    fun save(session: AgentPersistedSession) {
        file.parentFile?.mkdirs()
        file.writeTextAtomic(
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

    fun load(): AgentPersistedSession? {
        if (!file.exists()) return null
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        if (!o.optBoolean("locked", false)) return null
        val articleId = if (o.isNull("articleId")) null else o.getLong("articleId")
        return AgentPersistedSession(
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

data class AgentPersistedSession(
    val locked: Boolean,
    val barcode: String,
    val articleId: Long?,
    val designation: String?,
    val inGestiumCatalog: Boolean,
    val linkedViaAlternate: Boolean,
    val existingImagePath: String?,
)
