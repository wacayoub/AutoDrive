"""Create updater metadata from the built APK rather than guessed version information."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


def build(apk, repository, output, aapt=None, radio_maroc=False):
    apk = Path(apk).resolve()
    output = Path(output)
    assert re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository)
    if aapt is None:
        sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
        aapt = sdk / "build-tools/35.0.0" / ("aapt.exe" if os.name == "nt" else "aapt")
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True, encoding="utf-8")
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    assert package and minimum, "APK metadata missing"
    name, code, version = package.groups()
    expected_package = "com.autodrive.radiomaroc" if radio_maroc else "com.autodrive.launcher"
    assert name == expected_package, "APK package does not match the release channel"
    assert re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version), "Publish a stable semantic version"
    tag = f"radio-maroc-v{version}" if radio_maroc else f"v{version}"
    asset = f"Radio-Maroc-Coran-{version}.apk" if radio_maroc else "AutoDrive.apk"
    manifest_name = "radio-maroc-update.json" if radio_maroc else "autodrive-update.json"
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    metadata = dict(schema=1, packageName=name, versionCode=int(code), versionName=version,
                    minSdk=int(minimum.group(1)), apkUrl=f"https://github.com/{repository}/releases/download/{tag}/{asset}",
                    sha256=digest, size=apk.stat().st_size)
    output.mkdir(parents=True, exist_ok=True)
    (output / manifest_name).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text(f"{digest}  {asset}\n", encoding="ascii")
    return metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--repository", default="wacayoub/AutoDrive")
    parser.add_argument("--output", required=True)
    parser.add_argument("--aapt")
    parser.add_argument("--radio-maroc", action="store_true", help="Prepare metadata for the standalone radio")
    arguments = parser.parse_args()
    metadata = build(arguments.apk, arguments.repository, arguments.output, arguments.aapt, arguments.radio_maroc)
    print(f"Prepared version {metadata['versionName']} ({metadata['versionCode']}), SHA256 {metadata['sha256']}")
