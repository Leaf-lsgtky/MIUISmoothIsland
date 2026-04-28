package com.example.smoothisland

import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        XposedBridge.log("SmoothIsland: Kotlin-Native AndroidX Strategy active.")

        try {
            val targetClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController\$updateFakeViewOutline\$1",
                lpparam.classLoader
            )

            XposedHelpers.findAndHookMethod(targetClass, "getOutline", View::class.java, Outline::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val outline = param.args[1] as Outline
                    applySmoothOutline(outline)
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("SmoothIsland: Target Provider not found, skip.")
        }
    }

    private fun applySmoothOutline(outline: Outline) {
        try {
            val radius = XposedHelpers.callMethod(outline, "getRadius") as Float
            val bounds = XposedHelpers.callMethod(outline, "getBounds") as Rect
            
            if (bounds.height() > 10 && Math.abs(radius - (bounds.height() / 2.0f)) <= 1.5f) {
                val r = bounds.height() / 2.0f
                val path = generateSmoothPath(bounds.width().toFloat(), bounds.height().toFloat(), r)
                
                val matrix = Matrix()
                matrix.setTranslate(bounds.left.toFloat() + bounds.width() / 2f, bounds.top.toFloat() + bounds.height() / 2f)
                path.transform(matrix)
                
                outline.setPath(path)
            }
        } catch (ignored: Throwable) {}
    }

    private fun generateSmoothPath(width: Float, height: Float, r: Float): Path {
        val rounding = CornerRounding(r, 1.0f)
        
        // 使用 RoundedPolygon 的顶点构造器，这是所有版本都稳定的 Public API
        val polygon = RoundedPolygon(
            vertices = floatArrayOf(
                -width / 2f, -height / 2f,
                width / 2f, -height / 2f,
                width / 2f, height / 2f,
                -width / 2f, height / 2f
            ),
            rounding = rounding
        )
        
        return polygon.toPath()
    }
}
