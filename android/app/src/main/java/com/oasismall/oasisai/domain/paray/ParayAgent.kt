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
) {
    val name: String = ParayKnowledge.AGENT_NAME

    private val visualIndex = ParayVisualIndex(home)
    private val fingerprintStore = ParayFingerprintStore(home)
    private val barcodeMemory = ParayBarcodeMemory(home)
    val sessionStore = ParaySessionStore(home)
    private val cameraMatcher = ParayCameraMatcher(visualIndex, fingerprintStore)
    private val learnLog = home.learnEventsFile

    private val layoutFit = LayoutFitAgent(
        context = context,
        onProductObserved = { observation -> learnFromObservation(observation) },
    )

    fun layoutFitAgent(): LayoutFitAgent = layoutFit

    fun gpuProfile(): GpuProfile = layoutFit.gpuProfile()

    fun learnedProductCount(): Int = visualIndex.count()

    fun fingerprintCount(): Int = fingerprintStore.count()

    fun fingerprintMeta(): FingerprintMeta? = fingerprintStore.getMeta()

    fun learnEventCount(): Int =
        runCatching { learnLog.readLines().count { it.isNotBlank() } }.getOrDefault(0)

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
    }
}
