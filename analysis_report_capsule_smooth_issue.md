# MIUI 超级岛平滑圆角失效技术分析报告 (胶囊态)

## 1. 问题现象
在 MIUI/HyperOS 的超级岛（Dynamic Island）中，当处于“正常态”（即窄小的胶囊形状）时，即便开启了平滑圆角开关，其边框在视觉上仍表现为两个标准的半圆拼接，而非平滑的超椭圆（Squircle）。只有在展开态（Expanded State）下，平滑圆角算法才表现正常。

## 2. 核心原因分析
通过对 `miui.systemui.plugin` 插件包中 `miuix.smooth.SmoothPathProvider2` 类源码的深度分析，确定该问题源于算法内部的 **“坍塌检测”（Collapse Detection）** 逻辑。

### 2.1 关键逻辑：坍塌判断公式
在生成平滑路径前，系统会调用以下方法判断形状是否过窄：
```java
private static boolean isHeightCollapsed(float height, float radius, float unused, double smooth, float ksi) {
    // 公式判断高度是否足以支撑贝塞尔曲线的平滑过渡
    return ((double) height) <= ((double) (radius * 2.0f)) * ((smooth * ((double) ksi)) + 1.0d);
}
```
*   **height**: 岛屿高度
*   **radius**: 圆角半径（胶囊态下 radius = height / 2）
*   **smooth**: 平滑系数（默认 0.8）
*   **ksi**: 修正系数（默认 0.46）

### 2.2 现象触发路径
1.  在**胶囊态**下，`height` 正好等于 `radius * 2`。
2.  代入公式：`height <= height * (0.8 * 0.46 + 1.0)` -> `height <= height * 1.368`。
3.  该条件**永远成立**，因此系统判定高度已“坍塌”。
4.  判定坍塌后，`smoothForHeight` 会被强制计算为 **0**：
    ```java
    return isHeightCollapsed(...) ? Math.max(Math.min(((height / (radius * 2.0f)) - 1.0f) / ksi, 1.0f), 0.0f) : smooth;
    ```
    计算结果为 `(1.0 - 1.0) / 0.46 = 0`。

### 2.3 绘制行为回退
由于平滑系数被降至 0，`SmoothPathProvider2` 会在绘制时执行以下逻辑：
*   **跳过贝塞尔曲线**：`if (smoothForHorizontal != 0.0d)` 条件不成立，不绘制三阶贝塞尔路径。
*   **执行原生绘制**：回退到 `canvas.drawArc` 或 `path.arcTo`。
*   **最终结果**：图形退化为标准的圆角矩形（直线 + 半圆弧）。

## 3. 修复/优化建议 (Xposed Hook 方案)
要解决胶囊态下的视觉不一致，建议在 `MIUISmoothIsland` 模块中针对 `miui.systemui.plugin` 进行以下 Hook：

1.  **强行修正平滑系数**：
    Hook `miuix.smooth.SmoothPathProvider2.smoothForHeight` 和 `smoothForWidth` 方法。当检测到输入半径等于高度一半时，不返回 0，而是返回一个微小的保底平滑值（如 0.1 - 0.3），或者直接返回原始的 `smooth` 参数。

2.  **绕过坍塌检测**：
    Hook `isHeightCollapsed` 和 `isWidthCollapsed` 始终返回 `false`。
    *注意：此方案需要验证在极窄情况下贝塞尔曲线是否会产生绘制畸变。*

3.  **动态调整半径**：
    稍微减小绘制时的 `radius`（使其略小于 `height / 2`），从而人为避开坍塌公式的临界点。

## 4. 结论
胶囊态下的“不平滑”并非 Bug，而是 Miuix 库为了防止几何畸变而设置的保护机制。但在超级岛这种高频视觉组件上，这种保护导致了设计风格的不统一。通过 Xposed 注入干预该算法的参数计算，可以实现全态势下的平滑视觉体验。
