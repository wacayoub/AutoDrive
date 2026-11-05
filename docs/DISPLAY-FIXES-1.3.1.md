# AutoDrive 1.3.1 display and startup fixes

The owner's phone screenshot showed two narrow portrait columns, clipped YouTube/shortcut labels and Google's full-page Open App prompt. This update retains the website-based integration and changes the native layout and Maps startup preference.

## Changes

- Portrait uses full-width stacked panels. Landscape retains the 52/48 side-by-side proportions.
- Compact screens use icon-above-label shortcuts; Phone, Media, Apps and Settings remain complete and centered. The GPS quality text gets a separate header line rather than competing with the speed and clock.
- During typing, portrait gives the focused website the area above the keyboard. The other panel and dashboard chrome return when the keyboard closes.
- Maps starts with Google's documented map-view URL, `https://www.google.com/maps/@?api=1&map_action=map`. [Maps URL documentation](https://developers.google.com/maps/documentation/urls/get-started)
- If Maps presents a known English/French Stay on web action, a short, bounded script selects that existing provider control once per document. It runs only on the HTTPS Google Maps page, checks an exact label and a displayed button/link, and stops after selection or 15 seconds. It does not select Open App, Google account actions, consent actions or YouTube controls. No CSS, branding or map content is rewritten. This is a best-effort browser preference, not a documented Google SDK API; Google page changes may require an update or manual choice.

## YouTube Home

The shown **Try searching to get started** message belongs to YouTube. YouTube explains that Home recommendations may be absent when there is insufficient watch history. The app retains the official Home page and does not invent recommendations or automatically choose a video. [YouTube mobile Home behavior](https://support.google.com/youtube/answer/3008060?hl=en)

## Validation

- Android 13 emulator, **600 × 1376 / 240 dpi** portrait: stacked full-width panels, no ellipsis in native tabs/shortcuts, minimum shortcut height and panel lifecycle checks passed. A real YouTube search field remained visible above the keyboard; searching restored the dashboard layout afterward. [Portrait screenshot](../deliverables/dashboard-1.3.1-portrait.png)
- Same emulator restored to **800 × 480 / 160 dpi** landscape: side-by-side layout, label bounds and lifecycle checks passed. [Landscape screenshot](../deliverables/dashboard-1.3.1-800x480.png)
- Maps opened to its real map without the full-page app prompt during observed emulator startup. The fallback preference action was tested with delayed English/French synthetic buttons using the production WebView client. A negative fixture confirmed that no action occurred on a Google account page. The exact prompt in the owner's phone screenshot has not been tested on that phone.
- Final assembly and 11 existing unit tests passed. Lint: 0 errors, 25 warnings, 1 hint. New instrumentation: **2 tests passed in portrait and 2 in landscape**. The fixture tests verify preference selection and scope, not real Maps navigation.

Package `com.autodrive.launcher`, version code 12, version `1.3.1-web-preview`. APK SHA-256: `b7aefc0d37c5137125de2f82bb94c7b1f1e07222e85c89d1b5dbe57621a96491`.

No device installation was performed. Native turn-by-turn guidance, Google sign-in/personalized recommendations, actual box insets/density and the Incarlink's WebView remain hardware checks. This update does not change the integration into Android Auto or embed the installed Google Maps/YouTube activities.
