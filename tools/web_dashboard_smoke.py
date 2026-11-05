"""Check offline/retry behavior of the web preview on a disposable Android 13 AVD."""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", required=True)
parser.add_argument("--capture", required=True, type=Path)
args = parser.parse_args()
adb = [args.adb, "-s", args.serial]


def run(*command):
    return subprocess.check_output([*adb, *command], timeout=35)


def shell(*command):
    return run("shell", *command).decode("utf-8", errors="replace").strip()


assert re.fullmatch(r"emulator-\d+", args.serial), "Disposable emulator required"
assert shell("getprop", "ro.kernel.qemu") == "1"
assert shell("getprop", "ro.build.version.sdk") == "33"
assert "800x480" in shell("wm", "size")
buttons = []
try:
    shell("svc", "wifi", "disable")
    shell("svc", "data", "disable")
    time.sleep(2)
    shell("am", "force-stop", "com.autodrive.launcher")
    shell("am", "start", "-W", "-n", "com.autodrive.launcher/.WebDashboardActivity")
    time.sleep(1)
    shell("uiautomator", "dump", "--compressed", "/sdcard/autodrive-web-test.xml")
    nodes = list(ET.fromstring(run("exec-out", "cat", "/sdcard/autodrive-web-test.xml")).iter("node"))
    assert sum(node.get("text") == "Page indisponible" for node in nodes) == 2
    buttons = [node for node in nodes if node.get("content-desc") == "Réessayer"]
    assert len(buttons) == 2
    assert any("Hors connexion" in node.get("text", "") for node in nodes)
    for label in ("Phone", "Media", "Apps", "Settings"):
        assert sum(node.get("content-desc") == label for node in nodes) == 1
    print("PASS both offline panels show errors and retry; four shortcuts remain available", flush=True)
finally:
    shell("svc", "wifi", "enable")
    shell("svc", "data", "enable")

time.sleep(2)
for button in buttons:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", button.get("bounds")))
    shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
time.sleep(7)
image = run("exec-out", "screencap", "-p")
assert image.startswith(b"\x89PNG\r\n\x1a\n")
args.capture.write_bytes(image)
print("Captured actual pages after retry; inspect the capture to confirm online provider rendering", flush=True)
