# Original INCAR LINK interface

Reviewed 5 November 2026 following the owner's confirmation that the HEYINCAR H-MAX seller illustration matches the original interface on the Incarlink Max X15-SLM927-72.

## Owner's version screen

The owner's subsequent photo confirms **X15-SLM927-72 A13 2025-08-21 03:03**, display **800 × 480**, country code **MA**, and band region **XE**. The protocol version is **2025:08:18:21:00:28**. This is newer than the public May 2025 family image inspected below.

For the requested Android Auto startup, the family settings image contains **Bootup APP** and the manufacturer documents **Wireless auto connect** for reconnecting a paired phone. The owner's version screen identifies the firmware but does not show whether Bootup APP is exposed or how it behaves on this build. Validate that setting on the box before treating launch at boot as tested. [Manufacturer auto-connect instructions](https://www.carlinkit.com/faq_detail/53.html)

## Source and scope

The official Carlinkit X15-SLM927 page publishes the Android 13 archive named `update_x15_slm927_A13_chelianyi-led-2025-05-29-17-05.zip`. Its archive directory and selected application blocks were examined remotely. Selected blocks were checked against the SHA-256 hashes in the Android update manifest. The extracted applications were inspected as files on the development PC. This is evidence from a family firmware image. Its match to the owner's installed version has not been established. [Manufacturer download page](https://www.carlinkit.com/download/slm927.html)

## Findings

| Application in the family image | Static result |
| --- | --- |
| `SdLauncher3.apk` | Package `com.android.launcher3`, version 1.2, shared system UID. Contains the original launcher and vendor resources. |
| `MainAiBox.apk` | Package `com.carsyso.main`, version `xq_1.0.35`, shared system UID. |
| `SudingSettings.apk` | Package `com.suding.settings`, version 1.0. Contains `DisplaySetActivity` and original navigation configuration labels. |
| `SpeedPlay_chelianyi.apk` | Package `com.suding.speedplay`, version `sd_475.0.19`. Its application label is **AutoKit**, and its launchable activity is `com.suding.speedplay.ui.MainActivity`. Its manifest also declares a boot receiver. |

For an AppBoot picker that does not list Carlink or INCAR LINK, check **AutoKit**. That is the label read from the family image's native connection app. The branded name shown by the custom home screen can differ from the application label. The installed August 2025 build may differ, so confirm the available entry before selecting it. A boot receiver declaration by itself does not prove that the foreground Android Auto screen opens at boot.

The owner subsequently reported that the connection app is also unavailable in the picker. A separate **Démarrer INCAR LINK** helper now provides a standard MAIN/LAUNCHER entry and attempts to open the existing native connection app through its public activity. Emulator fixture checks passed; native AppBoot visibility and the actual installed firmware remain unverified. See [installation](../incarlink-start/README.md) and [test results](INCARLINK-START-TESTS.md).

The settings resources include the labels **Desktop Style**, **Launcher style type**, **Style**, **Skin theme**, **Skin theme: enabled** and **Skin theme: disabled**. These support investigating native style selection. Resource presence alone does not establish that a particular option is visible or enabled on the owner's firmware, or that it imports arbitrary custom themes.

The inspected launcher exposes no `overlayable` resources to an Android runtime resource overlay. Android requires an overlay to be preinstalled or signed like its target when the target has no overlayable declaration. This prevents treating an ordinary separately signed theme APK as a verified custom reskin of this launcher. [Android overlay requirements](https://source.android.com/docs/core/runtime/rros#restrict-resources)

The launcher manifest also declares an exported vendor `MainSDKService`. Its name establishes neither the supported client protocol nor permission to host its map panel in AutoDrive. No manufacturer documentation for that integration was found.

Launcher APK SHA-256 is `90f0a66c4cdfbc796f7c48c30b12a8fc722b10feadacfac86b459df792a17992`.

## Applicable next step

Use the original box settings to inspect **Desktop Style / Launcher style type** and any **Skin theme** option actually offered by this firmware. Assess the built-in choices against the requested charcoal and blue appearance. A custom replacement of panel colors, shapes and layout remains unverified.

The manufacturer's instruction manual documents replacing a shortcut by holding the application icon for approximately two to three seconds. It does not establish support for arbitrary icon artwork or a complete custom theme. [Manufacturer instruction manual](https://device.report/m/58c7310a02df66db08fb2b94c6b59f0ae9e3c90a9cef0c92d4841f20b7dc1885_optim.pdf)
