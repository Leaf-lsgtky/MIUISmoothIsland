# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android/Xposed project. Main code lives in `app/src/main/java/com/example/smoothisland/`, with the hook entry point in `XposedInit.java`. Module metadata is split between `app/src/main/AndroidManifest.xml` and `app/src/main/assets/xposed_init`. Scope resources live in `app/src/main/res/values/arrays.xml`. CI is defined in `.github/workflows/build.yml`, and project notes are kept in `analysis_report_miui_features.md` and `GEMINI.md`.

## Build, Test, and Development Commands
Use the system Gradle installation; this repo does not include `gradlew`.

- `gradle assembleDebug`: Build the debug APK used by CI and local device testing.
- `gradle assembleRelease`: Build a release APK for packaging checks.
- `gradle clean`: Remove Gradle build outputs before a fresh build.

Use JDK 17 and Android SDK 34 to match `build.gradle` and `app/build.gradle`.

## Coding Style & Naming Conventions
Write Java 17 with 4-space indentation. Follow Android naming norms: `UpperCamelCase` for classes, `lowerCamelCase` for methods and fields, and `UPPER_SNAKE_CASE` for constants such as `SYSTEMUI_PKG`. Keep hook logic small and explicit; move reusable geometry or filtering code into private helpers. Resource names should stay lowercase with underscores. When adding debug output, use `XposedBridge.log` and keep the `SmoothIsland:` prefix consistent.

## Testing Guidelines
There is no automated unit or instrumentation suite yet. Before opening a PR, run `gradle assembleDebug`, install the APK on a rooted MIUI/HyperOS device with LSPosed, scope it to `com.android.systemui`, restart System UI, and verify the island outline behavior visually and through LSPosed/Xposed logs. If you add automated tests, place JVM tests under `app/src/test/java/` and device tests under `app/src/androidTest/java/`.

## Commit & Pull Request Guidelines
Recent history favors short, imperative subjects and often uses Conventional Commit prefixes such as `fix:` and `refactor:`. Keep commits focused on one behavior change. PRs should include the target device/ROM, Android or HyperOS version, steps used to validate the hook, and screenshots or screen recordings for UI changes. Include relevant logs when modifying hook targets or outline behavior.
