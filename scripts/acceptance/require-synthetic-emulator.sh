#!/usr/bin/env bash
# Source this BEFORE any destructive Android QA operation. A comment saying
# "emulator only" is not an enforcement boundary: pm clear and adb uninstall
# can permanently erase the owner's Nexa settings on a USB-connected phone.
#
# This file never installs, clears, uninstalls, grants or touches app state.
# It pins ANDROID_SERIAL for every subsequent adb command in its caller.
nexa_require_synthetic_emulator() {
  local serial state qemu
  local -a devices=()
  if ! command -v adb >/dev/null 2>&1; then
    echo "REFUSED: adb is not installed; emulator-only QA was not started." >&2
    return 2
  fi
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    serial="$ANDROID_SERIAL"
  else
    mapfile -t devices < <(adb devices | awk 'NR>1 && $2=="device"{print $1}')
    if [[ "${#devices[@]}" -ne 1 ]]; then
      echo "REFUSED: exactly one authorized emulator is required; specify ANDROID_SERIAL when other devices are attached." >&2
      return 2
    fi
    serial="${devices[0]}"
  fi
  # Android's standard emulators use emulator-5554, emulator-5556, etc.
  # A physical USB or Wi-Fi serial is NEVER a substitute.
  if [[ ! "$serial" =~ ^emulator-[0-9]{4,}$ ]]; then
    echo "REFUSED: target is not an Android emulator serial. Never clear an owner's phone." >&2
    return 2
  fi
  state="$(adb -s "$serial" get-state 2>/dev/null | tr -d '\r\n')" || return 2
  if [[ "$state" != device ]]; then
    echo "REFUSED: selected Android emulator is not authorized and online." >&2
    return 2
  fi
  qemu="$(adb -s "$serial" shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r\n[:space:]')" || return 2
  if [[ "$qemu" != 1 ]]; then
    echo "REFUSED: Android does not confirm the QEMU emulator property." >&2
    return 2
  fi
  export ANDROID_SERIAL="$serial"
  echo "Verified disposable Android emulator: $ANDROID_SERIAL"
}
nexa_require_synthetic_emulator
