package com.example.smoothisland;

import android.view.View;
import android.graphics.Outline;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * MIUI 超级岛平滑圆角补丁
 * 基于对 libhwui.so 及 MIUI SystemUI (Host & Plugin) 的分析实现
 */
public class XposedInit implements IXposedHookLoadPackage {
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String TAG = "SmoothIsland: ";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        // 目标进程：com.android.systemui (超级岛在此进程运行)
        if (!lpparam.packageName.equals(SYSTEMUI_PKG)) return;

        XposedBridge.log(TAG + "Patching started.");

        // 1. Hook Outline.setRoundRect
        // 根据 libhwui.so 分析，uIsSmooth 逻辑由底层标志位控制。
        // 在 Java 层通过设置 mIsSmooth 字段实现 (此字段在 miuix.smooth.SmoothFrameLayout2 等类中被操作)
        XposedHelpers.findAndHookMethod(Outline.class, "setRoundRect", 
            int.class, int.class, int.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Outline outline = (Outline) param.thisObject;
                try {
                    // 该字段名在宿主包的 miuix.smooth 组件中被引用，用于告知渲染引擎应用 1.52866 系数的平滑算法
                    XposedHelpers.setBooleanField(outline, "mIsSmooth", true);
                } catch (Throwable ignored) {}
            }
        });

        // 2. Hook View.onAttachedToWindow
        // 确保超级岛相关的所有 View (包括宿主 fakeView 和插件 Content View) 都激活平滑属性
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                String name = view.getClass().getName();

                // 识别模式：DynamicIsland (插件类) 或 MiuiStatusIconContainer (宿主状态栏容器)
                if (name.contains("DynamicIsland") || name.contains("MiuiStatusIconContainer")) {
                    applySmooth(view);
                }
            }
        });
    }

    /**
     * 调用经反编译证实的 MIUI 官方隐藏方法
     * 来源：miuix.smooth.SmoothCornerHelper
     */
    private void applySmooth(View view) {
        try {
            XposedHelpers.callMethod(view, "setSmoothCornerEnabled", true);
            view.invalidate();
        } catch (Throwable ignored) {
            // 如果方法不存在，说明底层渲染引擎不支持该特定调用
        }
    }
}
