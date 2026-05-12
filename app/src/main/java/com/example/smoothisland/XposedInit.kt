package com.example.smoothisland

import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.toPath
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import kotlin.math.abs

class XposedInit : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        loadedProcessName = param.processName
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return
        val processName = loadedProcessName ?: return
        if (!processName.startsWith(SYSTEMUI_PKG)) return
        if (param.packageName != SYSTEMUI_PKG && param.packageName != PLUGIN_PKG) return

        log(LOG_INFO, "SmoothIsland", "Loading hooks for ${param.packageName} in $processName")
        installHooks(param.defaultClassLoader)
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
    private fun installHooks(classLoader: ClassLoader) {
        if (hooksInstalled) return
        log(LOG_INFO, "SmoothIsland", "Installing hooks...")

        hookOutlineRoundRect()
        hookTargetOutlineProvider(classLoader)
        hookSmoothPathProvider(classLoader)
        hooksInstalled = true
    }

    private fun hookSmoothPathProvider(classLoader: ClassLoader) {
        try {
            val smoothPathProvider = Class.forName("miuix.smooth.SmoothPathProvider2", false, classLoader)
            val smoothDataClass = Class.forName("miuix.smooth.SmoothPathProvider2\$SmoothData", false, classLoader)
            val getSmoothPathMethod = smoothPathProvider.getDeclaredMethod(
                "getSmoothPath",
                Path::class.java,
                smoothDataClass
            )

            hook(getSmoothPathMethod)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept { chain ->
                    val result = chain.proceed() as Path
                    val smoothData = chain.getArg(1)
                    
                    val widthField = smoothData.javaClass.getField("width")
                    val heightField = smoothData.javaClass.getField("height")
                    
                    val width = widthField.getFloat(smoothData)
                    val height = heightField.getFloat(smoothData)

                    if (height > 10f && abs(width / height) > 1.5f) {
                        result.reset()
                        result.addRect(0f, 0f, width, height, Path.Direction.CW)
                        log(LOG_INFO, "SmoothIsland", "Forced rectangle path for ${width}x${height} in ${loadedProcessName}")
                    }
                    result
                }
            log(LOG_INFO, "SmoothIsland", "Successfully hooked SmoothPathProvider2")
        } catch (e: Throwable) {
            log(LOG_ERROR, "SmoothIsland", "Failed to hook SmoothPathProvider2: ${e.message}")
        }
    }

    private fun hookTargetOutlineProvider(classLoader: ClassLoader) {
        try {
            val targetProvider = Class.forName(TARGET_PROVIDER_CLASS, false, classLoader)
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
            log(LOG_INFO, "SmoothIsland", "Successfully hooked TargetOutlineProvider")
        } catch (e: Throwable) {
            log(LOG_ERROR, "SmoothIsland", "Failed to hook TargetOutlineProvider: ${e.message}")
        }
    }

    private fun hookOutlineRoundRect() {
        try {
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

                    if (height > 10 && abs(radius - (height / 2.0f)) <= 1.0f) {
                        val path = createSmoothCapsulePath(left, top, right, bottom)
                        val outline = chain.thisObject as Outline
                        outline.setPath(path)
                        null
                    } else {
                        chain.proceed()
                    }
                }
            log(LOG_INFO, "SmoothIsland", "Successfully hooked Outline.setRoundRect")
        } catch (e: Throwable) {
            log(LOG_ERROR, "SmoothIsland", "Failed to hook Outline.setRoundRect: ${e.message}")
        }
    }

    private companion object {
        const val SYSTEMUI_PKG = "com.android.systemui"
        const val PLUGIN_PKG = "miui.systemui.plugin"
        const val TARGET_PROVIDER_CLASS =
            "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController\$updateFakeViewOutline\$1"
        const val SMOOTHING_PROP = "persist.smoothisland.smoothing"
        const val DEFAULT_SMOOTHING = 0.8f
        const val MIN_SMOOTHING = 0.0f
        const val MAX_SMOOTHING = 1.0f
        const val MAX_PATH_CACHE_SIZE = 32
        
        const val LOG_INFO = 2
        const val LOG_ERROR = 4
        
        val capsulePathCache = LinkedHashMap<CapsuleShape, Path>(MAX_PATH_CACHE_SIZE, 0.75f, true)
        @Volatile
        var hooksInstalled = false
        @Volatile
        var loadedProcessName: String? = null
    }

    private data class CapsuleShape(val width: Int, val height: Int, val smoothing: Float)
}
