# Flick

**Control any app hands-free.** Flick turns a nod, a smile, a wave or a knock into a swipe, a tap or a like, in the apps you choose. Free, open source, no ads, no accounts, no tracking. Everything runs on your phone.

> 🚧 **Work in progress.** Milestone 1 (core engine + "wave to scroll") is done. See [docs/PLAN.md](docs/PLAN.md) for the roadmap and [docs/RESEARCH.md](docs/RESEARCH.md) for the research behind it.

## Status

| Milestone | What | State |
|---|---|---|
| M1 | Accessibility engine, every action, proximity "wave to scroll" | ✅ |
| M2 | Head, face, hand, phone, volume-key and voice triggers + calibration | ⏳ |
| M3 | Per-app profiles with smart defaults | ⏳ |
| M4 | Auto-scroll, floating controller, macros, QS tile, mindful mode, privacy badge | ⏳ |
| M5 | In-app updates from GitHub Releases | ⏳ |
| M6 | Signed release pipeline | ⏳ |

## Privacy

- Camera frames (from M2 on) are processed in memory on your phone and never saved or uploaded.
- No analytics, no ads, no accounts.
- The only network request is an optional check for new releases on this GitHub repository.
- Flick uses Android's accessibility service only to perform gestures and system actions, and to know which app is open. It does not read your screen content or messages.

## Building from source

Requirements: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK with platform 37.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test testDebugUnitTest
```

Install the debug build with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. It uses the package `io.github.arnavdugad.flick.debug`, so it can sit next to a release build.

## License

[Apache License 2.0](LICENSE)
