package com.oasismall.oasisai.domain.layoutagent

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import org.json.JSONObject
import java.io.File

/**
 * Probes device GPU while the app is open and logs layout events for future on-device learning.
 * Phase 2: feed logs into a TFLite / ONNX placement head with NNAPI GPU delegate.
 */
data class GpuProfile(
    val gpuAvailable: Boolean,
    val glesVersion: String?,
    val lowRamDevice: Boolean,
)

class GpuLearningProbe(context: Context) {
    private val logDir = File(context.filesDir, "layout_agent").also { it.mkdirs() }
    private val sessionLog = File(logDir, "gpu_learning.jsonl")
    private val profile: GpuProfile = probeGpu(context)

    fun profile(): GpuProfile = profile

    fun onDesignSessionStart(itemCount: Int) {
        appendEvent(
            "design_session_start",
            JSONObject()
                .put("itemCount", itemCount)
                .put("gpuAvailable", profile.gpuAvailable)
                .put("glesVersion", profile.glesVersion)
                .put("rules", AppLayoutKnowledge.productRules.size),
        )
    }

    fun onPlacement(
        imagePath: String,
        templateId: String,
        contentAspect: Float,
        scale: Float,
        slotW: Float,
        slotH: Float,
    ) {
        appendEvent(
            "placement",
            JSONObject()
                .put("image", File(imagePath).name)
                .put("templateId", templateId)
                .put("contentAspect", contentAspect.toDouble())
                .put("scale", scale.toDouble())
                .put("slotW", slotW.toDouble())
                .put("slotH", slotH.toDouble())
                .put("gpuAvailable", profile.gpuAvailable),
        )
    }

    private fun appendEvent(type: String, payload: JSONObject) {
        val line = JSONObject()
            .put("type", type)
            .put("ts", System.currentTimeMillis())
            .put("data", payload)
        sessionLog.appendText(line.toString() + "\n")
    }

    companion object {
        fun probeGpu(context: Context): GpuProfile {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val lowRam = am.isLowRamDevice
            var display: EGLDisplay? = null
            var surface: EGLSurface? = null
            var eglContext: EGLContext? = null
            return try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (display == EGL14.EGL_NO_DISPLAY) {
                    return GpuProfile(false, null, lowRam)
                }
                val version = IntArray(2)
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                    return GpuProfile(false, null, lowRam)
                }
                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                val attribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE,
                )
                EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
                if (num[0] == 0) return GpuProfile(false, null, lowRam)

                val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
                val surfAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfAttribs, 0)
                EGL14.eglMakeCurrent(display, surface, surface, eglContext)
                val gles = EGL14.eglQueryString(display, EGL14.EGL_VERSION)
                GpuProfile(true, gles, lowRam)
            } catch (_: Exception) {
                GpuProfile(false, null, lowRam)
            } finally {
                runCatching {
                    if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                        surface?.let { EGL14.eglDestroySurface(display, it) }
                        eglContext?.let { EGL14.eglDestroyContext(display, it) }
                        EGL14.eglTerminate(display)
                    }
                }
            }
        }
    }
}
