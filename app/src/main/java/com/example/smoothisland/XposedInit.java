package com.example.smoothisland;

import android.view.View;
import android.graphics.RenderNode;
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

        XposedBridge.log(TAG + "Injecting MiWindowCorner Strategy...");

        // 1. Hook View 附加过程
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                String name = view.getClass().getName();

                if (name.contains("Island") || name.contains("MiuiStatusIcon")) {
                    applyMiWindowSmooth(view);
                }
            }
        });

        // 2. Hook 核心动画更新，确保每次半径变化都同步给 MiWindowCorner
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController",
                lpparam.classLoader,
                "updateFakeViewOutline",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object controller = param.thisObject;
                        View fakeView = (View) XposedHelpers.getObjectField(controller, "fakeView");
                        Object animator = XposedHelpers.getObjectField(controller, "dynamicWindowAnimator");
                        float radius = XposedHelpers.getFloatField(animator, "animRadius");

                        if (fakeView != null) {
                            applyMiWindowCorner(fakeView, radius);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}
    }

    private void applyMiWindowSmooth(View view) {
        try {
            XposedHelpers.callMethod(view, "setSmoothCornerEnabled", true);
            // 尝试获取当前半径并应用
            float radius = view.getHeight() / 2.0f;
            if (radius > 0) applyMiWindowCorner(view, radius);
        } catch (Throwable ignored) {}
    }

    /**
     * 核心：直接调用 SO 中发现的 RenderNode.setMiWindowCornerRadii
     */
    private void applyMiWindowCorner(View view, float radius) {
        try {
            RenderNode node = view.updateDisplayListIfDirty();
            float[] radii = new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
            
            // 直接反射调用 RenderNode 的隐藏方法 (对应 SO 里的 JNI 导出)
            XposedHelpers.callMethod(node, "setMiWindowCornerRadii", radii);
            
            // XposedBridge.log(TAG + "Applied MiWindowCornerRadii: " + radius);
            view.invalidate();
        } catch (Throwable t) {
            // 如果方法不在 RenderNode 上，尝试在 View 上查找
            try {
                XposedHelpers.callMethod(view, "setMiWindowCornerRadii", new float[]{radius, radius, radius, radius, radius, radius, radius, radius});
            } catch (Throwable ignored) {}
        }
    }
}
