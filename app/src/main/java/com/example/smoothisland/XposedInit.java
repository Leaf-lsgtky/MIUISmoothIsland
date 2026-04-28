package com.example.smoothisland;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "SmoothIsland: ";
    private static final String SYSTEMUI_PKG = "com.android.systemui";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(SYSTEMUI_PKG)) return;

        XposedBridge.log(TAG + "Verified Targeted Strategy active.");

        // 1. Hook the specific ViewOutlineProvider identified in source code
        // Class: com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController$updateFakeViewOutline$1
        try {
            Class<?> targetProvider = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController$updateFakeViewOutline$1", 
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(targetProvider, "getOutline", View.class, Outline.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Outline outline = (Outline) param.args[1];
                    overrideOutlineIfCapsule(outline);
                }
            });
            XposedBridge.log(TAG + "Successfully hooked targeted Island OutlineProvider.");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Targeted Provider Class not found, using geometric filtering only.");
        }

        // 2. Global geometric fallback to catch other island-like semi-circle elements
        // This ensures that even if class names vary across versions, the "half-circle" logic is caught.
        XposedHelpers.findAndHookMethod(Outline.class, "setRoundRect", 
            int.class, int.class, int.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int l = (int) param.args[0];
                int t = (int) param.args[1];
                int r = (int) param.args[2];
                int b = (int) param.args[3];
                float radius = (float) param.args[4];
                int h = b - t;

                // Precision check for capsule shape (radius == height / 2)
                if (h > 10 && Math.abs(radius - (h / 2.0f)) <= 1.0f) {
                    Path path = createIosFullSmoothPath(l, t, r, b, h / 2.0f);
                    Outline outline = (Outline) param.thisObject;
                    outline.setPath(path);
                    param.setResult(null);
                }
            }
        });
    }

    private void overrideOutlineIfCapsule(Outline outline) {
        try {
            float radius = (float) XposedHelpers.callMethod(outline, "getRadius");
            Rect bounds = (Rect) XposedHelpers.callMethod(outline, "getBounds");
            if (bounds != null && bounds.height() > 10) {
                int h = bounds.height();
                if (Math.abs(radius - (h / 2.0f)) <= 1.5f) {
                    Path path = createIosFullSmoothPath(bounds.left, bounds.top, bounds.right, bounds.bottom, h / 2.0f);
                    outline.setPath(path);
                }
            }
        } catch (Throwable ignored) {}
    }

    private Path createIosFullSmoothPath(int left, int top, int right, int bottom, float r) {
        Path path = new Path();
        final float L = 1.528665f * r;
        final float c1 = 0.169060f * r;
        final float c2 = 0.372824f * r;
        final float c3 = 0.074911f * r;
        final float c4 = 0.631494f * r;
        final float c5 = 0.868407f * r;
        final float c6 = 1.088493f * r;

        path.reset();
        path.moveTo(right - L, top);
        path.cubicTo(right - c6, top, right - c5, top, right - c4, top + c3);
        path.cubicTo(right - c2, top + c1, right - c1, top + c2, right - c3, top + c4);
        path.cubicTo(right, top + c5, right, top + c6, right, top + r);
        path.lineTo(right, bottom - r);
        path.cubicTo(right, bottom - c6, right, bottom - c5, right - c3, bottom - c4);
        path.cubicTo(right - c1, bottom - c2, right - c2, bottom - c1, right - c4, bottom - c3);
        path.cubicTo(right - c5, bottom, right - c6, bottom, right - L, bottom);
        path.lineTo(left + L, bottom);
        path.cubicTo(left + c6, bottom, left + c5, bottom, left + c4, bottom - c3);
        path.cubicTo(left + c2, bottom - c1, left + c1, bottom - c2, left + c3, bottom - c4);
        path.cubicTo(left, bottom - c5, left, bottom - c6, left, bottom - r);
        path.lineTo(left, top + r);
        path.cubicTo(left, top + c6, left, top + c5, left + c3, top + c4);
        path.cubicTo(left + c1, top + c2, left + c2, top + c1, left + c4, top + c3);
        path.cubicTo(left + c5, top, left + c6, top, left + L, top);
        path.close();
        return path;
    }
}
