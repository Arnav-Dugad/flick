<div align="center">

# Flick

**Control any Android app hands-free.**

A nod goes to the next reel. A smile likes it. A knock on the back of your phone goes back.
Free, open source, no ads, no accounts, no tracking. Everything runs on your phone.

[**⬇ Download the latest release**](https://github.com/Arnav-Dugad/flick/releases/latest)

</div>

> Screenshots coming soon.
> <!-- docs/screenshots/home.png · profile.png · calibration.png · onboarding.png -->

## What it does

- **37 gestures.** You can map each one to any action.
  - **Head**: look up/down, tilt left/right, turn left/right, nod
  - **Face**: long blink, double blink, smile, open mouth, raise eyebrows
  - **Hand**: open palm, fist, thumbs up/down, victory, point up, air swipes
  - **Phone**: tilt, shake, double/triple knock on the back
  - **Buttons**: volume up/down. A short press triggers the action; holding still changes the volume.
  - **Voice** (experimental, offline): "next", "back", "like", "pause", "stop"
- **Actions**: swipe, scroll, tap, double-tap to like, long-press, back, home, recent apps, notifications, quick settings, screenshot, lock screen, split screen, play/pause, next/previous track, volume.
- **Per-app profiles**: different gestures for Reels, YouTube, your browser or Kindle, with smart defaults. You can create your own from templates.
- **Live calibration**: see the signal, set the sensitivity, hold time and cooldown, and set your neutral pose.
- **Automatic updates** from this repository, checked against a SHA-256 checksum and Flick's signing certificate before installing.
- Material 3 with dynamic colour, dark theme and pure black, TalkBack labels, and support for large fonts.

## Install

Flick isn't on the Play Store. Install it from GitHub:

1. On your phone, open the [latest release](https://github.com/Arnav-Dugad/flick/releases/latest) and download `flick-x.y.z.apk`.
2. Open the file. If Android asks, allow your browser to **install unknown apps**.
3. **Android 13 and newer:** Android protects accessibility settings for apps installed from a file.
   1. Open Flick once.
   2. Go to **Settings → Apps → Flick**, tap **⋮** (top-right), then **Allow restricted settings**, and confirm.
   3. If you don't see that option, first try switching Flick on in Accessibility; Android shows the option after a blocked attempt.
4. Open Flick and follow the 5 short setup screens. They walk you through turning Flick on in **Accessibility** and trying your first gesture.

**Samsung, Xiaomi and other phones with aggressive battery savers:** if Flick stops working after a while, set its battery use to **Unrestricted**, and on Samsung remove it from *Sleeping apps*. Flick shows a reminder on its home screen when this happens. See [dontkillmyapp.com](https://dontkillmyapp.com) for your phone.

### Updates

Flick checks this repository once a day and when you open it. You'll see an update card with the release notes; tap **Update now**. Android may ask you once to let Flick install updates. You can turn off checks, limit them to Wi-Fi, or skip a version in **Settings → Updates**.

### Verify your download (optional)

Each release lists the APK's SHA-256. Flick's signing certificate SHA-256 is:

```
D0:66:22:AF:2E:42:B3:07:ED:BB:6B:76:91:00:2E:4C:45:6B:4A:D4:18:27:78:E8:0D:99:5E:31:2D:CF:42:2E
```

Check it with `apksigner verify --print-certs flick-x.y.z.apk`. Flick also shows it under **Settings → About**.

### Android developer verification (2026–2027)

Google has announced developer verification for Android apps. From **30 Sep 2026** it applies only to apps from app stores in Brazil, Indonesia, Singapore and Thailand. **It doesn't affect APKs downloaded from GitHub yet.** A global rollout is planned for 2027. This README will say what changes, if anything, before then.

## Privacy

- **Nothing leaves your phone.** Camera frames are analysed in memory by Google's on-device MediaPipe models and are never saved or uploaded. Voice keywords use Android's offline recogniser.
- **The camera only runs inside apps you've added to a profile that uses camera gestures**, and only while the screen is on. Android's green camera dot is always visible.
- **No accounts, ads, analytics or crash reporting.** The only network request is the update check against this repository's public GitHub Releases. You can turn it off.
- Flick uses Android's accessibility service to perform gestures and system actions, and to know which app is open so it can pick the right profile. It doesn't read your messages or screen content.

## Building from source

Requirements: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK with platform 37.

```sh
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test testDebugUnitTest  # unit tests
```

The debug build uses the package `io.github.arnavdugad.flick.debug`, so it can be installed next to the release. Maintainers: see [docs/RELEASING.md](docs/RELEASING.md). The design and research behind Flick are in [docs/PLAN.md](docs/PLAN.md) and [docs/RESEARCH.md](docs/RESEARCH.md).

## Status

| Milestone | What | State |
|---|---|---|
| M1 | Accessibility engine, every action | ✅ |
| M2 | Head, face, hand, phone, button and voice gestures with live calibration | ✅ |
| M3 | Per-app profiles with smart defaults | ✅ |
| M5 | In-app updates from GitHub Releases | ✅ |
| M6 | Signed release pipeline | ✅ |
| M4 | Auto-scroll, floating controller, macros, Quick Settings tile, mindful mode, edge glow, privacy badge | 🚧 next |

## License

[Apache License 2.0](LICENSE). The bundled MediaPipe models are © Google, Apache 2.0 (see [docs/MODELS.md](docs/MODELS.md)).
