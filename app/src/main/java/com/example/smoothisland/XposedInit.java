package com.example.smoothisland;

import android.graphics.Outline;
import android.graphics.Path;
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

        XposedBridge.log(TAG + "G2 Continuous Path Strategy active.");

        // 1. 接管 Outline 裁剪逻辑
        XposedHelpers.findAndHookMethod(Outline.class, "setRoundRect", 
            int.class, int.class, int.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                int left = (int) param.args[0];
                int top = (int) param.args[1];
                int right = (int) param.args[2];
                int bottom = (int) param.args[3];
                
                int height = bottom - top;
                if (height <= 0) return;

                // 强制实现 iOS 满圆胶囊感：半径为高度的一半
                float r = height / 2.0f;

                // 使用三段式贝塞尔曲线实现 G2 连续性（iOS 风格）
                Path path = createIosFullSmoothPath(left, top, right, bottom, r);
                
                Outline outline = (Outline) param.thisObject;
                outline.setPath(path);
                param.setResult(null);
            }
        });

        // 2. 激活 View 层的平滑支持
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                String name = view.getClass().getName();
                if (name.contains("Island") || name.contains("MiuiStatusIcon")) {
                    view.setClipToOutline(true);
                    try { 
                        XposedHelpers.callMethod(view, "setSmoothCornerEnabled", true); 
                    } catch (Throwable ignored) {}
                }
            }
        });
    }

    /**
     * 实现真正的 iOS 风格 G2 连续性圆角 (三段式贝塞尔逼近)
     * 这种算法让圆角与直线的连接处曲率变化为 0，视觉效果极其平滑且饱满
     */
    private Path createIosFullSmoothPath(int left, int top, int right, int bottom, float r) {
        Path path = new Path();
        float w = right - left;
        float h = bottom - top;
        
        // iOS 7+ 连续圆角核心常数
        // 这里的比例确保了从直线到曲线的平滑过渡
        final float L = 1.528665f * r;
        final float c1 = 0.169060f * r;
        final float c2 = 0.372824f * r;
        final float c3 = 0.074911f * r;
        final float c4 = 0.631494f * r;
        final float c5 = 0.868407f * r;
        final float c6 = 1.088493f * r;

        path.reset();
        
        // 1. 右上角
        path.moveTo(right - L, top);
        path.cubicTo(right - c6, top, right - c5, top, right - c4, top + c3);
        path.cubicTo(right - c2, top + c1, right - c1, top + c2, right - c3, top + c4);
        path.cubicTo(right, top + c5, right, top + c6, right, top + r);
        
        // 2. 右下角
        path.lineTo(right, bottom - r);
        path.cubicTo(right, bottom - c6, right, bottom - c5, right - c3, bottom - c4);
        path.cubicTo(right - c1, bottom - c2, right - c2, bottom - c1, right - c4, bottom - c3);
        path.cubicTo(right - c5, bottom, right - c6, bottom, right - L, bottom);
        
        // 3. 左下角
        path.lineTo(left + L, bottom);
        path.cubicTo(left + c6, bottom, left + c5, bottom, left + c4, bottom - c3);
        path.cubicTo(left + c2, bottom - c1, left + c1, bottom - c2, left + c3, bottom - c4);
        path.cubicTo(left, bottom - c5, left, bottom - c6, left, bottom - r);
        
        // 4. 左上角
        path.lineTo(left, top + r);
        path.cubicTo(left, top + c6, left, top + c5, left + c3, top + c4);
        path.cubicTo(left + c1, top + c2, left + c2, top + c1, left + c4, top + c3);
        path.cubicTo(left + c5, top, left + c6, top, left + L, top);

        path.close();
        return path;
    }
}
