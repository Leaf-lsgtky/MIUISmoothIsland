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
    
    // 使用 ThreadLocal 捕获当前 View 上下文
    private static final ThreadLocal<View> CURRENT_VIEW = new ThreadLocal<>();

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(SYSTEMUI_PKG)) return;

        XposedBridge.log(TAG + "Manual G2 Bezier Strategy active.");

        // 1. 拦截 View.updateOutline 以获取当前 View 的实例
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

        // 2. 核心 Hook：接管 Outline.setRoundRect
        XposedHelpers.findAndHookMethod(Outline.class, "setRoundRect", 
            int.class, int.class, int.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                View view = CURRENT_VIEW.get();
                if (view == null) return;

                String className = view.getClass().getName();
                
                // 恢复为关键词匹配，确保能抓到所有相关的 Island 视图
                if (className.contains("Island")) {
                    int left = (int) param.args[0];
                    int top = (int) param.args[1];
                    int right = (int) param.args[2];
                    int bottom = (int) param.args[3];
                    float radius = (float) param.args[4];
                    
                    int height = bottom - top;
                    if (height <= 0) return;

                    // 半径判定：如果半径大于高度的 1/3，我们就认为它需要被“胶囊化”和平滑化
                    if (radius >= height * 0.33f) {
                        float r = height / 2.0f;
                        // 完全依赖手动计算的 iOS G2 路径
                        Path path = createIosFullSmoothPath(left, top, right, bottom, r);
                        
                        Outline outline = (Outline) param.thisObject;
                        outline.setPath(path);
                        
                        // 阻止原有的 setRoundRect 执行，强制使用我们的 Path
                        param.setResult(null);
                    }
                    // 注意：这里去掉了对 mIsSmooth 的尝试，也去掉了对小半径元素的干预
                }
            }
        });

        // 3. 确保 View 开启了裁剪，但不调用系统的平滑开关
        XposedHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                if (view.getClass().getName().contains("Island")) {
                    view.setClipToOutline(true);
                    // 不再调用 setSmoothCornerEnabled(true)，由我们自己画
                }
            }
        });
    }

    /**
     * iOS 风格三段式 G2 连续性圆角路径
     * L = 1.528665 * r 是 iOS 满圆平滑的关键起始点
     */
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
        
        // 右上角
        path.moveTo(right - L, top);
        path.cubicTo(right - c6, top, right - c5, top, right - c4, top + c3);
        path.cubicTo(right - c2, top + c1, right - c1, top + c2, right - c3, top + c4);
        path.cubicTo(right, top + c5, right, top + c6, right, top + r);
        
        // 右下角
        path.lineTo(right, bottom - r);
        path.cubicTo(right, bottom - c6, right, bottom - c5, right - c3, bottom - c4);
        path.cubicTo(right - c1, bottom - c2, right - c2, bottom - c1, right - c4, bottom - c3);
        path.cubicTo(right - c5, bottom, right - c6, bottom, right - L, bottom);
        
        // 左下角
        path.lineTo(left + L, bottom);
        path.cubicTo(left + c6, bottom, left + c5, bottom, left + c4, bottom - c3);
        path.cubicTo(left + c2, bottom - c1, left + c1, bottom - c2, left + c3, bottom - c4);
        path.cubicTo(left, bottom - c5, left, bottom - c6, left, bottom - r);
        
        // 左上角
        path.lineTo(left, top + r);
        path.cubicTo(left, top + c6, left, top + c5, left + c3, top + c4);
        path.cubicTo(left + c1, top + c2, left + c2, top + c1, left + c4, top + c3);
        path.cubicTo(left + c5, top, left + c6, top, left + L, top);

        path.close();
        return path;
    }
}
