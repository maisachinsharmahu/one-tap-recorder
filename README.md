# One Tap Recorder

Nothing OS-inspired Android screen recorder with a launcher widget and Quick Settings tile.

Current version: **1.0.2**. This release normalizes MediaCodec timestamps, adds responsive 1×1/2×1/2×2 widgets, and defaults to the best available profile starting at 4K/60 FPS with device and microphone audio.

## Install

Install `OneTapRecorder-release.apk` on an Android 10+ phone. Open it once, choose the capture and audio settings, then add **One Tap Recorder** from the launcher widget picker. A **Screen record** tile is also available in Quick Settings edit mode.

The home widget is responsive: use it as a compact **1×1** record button, a **2×1** status control, or expand it to **2×2** for recording details and a settings shortcut.

Recordings are saved to `Movies/OneTapRecorder` and appear in Gallery/Photos.

## Android security behavior

Android requires the system screen-capture confirmation for each MediaProjection session. The widget opens that confirmation immediately; tapping **Start now** begins capture. While recording, Android shows its privacy/status indicator and the app keeps a persistent notification with a Stop button.

Device-audio capture depends on the source app allowing playback capture. DRM-protected video and apps that opt out cannot be captured. If 4K H.264 is unsupported by the device codec, recording automatically falls back to 1080p.

## Build

```sh
flutter build apk --release
```
