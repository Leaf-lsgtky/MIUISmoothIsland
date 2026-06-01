package com.example.smoothisland

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.toPath
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.WeakHashMap
import kotlin.math.abs

class XposedInit : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        loadedProcessName = param.processName
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        if (loadedProcessName == SYSTEMUI_PKG && param.packageName == SYSTEMUI_PKG) {
            installSystemUiHooks(param.defaultClassLoader)
        }
        if (param.packageName == MIUI_PLUGIN_PKG || loadedProcessName == SYSTEMUI_PKG) {
            installPluginHooks(param.defaultClassLoader)
        }
    }

    private fun overrideOutlineIfCapsule(outline: Outline) {
        val bounds = Rect()
        if (outline.getRect(bounds) && bounds.height() > 10) {
            val height = bounds.height()
            if (abs(outline.radius - (height / 2.0f)) <= 1.5f) {
                val path = createSmoothCapsulePath(
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom
                )
                outline.setPath(path)
            }
        }
    }

    private fun createSmoothCapsulePath(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Path {
        val width = right - left
        val height = bottom - top
        val path = getBaseCapsulePath(width, height)
        path.offset(left + (width / 2.0f), top + (height / 2.0f))
        return path
    }

    @Synchronized
    private fun getBaseCapsulePath(width: Int, height: Int): Path {
        val smoothing = readSmoothing()
        val key = CapsuleShape(width, height, smoothing)
        val cachedPath = capsulePathCache[key]
        if (cachedPath != null) {
            return Path(cachedPath)
        }

        val generatedPath = RoundedPolygon.pill(
            width = width.toFloat(),
            height = height.toFloat(),
            smoothing = smoothing
        ).toPath()

        if (capsulePathCache.size >= MAX_PATH_CACHE_SIZE) {
            val oldestKey = capsulePathCache.entries.iterator().next().key
            capsulePathCache.remove(oldestKey)
        }
        capsulePathCache[key] = generatedPath
        return Path(generatedPath)
    }

    private fun readSmoothing(): Float {
        val rawValue = getSystemProperty(SMOOTHING_PROP, DEFAULT_SMOOTHING.toString())
        val parsedValue = rawValue.toFloatOrNull() ?: DEFAULT_SMOOTHING
        return parsedValue.coerceIn(MIN_SMOOTHING, MAX_SMOOTHING)
    }

    private fun getSystemProperty(key: String, defaultValue: String): String {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod(
                "get",
                String::class.java,
                String::class.java
            )
            getMethod.invoke(null, key, defaultValue) as String
        } catch (_: Throwable) {
            defaultValue
        }
    }

    @Synchronized
    private fun installSystemUiHooks(classLoader: ClassLoader) {
        if (systemUiHooksInstalled) return

        hookOutlineRoundRectForDynamicIsland()
        hookTargetOutlineProviders(classLoader)
        systemUiHooksInstalled = true
    }

    @Synchronized
    private fun installPluginHooks(classLoader: ClassLoader) {
        if (pluginHooksInstalled) return

        try {
            val contentViewClass = Class.forName(PLUGIN_CONTENT_VIEW_CLASS, false, classLoader)
            val backgroundViewClass = Class.forName(PLUGIN_BACKGROUND_VIEW_CLASS, false, classLoader)

            hookPluginMedianLuma(contentViewClass, classLoader)
            hookPluginBackgroundGeometry(backgroundViewClass)
            pluginHooksInstalled = true
        } catch (_: Throwable) {
        }
    }

    private fun hookTargetOutlineProviders(classLoader: ClassLoader) {
        TARGET_PROVIDER_CLASSES.forEach { providerClassName ->
            try {
                val targetProvider = Class.forName(providerClassName, false, classLoader)
                val getOutlineMethod = targetProvider.getDeclaredMethod(
                    "getOutline",
                    View::class.java,
                    Outline::class.java
                )

                hook(getOutlineMethod)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .intercept { chain ->
                        val result = chain.proceed()
                        val outline = chain.getArg(1) as Outline
                        overrideOutlineIfCapsule(outline)
                        result
                    }
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookOutlineRoundRectForDynamicIsland() {
        val setRoundRectMethod = Outline::class.java.getDeclaredMethod(
            "setRoundRect",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Float::class.javaPrimitiveType!!
        )

        hook(setRoundRectMethod)
            .setPriority(XposedInterface.PRIORITY_DEFAULT)
            .intercept { chain ->
                val left = chain.getArg(0) as Int
                val top = chain.getArg(1) as Int
                val right = chain.getArg(2) as Int
                val bottom = chain.getArg(3) as Int
                val radius = chain.getArg(4) as Float
                val height = bottom - top

                if (
                    height > 10 &&
                    abs(radius - (height / 2.0f)) <= 1.0f &&
                    isDynamicIslandOutlineCall()
                ) {
                    val path = createSmoothCapsulePath(left, top, right, bottom)
                    val outline = chain.thisObject as Outline
                    outline.setPath(path)
                    null
                } else {
                    chain.proceed()
                }
            }
    }

    private fun isDynamicIslandOutlineCall(): Boolean {
        var hasDynamicIslandCaller = false

        Thread.currentThread().stackTrace.forEach { frame ->
            val className = frame.className
            val lowerClassName = className.lowercase()

            if (EXCLUDED_OUTLINE_CALLERS.any { lowerClassName.contains(it) }) {
                return false
            }
            if (DYNAMIC_ISLAND_CALLERS.any { lowerClassName.contains(it) }) {
                hasDynamicIslandCaller = true
            }
        }

        return hasDynamicIslandCaller
    }

    private fun hookPluginMedianLuma(contentViewClass: Class<*>, classLoader: ClassLoader) {
        val updateMedianLumaMethod = contentViewClass.getDeclaredMethod(
            "updateMedianLuma",
            Float::class.javaPrimitiveType!!
        )

        hook(updateMedianLumaMethod)
            .setPriority(XposedInterface.PRIORITY_DEFAULT)
            .intercept { chain ->
                val result = chain.proceed()
                val contentView = chain.thisObject ?: return@intercept result
                val medianLuma = chain.getArg(0) as Float
                scheduleSmoothPluginStroke(contentView, contentViewClass, classLoader, medianLuma)
                result
            }
    }

    private fun hookPluginBackgroundGeometry(backgroundViewClass: Class<*>) {
        GEOMETRY_SETTER_NAMES.forEach { methodName ->
            try {
                val method = backgroundViewClass.getDeclaredMethod(
                    methodName,
                    Int::class.javaPrimitiveType!!
                )
                hook(method)
                    .setPriority(XposedInterface.PRIORITY_DEFAULT)
                    .intercept { chain ->
                        val result = chain.proceed()
                        onPluginBackgroundGeometryChanged(chain.thisObject)
                        result
                    }
            } catch (_: Throwable) {
            }
        }
    }

    private fun scheduleSmoothPluginStroke(
        contentView: Any,
        contentViewClass: Class<*>,
        classLoader: ClassLoader,
        medianLuma: Float
    ) {
        try {
            val backgroundView = contentViewClass.getMethod("getBackgroundView").invoke(contentView)
                ?: return
            val drawable = backgroundView.javaClass.getMethod("getDrawable").invoke(backgroundView)
                as? GradientDrawable ?: return
            val isExpanded = contentViewClass.getMethod("isExpanded").invoke(contentView) as Boolean
            val strokeWidth = readPluginStrokeWidth(contentView, classLoader, isExpanded)
            if (strokeWidth <= 0) return

            val strokeColor = readPluginStrokeColor(
                contentView,
                contentViewClass,
                medianLuma,
                isExpanded
            )
            val state = pluginStrokeStates.getOrPut(backgroundView) {
                PluginStrokeState()
            }

            state.originalDrawable = drawable
            state.strokeWidth = strokeWidth
            state.strokeColor = strokeColor
            restorePluginStroke(backgroundView, state)
            scheduleStablePluginStroke(backgroundView, state)
        } catch (_: Throwable) {
        }
    }

    private fun readPluginStrokeWidth(
        contentView: Any,
        classLoader: ClassLoader,
        isExpanded: Boolean
    ): Int {
        val fieldName = if (isExpanded) "expanded_stroke" else "island_stroke"
        val dimenClass = Class.forName(PLUGIN_DIMEN_CLASS, false, classLoader)
        val resId = dimenClass.getField(fieldName).getInt(null)
        val context = contentView.javaClass.getMethod("getContext").invoke(contentView) as android.content.Context
        return context.resources.getDimensionPixelSize(resId)
    }

    private fun readPluginStrokeColor(
        contentView: Any,
        contentViewClass: Class<*>,
        medianLuma: Float,
        isExpanded: Boolean
    ): Int {
        val highlightColor = if (isExpanded) {
            null
        } else {
            readPluginHighlightColor(contentView, contentViewClass)
        }

        if (highlightColor == null) {
            val method = contentViewClass.getDeclaredMethod(
                "updateMedianLuma\$getStrokeColor",
                contentViewClass,
                Float::class.javaPrimitiveType!!
            )
            method.isAccessible = true
            return method.invoke(null, contentView, medianLuma) as Int
        }

        val color = Color.parseColor(highlightColor)
        val alphaRatio = (1.0f - medianLuma).coerceIn(0.0f, 1.0f)
        val alpha = (Color.alpha(color) * alphaRatio).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun readPluginHighlightColor(contentView: Any, contentViewClass: Class<*>): String? {
        val template = contentViewClass.getMethod("getTemplate").invoke(contentView) ?: return null
        return template.javaClass.getMethod("getHighlightColor").invoke(template) as? String
    }

    private fun onPluginBackgroundGeometryChanged(backgroundView: Any?) {
        if (backgroundView == null) return
        val state = pluginStrokeStates[backgroundView] ?: return
        state.token++
        restorePluginStroke(backgroundView, state)
        scheduleStablePluginStroke(backgroundView, state)
    }

    private fun scheduleStablePluginStroke(backgroundView: Any, state: PluginStrokeState) {
        val view = backgroundView as? View ?: return
        val token = ++state.token
        val bounds = readPluginBackgroundBounds(backgroundView) ?: return
        view.postDelayed({
            val latestState = pluginStrokeStates[backgroundView] ?: return@postDelayed
            if (latestState.token != token) return@postDelayed

            val latestBounds = readPluginBackgroundBounds(backgroundView) ?: return@postDelayed
            if (latestBounds != bounds) {
                scheduleStablePluginStroke(backgroundView, latestState)
                return@postDelayed
            }

            applySmoothPluginStroke(backgroundView, latestState)
        }, STABLE_STROKE_DELAY_MS)
    }

    private fun readPluginBackgroundBounds(backgroundView: Any): Rect? {
        return try {
            val viewClass = backgroundView.javaClass
            Rect(
                viewClass.getMethod("getActualLeft").invoke(backgroundView) as Int,
                viewClass.getMethod("getActualTop").invoke(backgroundView) as Int,
                viewClass.getMethod("getActualWidth").invoke(backgroundView) as Int,
                viewClass.getMethod("getActualHeight").invoke(backgroundView) as Int
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun applySmoothPluginStroke(backgroundView: Any, state: PluginStrokeState) {
        val original = state.originalDrawable ?: return
        try {
            original.setStroke(0, Color.TRANSPARENT)
            val smoothDrawable = SmoothStrokeDrawable(
                fillDrawable = original,
                strokeWidth = state.strokeWidth.toFloat(),
                strokeColor = state.strokeColor
            )
            backgroundView.javaClass.getMethod("setDrawable", Drawable::class.java)
                .invoke(backgroundView, smoothDrawable)
            state.smoothDrawable = smoothDrawable
            state.applied = true
            (backgroundView as? View)?.invalidate()
        } catch (_: Throwable) {
        }
    }

    private fun restorePluginStroke(backgroundView: Any?, state: PluginStrokeState) {
        if (!state.applied) return
        val original = state.originalDrawable ?: return

        try {
            original.setStroke(state.strokeWidth, state.strokeColor)
            backgroundView?.javaClass?.getMethod("setDrawable", Drawable::class.java)
                ?.invoke(backgroundView, original)
            state.applied = false
            state.smoothDrawable = null
            (backgroundView as? View)?.invalidate()
        } catch (_: Throwable) {
        }
    }

    private companion object {
        const val SYSTEMUI_PKG = "com.android.systemui"
        const val MIUI_PLUGIN_PKG = "miui.systemui.plugin"
        const val PLUGIN_CONTENT_VIEW_CLASS =
            "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
        const val PLUGIN_BACKGROUND_VIEW_CLASS =
            "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
        const val PLUGIN_DIMEN_CLASS = "miui.systemui.dynamicisland.R\$dimen"
        val TARGET_PROVIDER_CLASSES = listOf(
            "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController\$updateFakeViewOutline\$1",
            "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder\$Companion\$create\$1\$1"
        )
        val GEOMETRY_SETTER_NAMES = listOf(
            "setActualLeft",
            "setActualTop",
            "setActualWidth",
            "setActualHeight"
        )
        val DYNAMIC_ISLAND_CALLERS = listOf(
            "dynamicisland",
            "mediaisland"
        )
        val EXCLUDED_OUTLINE_CALLERS = listOf(
            "footerview",
            "footerviewbutton",
            "notificationstackscrolllayout",
            "notif_footer"
        )
        const val SMOOTHING_PROP = "persist.smoothisland.smoothing"
        const val DEFAULT_SMOOTHING = 0.8f
        const val MIN_SMOOTHING = 0.0f
        const val MAX_SMOOTHING = 1.0f
        const val MAX_PATH_CACHE_SIZE = 32
        const val STABLE_STROKE_DELAY_MS = 140L
        val capsulePathCache = LinkedHashMap<CapsuleShape, Path>(MAX_PATH_CACHE_SIZE, 0.75f, true)
        val pluginStrokeStates = WeakHashMap<Any, PluginStrokeState>()
        @Volatile
        var systemUiHooksInstalled = false
        @Volatile
        var pluginHooksInstalled = false
        @Volatile
        var loadedProcessName: String? = null
    }

    private data class CapsuleShape(val width: Int, val height: Int, val smoothing: Float)

    private data class PluginStrokeState(
        var originalDrawable: GradientDrawable? = null,
        var smoothDrawable: SmoothStrokeDrawable? = null,
        var strokeWidth: Int = 0,
        var strokeColor: Int = Color.TRANSPARENT,
        var token: Int = 0,
        var applied: Boolean = false
    )

    private inner class SmoothStrokeDrawable(
        private val fillDrawable: Drawable,
        private val strokeWidth: Float,
        private val strokeColor: Int
    ) : Drawable() {
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

        override fun draw(canvas: Canvas) {
            fillDrawable.setBounds(bounds)
            fillDrawable.draw(canvas)

            if (strokeWidth <= 0.0f || Color.alpha(strokeColor) == 0) return

            val halfStroke = strokeWidth / 2.0f
            val path = createSmoothCapsulePath(
                (bounds.left + halfStroke).toInt(),
                (bounds.top + halfStroke).toInt(),
                (bounds.right - halfStroke).toInt(),
                (bounds.bottom - halfStroke).toInt()
            )

            strokePaint.color = strokeColor
            strokePaint.strokeWidth = strokeWidth
            canvas.drawPath(path, strokePaint)
        }

        override fun setAlpha(alpha: Int) {
            fillDrawable.alpha = alpha
            strokePaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fillDrawable.colorFilter = colorFilter
            strokePaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
