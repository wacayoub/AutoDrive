# AutoDrive for Incarlink

AutoDrive is a Kotlin Android HOME launcher for an Incarlink Max Android 13 box on an 800 × 480 landscape display. Its dashboard follows the supplied Incarlink reference, with a GPS speed readout, a navigation panel, one Media Hub and four bottom shortcuts.

The current 1.2.0 build refreshes the charcoal and blue interface, hosts Google's Nearby Traffic widget and displays a selected YouTube video in the official embedded player. The requested Android Auto style of destination selection and turn-by-turn guidance inside the dashboard remains incomplete. See the [navigation audit](NAVIGATION-AUDIT.md#unmet-requirement-confirmed-on-5-november-2026).

## Try the debug APK

[Download AutoDrive 1.2.0](../deliverables/AutoDrive-1.2.0.apk). Use a compatible Android 13 device or the Android Studio emulator. Package name is `com.autodrive.launcher`. The APK is signed with a debug certificate.

1. Open this directory in Android Studio, or use the APK directly. Use an Android 13 AVD with a landscape display set to 800 × 480 at 160 dpi to match the included screenshot.
2. In Android Studio, choose the AVD and click **Run**. With ADB, use `adb -s emulator-5554 install -r deliverables/AutoDrive-1.2.0.apk` and substitute your emulator serial.
3. Open **AutoDrive** from Android's app list. This works with the existing Android Home app still selected.
4. Tap the GPS readout or open **Settings → GPS** and grant precise foreground location. GPS can take time to acquire a usable fix. Bluetooth status is a separate optional permission in Settings.
5. In **Settings**, assign the desired Android Phone, Radio and IPTV apps. YouTube uses the installed official Android YouTube app. To enable supported metadata and transport controls, use **Media access** and explicitly grant AutoDrive notification access in Android settings. Android may restrict this permission for sideloaded apps. AutoDrive uses that grant for MediaSession access and does not read or store notification contents.
6. In **Settings → Choose Android Home app**, select AutoDrive if the box presents Android's standard Home picker. Android decides when to start its selected Home app. To restore the prior launcher, use the same row or **Android Settings → Apps → Default apps → Home app** and select the original launcher. The OEM projection host can route its physical Home button elsewhere.

## Install on the Incarlink after approval

1. With the vehicle parked, transfer the debug APK to the Android box and open it with the box's file manager. If Android prompts to allow installation from that source, enable it for the file manager, install AutoDrive, and then turn the source permission off again.
2. Open AutoDrive manually first and verify Maps, GPS and the display. Set AutoDrive as Android Home only after checking that the box exposes a way back to the original Home app.
3. To remove AutoDrive, first switch back to the prior Home app and then uninstall AutoDrive from Android Settings.

The Incarlink hardware installation and on-car validation require the owner's explicit approval.

## Map on the dashboard

AutoDrive displays one official Google Maps **Nearby Traffic** widget on the dashboard. AutoDrive reuses an existing widget binding. For a first binding, approve Android's **Create** dialog if it appears. To retry setup, use **Settings → Enable Google Maps widget**. Google Maps supplies the traffic data and controls the widget's availability. If Google Maps requires location permission for the widget, manage that permission in Android's app settings for Maps.

AutoDrive passes input to the official widget. Use the zoom control when Google displays it. Some Google widget actions open the full Maps app, which handles destination search and guidance. Use Android Home or Back to return. See the [navigation audit](NAVIGATION-AUDIT.md).

The widget needs no Cloud project, API key, Play services dependency or billing configuration for AutoDrive. Google Maps itself has its own device and service requirements.

## Video on the dashboard

Choose **YouTube** or **IPTV** in the Media Hub.

- **YouTube:** On the Android device running AutoDrive, use YouTube's **Share → AutoDrive**, or choose **Ajouter une vidéo** in AutoDrive and paste a video link. The selected video loads directly on the dashboard, ready for a Play gesture. AutoDrive restores it on return to the dashboard and after a launcher restart. Use **Settings → Change dashboard YouTube video** to change it. Sharing on a separate phone does not send the video to the box. The video must allow embedding; this is the official embedded player, not the installed YouTube application's interface.
- **IPTV:** Choose **Watch an HTTPS HLS stream here** and paste a direct, authorized HLS channel URL from your provider. Tap **Load video**, then use the player's play control. If your subscription only works through an IPTV Smarters login or portal, use **Open IPTV Smarters** in the same menu.

Dashboard video starts when you press Play and closes when AutoDrive leaves the foreground. **Back** or the **Media** shortcut also closes it. At 800 × 480 / 160 dpi, the media tabs remain above the player; switching tabs closes the current player. On shorter windows the player can use the full media panel to preserve YouTube's minimum viewport. The playback controls work independently of GPS. After handoff, the external app controls its video.

Radio launches the Android app you assign. AutoDrive shows active session metadata and supported transport actions when the selected app provides them. The radio favorites menu appears when the app exposes explicitly labeled favorite actions.

## Build from source

Install JDK 17, Android SDK Platform 35, SDK Build Tools 35 and platform tools. Set `JAVA_HOME` and `ANDROID_HOME`, or let Android Studio create `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On macOS/Linux use `./gradlew` (run `chmod +x gradlew` if your checkout did not retain the executable bit). Build output is `app/build/outputs/apk/debug/app-debug.apk`.

The source separates the native dashboard views, activity and ViewModel, GPS filtering, app associations and Android intent handoffs, and MediaSession control. The preferred tab and app associations persist across process restarts. Live GPS and session state are reacquired from their respective providers when the launcher returns to the foreground.

[Current 800 × 480 screenshot](../deliverables/dashboard-1.2.0-800x480.png) · [Visual comparison](../previews/compare-1.2.0.html) · [Test results and hardware limits](TEST-RESULTS.md)
