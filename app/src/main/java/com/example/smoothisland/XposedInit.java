package com.example.smoothisland;

import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;
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

        XposedBridge.log(TAG + "Official AndroidX Shapes Strategy active.");

        try {
            // 依据源码确定的超级岛 OutlineProvider 类名
            Class<?> targetProvider = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController$updateFakeViewOutline$1", 
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(targetProvider, "getOutline", View.class, Outline.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Outline outline = (Outline) param.args[1];
                    overrideWithAndroidXShapes(outline);
                }
            });
        } catch (Throwable t) {
            applyGeometricFallback();
        }
    }

    private void applyGeometricFallback() {
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

                // 识别胶囊形特征
                if (h > 10 && Math.abs(radius - (h / 2.0f)) <= 1.0f) {
                    Path path = generateSmoothPath(l, t, r, b, h / 2.0f);
                    ((Outline) param.thisObject).setPath(path);
                    param.setResult(null);
                }
            }
        });
    }

    private void overrideWithAndroidXShapes(Outline outline) {
        try {
            float radius = (float) XposedHelpers.callMethod(outline, "getRadius");
            Rect bounds = (Rect) XposedHelpers.callMethod(outline, "getBounds");
            if (bounds != null && bounds.height() > 10) {
                int h = bounds.height();
                if (Math.abs(radius - (h / 2.0f)) <= 1.5f) {
                    Path path = generateSmoothPath(bounds.left, bounds.top, bounds.right, bounds.bottom, h / 2.0f);
                    outline.setPath(path);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 调用 androidx.graphics.shapes 官方库生成完美的平滑圆角路径
     */
    private Path generateSmoothPath(int left, int top, int right, int bottom, float r) {
        float width = (float) (right - left);
        float height = (float) (bottom - top);
        
        // 调用 Kotlin 辅助工具类，避免 Java 直接调用 Kotlin 的扩展函数和默认参数
        Path path = PathHelper.createSmoothPath(width, height, r);
        
        // 平移 Path 到指定位置
        Matrix matrix = new Matrix();
        matrix.setTranslate(left + width / 2f, top + height / 2f);
        path.transform(matrix);
        
        return path;
    }
}
