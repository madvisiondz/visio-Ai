package com.oasismall.oasisai.domain.paray

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.domain.layoutagent.GpuProfile
import com.oasismall.oasisai.domain.layoutagent.LayoutFitAgent
import com.oasismall.oasisai.domain.layoutagent.ProductContentBounds
import com.oasismall.oasisai.domain.layoutagent.ProductLearnContext
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.domain.layoutagent.ProductObservation
import org.json.JSONObject
import java.io.File

/**
 * **PARAY** — Oasis visual agent.
 *
 * - Fits product cutouts on shelf tickets (via [layoutFit])
 * - Learns shape, colors, typography, label design per article
 * - Will identify products from camera frames offline ([cameraMatcher])
 */
class ParayAgent(
    private val context: Context,
    val home: ParayHome,
    private val recognitionTrackerProvider: () -> ParayRecognitionTracker? = { null },
) {
    val name: String = ParayKnowledge.AGENT_NAME

    private val visualIndex = ParayVisualIndex(home)
    private val fingerprintStore = ParayFingerprintStore(home)
    private val barcodeMemory = ParayBarcodeMemory(home)
    val learnStore = ParayLearnStore(home)
    val learnSettingsStore = ParayLearnSettingsStore(home)
    val brandKnowledgeStore = ParayBrandKnowledgeStore(home)
    private val brandKnowledgeProvider = ParayBrandKnowledgeProvider(brandKnowledgeStore, learnStore)
    private val packagingVariantLog = ParayPackagingVariantLog(home)
    private val observerStore = ParayObserverStore(home)
    private val knowledgeStore = ParayKnowledgeStore(home)
    private val workflowStore = ParayWorkflowStore(home)
    private val recognitionStore = ParayRecognitionStore(home)
    val sessionStore = ParaySessionStore(home)
    private val cameraMatcher = ParayCameraMatcher(
        visualIndex,
        fingerprintStore,
        learnStore,
        brandKnowledgeProvider,
        onMatchResults = { matches ->
            recognitionTrackerProvider()?.onCameraMatcherResults(matches)
        },
    )
    private val learnLog = home.learnEventsFile
    @Volatile private var cachedLearnEventCount: Int? = null

    private val layoutFit = LayoutFitAgent(
        context = context,
        onProductObserved = { observation -> learnFromObservation(observation) },
    )

    fun layoutFitAgent(): LayoutFitAgent = layoutFit

    fun gpuProfile(): GpuProfile = layoutFit.gpuProfile()

    fun learnedProductCount(): Int = visualIndex.count()

    fun parayLearnedCount(): Int = learnStore.learnedCount()

    fun learnQueue(repository: OasisRepository) = ParayLearnQueue(repository, learnStore)

    fun learnPreload(repository: OasisRepository) = ParayLearnPreload(repository, this)

    fun hasFingerprintForBarcode(barcode: String): Boolean =
        fingerprintStore.getEmbedding(barcode) != null

    fun extractPngFeatures(pngPath: String): VisualFeatureExtractor.Features? {
        val bitmap = android.graphics.BitmapFactory.decodeFile(pngPath) ?: return null
        val bounds = ProductContentBounds.detect(bitmap)
        val content = if (bounds.isEmpty) {
            com.oasismall.oasisai.domain.layoutagent.ContentBounds(0, 0, bitmap.width, bitmap.height)
        } else {
            bounds
        }
        val features = VisualFeatureExtractor.extract(bitmap, content)
        bitmap.recycle()
        return features
    }

    fun logPackagingVariant(event: ParayPackagingVariantEvent) {
        packagingVariantLog.append(event)
        recognitionTrackerProvider()?.recordPackagingDrift(event)
        learnStore.get(event.articleId)?.let { existing ->
            learnStore.put(
                existing.copy(
                    packagingVariantDetected = true,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun loadLearnSettings(): ParayLearnSettings = learnSettingsStore.get()

    fun learnEngine(settings: ParayLearnSettings = learnSettingsStore.current()) =
        ParayLearnEngine(settings)

    /** Persist completed learn record and merge into visual index for AGENT recognition. */
    fun saveLearnRecord(record: ParayLearnRecord) {
        val enriched = record.copy(
            updatedAt = System.currentTimeMillis(),
            brandSignature = record.brand?.let { record.productSignature },
            familySignature = record.family?.let { record.productSignature },
        )
        learnStore.put(enriched)
        brandKnowledgeStore.upsertFromRecord(enriched)
        if (enriched.status != ParayLearnStatus.LEARNED) return
        val sideCaptures = listOfNotNull(
            enriched.leftCapture,
            enriched.rightCapture,
            enriched.backCapture,
        )
        val best = sideCaptures.maxByOrNull { it.confidence }
            ?: enriched.productSignature?.let {
                ParayViewCapture(
                    it.shapeAspect,
                    it.fillRatio,
                    it.dominantColors,
                    1f,
                )
            } ?: return
        val (wordCount, charCount) = VisualFeatureExtractor.typographyOf(enriched.designation)
        visualIndex.learn(
            ProductVisualSignature(
                articleId = enriched.articleId,
                barcode = enriched.barcode,
                designation = enriched.designation,
                shapeAspect = best.shapeAspect,
                fillRatio = best.fillRatio,
                dominantColors = best.dominantColors,
                designationWordCount = wordCount,
                designationCharCount = charCount,
                templateId = VisualFeatureExtractor.templateId(),
                labelPalette = VisualFeatureExtractor.shelfLabelPalette(),
                observationCount = enriched.learnedSideCount.coerceAtLeast(3),
                lastLearnedAt = enriched.learnedAt ?: System.currentTimeMillis(),
                imageFileName = File(enriched.pngFrontPath).name,
            ),
        )
        appendLearnEvent(
            ProductVisualSignature(
                articleId = enriched.articleId,
                barcode = enriched.barcode,
                designation = enriched.designation,
                shapeAspect = best.shapeAspect,
                fillRatio = best.fillRatio,
                dominantColors = best.dominantColors,
                designationWordCount = wordCount,
                designationCharCount = charCount,
                templateId = "paray_learn_v1",
                labelPalette = VisualFeatureExtractor.shelfLabelPalette(),
                observationCount = enriched.learnedSideCount,
                lastLearnedAt = enriched.learnedAt ?: System.currentTimeMillis(),
                imageFileName = File(enriched.pngFrontPath).name,
            ),
        )
    }

    fun fingerprintCount(): Int = fingerprintStore.count()

    fun fingerprintMeta(): FingerprintMeta? = fingerprintStore.getMeta()

    fun learnEventCount(): Int {
        cachedLearnEventCount?.let { return it }
        return runCatching { learnLog.readLines().count { it.isNotBlank() } }
            .getOrDefault(0)
            .also { cachedLearnEventCount = it }
    }

    fun readHomeDisplayCache(): ParayHomeDisplayCache? = observerStore.readHomeDisplayCache()

    fun readKnowledgeSummary(): ParayKnowledgeSummary = knowledgeStore.readSummary()

    fun readWorkflowSummary(): ParayWorkflowSummary = workflowStore.readSummary()

    fun readRecognitionSummary(): ParayRecognitionSummary = recognitionStore.readSummary()

    fun writeHomeDisplayCache(cache: ParayHomeDisplayCache) {
        observerStore.writeHomeDisplayCache(cache)
    }

    fun buildHomeDisplay(): ParayHomeDisplayCache = ParayHomeDisplayCache(
        manifest = homeManifest(),
        office = officeLink(),
        neural = buildNeuralSnapshot(),
        folders = homeFolders(),
        barcodePatterns = learnedBarcodePatterns(),
        cachedAt = System.currentTimeMillis(),
    )

    fun mission(): String = ParayKnowledge.mission

    fun buildNeuralSnapshot(
        learnedBefore: Int = learnedProductCount(),
        fingerprintsBefore: Int = fingerprintCount(),
    ): ParayNeuralSnapshot {
        val gpu = layoutFit.gpuProfile()
        val meta = fingerprintMeta()
        val learnedNow = learnedProductCount()
        val fingerprintsNow = fingerprintCount()
        val hasEmbeddings = fingerprintsNow > 0
        return ParayNeuralSnapshot(
            modelId = meta?.model.orEmpty(),
            embeddingDim = meta?.dim ?: 512,
            modelSource = meta?.source.orEmpty(),
            modelGeneratedAt = meta?.generatedAt.orEmpty(),
            learnedBefore = learnedBefore,
            learnedNow = learnedNow,
            fingerprintsBefore = fingerprintsBefore,
            fingerprintsNow = fingerprintsNow,
            learnEvents = learnEventCount(),
            gpuAvailable = gpu.gpuAvailable,
            glesVersion = gpu.glesVersion,
            lowRamDevice = gpu.lowRamDevice,
            matcherMode = if (hasEmbeddings) "shape + color + embeddings" else "shape + color",
            embeddingsReady = hasEmbeddings,
            cameraReady = hasEmbeddings || learnedProductCount() > 0,
        )
    }

    /** Reinforce visual memory after user confirms a teach / asset capture. */
    fun reinforceFromBitmap(bitmap: Bitmap, articleId: Long, barcode: String, designation: String, imagePath: String) {
        val bounds = ProductContentBounds.detect(bitmap)
        val content = if (bounds.isEmpty) {
            com.oasismall.oasisai.domain.layoutagent.ContentBounds(0, 0, bitmap.width, bitmap.height)
        } else {
            bounds
        }
        val features = VisualFeatureExtractor.extract(bitmap, content)
        val (wordCount, charCount) = VisualFeatureExtractor.typographyOf(designation)
        val signature = ProductVisualSignature(
            articleId = articleId,
            barcode = barcode,
            designation = designation,
            shapeAspect = features.shapeAspect,
            fillRatio = features.fillRatio,
            dominantColors = features.dominantColors,
            designationWordCount = wordCount,
            designationCharCount = charCount,
            templateId = VisualFeatureExtractor.templateId(),
            labelPalette = VisualFeatureExtractor.shelfLabelPalette(),
            observationCount = 1,
            lastLearnedAt = System.currentTimeMillis(),
            imageFileName = File(imagePath).name,
        )
        visualIndex.learn(signature)
        appendLearnEvent(signature)
    }

    fun fingerprintImporter(repository: OasisRepository) =
        ParayFingerprintImporter(repository, visualIndex, fingerprintStore)

    fun barcodeAdvisor(repository: OasisRepository) =
        ParayBarcodeAdvisor(repository, barcodeMemory)

    fun learnedBarcodePatterns(): Int = barcodeMemory.count()

    fun activateDesignSession(queueSize: Int) = layoutFit.activateDesignSession(queueSize)

    fun deactivateDesignSession() = layoutFit.deactivateDesignSession()

    fun drawProductInShelfSlot(
        canvas: Canvas,
        bitmap: Bitmap,
        item: PreselectionWithArticle,
        slotRect: RectF,
    ) {
        val ctx = ProductLearnContext(
            articleId = item.articleId,
            barcode = item.barcode,
            designation = item.designation,
            imagePath = item.imagePath.orEmpty(),
        )
        layoutFit.drawProductInShelfSlot(canvas, bitmap, slotRect, ctx)
    }

    /** Future: Scan & shoot / camera — identify without barcode. */
    fun identifyFromCamera(bitmap: Bitmap, topK: Int = 5): List<ParayMatch> =
        cameraMatcher.identify(bitmap, topK)

    fun getSignature(articleId: Long): ProductVisualSignature? =
        visualIndex.get(articleId)

    fun goToOffice(workplace: String) {
        home.touchOfficeLink(workplace)
    }

    fun homeManifest(): ParayManifest = home.readManifest()

    fun officeLink(): ParayOfficeLink = home.readOfficeLink()

    fun homeFolders(): List<ParayFolderEntry> = home.folderSummary()

    private fun learnFromObservation(observation: ProductObservation) {
        val bounds = observation.contentBounds
        val features = VisualFeatureExtractor.extract(observation.bitmap, bounds)
        val (wordCount, charCount) = VisualFeatureExtractor.typographyOf(observation.designation)

        val signature = ProductVisualSignature(
            articleId = observation.articleId,
            barcode = observation.barcode,
            designation = observation.designation,
            shapeAspect = features.shapeAspect,
            fillRatio = features.fillRatio,
            dominantColors = features.dominantColors,
            designationWordCount = wordCount,
            designationCharCount = charCount,
            templateId = VisualFeatureExtractor.templateId(),
            labelPalette = VisualFeatureExtractor.shelfLabelPalette(),
            observationCount = 1,
            lastLearnedAt = System.currentTimeMillis(),
            imageFileName = File(observation.imagePath).name,
        )

        visualIndex.learn(signature)
        appendLearnEvent(signature)
    }

    private fun appendLearnEvent(sig: ProductVisualSignature) {
        val line = JSONObject()
            .put("agent", name)
            .put("type", "learn")
            .put("ts", System.currentTimeMillis())
            .put(
                "data",
                JSONObject()
                    .put("articleId", sig.articleId)
                    .put("barcode", sig.barcode)
                    .put("shapeAspect", sig.shapeAspect.toDouble())
                    .put("fillRatio", sig.fillRatio.toDouble())
                    .put("colors", sig.dominantColors.size)
                    .put("words", sig.designationWordCount)
                    .put("templateId", sig.templateId),
            )
        learnLog.appendText(line.toString() + "\n")
        cachedLearnEventCount = (cachedLearnEventCount ?: learnEventCount()) + 1
    }
}
