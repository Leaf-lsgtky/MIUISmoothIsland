# MIUISmoothIsland

MIUISmoothIsland is an Android LSPosed/libxposed module for MIUI/HyperOS
SystemUI. Its goal is to make MIUI's Super Island / Dynamic Island capsule use
a smoother, squircle-like outline instead of the default round-rect arc.

The module does not provide a launcher UI. It is installed as an APK, enabled in
LSPosed, and scoped to `com.android.systemui` and `miui.systemui.plugin`.

## Current Implementation

The active entry point is:

```text
com.example.smoothisland.XposedInit
```

It is declared in:

```text
app/src/main/resources/META-INF/xposed/java_init.list
```

The module uses libxposed API 101 metadata:

```text
app/src/main/resources/META-INF/xposed/module.prop
```

The static scope is:

```text
app/src/main/resources/META-INF/xposed/scope.list
```

which currently contains:

```text
com.android.systemui
miui.systemui.plugin
```

## Hooking Model

`XposedInit.kt` installs host outline hooks in `com.android.systemui` and plugin
background stroke hooks for `miui.systemui.plugin`.

The module currently has two hook paths:

1. `android.graphics.Outline.setRoundRect(int, int, int, int, float)`

   When the requested outline is capsule-like, meaning the radius is close to
   half of the view height, the module replaces the standard round rect with a
   custom `Path`.

2. `DynamicIslandWindowAnimController$updateFakeViewOutline$1.getOutline(...)`

   When this MIUI/HyperOS class is available, the module lets the original
   method run first, then rewrites the returned capsule outline into a smooth
   capsule path.

Both paths use the same shape-generation code.

Plugin-side stroke handling is separate. `miui.systemui.plugin` draws the
dynamic-island semi-transparent border in
`DynamicIslandBaseContentView.updateMedianLuma(float)` by setting a stroke on
the `DynamicIslandBackgroundView` drawable. The module lets MIUI keep its
original stroke while geometry changes, then replaces the stable-state stroke
with a smooth stroke drawable using the same capsule path generator.

## Smooth Capsule Generation

Smooth paths are generated with:

```text
androidx.graphics.shapes.RoundedPolygon.pill(...).toPath()
```

The smoothing value is read from the system property:

```text
persist.smoothisland.smoothing
```

Behavior:

- Default value: `0.8`
- Minimum value: `0.0`
- Maximum value: `1.0`
- Invalid values fall back to the default

The generated base capsule paths are cached by width, height, and smoothing
value. The cache is capped at 32 entries.

## Why This Exists

The research notes in `analysis_report_miui_features.md` describe the original
problem:

- MIUI/HyperOS has lower-level smooth-corner support.
- Super Island visuals often rely on ordinary `GradientDrawable` and
  `Outline`-based clipping.
- Those paths can fall back to normal circular arcs instead of MIUI smooth
  corners.

This module bypasses that gap by replacing capsule outlines directly inside
SystemUI.

## Build Environment

This repository does not include `gradlew`. Use a local Gradle installation.
The GitHub Actions workflow uses Gradle 9.1.0 and JDK 17.

Project configuration:

- Android Gradle Plugin: `9.0.1`
- compileSdk: `34`
- minSdk: `31`
- targetSdk: `34`
- Java compile target: `17`
- libxposed API dependency: `io.github.libxposed:api:101.0.1`
- shape dependency: `androidx.graphics:graphics-shapes:1.1.0`

Common commands:

```bash
gradle assembleDebug
gradle assembleRelease
gradle clean
```

Release signing is optional and controlled by these environment variables:

```text
SIGNING_KEYSTORE_PATH
ALIAS
KEY_PASSWORD
KEYSTORE_PASSWORD
```

## Installation And Validation

1. Build the APK with `gradle assembleDebug`.
2. Install it on a rooted MIUI/HyperOS device with LSPosed/libxposed support.
3. Enable the module.
4. Confirm the scopes include `com.android.systemui` and `miui.systemui.plugin`.
5. Restart SystemUI or reboot.
6. Check the island outline visually.
7. Inspect LSPosed/Xposed logs for module-load or hook errors.

## Important Files

- `app/src/main/java/com/example/smoothisland/XposedInit.kt`: hook entry point
  and smooth capsule path generation.
- `app/src/main/resources/META-INF/xposed/java_init.list`: libxposed entry
  declaration.
- `app/src/main/resources/META-INF/xposed/module.prop`: libxposed module
  metadata.
- `app/src/main/resources/META-INF/xposed/scope.list`: static package scope.
- `app/proguard-rules.pro`: keeps the entry point and `XposedModule`
  subclasses for release builds.
- `.github/workflows/build.yml`: debug/release APK build workflow.
- `analysis_report_miui_features.md`: background analysis of MIUI Dynamic
  Island smooth-corner behavior.

## Known Documentation Notes

Older notes may mention legacy Xposed APIs, Java source names, or
`assets/xposed_init`. The current repository uses Kotlin source and libxposed
metadata under `META-INF/xposed/`.
