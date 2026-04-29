package com.example.smoothisland

import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.toPath
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlin.math.abs

class XposedInit : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != SYSTEMUI_PKG) return

        XposedBridge.log(TAG + "AndroidX Shapes capsule strategy active.")

        try {
            val targetProvider = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController\$updateFakeViewOutline\$1",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(
                targetProvider,
                "getOutline",
                View::class.java,
                Outline::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val outline = param.args[1] as Outline
                        overrideOutlineIfCapsule(outline)
                    }
                }
            )
            XposedBridge.log(TAG + "Successfully hooked targeted Island OutlineProvider.")
        } catch (t: Throwable) {
            XposedBridge.log(TAG + "Targeted Provider Class not found, using geometric filtering only.")
        }

        XposedHelpers.findAndHookMethod(
            Outline::class.java,
            "setRoundRect",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Float::class.javaPrimitiveType!!,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val left = param.args[0] as Int
                    val top = param.args[1] as Int
                    val right = param.args[2] as Int
                    val bottom = param.args[3] as Int
                    val radius = param.args[4] as Float
                    val height = bottom - top

                    if (height > 10 && abs(radius - (height / 2.0f)) <= 1.0f) {
                        val path = createSmoothCapsulePath(left, top, right, bottom)
                        val outline = param.thisObject as Outline
                        outline.setPath(path)
                        param.result = null
                    }
                }
            }
        )
    }

    private fun overrideOutlineIfCapsule(outline: Outline) {
        try {
            val radius = XposedHelpers.callMethod(outline, "getRadius") as Float
            val bounds = XposedHelpers.callMethod(outline, "getBounds") as Rect?

            if (bounds != null && bounds.height() > 10) {
                val height = bounds.height()
                if (abs(radius - (height / 2.0f)) <= 1.5f) {
                    val path = createSmoothCapsulePath(
                        bounds.left,
                        bounds.top,
                        bounds.right,
                        bounds.bottom
                    )
                    outline.setPath(path)
                }
            }
        } catch (_: Throwable) {
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

    private companion object {
        const val TAG = "SmoothIsland: "
        const val SYSTEMUI_PKG = "com.android.systemui"
        const val PILL_SMOOTHING = 1.0f
        const val MAX_PATH_CACHE_SIZE = 32
        val capsulePathCache = LinkedHashMap<CapsuleSize, Path>(MAX_PATH_CACHE_SIZE, 0.75f, true)
    }

    private data class CapsuleSize(val width: Int, val height: Int)
}
