# PulseHUD 0.2.0 — V2 source preview

A native Android / Kotlin / Jetpack Compose floating HUD project. The dashboard has been restyled toward a clean, dark AI-product interface with warm orange accents, metric cards, clear section labels, and less visual clutter.

**Target:** Android 16 / Infinix device  
**Minimum:** Android 10 (API 29)  
**Version:** 0.2.0

## V2 changes
- Reworked dashboard visual style with large metric cards and clearer section hierarchy.
- Added a live tap-test area so the CPS calculation can be checked inside PulseHUD.
- Added an explicit **Done editing** action to exit overlay drag mode.
- Added a HEX color field, color preview, and quick color swatches.
- Built-in preset bases now apply a group of settings.
- Custom presets now save and restore a settings snapshot instead of only saving a name.
- Added an overlay refresh counter labeled **HUD FPS**.
- Added permission/service status indicators.
- Added a GitHub Actions workflow to build a debug APK without compiling on the phone.

## Important technical limits
1. **CPS across other apps:** The AccessibilityService counts `TYPE_VIEW_CLICKED` and `TYPE_VIEW_LONG_CLICKED` events that foreground apps expose. It does not receive every physical touchscreen tap. Many games render their controls on custom surfaces and expose no accessibility click events, so CPS can still remain at zero in those games. The in-app tap test verifies the counter math, not universal game capture.
2. **FPS:** The displayed value is **HUD FPS**, measuring PulseHUD's own Choreographer frame callbacks. It is not the game's FPS. Game-wide FPS generally needs privileged/OEM-specific access that an ordinary app cannot assume.
3. **Overlay layout:** Horizontal/vertical/widget layout is saved as a preference, but the current floating overlay still renders a single compact panel. Independent widget positioning is not implemented yet.
4. **Fonts:** The TTF/OTF picker retains the selected font URI, but the selected font is not yet applied to overlay rendering.
5. **History:** The history preference is stored, but a full session-history database and viewer are not implemented.
6. **Coordinates:** Dragged X/Y coordinates are saved. Numeric X/Y editing and per-widget coordinates are not implemented.

These limitations are shown honestly rather than displaying fabricated readings or claiming unsupported Android permissions.

## Build APK using GitHub Actions
1. Create a GitHub repository and upload the contents of this `PulseHUD` folder to its root.
2. Open **Actions** in the repository.
3. Select **Build PulseHUD APK** and run the workflow, or push to the `main` branch.
4. When the workflow finishes, open its run and download the `PulseHUD-V2-debug-apk` artifact.
5. Extract the artifact ZIP; install `app-debug.apk` on the phone. Android may ask you to allow installation from that source.

The workflow uses Java 17, Android SDK 36, Gradle 8.11.1, and Android Gradle Plugin 8.10.1.

## Build locally
Open the `PulseHUD` directory in Android Studio with JDK 17 and Android SDK Platform 36 installed, then run:

```bash
gradle :app:assembleDebug
```

The debug APK output is `app/build/outputs/apk/debug/app-debug.apk`.

## First run
1. Launch PulseHUD.
2. Tap **Start HUD** and grant Display over other apps.
3. Open Android Accessibility settings and enable PulseHUD only if you agree to its event-counting behavior.
4. Return to PulseHUD. Use **Edit position** to drag the overlay, then **Done editing** to return to touch-through mode.
5. Use **Tap test · +1 click** to verify the in-app counter.

## Privacy
PulseHUD does not upload metrics. Settings and total click count are stored in app-private SharedPreferences. Accessibility access is powerful; enable it only after reviewing the service description and source.
