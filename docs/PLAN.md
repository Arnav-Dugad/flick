# Flick — Plan

_Built on [RESEARCH.md](RESEARCH.md). Status: awaiting "go"._

## 1. Key decisions

| Decision | Choice | Why |
|---|---|---|
| Language/UI | Kotlin 2.4 + Jetpack Compose + Material 3 | As required |
| Material 3 version | **material3 1.4.0 stable** + a custom spring-based motion scheme. Switch to `MaterialExpressiveTheme`/`MotionScheme` when **1.5.0 goes stable**; it is RC as of 2026-11-04. | The brief says to use Expressive only if it is stable, and it isn't yet. The upgrade is isolated to `ui/theme`. |
| Navigation | Navigation 3 (1.2.x stable) | Current recommended Compose navigation. Back stack is plain state, which makes shared-element transitions easy. |
| DI | **Manual DI** (`AppContainer` created in `FlickApp`) | (1) No verified KSP release for Kotlin 2.4, and the latest KSP has a regression, so Hilt would pin us to an older Kotlin. (2) The object graph is small: about 15 singletons. (3) The AccessibilityService, TileService and Workers all reach the container through `applicationContext` with no annotation magic. (4) Faster builds, smaller APK, no generated code to debug. |
| Persistence | DataStore: Preferences for settings, a JSON-serialised store (kotlinx.serialization) for profiles and macros | No Room needed. The data is a small document. |
| Networking | `HttpURLConnection` + kotlinx.serialization | It's one endpoint plus a file download, so OkHttp/Retrofit would only add size. |
| Vision | MediaPipe tasks-vision 1.2.x: **Face Landmarker** (blendshapes + transformation matrix) and **Gesture Recognizer**, whose results also include hand landmarks, so a separate Hand Landmarker model isn't needed | Two models instead of three gives a smaller APK and less RAM |
| Camera host | CameraX inside the **AccessibilityService process**, bound to a service-owned `LifecycleOwner` (Gameface pattern) | See RESEARCH §3. Proven by a spike at the start of M2, with a fallback camera FGS. |
| Build | AGP 9.5.x (built-in Kotlin), Gradle 9.9, version catalog, compileSdk 37 / targetSdk 37, minSdk 26 | Latest stable toolchain |
| ABIs | Release APK ships `arm64-v8a` + `armeabi-v7a`; debug also includes x86_64 for emulators | MediaPipe native libraries dominate APK size |
| License | **Apache-2.0** (recommended) | Same licence as MediaPipe and Gameface, and it includes an explicit patent grant |

## 2. Architecture

```
┌──────────────────────── :app (Android) ────────────────────────┐
│  UI (Compose, Nav3)  ◄── StateFlows ──  FlickEngine (singleton) │
│                                           │   ▲                 │
│  FlickAccessibilityService ── attaches ───┘   │ TriggerEvents   │
│   ├─ ForegroundAppMonitor ─► ProfileResolver ─► active Profile  │
│   ├─ ActionExecutor (gestures, globals, node scroll, media)     │
│   ├─ OverlayManager (glow, bubble, preview, privacy badge)      │
│   └─ KeyEventRouter (volume keys)                               │
│  TriggerSources: Proximity · Motion(tilt/shake/knock) · Keys ·  │
│                  Camera(Face, Hand) · Voice                     │
│  Updater: GitHubClient · ApkDownloader · ApkVerifier ·          │
│           InstallSession · UpdateWorker                         │
└───────────────────────────── uses ──────────────────────────────┘
┌──────────────────────── :core (pure Kotlin/JVM) ───────────────┐
│ Detectors (threshold/hysteresis/hold/cooldown/neutral gate),    │
│ head-pose math, blink/double-blink/nod/shake/knock/wave/        │
│ air-swipe state machines, Action/Trigger/Profile models,        │
│ default profiles, SemVer/versionCode compare, release parsing   │
└─────────────────────────────────────────────────────────────────┘
```

**Data flow:**
1. A raw sample (sensor value, blendshape score or key event) goes to a **Detector** in `:core`, which is pure and deterministic, with injected time.
2. The detector emits a `TriggerEvent(type, timestamp)`.
3. `FlickEngine` looks up the active profile's binding and asks `ActionExecutor` to run it.
4. `FeedbackController` shows the edge glow and plays a haptic.
5. The event is also recorded for mindful-mode stats.

**Engine runtime state machine:** `Off → Idle (service connected, no profile app) → Armed (profile app in foreground) → Paused (screen off / kill switch / thermal / camera busy)`.
- Each `TriggerSource` is started and stopped by the engine **only** when the active profile maps one of its triggers. That rule is what keeps the camera off outside profiled apps.
- **Revocation-safe:** every source checks its permission on start. `SecurityException`/`CameraAccessException` → the source reports `Unavailable(reason)` → the engine keeps the other sources running and the UI shows a repair card. Nothing is allowed to crash the service.

### Module and package layout
```
settings.gradle.kts, gradle/libs.versions.toml
:core   (kotlin-jvm)  dev.flick.core
  model/        Action, TriggerType, Profile, Binding, TriggerTuning, Macro
  detect/       Detector, ThresholdDetector, SequenceDetector (double-blink, knock),
                NodDetector, ShakeDetector, KnockDetector, WaveDetector, AirSwipeDetector,
                HeadPose (matrix→pitch/yaw/roll), Calibration (neutral baseline)
  profiles/     DefaultProfiles, ProfileTemplates
  update/       AppVersion (compare), ReleaseInfo, ReleaseParser
  util/         Clock
:app    (android-application)  dev.flick
  FlickApp, di/AppContainer
  service/      FlickAccessibilityService, ServiceLifecycleOwner, ForegroundAppMonitor,
                KeyEventRouter, ServiceHealth
  engine/       FlickEngine, EngineState, ProfileResolver
  action/       ActionExecutor, GestureFactory, GestureQueue, GlobalActions, NodeScroller,
                MediaActions
  trigger/      TriggerSource + proximity/, motion/, keys/, camera/, voice/
  vision/       CameraController, FrameThrottler, FaceTracker, HandTracker, VisionModels
  overlay/      OverlayManager, EdgeGlow, FloatingBubble, CameraPreviewBubble, PrivacyBadge,
                MacroRecorderOverlay, NudgeCard  (Compose in TYPE_ACCESSIBILITY_OVERLAY)
  feature/      autoscroll/, macro/, mindful/
  feedback/     FeedbackController (haptics + glow)
  data/         SettingsRepository, ProfileRepository, MacroRepository, StatsRepository
  update/       GitHubReleaseClient, UpdateRepository, ApkDownloader, ApkVerifier,
                PackageInstallerSession, InstallResultReceiver, UpdateWorker
  system/       FlickTileService, StatusNotification, PermissionState, OemTips
  ui/           theme/, components/, illustrations/ (animated vector gestures),
                navigation/, screens/…
```

## 3. Feature list

**MVP (v1.0 = milestones M1–M6, as specified)**
- **Engine:** AccessibilityService, Action layer (swipe in 4 directions, node scroll, tap, double-tap, long-press, every global action the device supports, media and volume), serialised gesture queue
- **Triggers:**
  - Proximity wave
  - Head tilt (4 directions), nod, turn
  - Long blink, double blink, mouth open, smile, eyebrow raise
  - Open palm, fist, thumbs up/down, victory, pointing up, air swipe (left/right/up/down)
  - Phone tilt, shake, double/triple back-knock
  - Volume keys with a pass-through toggle
  - Voice keywords (**experimental**: on-device recogniser, API 31+, hidden when unavailable)
- **Tuning per trigger:** sensitivity, hold time and cooldown, plus a live calibration screen with a neutral-pose baseline
- **Per-app profiles:**
  - Auto-detection of the foreground app. Smart defaults for Instagram, YouTube, TikTok and other short-video apps, Chrome and other browsers, Kindle, Play Books and PDF readers.
  - Templates: Reels, Reading, Cooking, Book.
  - Create, edit, duplicate and delete profiles.
- **Extras:**
  - Auto-scroll (Reading/Teleprompter/Cooking presets, pause on face-away)
  - Floating bubble
  - Gesture macros
  - QS tile + notification controls
  - Mindful mode
  - Media controls
  - Screenshot, lock, shade and recents actions
  - Edge glow + haptic feedback, optional preview bubble
  - Privacy badge + kill switch
- **Updates:** WorkManager daily check + check on open, update sheet, SHA-256 + signing-cert verification, PackageInstaller install, auto-check / Wi-Fi only / skip version settings
- **Release:** GitHub Actions signed builds on `v*` tags, RELEASING.md, R8
- **Quality:**
  - Onboarding (5 screens), Home, all settings screens, light/dark/AMOLED with dynamic colour
  - TalkBack support, 200% font scaling
  - README

**v1.1**
- Upgrade to Material 3 Expressive (1.5.0 stable)
- Per-profile trigger-tuning overrides; v1.0 uses global tuning per trigger
- Profile import/export (JSON share)
- Context-aware actions (for example, smile = like only when a Reels view is detected via node IDs)
- Voice keywords promoted from experimental once proven on real devices
- Translations

**Later**
- Combo triggers (e.g., blink + tilt)
- Head-controlled cursor (Gameface-style)
- Schedule-based profiles
- Wear OS / Bluetooth remote triggers
- Per-ABI split APKs if size becomes an issue

## 4. Smart default profiles

| Profile | Apps (package) | Default bindings |
|---|---|---|
| **Reels & Shorts** | Instagram `com.instagram.android`, TikTok `com.zhiliaoapp.musically` / `com.ss.android.ugc.trill`, Facebook `com.facebook.katana`, Snapchat `com.snapchat.android` | Head down → next (swipe up). Head up → previous. Smile → like (double-tap centre). Long blink → pause (tap centre). Proximity wave → next. |
| **YouTube** | `com.google.android.youtube` | Head down/up → next/previous Short. Mouth open → play/pause (tap centre). Eyebrow raise → back. Smile is unbound (double-tap seeks in normal videos). |
| **Browser / Reading** | Chrome, Firefox, Samsung Internet, Brave, Edge | Head down/up → scroll 60% of the page. Eyebrow raise → back. Wave → auto-scroll on/off. |
| **Books & PDFs** | Kindle `com.amazon.kindle`, Play Books, Adobe Reader, Moon+ Reader, ReadEra, Librera, Google Drive | Phone tilt right/left → next/previous page (tap the right/left edge). Head turn right/left → the same. |
| **Cooking** _(template)_ | Assigned by the user, typically to Chrome for recipe sites | Proximity wave → scroll down. Open palm → scroll down. Fist → scroll up. Victory → auto-scroll toggle. No face triggers. |

Only the Reels and Books profiles are enabled out of the box. The others show as one-tap suggestions when Flick detects the app is installed.

## 5. Screens

1. **Onboarding** (5 screens, skippable where safe)
   1. *What Flick does*: an animated head-nod scrolling a phone mock-up
   2. *Privacy promise*: on-device only, no accounts, one optional network call, open source
   3. *Unlock restricted settings*: Android 13+ only. An animated step-by-step Settings mock-up and a deep link to App info. Auto-advances on return when it detects the change.
   4. *Turn on Flick*: prominent disclosure, accessibility deep link, camera + notification permissions, all detected automatically
   5. *Try your first gesture*: pick a gesture and perform it live, with glow, haptic and confetti-free celebration
2. **Home**
   - A big master toggle with a spring morph
   - Status line: Ready / Watching Instagram / Paused: camera busy
   - **Active profile card** (shared element → editor)
   - **Try a gesture** sandbox
   - Repair cards: service killed, permission revoked, battery optimisation
   - Quick tiles: Auto-scroll, Mindful, Macros
3. **Profiles**: list of app-icon cards with enable switches, suggested profiles, an FAB to create one, and an empty state with an illustration
4. **Profile editor**: apps picker, binding rows (trigger chip → action chip), templates, delete/duplicate
5. **Trigger picker** and **Action picker** (bottom sheets, grouped and searchable, each with an animated gesture illustration)
6. **Trigger detail / calibration**
   - Live signal meter with threshold and release lines
   - Sliders for sensitivity, hold and cooldown
   - "Set neutral" button and a test counter
   - Camera preview with landmark dots for camera triggers
   - "Sensor not available" state
7. **Auto-scroll**: preset cards, speed slider, a live preview of the scroll speed, pause-on-face-away
8. **Macros**: list, record flow (overlay countdown → record → name → bind), playback test, empty state
9. **Mindful mode**: counter per day, nudge threshold, auto-pause timer, gentle copy
10. **Settings**
    - Appearance: theme, AMOLED, dynamic colour
    - Feedback: glow, haptics, preview
    - Privacy: badge, kill switch, network/update check
    - Volume keys
    - Performance: GPU/CPU, battery-saver behaviour
    - Updates
    - Diagnostics, About/licences
11. **Update sheet**: version, size, rendered release notes, then Download → progress → Install. Error states: offline, rate-limited (with retry time), checksum or signature mismatch, storage.
12. **Diagnostics / Health**: service connected vs enabled, battery optimisation, OEM-specific tips (dontkillmyapp link), camera and sensor availability, model delegate in use, recent trigger log (in memory only)
13. **About & Privacy**: privacy statement, licence, open-source notices, signing-certificate fingerprint

**Motion:**
- Spring specs everywhere (`spring(dampingRatio = 0.8f, stiffness = Medium)`)
- Shared elements: profile card → editor, trigger chip → detail
- Animated vector gesture illustrations drawn in Compose `Canvas`: head, hand and phone glyphs with path animations
- Respects the system "remove animations" setting

## 6. Update mechanism

- **Release assets:** `flick-<ver>.apk`, `flick-<ver>.apk.sha256`, `version.json` (`{versionCode, versionName, apk, sha256, minSdk}`).
- **When it checks:**
  - On app open, throttled to once per 6 h
  - A `PeriodicWorkRequest` every 24 h, with the `UNMETERED` constraint when Wi-Fi only is on
  - Never if the user turns update checks off
- **Request:** `GET /repos/<owner>/<repo>/releases/latest` with ETag caching. On 403/429, back off until `x-ratelimit-reset`.
- **Version comparison:**
  - Read `versionCode` from `version.json`, falling back to the SemVer tag.
  - Newer and not skipped → show the sheet in the app, or a notification from the worker.
- **Install:**
  1. Download to `cacheDir/updates/`.
  2. Verify the SHA-256.
  3. Verify that the APK's signing certificate equals Flick's own.
  4. Open a PackageInstaller session with `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` on API 31+.
  5. Commit with a **mutable** PendingIntent and handle every status: `PENDING_USER_ACTION` launches the confirm UI.
- **"Install unknown apps" permission:** checked first, with a deep-link explainer.

## 7. Release pipeline

- **Trigger:** `.github/workflows/release.yml` runs on a `v*` tag push.
- **Steps:**
  1. Checkout and set up JDK 21 (Temurin) and the Android SDK.
  2. Decode the keystore from the `KEYSTORE_BASE64` secret into a temp file.
  3. Pass `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` as environment variables.
  4. Derive `versionName` from the tag. `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH`.
  5. Run `./gradlew testDebugUnitTest :app:assembleRelease`.
  6. Verify the signature with `apksigner verify --print-certs`.
  7. Generate the `.sha256` and `version.json`.
  8. Publish with `gh release create` using notes from `CHANGELOG.md` (falling back to `--generate-notes`), attaching the 3 assets.
- **Safety:**
  - A separate `ci.yml` builds debug + tests on push/PR.
  - Secrets are never echoed.
  - Signing config is only read from environment variables, so local release builds without them fail clearly.
- **docs/RELEASING.md:** one-time `keytool -genkeypair` instructions, base64 → secrets, backup strategy (2 offline copies + password manager), **a loud warning that a lost keystore means users can never update again**, and how to cut a release.

## 8. Testing

- **Unit tests in `:core`** (JVM, fast, deterministic with a fake clock) cover:
  - Every detector: threshold, hysteresis, hold, cooldown, debounce, neutral gate, sequences
  - Head-pose math
  - Version comparison and release parsing
  - Profile serialisation round-trip
  - Default-profile sanity
- **App-level unit tests** cover GestureFactory geometry and ProfileResolver filtering (systemui/IME/self).
- **Each milestone:** `assembleDebug` + unit tests green, warnings fixed, a commit, and a phone test checklist for you.

## 9. Milestone notes

- **M1:** Project scaffolding, theme, a minimal home and onboarding shell (enough to enable the service), the service, the Action layer, and the proximity-wave trigger end to end.
- **M2:**
  - **Starts with a camera spike on your phone.** If the camera runs from the service while Instagram is in front, we use the plan as written. If not, we switch to the fallback camera FGS before building the rest.
  - Then every trigger, the tuning UI and calibration.
- **M3:** Profiles plus the camera-gating rule.
- **M4:** Extras.
- **M5:** Updater. Testable before M6 against a temporary test release.
- **M6:** CI release, RELEASING.md, R8 rules for MediaPipe, size audit.
- **Final:** Polish pass over every screen (empty, loading and error states, 200% font, TalkBack), README.

## 10. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Camera blocked from the accessibility service on some OS/OEM builds | Low–Med | High | M2 spike first. Fallback camera FGS started from an activity or notification. Non-camera triggers keep working. |
| OEM kills the service | High on Xiaomi/Samsung/Oppo | High | Health check, repair card, battery-exemption prompt, OEM tips, QS tile restart |
| False positives from face/hand triggers | Medium | Medium | Hysteresis + hold + neutral gate + calibration. Conservative defaults. |
| Virtual or absent proximity sensor | Medium on 2024+ flagships | Low | Detect on the calibration screen and suggest wave → hand gesture |
| Third-party app UI changes (Reels layout) | Medium | Low | Coordinate-relative gestures, editable profiles |
| APK size (MediaPipe natives + 2 models) | Certain | Low | 2 ABIs, R8, 2 models instead of 3; measured in M6 (expect ~30–45 MB) |
| On-device voice recognition availability | High variance | Low | Experimental flag, hidden when unsupported |
| Developer verification in 2027 | Certain (announced) | Medium | README guidance. Decide on registration before the 2027 rollout. |
| Keystore loss | Low | Catastrophic | RELEASING.md backup procedure |
| GitHub rate limit (60/h per IP) | Low | Low | ≥6 h throttle, ETag, backoff |
| Local toolchain (JDK 26 + AGP 9.5) | Low | Low | Fall back to Android Studio's JBR 25 for Gradle |
