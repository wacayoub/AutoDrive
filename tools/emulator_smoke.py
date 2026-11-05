"""Run the AutoDrive fallback smoke suite on a disposable 800x480 AOSP AVD."""
import argparse
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.autodrive.launcher"
ACTIVITY = f"{PACKAGE}/.MainActivity"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--screenshot", required=True, type=Path)
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial]

    def run(*command):
        return subprocess.check_output([*adb, *command], timeout=45)

    def shell(*command):
        return run("shell", *command).decode("utf-8", errors="replace").strip()

    def ui():
        for _ in range(3):
            result = shell("uiautomator", "dump", "/sdcard/autodrive-test.xml")
            if "dumped to" in result:
                return list(ET.fromstring(run("exec-out", "cat", "/sdcard/autodrive-test.xml")).iter("node"))
            time.sleep(.5)
        raise AssertionError("Could not read the emulator UI")

    def resumed(package):
        activities = shell("dumpsys", "activity", "activities")
        return any(package + "/" in line for line in activities.splitlines()
                   if "ResumedActivity" in line)

    def find(nodes, value, attribute="text"):
        result = [node for node in nodes if node.get(attribute, "").casefold() == value.casefold()]
        assert result, f"Missing {attribute} {value!r}"
        return result[0]

    def tap_node(node):
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node.get("bounds")))
        shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(.3)

    def tap(value, attribute="content-desc"):
        tap_node(find(ui(), value, attribute))

    def back():
        shell("input", "keyevent", "KEYCODE_BACK")
        time.sleep(.4)

    def launch():
        result = shell("am", "start", "-W", "-n", ACTIVITY)
        assert "Status: ok" in result, result
        return result

    def check_dashboard():
        nodes = ui()
        find(nodes, "Media Hub")
        return nodes

    def installed(package):
        return f"package:{package}" in shell("pm", "list", "packages", package).splitlines()

    assert re.fullmatch(r"emulator-\d+", args.serial), "Use a disposable emulator"
    assert shell("getprop", "ro.kernel.qemu") == "1", "Target is not a verified emulator"
    assert shell("getprop", "ro.build.version.sdk") == "33"
    assert "800x480" in shell("wm", "size")
    assert "160" in shell("wm", "density")
    for package in ("com.google.android.apps.maps", "com.google.android.gms", "com.google.android.youtube"):
        assert not installed(package), f"Use an AOSP image without {package}"

    original_home = shell("cmd", "package", "resolve-activity", "--brief", "-a",
                          "android.intent.action.MAIN", "-c", "android.intent.category.HOME").splitlines()[-1]
    assert "/" in original_home and PACKAGE not in original_home, original_home
    if installed(PACKAGE):
        assert "Success" in run("uninstall", PACKAGE).decode()
    assert "Success" in run("install", str(args.apk)).decode()
    print(launch(), flush=True)
    nodes = check_dashboard()
    for value in ("Precise location off", "--", "Google Maps app unavailable", "Ajouter une vidéo"):
        find(nodes, value)
    for value in ("Phone", "Media", "Apps", "Settings"):
        actions = [node for node in nodes if node.get("content-desc") == value and node.get("clickable") == "true"]
        assert len(actions) == 1, value
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", actions[0].get("bounds")))
        assert x2 - x1 >= 48 and y2 - y1 >= 48 and x2 <= 800 and y2 <= 480
    assert not any(node.get("content-desc") == "Home" or node.get("text") == "Hybrid Energy" for node in nodes)
    screenshot = run("exec-out", "screencap", "-p")
    assert screenshot[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack(">II", screenshot[16:24]) == (800, 480)
    args.screenshot.write_bytes(screenshot)
    print("PASS clean install, denied location, missing apps, 800x480 capture and four shortcut bounds", flush=True)

    assert not any(node.get(attribute) in {"Open Google Maps", "Explore", "Find place", "Directions"}
                   for node in nodes for attribute in ("text", "content-desc"))
    tap("Google Maps", "text")
    assert resumed(PACKAGE)
    find(check_dashboard(), "Google Maps app unavailable")
    print("PASS missing Maps panel stays on the dashboard when tapped", flush=True)

    tap("Radio")
    find(ui(), "Radio app not selected")
    tap("IPTV")
    find(ui(), "IPTV app not selected")
    shell("am", "force-stop", PACKAGE)
    launch()
    nodes = check_dashboard()
    assert find(nodes, "IPTV", "content-desc").get("selected") == "true"
    print("PASS Radio/IPTV missing, tab switching and persisted tab after force-stop", flush=True)

    tap("Settings")
    shell("input", "swipe", "380", "425", "380", "130", "380")
    find(ui(), "Google Play services · Unavailable")
    shell("input", "swipe", "380", "120", "380", "430", "380")
    tap("IPTV · Choose app")
    nodes = ui()
    option = next(node for node in nodes if node.get("text", "").endswith("com.android.deskclock"))
    tap_node(option)
    back()
    find(ui(), "Watch here ›")
    tap("Watch here ›", "text")
    tap("Watch an HTTPS HLS stream here", "text")
    find(ui(), "Watch IPTV here")
    back()
    tap("YouTube")
    tap("Ajouter une vidéo")
    find(ui(), "Watch YouTube here")
    back()
    tap("Settings")
    tap_node(next(node for node in ui() if node.get("content-desc", "").startswith("IPTV · ")))
    tap("Clear selection", "text")
    tap("Radio · Choose app")
    nodes = ui()
    option = next(node for node in nodes if node.get("text", "").endswith("com.android.deskclock"))
    tap_node(option)
    back()
    tap("Radio")
    find(ui(), "Media access is off")
    tap("Open Radio ›", "text")
    allow = next((node for node in ui() if node.get("text", "").casefold() == "allow"), None)
    if allow is not None:
        tap_node(allow)
    for _ in range(8):
        if resumed("com.android.deskclock"):
            break
        time.sleep(.5)
    else:
        raise AssertionError("Radio launch fixture did not reach the foreground")
    back()
    launch()
    print("PASS app association, external Radio fixture handoff and video setup with GPS permission absent", flush=True)

    shell("pm", "grant", PACKAGE, "android.permission.ACCESS_COARSE_LOCATION")
    shell("pm", "grant", PACKAGE, "android.permission.ACCESS_FINE_LOCATION")
    try:
        shell("cmd", "location", "set-location-enabled", "false")
        shell("svc", "wifi", "disable")
        shell("svc", "data", "disable")
        time.sleep(2)
        nodes = check_dashboard()
        find(nodes, "GPS disabled")
        find(nodes, "--")
        find(nodes, "Internet unavailable", "content-desc")
    finally:
        shell("cmd", "location", "set-location-enabled", "true")
        shell("svc", "wifi", "enable")
        shell("svc", "data", "enable")
    print("PASS GPS disabled and offline state", flush=True)

    tap("Apps")
    find(ui(), "Back", "content-desc")
    back()
    check_dashboard()
    try:
        result = shell("cmd", "package", "set-home-activity", "--user", "0", ACTIVITY)
        assert "Success" in result, result
        tap("Apps")
        shell("input", "keyevent", "KEYCODE_HOME")
        check_dashboard()
    finally:
        shell("cmd", "package", "set-home-activity", "--user", "0", original_home)
    print("PASS app drawer, Android Back and HOME with previous HOME restored", flush=True)


if __name__ == "__main__":
    main()
