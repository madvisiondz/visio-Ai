package com.oasismall.oasisai.domain.layoutagent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min

/**
 * On-device layout fitter — used by **Design** shelf ticket placement.
 */
class LayoutFitAgent(
    context: Context,
    private val onProductObserved: ((ProductObservation) -> Unit)? = null,
) {
    private val memory = LayoutFitMemory(context)
    private val gpuProbe = GpuLearningProbe(context)
    private var designSessionActive = false

    fun gpuProfile(): GpuProfile = gpuProbe.profile()

    fun appRules(): List<String> = AppLayoutKnowledge.productRules

    fun activateDesignSession(queueSize: Int) {
        designSessionActive = true
        gpuProbe.onDesignSessionStart(queueSize)
    }

    fun deactivateDesignSession() {
        designSessionActive = false
    }

    fun drawProductInShelfSlot(
        canvas: Canvas,
        bitmap: Bitmap,
        slotRect: RectF,
        learn: ProductLearnContext?,
    ) {
        val bounds = ProductContentBounds.detect(bitmap)
        val content = if (bounds.isEmpty) {
            ContentBounds(0, 0, bitmap.width, bitmap.height)
        } else {
            bounds
        }

        val contentW = content.width.toFloat()
        val contentH = content.height.toFloat()
        val contentAspect = if (contentH > 0f) contentW / contentH else 1f

        val usableW = slotRect.width()
        val usableH = slotRect.height()
        var scale = min(usableW / contentW, usableH / contentH)

        val imagePath = learn?.imagePath.orEmpty()
        if (imagePath.isNotBlank()) {
            val hint = memory.loadHint(imagePath, AppLayoutKnowledge.SHELF_TEMPLATE_ID)
            scale = memory.adjustScale(hint, contentAspect, scale)
        }

        val dw = contentW * scale
        val dh = contentH * scale
        val dx = slotRect.left + (usableW - dw) / 2f
        val dy = slotRect.top + (usableH - dh) / 2f

        val src = Rect(content.left, content.top, content.right, content.bottom)
        val dst = RectF(dx, dy, dx + dw, dy + dh)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        canvas.save()
        canvas.clipRect(slotRect)
        canvas.drawBitmap(bitmap, src, dst, paint)
        canvas.restore()

        if (imagePath.isNotBlank()) {
            memory.saveHint(imagePath, AppLayoutKnowledge.SHELF_TEMPLATE_ID, contentAspect, scale)
        }
        if (designSessionActive && imagePath.isNotBlank()) {
            gpuProbe.onPlacement(
                imagePath = imagePath,
                templateId = AppLayoutKnowledge.SHELF_TEMPLATE_ID,
                contentAspect = contentAspect,
                scale = scale,
                slotW = usableW,
                slotH = usableH,
            )
        }

        if (learn != null) {
            onProductObserved?.invoke(
                ProductObservation(
                    bitmap = bitmap,
                    contentBounds = content,
                    articleId = learn.articleId,
                    barcode = learn.barcode,
                    designation = learn.designation,
                    imagePath = learn.imagePath,
                ),
            )
        }
    }
}
