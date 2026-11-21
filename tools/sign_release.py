"""
Signs a release APK with MediMinder's key rotation (old key -> new key).

Android 9+ phones learn the new key from the lineage the first time they update; after that an
APK signed only with the old key is refused. Android 7-8 phones keep checking the old key's
v1/v2 signatures, which this also writes.

Keys never live in this repository. They are read from, in order:
  * environment variables (the GitHub release workflow):
        KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD           - the old key
        NEW_KEYSTORE_PATH, NEW_KEYSTORE_PASSWORD, NEW_KEY_ALIAS             - the new key
        SIGNING_LINEAGE_PATH                                                - old -> new lineage
  * keystore.properties in the project root (git-ignored), with
        storeFile, storePassword, keyAlias, keyPassword,
        newStoreFile, newStorePassword, newKeyAlias, lineageFile

Usage:  python tools/sign_release.py [path/to/MediMinder-<version>-release.apk]
"""

import glob
import os
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
RELEASE_DIR = ROOT / "app" / "build" / "outputs" / "apk" / "release"


def default_apk() -> pathlib.Path:
    """The newest MediMinder-<version>-release.apk Gradle made."""
    found = sorted(RELEASE_DIR.glob("MediMinder-*-release.apk"), key=lambda p: p.stat().st_mtime)
    return found[-1] if found else RELEASE_DIR / "app-release.apk"


def properties() -> dict:
    path = ROOT / "keystore.properties"
    values = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    return values


def setting(props: dict, prop: str, env: str) -> str:
    value = os.environ.get(env) or props.get(prop)
    if not value:
        sys.exit(f"Missing signing setting: {env} (or {prop} in keystore.properties)")
    return value


def apksigner() -> list:
    """The newest apksigner.jar in the Android SDK, run with the current Java."""
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") \
        or str(pathlib.Path.home() / "AppData" / "Local" / "Android" / "Sdk")
    jars = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "lib", "apksigner.jar")))
    if not jars:
        sys.exit(f"apksigner.jar not found under {sdk}/build-tools")
    java = shutil.which("java") or os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "java")
    return [java, "-jar", jars[-1]]


def main() -> None:
    apk = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else default_apk()
    if not apk.exists():
        sys.exit(f"No APK at {apk}")
    p = properties()
    tool = apksigner()
    subprocess.run(tool + [
        "sign",
        "--ks", setting(p, "storeFile", "KEYSTORE_PATH"),
        "--ks-key-alias", setting(p, "keyAlias", "KEY_ALIAS"),
        "--ks-pass", "pass:" + setting(p, "storePassword", "KEYSTORE_PASSWORD"),
        "--key-pass", "pass:" + setting(p, "keyPassword", "KEY_PASSWORD"),
        "--next-signer",
        "--ks", setting(p, "newStoreFile", "NEW_KEYSTORE_PATH"),
        "--ks-key-alias", setting(p, "newKeyAlias", "NEW_KEY_ALIAS"),
        "--ks-pass", "pass:" + setting(p, "newStorePassword", "NEW_KEYSTORE_PASSWORD"),
        "--lineage", setting(p, "lineageFile", "SIGNING_LINEAGE_PATH"),
        "--rotation-min-sdk-version", "28",
        str(apk),
    ], check=True)
    result = subprocess.run(tool + ["verify", "--print-certs", "--verbose", str(apk)], check=True, capture_output=True, text=True)
    lines = [l for l in result.stdout.splitlines()
             if l.startswith(("Verified using", "Signer #", "Number of signers")) and "SHA-1" not in l and "MD5" not in l]
    print("\n".join(lines))
    print(f"Signed with key rotation: {apk}")


if __name__ == "__main__":
    main()
