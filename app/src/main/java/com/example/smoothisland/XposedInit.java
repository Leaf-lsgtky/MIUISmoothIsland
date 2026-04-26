package com.example.smoothisland;

import android.graphics.Outline;
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

        XposedBridge.log(TAG + "Applying Geometric Bypass Patch...");

        // 核心 Hook：绕过 libhwui.so 的比例校验
        XposedHelpers.findAndHookMethod(Outline.class, "setRoundRect", 
            int.class, int.class, int.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int left = (int) param.args[0];
                int top = (int) param.args[1];
                int right = (int) param.args[2];
                int bottom = (int) param.args[3];
                float radius = (float) param.args[4];

                int width = right - left;
                int height = bottom - top;

                if (width <= 0 || height <= 0) return;

                // 算法逻辑：根据 SO 分析，若 2*R >= Width 或 2*R >= Height，会回退到普通圆角
                // 我们强制让半径减小 1 像素，确保满足 2*R < Width 和 2*R < Height
                float maxAllowedRadius = Math.min(width, height) / 2.0f - 1.0f;
                
                if (radius > maxAllowedRadius) {
                    param.args[4] = maxAllowedRadius;
                    // XposedBridge.log(TAG + "Radius adjusted to bypass check: " + maxAllowedRadius);
                }
            }
        });

        // 激活 View 的平滑标志位 (开启底层 Shader)
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                String className = view.getClass().getName();

                if (className.contains("DynamicIsland") || className.contains("MiuiStatusIconContainer")) {
                    try {
                        XposedHelpers.callMethod(view, "setSmoothCornerEnabled", true);
                        view.setClipToOutline(true);
                        view.invalidate();
                    } catch (Throwable ignored) {}
                }
            }
        });
    }
}
