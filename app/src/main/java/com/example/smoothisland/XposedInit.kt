package com.example.smoothisland

import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedInit : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "SmoothIsland: "
        private const val SYSTEMUI_PKG = "com.android.systemui"
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != SYSTEMUI_PKG) return

        XposedBridge.log(TAG + "Kotlin-Native AndroidX Shapes Strategy active.")

        try {
            val targetProvider = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController\$updateFakeViewOutline\$1",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(targetProvider, "getOutline", View::class.java, Outline::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val outline = param.args[1] as Outline
                    overrideWithAndroidXShapes(outline)
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log(TAG + "Target Provider Class not found: " + t.message)
        }
    }

    private fun overrideWithAndroidXShapes(outline: Outline) {
        try {
            val radius = XposedHelpers.callMethod(outline, "getRadius") as Float
            val bounds = XposedHelpers.callMethod(outline, "getBounds") as Rect
            
            if (bounds.height() > 10 && Math.abs(radius - (bounds.height() / 2.0f)) <= 1.5f) {
                val r = bounds.height() / 2.0f
                val path = generateSmoothPath(bounds.width().toFloat(), bounds.height().toFloat(), r)
                
                // 平移 Path 到原点
                val matrix = android.graphics.Matrix()
                matrix.setTranslate(bounds.left.toFloat() + bounds.width() / 2f, bounds.top.toFloat() + bounds.height() / 2f)
                path.transform(matrix)
                
                outline.setPath(path)
            }
        } catch (ignored: Throwable) {}
    }

    private fun generateSmoothPath(width: Float, height: Float, r: Float): Path {
        val rounding = CornerRounding(r, 1.0f)
        val polygon = RoundedPolygon.rectangle(width, height, rounding, rounding, rounding, rounding)
        return polygon.toPath()
    }
}
