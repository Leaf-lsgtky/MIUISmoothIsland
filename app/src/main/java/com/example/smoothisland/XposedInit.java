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

        XposedBridge.log(TAG + "Manual Smooth Path Strategy active.");

        // 1. 接管 Outline 裁剪逻辑
        // 拦截 setRoundRect 并将其替换为我们手动计算的平滑 Path
        XposedHelpers.findAndHookMethod(Outline.class, "setRoundRect", 
            int.class, int.class, int.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Outline outline = (Outline) param.thisObject;
                int left = (int) param.args[0];
                int top = (int) param.args[1];
                int right = (int) param.args[2];
                int bottom = (int) param.args[3];
                float radius = (float) param.args[4];

                // 如果半径极小（接近直角），则不干预
                if (radius < 5) return;

                // 核心：生成手动计算的平滑圆角路径 (Squircle)
                Path squirclePath = createSmoothRectPath(left, top, right, bottom, radius);
                
                // 将 Outline 设置为 Path 模式
                // 这样系统会按照我们定义的贝塞尔曲线进行裁剪
                outline.setPath(squirclePath);
                
                // 阻止原有的 setRoundRect 执行，防止它覆盖我们的 Path
                param.setResult(null);
            }
        });

        // 2. 确保相关 View 开启了硬件加速裁剪
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                String name = view.getClass().getName();
                if (name.contains("Island") || name.contains("MiuiStatusIcon")) {
                    view.setClipToOutline(true);
                    // 禁用系统可能的原生平滑开关，避免冲突
                    try { 
                        XposedHelpers.callMethod(view, "setSmoothCornerEnabled", false); 
                    } catch (Throwable ignored) {}
                }
            }
        });
    }

    /**
     * 手动生成平滑圆角矩形路径 (Squircle 近似算法)
     * 使用三阶贝塞尔曲线，曲率比例 c 设为 0.72f 左右，能获得类似 HyperOS 的视觉饱满感
     */
    private Path createSmoothRectPath(int left, int top, int right, int bottom, float radius) {
        Path path = new Path();
        float w = right - left;
        float h = bottom - top;
        
        // 限制半径不超过最小边的一半
        float r = Math.min(radius, Math.min(w, h) / 2f);

        // 平滑系数：
        // 0.55228f 是标准圆弧
        // 0.72f 左右能达到明显的超椭圆平滑感
        float c = r * 0.72f; 

        path.reset();
        // 从左上角圆角终点顺时针绘制
        path.moveTo(left + r, top);
        
        // 顶部边
        path.lineTo(right - r, top);
        
        // 右上角平滑曲线
        path.cubicTo(right - r + c, top, right, top + r - c, right, top + r);
        
        // 右侧边
        path.lineTo(right, bottom - r);
        
        // 右下角平滑曲线
        path.cubicTo(right, bottom - r + c, right - r + c, bottom, right - r, bottom);
        
        // 底部边
        path.lineTo(left + r, bottom);
        
        // 左下角平滑曲线
        path.cubicTo(left + r - c, bottom, left, bottom - r + c, left, bottom - r);
        
        // 左侧边
        path.lineTo(left, top + r);
        
        // 左上角平滑曲线
        path.cubicTo(left, top + r - c, left + r - c, top, left + r, top);
        
        path.close();
        return path;
    }
}
