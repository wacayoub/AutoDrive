"""Publish AutoDrive using GitHub authentication from the environment or Git Credential Manager.

Never prints tokens, passwords or signing material. Repository creation and signing-secret upload
are explicit maintenance options. Normal publication creates a draft, uploads assets, then publishes.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from build_update_manifest import build


def credential(owner):
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        return token
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0", GCM_INTERACTIVE="never")
    result = subprocess.run(["git", "credential", "fill"], input=f"protocol=https\nhost=github.com\nusername={owner}\n\n",
                            capture_output=True, text=True, env=env, check=True, timeout=20)
    fields = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
    if not fields.get("password"):
        raise RuntimeError("No GitHub credential available")
    return fields["password"]


class GitHub:
    def __init__(self, repository):
        self.repository = repository
        self.token = credential(repository.split("/")[0])

    def call(self, path, method="GET", data=None, binary=None):
        url = path if path.startswith("https://uploads.github.com/") else "https://api.github.com" + path
        headers = {"Authorization": "Bearer " + self.token, "User-Agent": "AutoDrive-release-tools",
                   "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
        payload = json.dumps(data).encode() if data is not None else None
        if binary is not None:
            payload = binary.read_bytes()
            headers["Content-Type"] = "application/vnd.android.package-archive" if binary.suffix == ".apk" else "application/octet-stream"
        elif payload is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(url, data=payload, method=method, headers=headers)
        with urllib.request.urlopen(request, timeout=120) as response:
            body = response.read()
            return json.loads(body) if body else {}

    def setup(self, keystore=None):
        owner, name = self.repository.split("/")
        assert self.call("/user")["login"] == owner, "Authenticated account differs from repository owner"
        try:
            repo = self.call("/repos/" + self.repository)
            assert repo["visibility"] == "public", "Repository must be public for anonymous device updates"
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            repo = self.call("/user/repos", "POST", dict(name=name, private=False, auto_init=False,
                description="AutoDrive: native Android 13 launcher for Incarlink, Maps/YouTube web panels and signed online updates."))
            print("Created", repo["html_url"])
        if keystore:
            from nacl.public import PublicKey, SealedBox
            key = self.call(f"/repos/{self.repository}/actions/secrets/public-key")
            box = SealedBox(PublicKey(base64.b64decode(key["key"])))
            values = {"ANDROID_KEYSTORE_BASE64": base64.b64encode(Path(keystore).read_bytes()).decode(),
                "ANDROID_KEYSTORE_PASSWORD": os.environ["AUTODRIVE_KEYSTORE_PASSWORD"],
                "ANDROID_KEY_ALIAS": os.environ["AUTODRIVE_KEY_ALIAS"],
                "ANDROID_KEY_PASSWORD": os.environ["AUTODRIVE_KEY_PASSWORD"]}
            for name, value in values.items():
                encrypted = base64.b64encode(box.encrypt(value.encode())).decode()
                self.call(f"/repos/{self.repository}/actions/secrets/{name}", "PUT",
                          dict(encrypted_value=encrypted, key_id=key["key_id"]))
                print("Stored encrypted signing secret", name)
        return repo

    def publish(self, apk, output, commit, aapt=None):
        metadata = build(apk, self.repository, output, aapt)
        tag = "v" + metadata["versionName"]
        try:
            existing = self.call(f"/repos/{self.repository}/releases/tags/{tag}")
            assert existing.get("draft"), "A published version already exists; do not overwrite installed update assets"
            release = existing
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            release = self.call(f"/repos/{self.repository}/releases", "POST", dict(tag_name=tag,
                target_commitish=commit, name="AutoDrive " + metadata["versionName"], draft=True, prerelease=False,
                body="Mises à jour intégrées depuis Settings → Mises à jour. Téléchargement vérifié, installation Android avec confirmation. Réglages conservés.\n\nGoogle Maps s’ouvre dans son application Android installée. YouTube conserve son panneau web. Android Auto et l’intégration de Maps dans un widget ne sont pas fournis."))
        assets = {item["name"] for item in self.call(f"/repos/{self.repository}/releases/{release['id']}/assets")}
        upload = release["upload_url"].split("{")[0]
        files = [(Path(apk), "AutoDrive.apk"), (Path(output) / "autodrive-update.json", "autodrive-update.json"),
                 (Path(output) / "SHA256SUMS.txt", "SHA256SUMS.txt")]
        assert not assets, "Draft contains assets already; inspect it before resuming"
        for file, name in files:
            self.call(upload + "?name=" + urllib.parse.quote(name), "POST", binary=file)
            print("Uploaded", name)
        result = self.call(f"/repos/{self.repository}/releases/{release['id']}", "PATCH", dict(draft=False, make_latest="true"))
        print("Published", result["html_url"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default="wacayoub/AutoDrive")
    parser.add_argument("--setup", action="store_true")
    parser.add_argument("--signing-keystore")
    parser.add_argument("--apk")
    parser.add_argument("--output", default="build/publish")
    parser.add_argument("--commit", default="main")
    parser.add_argument("--aapt")
    args = parser.parse_args()
    try:
        github = GitHub(args.repository)
        if args.setup:
            github.setup(args.signing_keystore)
        if args.apk:
            github.publish(args.apk, args.output, args.commit, args.aapt)
    except Exception as error:
        print(f"GitHub operation failed: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(1)
