#!/bin/zsh

set -u

installer_dir="${0:A:h}"
apk_path="$installer_dir/OneTap-v1.2.1-release.apk"
adb_path="$(command -v adb 2>/dev/null || true)"

if [[ -z "$adb_path" && -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
  adb_path="$HOME/Library/Android/sdk/platform-tools/adb"
fi

echo "One Tap Recorder — developer installer"
echo

if [[ -z "$adb_path" ]]; then
  echo "ADB was not found. Install Android SDK Platform Tools first."
  echo "https://developer.android.com/tools/releases/platform-tools"
  echo
  read "?Press Return to close..."
  exit 1
fi

if [[ ! -f "$apk_path" ]]; then
  echo "Place OneTap-v1.2.1-release.apk beside this installer."
  echo
  read "?Press Return to close..."
  exit 1
fi

echo "1. Unlock your phone."
echo "2. Enable Developer options > USB debugging."
echo "3. Connect USB and accept the debugging prompt on the phone."
echo

device_count="$($adb_path devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count+0 }')"
if [[ "$device_count" -ne 1 ]]; then
  echo "Expected exactly one authorized Android device; found $device_count."
  echo
  $adb_path devices
  echo
  read "?Connect/authorize the phone, then press Return to retry..."
fi

echo
echo "Installing the production-signed APK through Android's developer bridge..."
install_output="$($adb_path install --no-incremental -r "$apk_path" 2>&1)"
install_status=$?
echo "$install_output"

if [[ $install_status -eq 0 ]]; then
  echo
  echo "Installed successfully. Open One Tap Recorder on the phone."
elif [[ "$install_output" == *"INSTALL_FAILED_UPDATE_INCOMPATIBLE"* ]]; then
  echo
  echo "The installed copy uses the old debug certificate."
  echo "Uninstall One Tap Recorder from the phone once, then run this installer again."
  echo "Videos in Movies/OneTapRecorder are not removed with the app."
else
  echo
  echo "Installation failed. The exact Android error is printed above."
fi

echo
read "?Press Return to close..."
