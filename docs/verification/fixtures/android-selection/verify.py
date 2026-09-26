#!/usr/bin/env python3
"""Verify and preserve one committed synthetic fixture; never install or use ADB."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def run(args):
    return subprocess.check_output(args, stderr=subprocess.STDOUT)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.sdk is None:
        parser.error("--sdk or ANDROID_HOME is required")
    fixture = Path(__file__).resolve().parent
    os.chdir(fixture)
    if run(["git", "status", "--porcelain", "--untracked-files=all", "--", "."]).strip():
        parser.error("Commit the fixture before verifying a fixed candidate")
    prefix = run(["git", "rev-parse", "--show-prefix"]).decode().strip().rstrip("/")
    commit = run(["git", "rev-parse", "HEAD"]).decode().strip()
    tree = run(["git", "rev-parse", "HEAD:" + prefix]).decode().strip()
    tools = args.sdk.resolve() / "build-tools/36.0.0"
    artifacts = []
    for module, color in [("appRed", "red"), ("appBlue", "blue")]:
        for variant in ["debug", "release"]:
            apk = fixture / module / "build/outputs/apk" / variant / f"{module}-{variant}.apk"
            package = f"dev.example.issue150.{color}" + (".debug" if variant == "debug" else "")
            badging = run([str(tools / "aapt"), "dump", "badging", str(apk)]).decode()
            assert f"package: name='{package}'" in badging, package
            assert "sdkVersion:'26'" in badging, package
            assert "targetSdkVersion:'37'" in badging, package
            assert ("application-debuggable" in badging) == (variant == "debug"), package
            assert "uses-permission" not in badging, package
            with zipfile.ZipFile(apk) as archive:
                dex = b"".join(archive.read(n) for n in archive.namelist() if n.endswith(".dex"))
            for marker in [b"I150_TARGET", b"I150_EXPECTED_CRASH:", b"ActivityMainBinding"]:
                assert marker in dex, (package, marker)
            signature = run([str(tools / "apksigner"), "verify", "--verbose", str(apk)]).decode()
            artifacts.append((apk, {"module": module, "variant": variant, "package": package,
                                   "sha256": sha(apk), "badging": badging, "signature": signature}))
    # mkdir without exist_ok prevents replacing an accepted candidate.
    args.output.mkdir(parents=True)
    archive = args.output / "source.tar"
    archive.write_bytes(run(["git", "archive", "--format=tar", commit + ":" + prefix]))
    shutil.copy2(fixture / ".fixture-signing/debug.keystore", args.output / "fixture-debug.keystore")
    manifest = {"fixture": "issue150-selection-fixture-v2", "source_commit": commit,
                "source_tree": tree, "source_archive_sha256": sha(archive), "apks": [],
                "gui_status": "pending", "candidate_is_old_fixture": False}
    for apk, result in artifacts:
        shutil.copy2(apk, args.output / apk.name)
        manifest["apks"].append({k: v for k, v in result.items() if k not in ("badging", "signature")})
        (args.output / (apk.stem + ".verification.txt")).write_text(result["badging"] + result["signature"])
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
