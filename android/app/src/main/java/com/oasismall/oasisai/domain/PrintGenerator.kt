package com.oasismall.oasisai.domain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle
import com.oasismall.oasisai.data.db.entity.PrintBatchEntity
import com.oasismall.oasisai.data.db.entity.PrintBatchItemEntity
import com.oasismall.oasisai.data.db.entity.PrintTemplateEntity
import com.oasismall.oasisai.data.model.PrintBatchStatus
import com.oasismall.oasisai.data.model.TemplateType
import com.oasismall.oasisai.data.repository.OasisRepository
import com.oasismall.oasisai.util.PriceFormatter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrintGenerator(
    private val context: Context,
    private val repository: OasisRepository,
) {
    private val exportsDir: File
        get() = File(context.filesDir, "exports").also { it.mkdirs() }

    suspend fun generateFromPreselection(
        template: PrintTemplateEntity,
        items: List<PreselectionWithArticle>,
        isPromo: Boolean = false,
        promoStart: Long? = null,
        promoEnd: Long? = null,
        campaignName: String? = null,
    ): PrintGenerationResult {
        if (items.isEmpty()) {
            return PrintGenerationResult(false, errorMessage = "Pre-selection is empty")
        }

        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val baseName = "${template.type.lowercase()}_$timestamp"
            val pdfFile = File(exportsDir, "$baseName.pdf")

            when (TemplateType.valueOf(template.type)) {
                TemplateType.SHELF -> renderShelfA4(pdfFile, template, items, isPromo, campaignName)
                TemplateType.FREEZER -> renderSingleCards(pdfFile, template, items, isPromo, campaignName, large = true)
                TemplateType.PODIUM -> renderSingleCards(pdfFile, template, items, isPromo, campaignName, large = true)
                TemplateType.BOARD -> renderSingleCards(pdfFile, template, items, isPromo, campaignName, large = false)
            }

            val batchId = repository.createPrintBatch(
                batch = PrintBatchEntity(
                    templateId = template.id,
                    templateName = template.name,
                    exportPath = pdfFile.absolutePath,
                    previewPath = pdfFile.absolutePath,
                    isPromo = isPromo,
                    promoStart = promoStart,
                    promoEnd = promoEnd,
                    campaignName = campaignName,
                    status = PrintBatchStatus.GENERATED.name,
                    itemCount = items.size,
                ),
                items = items.mapIndexed { index, item ->
                    PrintBatchItemEntity(
                        batchId = 0,
                        articleId = item.articleId,
                        designationSnapshot = item.designation,
                        priceSnapshot = item.price,
                        barcodeSnapshot = item.barcode,
                        imageSnapshotPath = item.imagePath,
                        sortOrder = index,
                    )
                },
            )

            PrintGenerationResult(
                success = true,
                batchId = batchId,
                exportPath = pdfFile.absolutePath,
                previewPath = pdfFile.absolutePath,
            )
        } catch (e: Exception) {
            PrintGenerationResult(false, errorMessage = e.message ?: "Print generation failed")
        }
    }

    private fun renderShelfA4(
        file: File,
        template: PrintTemplateEntity,
        items: List<PreselectionWithArticle>,
        isPromo: Boolean,
        campaignName: String?,
    ) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val capacity = template.capacity.coerceAtLeast(1)
        val chunks = items.chunked(capacity)

        chunks.forEachIndexed { pageIndex, chunk ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val cols = 2
            val rows = 5
            val cellW = pageWidth / cols
            val cellH = pageHeight / rows
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f
                color = Color.DKGRAY
            }
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 12f
                color = Color.BLACK
                isFakeBoldText = true
            }
            val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 18f
                color = if (isPromo) Color.RED else Color.BLACK
                isFakeBoldText = true
            }

            canvas.drawText("Oasis AI — ${template.name}", 24f, 24f, titlePaint)
            if (isPromo && campaignName != null) {
                canvas.drawText("PROMO: $campaignName", 24f, 40f, titlePaint)
            }

            chunk.forEachIndexed { index, item ->
                val col = index % cols
                val row = index / cols
                val left = col * cellW + 16f
                val top = row * cellH + 60f
                drawLabel(canvas, left, top, cellW - 32f, item, namePaint, pricePaint)
            }

            document.finishPage(page)
        }

        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
    }

    private fun renderSingleCards(
        file: File,
        template: PrintTemplateEntity,
        items: List<PreselectionWithArticle>,
        isPromo: Boolean,
        campaignName: String?,
        large: Boolean,
    ) {
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        items.forEachIndexed { index, item ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = if (large) 28f else 20f
                color = Color.BLACK
                isFakeBoldText = true
            }
            val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = if (large) 48f else 36f
                color = if (isPromo) Color.RED else Color.BLACK
                isFakeBoldText = true
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 12f
                color = Color.DKGRAY
            }

            canvas.drawText(template.name, 40f, 60f, metaPaint)
            if (isPromo && campaignName != null) {
                canvas.drawText("PROMO — $campaignName", 40f, 90f, metaPaint)
            }
            canvas.drawText(item.designation, 40f, 180f, namePaint)
            canvas.drawText(PriceFormatter.format(item.price), 40f, 260f, pricePaint)
            canvas.drawText(item.barcode, 40f, 320f, metaPaint)

            document.finishPage(page)
        }

        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
    }

    private fun drawLabel(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        item: PreselectionWithArticle,
        namePaint: Paint,
        pricePaint: Paint,
    ) {
        canvas.drawLine(x, y, x + width, y, Paint().apply { color = Color.LTGRAY })
        val lines = wrapText(item.designation, namePaint, width)
        lines.take(3).forEachIndexed { i, line ->
            canvas.drawText(line, x, y + 20f + i * 16f, namePaint)
        }
        canvas.drawText(PriceFormatter.format(item.price), x, y + 80f, pricePaint)
        canvas.drawText(item.barcode, x, y + 100f, namePaint.apply { textSize = 9f; isFakeBoldText = false })
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            val test = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(test) <= maxWidth) {
                current = StringBuilder(test)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
