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

The optional hardware shortcut uses an Android Accessibility service only to
observe Volume Up key events. It cannot retrieve window content and does not
enable Touch Exploration, inspect UI elements, poll sensors, use overlays, or
access the network. Key events are returned to Android so normal volume
behavior continues.

Questions can be opened as an issue in this repository.
