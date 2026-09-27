# One Tap Recorder

**Start a high-quality screen recording straight from your home screen.**

Android already has a capable screen recorder, but getting to it means
opening Quick Settings, finding the tile, choosing the audio source, and
confirming the capture every time. One Tap Recorder removes the digging:
put its widget wherever your thumb already is, tap it, accept Android's
capture confirmation, and recording starts with device audio and your
microphone already configured.

The widget adapts to the space you give it — a tiny record button when you
want it out of the way, a status control when you have a row to spare, or a
full recording panel when you expand it.

It's a small Android app with a Nothing OS-inspired interface. No accounts,
no ads, no analytics, no network access — see [PRIVACY.md](PRIVACY.md).

## What it does

- **Records from a home-screen widget.** No need to open the app or hunt
  through the notification shade first.
- **Three responsive widget layouts.** Use it as a compact `1×1` record
  button, a `2×1` recording-status control, or a detailed `2×2` panel. Drag
  the widget handles in your launcher and it changes layout automatically.
- **Captures device audio and microphone together.** Game/music/app audio
  and your voice are mixed into one AAC track in real time.
- **Starts at the best profile.** The default is 4K at 60 FPS. If the
  phone's hardware encoder cannot sustain that profile, it falls back in
  order through 4K/30, 1080p/60, and 1080p/30 instead of failing the whole
  recording.
- **Stops from wherever you are.** Tap the widget again or use the persistent
  recording notification's **Stop** action; the MP4 is finalized and appears
  in your gallery.
- **Includes a Quick Settings tile.** If the widget is not convenient for a
  particular layout, the same recorder is available from the system shade.
- **Shows the real system recording state.** Android's status/privacy
  indicator and an ongoing notification remain visible for the full capture.
- **Stays local.** Recordings never leave the phone.

## Install

### Option A — Download (recommended)

Download the latest APK from the [Releases](../../releases) page, open it on
your Android phone, and allow installation from your browser or file manager
when Android asks.

Open One Tap Recorder once to grant microphone and notification permissions.
Then long-press an empty area of the home screen, choose **Widgets**, find
**One Tap Recorder**, and drag it onto the home screen. Resize it to switch
between the `1×1`, `2×1`, and `2×2` layouts.

The app targets Android 10 and newer for device-audio capture. It can install
on Android 7+, but internal audio is only available where Android's playback
capture API exists.

### Option B — Build from source

Requires the Flutter SDK, Android SDK, and Java 17.

```bash
git clone https://github.com/maisachinsharmahu/one-tap-recorder.git
cd one-tap-recorder
flutter pub get
flutter build apk --release
```

The APK will be written to
`build/app/outputs/flutter-apk/app-release.apk`.

## Getting started

1. Open the app once and keep **Device audio** and **Microphone** enabled.
2. Add the home-screen widget and resize it to whichever layout fits.
3. Tap the red record control.
4. Android displays its official screen-capture dialog. Tap **Start now**.
5. Record normally. Tap the widget or notification action to stop and save.

Recordings are stored in `Movies/OneTapRecorder` and are indexed by Android,
so they show up in Gallery and Google Photos without a manual import.

## Why Android still shows a confirmation

Android deliberately requires the system MediaProjection confirmation for a
new capture session. A normal third-party app cannot silently approve that
dialog, reuse an expired capture token, or trigger an OEM's private recorder
without privileged system access.

One Tap Recorder takes you directly to that confirmation and starts capture
as soon as it is approved. Removing or faking it would require bypassing an
Android security boundary, so the app does not pretend to do that.

## Device audio + microphone

The recorder opens two independent audio inputs: Android's playback-capture
stream and the physical microphone. Both PCM streams are mixed sample by
sample, encoded as AAC, and muxed with the H.264 video track into a standard
MP4 file.

Android lets individual apps opt out of playback capture, and DRM-protected
video is never exposed to third-party recorders. Those sources may be silent
in the device-audio side of a recording; this is enforced by Android, not an
app setting One Tap Recorder can override. Your microphone track continues
to work normally.

## About 4K

The best profile requests a 3840-pixel long edge from the hardware H.264
encoder. A phone with a lower-resolution display cannot produce visual detail
that is not present on its panel — "4K" describes the encoded output canvas,
not invented screen detail. The automatic fallback chain keeps recording
reliable on devices whose encoder rejects 4K or 60 FPS.

## Permissions

| Permission | Why it is used |
| --- | --- |
| Microphone | Adds your voice to the recording |
| Screen capture | Granted through Android's system MediaProjection dialog |
| Notifications | Shows the active recording and Stop action |
| Foreground service | Keeps capture alive when the app UI is closed |

There is intentionally no Internet permission.

## Project structure

- `lib/main.dart` — Nothing OS-inspired settings and recording interface.
- `RecordingService.kt` — H.264/AAC encoders, audio mixer, MP4 muxer, and
  MediaStore output.
- `RecorderWidgetProvider.kt` — responsive widget selection and state.
- `RecorderTileService.kt` — Quick Settings integration.
- `ConsentActivity.kt` — Android permission and MediaProjection handoff.

## Uninstall

Uninstall **One Tap Recorder** from Android Settings or the launcher. Existing
videos in `Movies/OneTapRecorder` are normal media files and remain on the
device; delete them separately from Gallery or your file manager if desired.

## Contributing

Issues and pull requests are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE) — use, modify, and distribute it freely.

Built by [Sachin Sharma](https://sachinsharma.dev).
