# Contributing

Issues and pull requests are welcome.

## Development setup

1. Install Flutter, the Android SDK, and Java 17.
2. Fork and clone the repository.
3. Run `flutter pub get`.
4. Run `flutter analyze` before submitting a change.
5. Build with `flutter build apk --release`.

Changes to the capture engine should be tested on a physical Android device.
Please include the Android version, device model, selected recording profile,
and whether device/microphone audio were enabled when reporting recording
bugs. Do not attach private recordings to public issues.

Keep pull requests focused and explain any user-visible behavior change.
