package com.oasismall.oasisai.domain.paray

import org.json.JSONObject

/** PARAY-owned audit log for shelf ticket scans and verifications. */
class ParayTicketStore(private val home: ParayHome) {

    fun recordScan(
        barcode: String,
        articleId: Long?,
        designation: String?,
        assessment: ParayTicketAssessment,
    ) {
        appendEvent(
            type = "TICKET_SCAN",
            barcode = barcode,
            articleId = articleId,
            designation = designation,
            assessment = assessment,
        )
    }

    fun recordVerified(
        barcode: String,
        articleId: Long?,
        designation: String?,
        assessment: ParayTicketAssessment,
    ) {
        appendEvent(
            type = "TICKET_VERIFIED",
            barcode = barcode,
            articleId = articleId,
            designation = designation,
            assessment = assessment,
        )
    }

    private fun appendEvent(
        type: String,
        barcode: String,
        articleId: Long?,
        designation: String?,
        assessment: ParayTicketAssessment,
    ) {
        val line = JSONObject()
            .put("type", type)
            .put("at", System.currentTimeMillis())
            .put("barcode", barcode)
            .put("articleId", articleId ?: JSONObject.NULL)
            .put("designation", designation ?: JSONObject.NULL)
            .put("status", assessment.status.name)
            .put("catalogPrice", assessment.catalogPrice ?: JSONObject.NULL)
            .put("lastPrintedPrice", assessment.lastPrintedPrice ?: JSONObject.NULL)
            .put("rayon", assessment.rayon ?: JSONObject.NULL)
            .put("needsTicketUpdate", assessment.needsTicketUpdate)
            .put("message", assessment.message)
            .toString()
        home.ticketEventsFile.parentFile?.mkdirs()
        home.ticketEventsFile.appendText(line + "\n")
    }
}
