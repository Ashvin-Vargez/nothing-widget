# Nothing Widget

A Nothing-OS-style dot-matrix Android home-screen widget featuring:

- **Day name** – rotated CCW down the left edge, depleting top → bottom as time passes
- **MONTUEWEDTHUFRISATSUN** – week progress bar across the top
- **JUN / month** – large dot-matrix month bar + today's date in red (e.g. `26`)
- **2026 / year** – year progress bar
- **Countdown timer** – `H:MM` in white, `:SS` in red/smaller
- **Chrome Dino runner** – scrolling cactus, auto-jumping dino, sun/moon based on hour
- All percentages in red; breathing red edge on slow bars, fast-blink edge on the day bar
- Configurable **day start/end time** (default 07:00 – 23:00)

---

## Build via GitHub Actions (recommended)

1. **Fork or push** this repo to your GitHub account.
2. Go to **Actions → Build APK → Run workflow**.
3. After ~3 minutes the workflow finishes; click **Artifacts → nothing-widget-debug** to download the APK.
4. Sideload it on your phone (`adb install app-debug.apk` or open the file in a file manager with "Install unknown apps" enabled).

The APK is built automatically on every push to `main`.

---

## Build locally

```bash
# Prerequisites: JDK 17, Android SDK
./gradlew assembleDebug
# APK lands at: app/build/outputs/apk/debug/app-debug.apk
```

---

## Usage

1. **Install** the APK.
2. Long-press your home screen → **Widgets** → find *Nothing Widget* → drag it to a 4×4 cell.
3. Open the **Nothing Widget** app to set your day window (start / end hour) and tap **Save**.

The widget runs a lightweight foreground service (shown as a silent notification) that re-renders at ~15 fps. Battery impact is minimal — it's pure CPU Canvas drawing with no network activity.

---

## Project layout

```
app/src/main/kotlin/com/nothingwidget/
├── Glyphs.kt               5×7 dot-matrix font data
├── WidgetSettings.kt       SharedPreferences wrapper (start/end hour)
├── DinoGameState.kt        Chrome Dino physics & scroll state
├── WidgetRenderer.kt       Full Canvas renderer (280 px virtual space)
├── NothingWidgetProvider.kt AppWidgetProvider – starts the service
├── WidgetUpdateService.kt  Foreground service driving ~15 fps frames
├── BootReceiver.kt         Restarts service after reboot
└── MainActivity.kt         Settings screen
```

---

## Customisation

| What | Where |
|------|-------|
| Day start/end time | Settings screen in the app |
| Dino speed | `DinoGameState.CSPD` constant |
| Frame rate | `WidgetUpdateService` → `postDelayed(66L)` (66 ms ≈ 15 fps) |
| Colours | `WidgetRenderer` colour constants at the top |
| Widget size | `res/xml/widget_info.xml` → `targetCellWidth/Height` |
