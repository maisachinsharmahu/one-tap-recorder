# Privacy

One Tap Recorder does not collect, store, sell, or transmit personal data.
It has no accounts, analytics, advertising SDKs, crash-reporting service, or
network permission.

Screen and audio data are processed locally while a recording is active. The
resulting MP4 is written directly to `Movies/OneTapRecorder` on the device.
The app does not upload recordings anywhere.

Android itself displays the screen-capture confirmation and recording privacy
indicator. Device-audio availability is controlled by Android and by the app
being recorded.

The optional two-finger gesture uses an Android Accessibility service because
Android exposes global multi-finger gesture events only through that API. The
service declares that it cannot retrieve window content, does not inspect UI
elements, and does not use sensor polling, overlays, networking, or wake locks.
It reacts only when Android reports the configured gesture. The feature is
disabled until the user explicitly enables it in Android Accessibility
settings and can be disabled there at any time.

Questions can be opened as an issue in this repository.
