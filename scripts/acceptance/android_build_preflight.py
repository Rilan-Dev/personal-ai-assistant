#!/usr/bin/env python3
"""Non-mutating preflight for native Android build/emulator resource safety.

Use before running Gradle on a shared host. It never installs SDKs, starts
services, touches credentials or triggers GitHub Actions.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
from pathlib import Path

REQUIREMENTS = {
    "compile": {"memory_gib": 4.0, "disk_gib": 8.0, "kvm": False},
    "release": {"memory_gib": 4.0, "disk_gib": 8.0, "kvm": False},
    "emulator": {"memory_gib": 6.0, "disk_gib": 12.0, "kvm": True},
}
GIB = 1024 ** 3


def available_memory_gib():
    try:
        values = Path("/proc/meminfo").read_text()
        matched = re.search(r"^MemAvailable:\s+(\d+)\s+kB", values, re.MULTILINE)
        return round(int(matched.group(1)) * 1024 / GIB, 2) if matched else None
    except (OSError, ValueError):
        return None


def java_major():
    binary = shutil.which("java")
    if not binary:
        return None
    try:
        response = subprocess.run([binary, "-version"], capture_output=True,
                                  text=True, timeout=6, check=False)
        matched = re.search(r'version "[^"]*?(\d+)(?:\.|")',
                            response.stderr + response.stdout)
        return int(matched.group(1)) if matched else None
    except (OSError, subprocess.TimeoutExpired, ValueError):
        return None


def evaluate(mode, workspace):
    needed = REQUIREMENTS[mode]
    memory = available_memory_gib()
    free_disk = round(shutil.disk_usage(workspace).free / GIB, 2)
    sdk_dir = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    sdk_ok = bool(sdk_dir and
                  (Path(sdk_dir) / "platforms/android-36/android.jar").is_file())
    java_version = java_major()
    kvm_ok = os.path.exists("/dev/kvm") and os.access("/dev/kvm", os.R_OK | os.W_OK)
    problems = []
    if memory is None or memory < needed["memory_gib"]:
        problems.append("insufficient available RAM for a shared-host native build")
    if free_disk < needed["disk_gib"]:
        problems.append("insufficient free disk for Android dependencies and build outputs")
    if not sdk_ok:
        problems.append("Android SDK platform 36 not installed or ANDROID_HOME not set")
    if java_version is None or java_version < 17:
        problems.append("Java 17 or newer is not available on PATH")
    if needed["kvm"] and not kvm_ok:
        problems.append("hardware-accelerated Android emulator is unavailable (/dev/kvm)")
    return {
        "mode": mode,
        "safe_to_attempt": not problems,
        "available_memory_gib": memory,
        "free_disk_gib": free_disk,
        "java_major": java_version,
        "android_36_installed": sdk_ok,
        "kvm_available": kvm_ok,
        "minimum_memory_gib": needed["memory_gib"],
        "minimum_disk_gib": needed["disk_gib"],
        "issues": problems,
        "warning": "Advisory check only; a native build can still use more resources.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=sorted(REQUIREMENTS), default="compile")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    report = evaluate(args.mode, Path.cwd())
    if args.json:
        print(json.dumps(report, sort_keys=True))
    else:
        print("Android", args.mode, "preflight:",
              "READY" if report["safe_to_attempt"] else "NOT READY")
        for issue in report["issues"]:
            print(" -", issue)
        print("RAM available:", report["available_memory_gib"], "GiB;",
              "disk free:", report["free_disk_gib"], "GiB")
    return 0 if report["safe_to_attempt"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
