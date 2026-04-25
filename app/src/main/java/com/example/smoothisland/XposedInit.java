package com.example.smoothisland;

import android.view.View;
import android.graphics.Outline;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedInit implements IXposedHookLoadPackage {
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    private static final String TAG = "SmoothIsland: ";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(SYSTEMUI_PKG)) return;

        XposedBridge.log(TAG + "Module loaded into " + lpparam.packageName);

        // 1. Hook Outline.setRoundRect (带详细 Log)
        XposedHelpers.findAndHookMethod(Outline.class, "setRoundRect", 
            int.class, int.class, int.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Outline outline = (Outline) param.thisObject;
                try {
                    XposedHelpers.setBooleanField(outline, "mIsSmooth", true);
                    // 只有在特定调试时开启，否则 Log 太杂
                    // XposedBridge.log(TAG + "Outline.mIsSmooth set to true");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "Failed to set mIsSmooth: " + t.getMessage());
                }
            }
        });

        // 2. Hook View.onAttachedToWindow (带类名匹配 Log)
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                String name = view.getClass().getName();

                // 记录所有疑似超级岛的 View 挂载过程
                if (name.contains("Island") || name.contains("MiuiStatusIcon")) {
                    XposedBridge.log(TAG + "Matched view attached: " + name);
                    applySmooth(view);
                }
            }
        });
    }

    private void applySmooth(View view) {
        try {
            XposedHelpers.callMethod(view, "setSmoothCornerEnabled", true);
            XposedBridge.log(TAG + "Successfully called setSmoothCornerEnabled(true) for " + view.getClass().getSimpleName());
            view.invalidate();
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Method setSmoothCornerEnabled not found for " + view.getClass().getSimpleName());
        }
    }
}
