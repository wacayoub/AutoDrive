# AutoDrive validation

## Version 1.4.1 — Bleu profond and icon-only media tabs

Validated 5 November 2026. Package `com.autodrive.launcher`, version code 15. The owner selected design 3 (Bleu profond). The native interface now uses navy surfaces, rounded map/media frames, blue selection and icon-only Radio/YouTube/IPTV tabs. Provider website content is unchanged.

- Debug assembly and Android test APK assembly passed. All **14 JVM tests** passed. Android lint reported **0 errors, 28 warnings, 1 hint**.
- Android 13 AOSP emulator, **800 × 480 / 160 dpi**: instrumentation returned **OK (4 tests)** for dashboard layout/lifecycle, Maps web preference and both installed YouTube association tests. The dashboard test checks each icon-only tab has its accessible name and tooltip, a minimum 48 × 48 dp target and the correct selected state. It switches Radio → IPTV → YouTube, checks panel recreation, four unique bottom shortcuts, and no clipped shortcut labels.
- The same dashboard instrumentation returned **OK (1 test)** at **600 × 1376 / 240 dpi portrait**. This also checks stacked full-width panels and visible shortcut labels. The emulator was restored to 800 × 480 / 160 dpi afterward.
- Real captures: [800 × 480 landscape](../deliverables/dashboard-1.4.1-800x480.png), [phone portrait](../deliverables/dashboard-1.4.1-portrait.png). These are app captures rather than generated proposals. Hardware-specific WebView behavior, real GPS, the owner's YouTube Advanced app and IPTV subscription remain untested. No software was installed on the owner's Incarlink.
- Both GitHub workflows succeeded for the released code: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42105858319), [signed release](https://github.com/wacayoub/AutoDrive/actions/runs/42105859050). The actual public APK was downloaded anonymously and its size, SHA-256, package, version and original signing certificate were verified.

Published APK SHA-256: `a93ef145e36ed13ac5d8137db4ce4f3054f199d556ca208563dc68a101bf3cae`. [Release 1.4.1](https://github.com/wacayoub/AutoDrive/releases/tag/v1.4.1).

## Version 1.4.0 — public GitHub releases and in-app updates

Validated 5 November 2026. The public repository is [wacayoub/AutoDrive](https://github.com/wacayoub/AutoDrive), with a published [1.4.0 release](https://github.com/wacayoub/AutoDrive/releases/tag/v1.4.0). Package `com.autodrive.launcher`, version code 14.

- Local release assembly, **14 JVM unit tests**, and Android lint passed: **0 errors, 28 warnings, 1 hint**. Both GitHub workflows also succeeded: [Android checks](https://github.com/wacayoub/AutoDrive/actions/runs/42100805260) and [Signed Android release](https://github.com/wacayoub/AutoDrive/actions/runs/42100864446). The latter built, signed and published the actual public APK and update metadata.
- On the Android 13 / 800 × 480 emulator, instrumentation returned **OK (5 tests)**. These covered the existing dashboard, map web preference and YouTube association behavior, plus APK verification accepting the correctly signed candidate and rejecting a different signing key, an incorrect hash and inconsistent version metadata.
- For the update journey, a dedicated version-code-13 test build containing the updater was installed first. Its Settings page discovered the live public GitHub release, downloaded the real release APK and verified it. Android requested source authorization and then displayed its normal update confirmation. After confirmation, package inspection reported version code 14. The chosen application association survived the update, and the installed 1.4.0 release reported **AutoDrive est à jour**. [Actual installed-release screenshot](../deliverables/updates-1.4.0-800x480.png)
- Earlier distributed versions do not contain this updater. Users must install 1.4.0 or later once; subsequent releases can be checked, downloaded and offered to Android from Settings. No silent installation is claimed. The original signing identity was preserved; private signing material is excluded from the public source.
- The end-to-end installation used only the emulator. The Incarlink's installer policy, network and firmware behavior remain untested. No software was installed on the owner's hardware. This release does not change the documented Maps/YouTube web integration boundaries.

Published APK SHA-256: `20a095a405b1cd9139ef4d20c9c85147b7dbaf8c11f5daeade233c0f20d75dbf`. Signing certificate SHA-256: `1ef249ecb8d4af1bbd730a15aea2248acf5d578dffde2d637fd01b7e5f637efb`.

## Version 1.3.2-web-preview — choose the installed YouTube app

Debug assembly, all 11 existing unit tests and lint passed: **0 errors, 25 warnings, 1 hint**. The Android 13 / 800 × 480 emulator instrumentation returned **OK (3 tests)**: the existing dashboard layout/lifecycle test plus the two new YouTube selection tests.

- The actual Settings picker selected the emulator's launchable **Clock** app as a fixture. Its label updated and the selection survived force-stop/relaunch. **Ouvrir l’application YouTube** then opened that selected fixture. The dialog's **Effacer le choix** action cleared the preference. The screenshot shows the reset state. [Settings capture](../deliverables/settings-1.3.2-800x480.png)
- A synthetic YouTube-origin test page supplied an explicit Open App intent. A real injected test touch triggered the production browser handler and the selected Clock app reached the foreground. Normal Maps/YouTube page URLs and the two-panel dashboard lifecycle remained covered by the dashboard instrumentation test. The synthetic page belongs only to the test APK and is not used by the shipped launcher.
- The selection test verifies a new AppLinks instance reads the saved component, missing selections remain saved and return unavailable, clearing removes the override, and account/lookalike-host URLs are rejected for native YouTube handoff. An app that cannot handle a supported YouTube URL falls back to its normal launcher activity.
- The exact YouTube Advanced/ReVanced build on the owner's box is **not installed in the emulator and remains untested**. Tests establish the generic installed-app selection/launch mechanism. The dashboard continues displaying the official YouTube website; no native app screen embedding or account transfer is claimed. No software was installed on the owner's hardware.

Package `com.autodrive.launcher`, version code 13. APK SHA-256: `cb360c42289b431d1d2cae8a9febb1c7e95c41888dd1a55999156e6b34984e7b`.

## Version 1.3.1-web-preview — responsive display and web preference

Debug assembly, all 11 existing unit tests and lint passed: 0 lint errors, 25 warnings, 1 hint. Instrumentation passed **2 tests on 600 × 1376 / 240 dpi portrait** and **2 on 800 × 480 / 160 dpi landscape**. Checks include native label ellipsis, 48 dp shortcut height, stacked/side-by-side panel geometry, panel lifecycle, delayed Maps web-choice selection in English/French and no action on a Google account page. Real map startup and portrait YouTube search/keyboard layout were visually checked. The exact owner's device remains untested. See [detailed changes, captures and limitations](DISPLAY-FIXES-1.3.1.md).

APK SHA-256: `b7aefc0d37c5137125de2f82bb94c7b1f1e07222e85c89d1b5dbe57621a96491`.

## Version 1.3.0-web-preview — actual website panels

Validated 5 November 2026 on the 800 × 480 / 160 dpi AOSP Android 13 emulator, Android System WebView 101.0.4951.61. No Google Maps app, YouTube app or Google Play services were needed for the observed website rendering. No software was installed on the owner's Incarlink.

- Final debug build and **11 existing unit tests passed**. Android lint: **0 errors, 25 warnings, 1 hint**. APK package `com.autodrive.launcher`, version code 11; existing debug signing certificate verified. The exported HOME/launcher entry is now `WebDashboardActivity`.
- The new `WebDashboardTest` passed on the emulator. It verifies two browser panels at the actual Maps/YouTube home URLs, no Add Video button, exactly four bottom shortcuts with 48 dp minimum height, Radio/YouTube switching, browser release when opening Settings, and browser recreation after Back. This test validates panel/lifecycle behavior rather than remote content availability.
- Live Google Maps rendered its actual map and attributions. A touch drag changed the visible map area and a double tap zoomed in, with YouTube video still visible beside it. Searching **Mosquee Hassan II** returned real results and opening a result showed the **Mosquée Hassan-II** place details with Google's own controls. [Real map/video capture](../deliverables/dashboard-1.3.0-web-live-800x480.png)
- Live YouTube Home rendered inside the right panel. The website initially showed a search prompt in the anonymous session. Searching **Android Developers**, scrolling and selecting a video opened the watch page in the same panel. The video rendered changing frames and its player displayed elapsed time **0:11 / 25:40** with the pause control. This verifies streaming video in this emulator run, not every video, format or the owner's hardware. [Playback capture](../deliverables/dashboard-1.3.0-web-playing-800x480.png) · [Search capture](../deliverables/dashboard-1.3.0-web-search-800x480.png)
- The initial keyboard layout was too short for useful search; the final build handles IME insets explicitly and temporarily hides the native header/tabs/footer during typing. The final Maps search field and suggestions remained visible above the keyboard and the dashboard controls returned afterward.
- The offline/retry smoke script disables both emulator networks, checks that both panels show a missing-page message and retry control while the four shortcuts remain available, then restores networking and captures the retried real pages. See `tools/web_dashboard_smoke.py` and [home capture](../deliverables/dashboard-1.3.0-web-home-800x480.png).
- A UIAutomator hierarchy dump crashed in Android's `AccessibilityNodeInfoDumper` during one live dynamic-page inspection. The app remained running. Native instrumentation, touch interaction and screenshots were used to complete the UI checks.
- **Not validated:** exact Incarlink WebView/firmware behavior; native Maps handoff on the box; live GPS recentering in the web map; full route-selection flow; turn-by-turn guidance; live driving ETA/traffic; Google account login/personalized YouTube Home; IPTV subscription playback or Toyota tuner integration. The website's native-app prompts remain intact. The browser preview does not resolve the authorized Google Navigation SDK requirement. [Integration boundaries](WEB-DASHBOARD-AUDIT.md)

Final APK SHA-256: `1c93b51482b06a797391d0f8690a2b3a0eb748ff0e1c528f4e45dc378425cc23`.

## Version 1.2.0 — refreshed dashboard and YouTube sharing

Validated 5 November 2026. Debug assembly, all **11 unit tests**, and Android lint completed successfully with JDK 17. Lint: **0 errors, 22 warnings, 1 hint**. The APK retains package `com.autodrive.launcher` and the existing debug signing certificate, version code 10.

- All six Android 13 AOSP smoke groups passed at **800 × 480 / 160 dpi**: clean installation; missing Maps and media apps; four unique shortcuts with minimum 48 dp touch targets; tab switching and persisted tab; external radio handoff using the emulator clock app as a fixture; GPS permission denied and GPS disabled; offline state; app drawer; Back and Home with the original Home selection restored.
- Instrumentation runner returned **OK (4 tests)**. Executed checks validate share-to-dashboard, a visible official YouTube iframe without autoplay, minimum 200 × 200 dp player viewport, tabs retained above the player, return from Settings, switching away from and back to YouTube, and touch delivery to the hosted map view. The HLS test was skipped because no stream URL was supplied; this release makes no new HLS playback claim.
- A separate **800 × 480 / 213 dpi** check passed: the player uses the entire media panel to preserve its minimum viewport, with no player clipping. In this compact mode, use Media or Back to return to the tabs. Density was restored after the check.
- [Real dashboard screenshot](../deliverables/dashboard-1.2.0-800x480.png) shows the missing-Maps/no-selected-video state on the emulator. [Reference comparison](../previews/compare-1.2.0.html) preserves the supplied reference photo and places the real screenshot beside it; no synthetic map or video is used.
- The Google Play image did not reach a usable connected emulator state during this run. Actual Google widget traffic, zoom behavior and remote YouTube video playback were **not validated for 1.2.0**. An iframe being present does not establish successful video playback. Provider restrictions, connectivity and the box's WebView still need validation.
- No software was installed on the owner's Incarlink. The exact box density/insets, widget permissions, native Home routing, codec behavior and YouTube/Google service compatibility remain hardware checks. The map remains Nearby Traffic; inline destination selection and turn-by-turn guidance are unresolved. A selected YouTube video uses the official embedded player and does not reproduce the official YouTube application's browse screen.

APK SHA-256: `abf0abd03ce4231baf7824413e658cd4e1c0fd5eac7fa1fe845acdcb8b261079`.

## Version 1.1.7

- Debug assembly, all ten unit tests and all six Android 13 AOSP smoke groups passed. Android lint reported zero errors, 22 warnings and one hint.
- The Android 13 instrumentation test injected a tap and verified that it reached the view hosted in the map panel.
- The 800 × 480 Google Play emulator upgraded in place from 1.1.6 and reused a single bound Nearby Traffic widget. Google kept the widget in its loading state, so the native zoom and Google activity launch were unavailable to verify in this run. [Widget screenshot](../deliverables/dashboard-1.1.7-widget-800x480.png)
- APK SHA-256 is `928ac2b50e9ccc08de393ac97ce1467da72e9779d625be8eef0ca320f554c60a`. The package and signing certificate match 1.1.6.

## Version 1.1.6

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 22 warnings and one hint.
- On the 800 × 480 Android 13 Google Play emulator, AutoDrive requested consent on first binding and reused the existing binding on restart. The final build also removed an orphan binding from an older build. Android reported one AutoDrive widget binding after the upgrade.
- With AutoDrive as the Android Home app, repeated taps and a swipe on the widget kept AutoDrive fullscreen and launched no Maps activity. Google supplied the loading view. Live traffic was unavailable during the test. [Widget screenshot](../deliverables/dashboard-1.1.6-widget-800x480.png)
- The Android 13 AOSP suite verified that the map panel stayed on the dashboard when Maps was unavailable. The physical Incarlink widget and its traffic availability still need verification.
- APK SHA-256 is `c41c32282c90b9322100fffd08fbc1dab149289f0e82b27b315d49fad27294bd`. The package and debug signing certificate match 1.1.5.

## Version 1.1.5

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 25 warnings and one hint.
- The 800 × 480 Android 13 AOSP smoke suite passed all six groups, including missing Maps browser handoff, no separate map buttons, offline state, GPS permission, and Android Home and Back. [AOSP dashboard](../deliverables/dashboard-1.1.5-800x480.png)
- The Android 13 Google Play emulator upgraded in place from 1.1.4 with a bound Nearby Traffic widget. The upgraded dashboard used the interactive Maps entry point. Starting AutoDrive through Android Home produced a real Home task, and tapping the panel then placed a regular AutoDrive task and Google Maps in two visible Android windows.
- On that emulator, the optional Nearby Traffic widget could still be enabled from Settings and removed by switching back to interactive Maps.
- In the Maps window, double-tap and drag zoomed in and out. Native search found Golden Gate Bridge, and Start displayed genuine guidance, a route line and an ETA with the Media Hub beside it. The route used an emulator location. [Guidance capture](../deliverables/dashboard-1.1.5-guidance-800x480.png)
- Outside active navigation, pressing Android Home returned to the dashboard and another tap reopened both windows. During active guidance, Home returned to the dashboard with Google Maps in Android picture-in-picture. Tapping the map panel while Maps was pinned did not restore the split in that test.
- The 1.1.5 APK uses the same package and signing certificate as 1.1.4. SHA-256 is `3cc146e1b33c166accb4ffac9f36819b4725c5fb80cec8205852744cb828cbd1`. The physical Incarlink split and Home-key behavior remain unverified.

## Version 1.1.4

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 25 warnings and one hint.
- The 800 × 480 Android 13 AOSP smoke suite passed all six groups. It verified that the three launcher map buttons were gone and that tapping the map panel offered a browser handoff when Maps was absent. [Dashboard capture](../deliverables/dashboard-1.1.4-800x480.png)
- On the Android 13 Google Play emulator, the Nearby Traffic widget filled the left panel. Google reported “Can't show nearby traffic” and a tap on the widget opened official Maps fullscreen. After removing the widget through Settings on the emulator, a tap on the same panel opened official Maps next to AutoDrive in two visible Android windows. [Split-screen capture](../deliverables/dashboard-1.1.4-maps-800x480.png)
- In the adjacent Maps window, native search found Golden Gate Bridge. Tapping Start displayed Google's next turn, route line and ETA while the Media Hub remained visible. This used the emulator location. [Guidance capture](../deliverables/dashboard-1.1.4-guidance-800x480.png)
- Follow-up audit with the same APK and the widget removed: selecting AutoDrive as Android Home and launching it with the Home key produced a `type=home` task. Tapping the panel opened Maps in a split-screen stage, with no visible AutoDrive task or Media Hub after Maps loaded. The earlier successful split used AutoDrive as a regular app. The emulator's previous Home app was restored. [Pre-release audit](NAVIGATION-AUDIT.md#pre-release-audit-of-the-fullscreen-tap-in-114)
- The APK upgrades 1.1.3 with the same package and signing certificate. SHA-256 is `fff2fc67785d1d170f00914d7841223eb5db7753816bbd301715ed4a1552965b`. Split-screen and widget behavior on the physical Incarlink still need hardware verification.

## Version 1.1.3

- Debug assembly and ten unit tests passed. Android lint reported zero errors, 29 warnings and one hint.
- On the Android 13 Google Play emulator, **Explore** opened official Maps beside AutoDrive. **Find place** found Ferry Building in a separate adjacent Maps window with a map pin, details and native route actions.
- Zoomed in and out using Google's double-tap-and-drag gesture on the Maps pane at 800 × 480. [Map and place detail screenshot](../deliverables/dashboard-maps-800x480.png)
- The AOSP emulator opened the browser fallback for map display, place search and directions with the Maps app absent.
- APK package and signing certificate match 1.1.2 for an in-place upgrade. SHA-256 is `f7e3a5aea89a790a3a68945a38a166ed8f3537e0098f3285a43cd11e843e9863`.

## Version 1.1.2

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 27 warnings and one hint.
- On the Android 13 Google Play emulator, the dashboard IPTV player opened and rendered an authorized HLS test stream after an explicit Play action while the simulated GPS speed was 56 km/h.
- The Android 13 AOSP smoke suite passed all six groups, including opening the YouTube and IPTV setup dialogs without location permission.
- APK package and signing certificate match 1.1.1 for an in-place upgrade. SHA-256 is `54ba69dba127f007c7fee1504cc95a2cb4d4cb9528811c53ed6a63d05ec69052`.

## Version 1.1.1

- Debug assembly and all ten unit tests passed. Android lint reported zero errors, 27 warnings and one hint.
- On the Android 13 Google Play emulator, opening a fresh launcher triggered Android's widget consent directly. After approval, Nearby Traffic appeared in the expanded map panel and was restored after force-stop.
- With an emulator-provided stationary GPS reading, the YouTube tab accepted a video on the first visit. Reopening the tab brought back the saved video player after parked confirmation without re-entering the link.
- The Android 13 AOSP smoke suite passed all six groups, including offline and missing-app fallback, media tab persistence, parked gating and Android Home/Back.
- The APK uses the same package and signing certificate as 1.1.0. SHA-256 is `730f8f7dfd4a0896da6a37eeedf3610bc70c9744110792f4cdf33f474acd205d`.

## Version 1.1.0

Tested on 4 November 2026 with Android 13 AOSP and Google Play emulators at 800 × 480. The updated APK installed over 1.0 with the same package and signing certificate.

| Check | Result |
| --- | --- |
| Build and lint | Debug APK and instrumentation APK built. Android lint reported zero errors, 25 warnings and one hint |
| Unit tests | Ten passed, including four new tests for supported video links and invalid URL rejection |
| Instrumentation | Two passed. The YouTube view created the official embed with autoplay disabled. Media3 prepared and rendered the public Shaka HLS test stream and playback advanced after Play |
| AOSP smoke suite | Passed the six groups covering startup, missing apps, offline state, GPS unavailable, parking gate, tab persistence, Home and Back |
| YouTube on dashboard | Verified actual playback in the right panel after loading the link, confirming parked and tapping Play. The screen capture shows the official player rendering video |
| Nearby Traffic widget | Google Maps 11.129.0103's widget was bound through Android's Create dialog and restored after APK upgrade. The widget displayed its own location prompt and loading screen. Traffic tiles did not finish loading on the emulator during this test |
| Directions from the dashboard | Entered a destination in AutoDrive. Official Maps and AutoDrive opened in separate adjacent Android windows. Maps showed an active route, the next turn and ETA while AutoDrive's Media Hub stayed visible. [800 × 480 navigation capture](../deliverables/dashboard-navigation-800x480.png) |
| IPTV Smarters | The assigned Smarters app is an external option. Direct HLS playback was verified with a public test stream, independent of a Smarters account |
| Movement while playing | On the dashboard, the HLS test stream reached four seconds of playback at an emulator-reported 0 km/h. After the emulator supplied a moving GPS fix, the player closed and the Media Hub returned |
| APK SHA-256 | `c199fdfca0c8f42e9d8b5159a3d1e098ba07af5bc1befdcdf91d488f57f134f6` |

The device-specific Inlink widget implementation, widget traffic tiles on the box and playback of the owner's actual videos and IPTV channels remain unverified.

## Version 1.0 baseline

Test date 4 November 2026. The starting workspace contained the approved dashboard photo. AutoDrive is a new Android Studio project. The existing local Android toolchain was reused for development. All installations and Android configuration changes during validation targeted isolated emulators.

### Build

| Check | Result |
| --- | --- |
| Gradle Wrapper 8.13, JDK 17, compile SDK 35 | Build completed using the supplied wrapper |
| `:app:assembleDebug` | Passed. Package `com.autodrive.launcher`, version `1.0.0`, minimum API 33 |
| `:app:testDebugUnitTest` | Six tests passed. GPS conversion, smoothing, stationary detection, accuracy, stale readings and resets |
| `:app:lintDebug` | Zero errors, 16 warnings and one hint. The build retains the verified SDK and dependency versions and uses a landscape preference for the target dashboard |
| APK signature | `apksigner verify` passed for the debug APK, APK Signature Scheme v2 |
| APK SHA-256 | `88ab5aaef93315c651c2088bebefc848d42e7ea78b9ac9eb82dece24feb9e305` |

### Emulator coverage

Android 13 / API 33 x86_64 at 800 × 480 landscape, with an AOSP image for absent-package and no-Play-services coverage and a Google Play image for genuine Maps and YouTube package coverage.

| Scenario | Observed result |
| --- | --- |
| Clean installation, location permission absent | Launcher opened without forcing a permission request. The readout displayed `--` and reported precise location off |
| GPS disabled | With location permission granted and Android location turned off, the launcher reported GPS disabled and displayed `--` |
| Offline | With emulator Wi-Fi and mobile data disabled, the launcher stayed responsive and reported internet unavailable |
| Google Maps missing | Display and destination actions offered web Maps. The emulator browser received the map URL and the driving-directions URL. This verifies browser handoff |
| Google Play services missing | AutoDrive opened normally and the AOSP Settings page reported Play services unavailable |
| API key absent and embedded licensing unresolved | Both emulator paths ran without credentials. The dashboard used the external navigation panel |
| Official Google Maps installed | **Open Maps** brought Google Maps 11.129.0103 to the foreground. The driving action delivered `google.navigation:` to the official Maps package. After the Maps first-use notices, the app entered active driving guidance and displayed the next maneuvers, distance and ETA. The route used emulator-provided San Francisco coordinates |
| Split-screen | Android placed AutoDrive and official Google Maps in separate adjacent windows on the Google Play AVD |
| Radio missing or unassigned | The Radio tab reported that an app was not selected. Selecting Android's Clock as a launch fixture verified external app handoff. A separate attempt with the AOSP Calendar app triggered a crash in Calendar and returned to a responsive AutoDrive |
| YouTube missing | The AOSP Media Hub displayed YouTube unavailable and disabled the playback controls |
| Official YouTube installed, speed unavailable | The Google Play AVD recognized the official YouTube app. The launch path showed the parked-viewing restriction when the launcher lacked a trustworthy speed reading |
| IPTV missing or unassigned | The IPTV tab reported that an app was not selected. An assigned launch fixture was blocked by the parked-viewing restriction while speed was unknown |
| Media tab switching and restart | All three tabs could be selected. The selected IPTV tab survived force-stop and relaunch |
| App drawer, Android Back and Home | The drawer opened, Back returned to the dashboard, and Android Home returned to AutoDrive when selected as the emulator's default Home. The previous Home app was restored |
| 800 × 480 layout | Captured and visually reviewed the dashboard at 160 dpi and 200 dpi. At 160 dpi the four bottom shortcuts were each at least 48 × 48 dp. The design comparison shows the supplied dashboard crop and a real 160 dpi emulator capture |

The current dashboard capture shows version 1.1 playing an official YouTube embed on the Google Play emulator. The Nearby Traffic widget is loading on the left. [Dashboard capture](../deliverables/dashboard-800x480.png) · [Side-by-side comparison](../deliverables/dashboard-comparison.png)

Repeat the AOSP smoke checks on a disposable AVD. The script reinstalls AutoDrive and temporarily changes emulator connectivity, location and Home settings.

```powershell
python tools/emulator_smoke.py --serial emulator-5554 --apk deliverables/AutoDrive-debug.apk --screenshot dashboard-test.png
```

## Remaining hardware validation

- Exact X15-SLM927-72 Android package inventory, Maps and Google Play services versions and compatibility, GPS speed accuracy, stale fixes and course quality. Live driving speed was not measured on vehicle hardware.
- Google Maps routing in the target region, spoken guidance, real traffic, offline routes and route resumption. Visual turn-by-turn was verified with an emulator location. Vehicle movement and audio were outside that test.
- The box's reversible Home picker, ignition boot and wake behavior, physical Home and Back routing, Android split-screen under projection, display density and usable insets. Emulator results do not establish hardware behavior or performance.
- Vendor radio, Toyota FM tuner access, favorites, phone audio and Bluetooth routing. The actual radio and IPTV apps must be selected and tested on the box.
- Real YouTube and IPTV playback, media metadata, transport controls, HLS codecs, DRM, buffering and recovery depend on the selected apps. No live media session or IPTV stream was verified in this run.
- The parked restriction gates launches and playback-start requests from the launcher. It requires GPS and a user confirmation, because a gear-position signal was unavailable. Behavior of external video after handoff remains under the external app's control.
