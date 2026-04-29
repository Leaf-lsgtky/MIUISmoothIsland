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
        if (loadedProcessName != SYSTEMUI_PKG) return
        if (param.packageName != SYSTEMUI_PKG) return

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
        val key = CapsuleSize(width, height)
        val cachedPath = capsulePathCache[key]
        if (cachedPath != null) {
            return Path(cachedPath)
        }

        val generatedPath = RoundedPolygon.pill(
            width = width.toFloat(),
            height = height.toFloat(),
            smoothing = PILL_SMOOTHING
        ).toPath()

        if (capsulePathCache.size >= MAX_PATH_CACHE_SIZE) {
            val oldestKey = capsulePathCache.entries.iterator().next().key
            capsulePathCache.remove(oldestKey)
        }
        capsulePathCache[key] = generatedPath
        return Path(generatedPath)
    }

    @Synchronized
    private fun installHooks(classLoader: ClassLoader) {
        if (hooksInstalled) return

        hookOutlineRoundRect()
        hookTargetOutlineProvider(classLoader)
        hooksInstalled = true
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
        } catch (_: Throwable) {
        }
    }

    private fun hookOutlineRoundRect() {
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
    }

    private companion object {
        const val SYSTEMUI_PKG = "com.android.systemui"
        const val TARGET_PROVIDER_CLASS =
            "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController\$updateFakeViewOutline\$1"
        const val PILL_SMOOTHING = 0.8f
        const val MAX_PATH_CACHE_SIZE = 32
        val capsulePathCache = LinkedHashMap<CapsuleSize, Path>(MAX_PATH_CACHE_SIZE, 0.75f, true)
        @Volatile
        var hooksInstalled = false
        @Volatile
        var loadedProcessName: String? = null
    }

    private data class CapsuleSize(val width: Int, val height: Int)
}
