# MIUISmoothIsland

MIUISmoothIsland is an Xposed/LSPosed module designed to force "Smooth Corners" (Squircle/Superellipse) for MIUI's "Super Island" (Dynamic Island) and other SystemUI components. It overrides the standard `Outline.setRoundRect` implementation with a manual cubic Bezier path strategy to achieve a more visually pleasing "HyperOS-style" aesthetic.

## Project Overview

- **Type:** Android Xposed Module
- **Core Technology:** Java 17, Gradle 8.2, Android SDK 34 (Upside Down Cake)
- **Target Process:** `com.android.systemui`
- **Key Dependencies:** `de.robv.android.xposed:api:82`

### Architecture

The module operates by hooking into the `com.android.systemui` process and intercepting UI-related methods:

1.  **Outline Hook:** Intercepts `android.graphics.Outline.setRoundRect` to replace the default circular arc rounding with a manually calculated Squircle path using `Path.cubicTo`.
2.  **View Hook:** Hooks `View.onAttachedToWindow` for components containing "Island" or "MiuiStatusIcon" in their class names to ensure `clipToOutline` is enabled and native MIUI smooth corner settings are managed.

## Building and Running

### Build Commands

- **Build Debug APK:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Build Release APK:**
  ```bash
  ./gradlew assembleRelease
  ```
- **Clean Project:**
  ```bash
  ./gradlew clean
  ```

### Installation & Deployment

1.  Build the APK using the commands above.
2.  Install the APK on a rooted MIUI device with LSPosed installed.
3.  Open the LSPosed Manager and enable the **MIUI Super Island Smooth Corner** module.
4.  Ensure the scope is set to **System UI** (`com.android.systemui`).
5.  Restart System UI or the device to apply changes.

## Development Conventions

- **Entry Point:** `com.example.smoothisland.XposedInit` (defined in `assets/xposed_init`).
- **Hooking Strategy:** Prefer `beforeHookedMethod` for replacing logic and `afterHookedMethod` for modifying state.
- **Path Calculation:** The `createSmoothRectPath` method uses a curvature coefficient `c = r * 0.72f` to approximate a squircle.
- **Logging:** Use `XposedBridge.log` with the `SmoothIsland: ` prefix for debugging.

## Key Files

- `app/src/main/java/com/example/smoothisland/XposedInit.java`: Main logic for Xposed hooks and path generation.
- `analysis_report_miui_features.md`: Detailed research on MIUI's Dynamic Island implementation and why native smooth corners often fail.
- `app/src/main/AndroidManifest.xml`: Module metadata and LSPosed scope configuration.
