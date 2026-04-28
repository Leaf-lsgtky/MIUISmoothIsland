package com.example.smoothisland;

import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class XposedInit implements IXposedHookLoadPackage {
    private static final String TAG = "SmoothIsland: ";
    private static final String SYSTEMUI_PKG = "com.android.systemui";
    
    // 精确的目标类名集合，确保零误伤
    private static final Set<String> TARGET_CLASSES = new HashSet<>(Arrays.asList(
        "miui.systemui.dynamicisland.window.view.DynamicIslandWindowView",
        "miui.systemui.dynamicisland.window.view.DynamicIslandBackgroundView",
        "miui.systemui.dynamicisland.window.view.DynamicIslandBaseContentView",
        "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController$DynamicWindowAnimator"
    ));

    private static final ThreadLocal<View> CURRENT_VIEW = new ThreadLocal<>();

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(SYSTEMUI_PKG)) return;

        XposedBridge.log(TAG + "Absolute Precision G2 Strategy active (Restored).");

        // 1. 拦截 View.updateOutline，捕获当前 View
        XposedHelpers.findAndHookMethod(View.class, "updateOutline", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                CURRENT_VIEW.set((View) param.thisObject);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                CURRENT_VIEW.set(null);
            }
        });

        // 2. 核心 Hook：仅对精确匹配的类应用 G2 路径
        XposedHelpers.findAndHookMethod(Outline.class, "setRoundRect", 
            int.class, int.class, int.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = CURRENT_VIEW.get();
                if (view == null) return;

                String className = view.getClass().getName();
                
                // 只有类名完全匹配目标列表，才进行处理
                if (TARGET_CLASSES.contains(className)) {
                    int left = (int) param.args[0];
                    int top = (int) param.args[1];
                    int right = (int) param.args[2];
                    int bottom = (int) param.args[3];
                    float radius = (float) param.args[4];
                    
                    int height = bottom - top;
                    if (height <= 0) return;

                    // 即使类名匹配，也通过半径判断是否为“胶囊态”背景
                    if (radius >= height * 0.4f) {
                        float r = height / 2.0f;
                        Path path = createIosFullSmoothPath(left, top, right, bottom, r);
                        Outline outline = (Outline) param.thisObject;
                        outline.setPath(path);
                        param.setResult(null);
                    } else {
                        // 对于非胶囊态的圆角，尝试激活系统原生平滑
                        try {
                            XposedHelpers.setBooleanField(param.thisObject, "mIsSmooth", true);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        });

        // 3. 开启硬件加速裁剪
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                if (TARGET_CLASSES.contains(view.getClass().getName())) {
                    view.setClipToOutline(true);
                    try { 
                        XposedHelpers.callMethod(view, "setSmoothCornerEnabled", true); 
                    } catch (Throwable ignored) {}
                }
            }
        });
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
