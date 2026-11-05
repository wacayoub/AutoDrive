# AutoDrive 1.3.0 web preview

Reviewed 5 November 2026 after the owner rejected a traffic widget and a YouTube link-entry player. The revised target is a manipulable Google map and the actual YouTube home/browse page in the dashboard.

The **1.3.1** update adds responsive portrait layout and a bounded, origin-scoped action for Maps' existing English/French **Stay on web** choice. It uses the documented map-view URL at startup. See the [display/startup audit and tests](DISPLAY-FIXES-1.3.1.md). Website changes can affect this preference action.

The **1.3.2** update adds **Settings → Application YouTube**. The preferred launchable component persists and is used for explicit native-app opening and supported YouTube Open App handoffs. The dashboard remains website-based; this setting does not embed the chosen app or transfer its account state. Only trusted YouTube HTTPS URLs are passed to the selected package, with a regular launcher fallback when deep links are unsupported. Missing selections can be changed or cleared in Settings.

## Implementation

The launcher entry is `WebDashboardActivity`. It displays two independent **top-level Android WebViews**, opening `https://www.google.com/maps` and `https://m.youtube.com/`. These are complete provider websites, not iframes around the installed applications, Android AppWidgets, a Google Maps SDK, or Android Auto. The former activity remains in source for historical code/tests and is not exported or a launcher entry in this preview.

Google documents Maps URLs for map display, search and directions, including browser behavior when the app is unavailable. It distinguishes mobile turn-by-turn navigation from browser features. [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started)

Google documents the YouTube mobile website, its Home tab and search. This preview opens that actual website, so no video URL entry or YouTube Data API catalog is needed. Recommendations and browsing content are supplied by YouTube and depend on its current site behavior. A fresh anonymous session can show a search prompt instead of recommendations. [YouTube mobile browsers](https://support.google.com/youtube/answer/3008060?hl=en)

## Boundaries

- Google Maps pan, double-tap zoom, search and a place-details page were observed inside the panel. Native turn-by-turn guidance, live driving ETA/traffic and all route-selection behavior have **not** been validated for this preview. The website's own Open App/Start controls can request the external Maps app. This preview does not turn browser directions into a homemade navigation engine.
- Google Maps Platform Navigation SDK eligibility for the vehicle deployment remains unresolved, as recorded in the [navigation audit](NAVIGATION-AUDIT.md). No SDK dependencies, API keys or billing project are used by this browser preview. Full Google pages retain their own branding, attributions, prompts and controls. This is not evidence of approval to use a vehicle-restricted SDK.
- The embedded pages are not automatically signed in to the native Google apps. Account pages outside the panel's allowed sites are handed to an external browser on a user gesture. The app does not copy account cookies or credentials, spoof the user agent or circumvent Google's embedded-login restrictions. External login does not promise to sign the panel in. Personalized subscriptions/recommendations remain unverified. [Google embedded-browser authentication restrictions](https://developers.googleblog.com/en/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/)
- Version 1.3.0 injected no page script. Version 1.3.1 runs a narrow script to select the existing Maps web-choice action once per document; it rewrites no HTML/CSS and does not suppress advertising or branding. Website shortlinks and documented web fallbacks stay in the panel when they resolve to an allowed Google Maps or YouTube site; unrelated links can open externally on a user gesture.
- Geolocation is enabled only for the Maps browser, and only trusted HTTPS Google map origins can receive location after Android foreground location permission is granted. No location or speed is invented. The separate speed header uses the existing Android location monitor.
- Video is selected through the website. AutoDrive does not initiate a Play action. The site's normal playback controls remain intact. Both WebViews are released when the dashboard leaves the foreground, stopping its page playback; they are recreated on return. Browser history is retained in the activity state when available.
- Radio and IPTV launch the user's selected installed application. This preview does not embed IPTV Smarters or claim tuner access.

## Verification

See [the 1.3.0 results](TEST-RESULTS.md). Actual map rendering, place search, YouTube search and streaming video were observed on an Android 13 AOSP emulator with WebView 101, without the Google Maps or YouTube native apps installed. This does not validate the exact Incarlink firmware, its WebView build or its network behavior.
