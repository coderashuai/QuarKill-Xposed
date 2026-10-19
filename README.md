# Quark Lite Xposed — 0.1.0-alpha

**Target:** Quark `com.quark.browser`, requested version 10.19.0.1165. This is an **unverified prototype**, not a tested compatibility release.

## What actually works in source

- Legacy Xposed API module metadata and entry point.
- Hooks `com.ucpro.MainActivity.onResume` only in the target app's main process.
- Makes three delayed scans of Android View hierarchy and hides TextViews with **exact** matching labels: 热搜、资讯、小说、短视频、AI、AI助手、AI 搜索、热门推荐.
- Does not modify APK, permissions, components, package-manager state, downloads, networking or storage.
- LSPosed disable + force-stop Quark restores original behavior. Existing app data is untouched.

## Important limitations

- The `com.ucpro.MainActivity` hook target was suggested by preliminary manifest inspection, **not verified by method-level decompilation**.
- Many modern interfaces use WebView, Compose, custom views, dynamic data, or obfuscated names; these elements will not be hidden by this prototype.
- This does **not** implement webpage ad blocking, tracking blocking, service disabling or AI execution blocking. Those require further DEX analysis and device tests.
- It may hide a matching text label outside the homepage, because MainActivity can host other screens. No click targets are removed, and UI text is not a security boundary.
- On some devices `xposed_init` legacy module registration requires enabling legacy modules in LSPosed.

## Build

Open in Android Studio with Android SDK 35, JDK 17 and network access for dependencies, and build debug APK. Or upload this source to a GitHub repository and run the included **Build APK** workflow. The resulting artifact contains `app-debug.apk`.

## Install and rollback

1. Install the debug APK.
2. In LSPosed, enable **Quark Lite (Alpha)** and scope it **only** to `com.quark.browser`.
3. Force-stop and restart Quark. Observe Xposed logs for `QuarkLite`.
4. To roll back, disable the module in LSPosed and force-stop/restart Quark, or uninstall the module APK.

No guarantee is made that any label is actually hidden on version 10.19.0.1165. Verify browser tabs, page rendering, downloads and login on your device.
