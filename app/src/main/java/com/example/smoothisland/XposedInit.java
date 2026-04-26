package com.example.smoothisland;

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

        XposedBridge.log(TAG + "Module loaded, applying fixes...");

        // 1. Hook View 挂载过程
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

        // 2. Hook 宿主包核心动画更新逻辑
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
                            // 同步更新窗口级圆角参数
                            applyMiWindowCorner(fakeView, radius);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}
    }

    private void applyMiWindowSmooth(View view) {
        try {
            // 反射调用 View 的隐藏方法
            XposedHelpers.callMethod(view, "setSmoothCornerEnabled", true);
            float radius = view.getHeight() / 2.0f;
            if (radius > 0) {
                applyMiWindowCorner(view, radius);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 核心：通过全反射调用 RenderNode 的隐藏方法，避免编译失败
     */
    private void applyMiWindowCorner(View view, float radius) {
        try {
            // 1. 通过反射调用隐藏的 updateDisplayListIfDirty 获取 RenderNode 对象
            Object renderNode = XposedHelpers.callMethod(view, "updateDisplayListIfDirty");
            
            if (renderNode != null) {
                // 2. 准备 Radii 数组 (8个参数分别对应 4个角的 x,y 半径)
                float[] radii = new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
                
                // 3. 反射调用 RenderNode.setMiWindowCornerRadii (我们在 libhwui.so 中发现的函数)
                // 注意：在调用带数组参数的方法时，需强转为 Object 以防被识别为可变参数
                XposedHelpers.callMethod(renderNode, "setMiWindowCornerRadii", (Object) radii);
            }
            view.invalidate();
        } catch (Throwable t) {
            // 如果 RenderNode 方案失败，尝试直接在 View 上寻找该方法（某些 MIUI 版本的变体）
            try {
                float[] radii = new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
                XposedHelpers.callMethod(view, "setMiWindowCornerRadii", (Object) radii);
            } catch (Throwable ignored) {}
        }
    }
}
