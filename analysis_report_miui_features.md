# MIUI 特性分析报告：平滑圆角与超级岛 (Dynamic Island) 深度解析

## 1. 核心架构：宿主与插件
MIUI 超级岛采用“容器+插件”的架构实现：
- **宿主 (com.android.systemui)**：提供全局容器 `DynamicIslandWindowAnimController`，负责外层 `Outline` 裁剪和动画框架。
- **插件 (miui.systemui.plugin)**：具体的业务逻辑实现，包含 `NotificationDynamicIslandPluginImpl` 和视图创建器 `DynamicIslandWindowViewCreator`。

## 2. 绘制逻辑与背景实现
在插件包中，超级岛的视觉呈现由以下组件构成：
- **视图层级**：根视图为 `DynamicIslandWindowView`，内部包含 `smallIslandView`（小岛）和 `bigIslandView`（大岛/矩形）。
- **背景绘制**：
    - 使用了 `DynamicIslandBackgroundView`。
    - **关键发现**：其背景主要依赖于标准的 `GradientDrawable`（如 `R.drawable.dynamic_island_background`）。
    - **形状细节**：通过设置 `GradientDrawable` 的半径为高度的一半实现“胶囊形”。在多岛态下，宽度缩减至等于高度，形成“圆形”。
- **特效支持**：
    - **模糊与混合**：调用了 `MiBlurCompat.setMiBackgroundBlendColors`，在底层触发高级模糊和颜色混合。
    - **阴影**：通过 `MiShadowUtils` 实现动态阴影。

## 3. 平滑圆角 (Smooth Corner) 的技术断层
- **底层支持**：`libhwui.so` 中存在 `SmoothCorner` 类，且插件包的 `Manifest` 中声明了 `PROPERTY_MIUI_SMOOTH_CORNER_ENABLED = true`。
- **Java 层实现**：项目中包含 `miuix.smooth.SmoothCornerHelper` 类，该类提供了反射调用 `setSmoothCornerEnabled(boolean)` 的能力。
- **失效原因**：
    - 尽管有底层支持，但超级岛的 `BaseContentView` 在更新背景时（`updateBackgroundBg` 方法）并没有显式调用平滑激活指令。
    - 由于背景是标准的 `GradientDrawable` 而非 `SmoothContainerDrawable`，且 `Outline` 裁剪未开启 `mIsSmooth` 标志位，导致渲染引擎回退到标准圆弧路径。

## 4. 自动化补丁 (LSPosed) 逻辑
为修复上述“断层”，补丁采取了以下精准 Hook 方案：
- **针对裁剪**：Hook `android.graphics.Outline.setRoundRect`，强制将私有标志位 `mIsSmooth` 设为 `true`。
- **针对绘制**：Hook `View.onAttachedToWindow`，识别 `DynamicIsland` 相关类名，通过反射调用 MIUI 隐藏 API `setSmoothCornerEnabled(true)`。
- **作用域**：仅针对 `com.android.systemui` 进程，确保在宿主和插件加载时同步生效。

## 5. 关键符号与类名
- **核心控制器**：`miui.systemui.notification.NotificationDynamicIslandPluginImpl`
- **背景控制**：`miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView`
- **隐藏 API**：`View.setSmoothCornerEnabled(boolean)`
- **平滑标志位**：`Outline.mIsSmooth`
