package com.oasismall.oasisai.domain.paray

import org.json.JSONObject

/** Persists PARAY Observer state, events, and rolling knowledge under `paray_home/observer/`. */
class ParayObserverStore(private val home: ParayHome) {
    private val stateFile = home.observerStateFile
    private val eventsFile = home.observerEventsFile
    private val knowledgeFile = home.observerKnowledgeFile

    fun readState(): ParayObserverState {
        if (!stateFile.exists()) return ParayObserverState()
        val o = runCatching { JSONObject(stateFile.readText()) }.getOrElse { return ParayObserverState() }
        return ParayObserverState(
            lastArticleCount = o.optInt("lastArticleCount", 0),
            lastPngCount = o.optInt("lastPngCount", 0),
            lastMissingPngCount = o.optInt("lastMissingPngCount", 0),
            lastImportId = o.optLong("lastImportId", 0L),
            lastImportTimestamp = o.optLong("lastImportTimestamp", 0L),
            lastObservationRun = o.optLong("lastObservationRun", 0L),
            lastKnowledgeRefresh = o.optLong("lastKnowledgeRefresh", 0L),
        )
    }

    fun writeState(state: ParayObserverState) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(
            JSONObject()
                .put("lastArticleCount", state.lastArticleCount)
                .put("lastPngCount", state.lastPngCount)
                .put("lastMissingPngCount", state.lastMissingPngCount)
                .put("lastImportId", state.lastImportId)
                .put("lastImportTimestamp", state.lastImportTimestamp)
                .put("lastObservationRun", state.lastObservationRun)
                .put("lastKnowledgeRefresh", state.lastKnowledgeRefresh)
                .toString(2),
        )
    }

    fun appendEvent(type: String, trigger: ParayObserverTrigger, data: JSONObject) {
        val line = JSONObject()
            .put("type", type)
            .put("trigger", trigger.name)
            .put("ts", System.currentTimeMillis())
            .put("data", data)
        eventsFile.parentFile?.mkdirs()
        eventsFile.appendText(line.toString() + "\n")
    }

    fun readKnowledge(): ParayObserverKnowledge {
        if (!knowledgeFile.exists()) return ParayObserverKnowledge()
        val o = runCatching { JSONObject(knowledgeFile.readText()) }.getOrElse { return ParayObserverKnowledge() }
        return ParayObserverKnowledge(
            lastChangeSummary = o.optString("lastChangeSummary"),
            lastCsvImportId = o.optLong("lastCsvImportId", 0L),
            lastCsvNewCount = o.optInt("lastCsvNewCount", 0),
            lastCsvPriceChanges = o.optInt("lastCsvPriceChanges", 0),
            lastCsvRenamed = o.optInt("lastCsvRenamed", 0),
            lastCsvRemoved = o.optInt("lastCsvRemoved", 0),
            lastPngLinkedCount = o.optInt("lastPngLinkedCount", 0),
            lastPngGainedEstimate = o.optInt("lastPngGainedEstimate", 0),
            lastPngLostEstimate = o.optInt("lastPngLostEstimate", 0),
            lastMissingPngCount = o.optInt("lastMissingPngCount", 0),
            lastActiveArticleCount = o.optInt("lastActiveArticleCount", 0),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    fun writeKnowledge(knowledge: ParayObserverKnowledge) {
        knowledgeFile.parentFile?.mkdirs()
        knowledgeFile.writeText(
            JSONObject()
                .put("lastChangeSummary", knowledge.lastChangeSummary)
                .put("lastCsvImportId", knowledge.lastCsvImportId)
                .put("lastCsvNewCount", knowledge.lastCsvNewCount)
                .put("lastCsvPriceChanges", knowledge.lastCsvPriceChanges)
                .put("lastCsvRenamed", knowledge.lastCsvRenamed)
                .put("lastCsvRemoved", knowledge.lastCsvRemoved)
                .put("lastPngLinkedCount", knowledge.lastPngLinkedCount)
                .put("lastPngGainedEstimate", knowledge.lastPngGainedEstimate)
                .put("lastPngLostEstimate", knowledge.lastPngLostEstimate)
                .put("lastMissingPngCount", knowledge.lastMissingPngCount)
                .put("lastActiveArticleCount", knowledge.lastActiveArticleCount)
                .put("updatedAt", knowledge.updatedAt)
                .toString(2),
        )
    }

    fun readSummary(): ParayObserverSummary {
        if (home.observerSummaryFile.exists()) {
            val o = runCatching { JSONObject(home.observerSummaryFile.readText()) }
                .getOrElse { return fallbackSummary() }
            return ParayObserverSummary(
                lastObservationAt = o.optLong("lastObservationAt", 0L),
                lastKnowledgeRefresh = o.optLong("lastKnowledgeRefresh", 0L),
                catalogChangesDetected = o.optBoolean("catalogChangesDetected", false),
                lastChangeSummary = o.optString("lastChangeSummary"),
                newProducts = o.optInt("newProducts", 0),
                priceChanges = o.optInt("priceChanges", 0),
                renamedProducts = o.optInt("renamedProducts", 0),
                removedProducts = o.optInt("removedProducts", 0),
                lastImportId = o.optLong("lastImportId", 0L),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        return fallbackSummary()
    }

    fun writeSummary(summary: ParayObserverSummary) {
        home.observerSummaryFile.parentFile?.mkdirs()
        home.observerSummaryFile.writeText(
            JSONObject()
                .put("lastObservationAt", summary.lastObservationAt)
                .put("lastKnowledgeRefresh", summary.lastKnowledgeRefresh)
                .put("catalogChangesDetected", summary.catalogChangesDetected)
                .put("lastChangeSummary", summary.lastChangeSummary)
                .put("newProducts", summary.newProducts)
                .put("priceChanges", summary.priceChanges)
                .put("renamedProducts", summary.renamedProducts)
                .put("removedProducts", summary.removedProducts)
                .put("lastImportId", summary.lastImportId)
                .put("updatedAt", summary.updatedAt)
                .toString(2),
        )
    }

    private fun fallbackSummary(): ParayObserverSummary =
        ParayObserverSummary.from(readState(), readKnowledge())

    fun readHomeDisplayCache(): ParayHomeDisplayCache? {
        val file = home.homeDisplayCacheFile
        if (!file.exists()) return null
        val o = runCatching { JSONObject(file.readText()) }.getOrElse { return null }
        val neuralO = o.optJSONObject("neural") ?: return null
        return ParayHomeDisplayCache(
            manifest = ParayManifest(
                agentName = o.optString("agentName", ParayKnowledge.AGENT_NAME),
                version = o.optString("agentVersion", ParayKnowledge.VERSION),
                homePath = o.optString("homePath"),
                createdAt = o.optLong("manifestCreatedAt", 0L),
                motto = o.optString("motto", ParayKnowledge.motto),
            ),
            office = ParayOfficeLink(
                oasisApp = o.optString("oasisApp", ParayHome.OASIS_OFFICE_ID),
                lastVisitAt = o.optLong("officeLastVisitAt", 0L),
                lastWorkplace = o.optString("lastWorkplace", ""),
                status = o.optString("officeStatus", "linked"),
            ),
            neural = ParayNeuralSnapshot(
                modelId = neuralO.optString("modelId"),
                embeddingDim = neuralO.optInt("embeddingDim", 512),
                modelSource = neuralO.optString("modelSource"),
                modelGeneratedAt = neuralO.optString("modelGeneratedAt"),
                learnedNow = neuralO.optInt("learnedNow", 0),
                fingerprintsNow = neuralO.optInt("fingerprintsNow", 0),
                learnEvents = neuralO.optInt("learnEvents", 0),
                gpuAvailable = neuralO.optBoolean("gpuAvailable", false),
                glesVersion = neuralO.optString("glesVersion").takeIf { it.isNotBlank() },
                lowRamDevice = neuralO.optBoolean("lowRamDevice", false),
                matcherMode = neuralO.optString("matcherMode", "shape + color"),
                embeddingsReady = neuralO.optBoolean("embeddingsReady", false),
                cameraReady = neuralO.optBoolean("cameraReady", false),
            ),
            folders = emptyList(),
            barcodePatterns = o.optInt("barcodePatterns", 0),
            cachedAt = o.optLong("cachedAt", 0L),
        )
    }

    fun writeHomeDisplayCache(cache: ParayHomeDisplayCache) {
        val file = home.homeDisplayCacheFile
        file.parentFile?.mkdirs()
        val neural = cache.neural
        file.writeText(
            JSONObject()
                .put("agentName", cache.manifest.agentName)
                .put("agentVersion", cache.manifest.version)
                .put("homePath", cache.manifest.homePath)
                .put("manifestCreatedAt", cache.manifest.createdAt)
                .put("motto", cache.manifest.motto)
                .put("oasisApp", cache.office.oasisApp)
                .put("officeLastVisitAt", cache.office.lastVisitAt)
                .put("lastWorkplace", cache.office.lastWorkplace)
                .put("officeStatus", cache.office.status)
                .put("barcodePatterns", cache.barcodePatterns)
                .put("cachedAt", cache.cachedAt)
                .put(
                    "neural",
                    JSONObject()
                        .put("modelId", neural.modelId)
                        .put("embeddingDim", neural.embeddingDim)
                        .put("modelSource", neural.modelSource)
                        .put("modelGeneratedAt", neural.modelGeneratedAt)
                        .put("learnedNow", neural.learnedNow)
                        .put("fingerprintsNow", neural.fingerprintsNow)
                        .put("learnEvents", neural.learnEvents)
                        .put("gpuAvailable", neural.gpuAvailable)
                        .put("glesVersion", neural.glesVersion)
                        .put("lowRamDevice", neural.lowRamDevice)
                        .put("matcherMode", neural.matcherMode)
                        .put("embeddingsReady", neural.embeddingsReady)
                        .put("cameraReady", neural.cameraReady),
                )
                .toString(2),
        )
    }
}
