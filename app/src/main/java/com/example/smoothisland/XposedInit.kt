package com.example.smoothisland

import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlin.math.abs

class XposedInit : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != SYSTEMUI_PKG) return

        XposedBridge.log(TAG + "Verified Targeted Strategy active.")

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
                        val path = createIosFullSmoothPath(left, top, right, bottom, height / 2.0f)
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
                    val path = createIosFullSmoothPath(
                        bounds.left,
                        bounds.top,
                        bounds.right,
                        bounds.bottom,
                        height / 2.0f
                    )
                    outline.setPath(path)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun createIosFullSmoothPath(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        radius: Float
    ): Path {
        val path = Path()
        val l = 1.528665f * radius
        val c1 = 0.169060f * radius
        val c2 = 0.372824f * radius
        val c3 = 0.074911f * radius
        val c4 = 0.631494f * radius
        val c5 = 0.868407f * radius
        val c6 = 1.088493f * radius

        path.reset()
        path.moveTo(right - l, top.toFloat())
        path.cubicTo(right - c6, top.toFloat(), right - c5, top.toFloat(), right - c4, top + c3)
        path.cubicTo(right - c2, top + c1, right - c1, top + c2, right - c3, top + c4)
        path.cubicTo(right.toFloat(), top + c5, right.toFloat(), top + c6, right.toFloat(), top + radius)
        path.lineTo(right.toFloat(), bottom - radius)
        path.cubicTo(right.toFloat(), bottom - c6, right.toFloat(), bottom - c5, right - c3, bottom - c4)
        path.cubicTo(right - c1, bottom - c2, right - c2, bottom - c1, right - c4, bottom - c3)
        path.cubicTo(right - c5, bottom.toFloat(), right - c6, bottom.toFloat(), right - l, bottom.toFloat())
        path.lineTo(left + l, bottom.toFloat())
        path.cubicTo(left + c6, bottom.toFloat(), left + c5, bottom.toFloat(), left + c4, bottom - c3)
        path.cubicTo(left + c2, bottom - c1, left + c1, bottom - c2, left + c3, bottom - c4)
        path.cubicTo(left.toFloat(), bottom - c5, left.toFloat(), bottom - c6, left.toFloat(), bottom - radius)
        path.lineTo(left.toFloat(), top + radius)
        path.cubicTo(left.toFloat(), top + c6, left.toFloat(), top + c5, left + c3, top + c4)
        path.cubicTo(left + c1, top + c2, left + c2, top + c1, left + c4, top + c3)
        path.cubicTo(left + c5, top.toFloat(), left + c6, top.toFloat(), left + l, top.toFloat())
        path.close()
        return path
    }

    private companion object {
        const val TAG = "SmoothIsland: "
        const val SYSTEMUI_PKG = "com.android.systemui"
    }
}
