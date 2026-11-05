# INCAR LINK startup helper 1.0.0

Built and checked 5 November 2026. This helper opens the existing native connection application; the firmware's AppBoot setting is responsible for invoking the helper at startup.

## Build

`./gradlew :incarlink-start:assembleDebug :incarlink-start:lintDebug` completed successfully with JDK 17. Lint: 0 errors, 3 warnings (SDK 35 rather than 36; target SDK 35; backup extraction rules). The app stores no user data and requests no permissions.

The debug APK signature verified successfully. Package: `com.autodrive.incarlinkstart`, version 1.0.0 (1), minimum Android 13/API 33. SHA-256:

`894714bd4e649b23ef1eda9d70a6adbab202bde0db8a28f8a5cf45bb807b1991`

## Runtime checks

AOSP Android 13/API 33 emulator at 800 × 480. The target package was represented by small synthetic fixtures, not the proprietary manufacturer's application.

| Scenario | Result |
| --- | --- |
| Target app absent | PASS: visible missing-app message, no crash |
| Standard Android app list | PASS: helper appears as a launchable application |
| Target exposes MAIN/LAUNCHER | PASS: target fixture opens and helper finishes |
| Target has only an exported explicit activity | PASS: target fixture opens despite no launcher entry |
| Target activity requires a privileged permission | PASS: refusal message, no crash |
| Target app disabled | PASS: visible error, no crash |

The public component was identified in the manufacturer's May 2025 X15-SLM927 firmware image. The owner's August 2025 native app, AppBoot filtering, actual boot timing, Wireless Auto Connect, and phone Android Auto projection remain **untested on hardware**. A successful fixture handoff does not prove these functions. No software was installed on the owner's Incarlink.

## Manual validation

Follow [the helper installation guide](../incarlink-start/README.md). Open the helper manually before assigning it to AppBoot. If the native connection app does not open, leave the existing boot setting in place and record the error shown. No firmware modifications are needed.
