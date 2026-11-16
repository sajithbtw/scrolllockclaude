# SweatLock
Blocks distracting apps until you finish a workout (pushups / squats) counted on-device with ML Kit.

Build: push to GitHub -> Actions -> download `sweatlock-debug-apk`.

Packages: `blocking` (service, lock screen, unlock timers), `workout` (pose + RepCounter),
`data` (Room + DataStore), `ui` (Compose, glass design), `di` (Hilt).

Android 13+ sideloaded APK: if the Accessibility toggle is greyed out, go to
App info -> menu -> "Allow restricted settings", then enable the service.
