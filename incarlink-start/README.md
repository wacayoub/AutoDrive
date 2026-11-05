# Démarrer INCAR LINK

This small Android 13 application provides a normal launcher entry that can be selected in the box's **AppBoot / Bootup APP** list. It opens the existing connection application using Android intents.

The target package `com.suding.speedplay` and public activity `com.suding.speedplay.ui.MainActivity` were found in the official X15-SLM927 family image. This application first uses the package's normal launcher intent, then its exported main activity if the package has no launcher entry. The owner's installed August 2025 build remains untested.

## Installation and setup

1. Install `Incarlink-Start-1.0.0.apk` on the box while parked.
2. Open **Démarrer INCAR LINK** manually. Verify that it opens the original connection application.
3. If the manual launch succeeds, select **Démarrer INCAR LINK** in **AppBoot / Bootup APP**.
4. Enable **Wireless Auto Connect** in the original connection app, with the phone already paired.
5. Test the next normal startup with the phone nearby and Bluetooth and Wi-Fi enabled.

If the target package is missing, disabled or protected, the helper shows an error and lets the user retry or close it. Actual Android Auto projection and firmware startup need verification on the Incarlink. The helper requests no permissions and runs no background service. The native AppBoot setting performs the startup.

To undo the setup, choose the previous AppBoot application or disable the native AppBoot selection, then uninstall **Démarrer INCAR LINK**.

## Build

Use JDK 17 and the SDK setup of the parent project.

```powershell
.\gradlew.bat :incarlink-start:assembleDebug :incarlink-start:lintDebug
```
