# Flick — Phase 1 Research

_Compiled 2026-11-09. Every platform claim below is tagged:_
- **[Verified]**: read on an official page or in source code (link given)
- **[Inferred]**: my reasoning from verified facts; needs an on-device check
- **[Unverified]**: reported by third parties, or not found in official text

---

## 1. Existing hands-free / gesture apps and what users think

### Landscape

| App | Input | Model | Notes |
|---|---|---|---|
| **Google Camera Switches** (Android Accessibility Suite / Switch Access) | Face gestures: look L/R/up, smile, raise eyebrows, open mouth | Free, system | Drives *Switch Access scanning*, not direct actions, so it is slow for casual use. Built for motor-impaired users. [blog.google](https://blog.google/outreach-initiatives/accessibility/making-android-more-accessible/), [XDA](https://www.xda-developers.com/android-camera-switches-control-phone-with-face) |
| **Project Activate** (Google) | Same face gestures → canned phrases/actions | Free | Communication-focused. |
| **Project Gameface** (Google, open source, archived Sep 2025) | Head-moved cursor + face gestures via MediaPipe Face Landmarker | Free, Apache-2.0 | Closest technical precedent to Flick. Camera runs inside an AccessibilityService. [blog](https://developers.googleblog.com/en/project-gameface-launches-on-android/), [repo](https://github.com/google/project-gameface) |
| **Voice Access** (Google) | Voice | Free | Reported issues: forced language-pack downloads, "hard of hearing", device-specific failures. [unitQ](https://unitq.com/unitq-scorecards/voiceaccess) |
| **Tap, Tap** (Kieron Quinn, XDA) | Back-tap via accelerometer/ML | Free, open source | Long-running "service killed" problem had its own pinned issue. Workarounds added: an optional foreground notification, an hourly restart, and a QS tile to restart. [XDA thread](https://forum.xda-developers.com/t/app-1-5-tap-tap-double-tap-on-back-of-device-gesture-from-android-12-port.4140573/post-83175315), [0.7 notes](https://newreleases.io/project/github/KieronQuinn/TapTap/release/0.7_alpha), [0.10 notes](https://newreleases.io/project/github/KieronQuinn/TapTap/release/0.10_beta) |
| **Spatial Touch** | Hand gestures in the air (≤2 m) | Freemium, ~19M installs | Uses an AccessibilityService. The developer stresses hand filters against false detections. One review cites battery drain. [listing summary](https://appgoblin.info/apps/io.vtouch.spatial_touch), [APKPure review](https://apkpure.com/reviews/spatial-touch-review) |
| **Auto Scroll – Reels & Shorts** (Artilio, ~195K installs), **Auto Scroll: Hands Free Swipe**, **Auto Scroll TT**, **Automatic Scrolling** (tafayor) | Timers, video-progress detection | Ads + IAP | Reviews: "loads of ads", "limited timer on free version", "no support", "continues to scroll in every app after I turn it off", and needed fixes for Android 15/16 breakage. Auto Scroll TT reads the progress bar and falls back to a 15 s timer. [Play: moumen.autoPlay](https://play.google.com/store/apps/details?id=com.moumen.autoPlay&hl=en_IN), [Play: handsfreeswipe](https://play.google.com/store/apps/details?id=com.autoscroll.handsfreeswipe&hl=en), [AppGoblin](https://appgoblin.info/apps/com.moumen.autoPlay) |
| **MotionScroll** (open source), **EyeScroll** | Head tilt / eye tracking via front camera | Free / freemium | MotionScroll runs as an AccessibilityService, the same as Flick. [motionscroll.app](https://www.motionscroll.app/), [Play: EyeScroll](https://play.google.com/store/apps/details?id=io.eyescroll.app&hl=en) |
| iOS/Mac/Web equivalents: **Head Tilt Browser**, **ScrollPods**, **NodScroll**, **RealScroll** | Head tilt / AirPods motion / webcam | Mixed | Head Tilt Browser review: "doesn't recognize head tilts reliably, no sensitivity controls". ScrollPods review: "jumps when your head moves slightly". [App Store](https://apps.apple.com/us/app/head-tilt-browser-face-scroll/id1420399974), [The Register](https://www.theregister.com/a/5270096) |

**What users love:** truly hands-free use while eating, cooking or lying down, no root needed, and on-device privacy (Google promotes that Camera Switches never uploads images). Open-source apps such as Tap, Tap and Gameface earn trust because people can read the code.

**Evidence quality:** Public review text is thin. Search engines rarely index Play reviews and Reddit threads. The pain points below come from the reviews and forum posts above, plus the well-documented OEM background-kill problem ([dontkillmyapp](https://dontkillmyapp.com), [AppBlock](https://appblock.app/why-do-some-device-manufacturers-break-accessibility-or-background-blocking/)). Treat the ranking as informed judgement, not a survey.

### The 10 biggest UX pain points, and how Flick avoids each

| # | Pain point | Evidence | Flick's answer |
|---|---|---|---|
| 1 | **False triggers / over-sensitivity**: a smile fires while laughing at a reel, slight head motion scrolls | Camera Switches guides warn it's "too sensitive"; ScrollPods jumps; Head Tilt Browser has no sensitivity control ([nerdschalk](https://nerdschalk.com/android-12-how-to-control-your-screen-using-facial-gestures/), [Register](https://www.theregister.com/a/5270096)) | Every trigger is a small state machine with a **threshold + hysteresis + hold time + cooldown + return-to-neutral gate**, plus per-user **calibration** of the neutral pose. Defaults favour **deliberate** gestures: a long blink (≥400 ms), a held head tilt, a double blink. Live sensitivity sliders are shown against a real-time meter. |
| 2 | **Service silently dies**: OEM battery managers kill or freeze it, and the toggle still shows "on" | Tap, Tap pinned "service killed" issue; Xiaomi/Samsung reports ([AppBlock](https://appblock.app/why-do-some-device-manufacturers-break-accessibility-or-background-blocking/), [Samsung community](https://r1.community.samsung.com/t5/galaxy-s/accessibility-keeps-turning-itself-off/td-p/22542025)) | A **health check** compares "enabled in Settings" with "actually connected" and shows a repair card. Flick asks for a battery-optimisation exemption and gives **OEM-specific steps** (detects Xiaomi/Samsung/OnePlus/etc. and links to dontkillmyapp). QS tile shows the real state. |
| 3 | **Battery drain from an always-on camera** | Camera Switches power-drain forum report; Spatial Touch review ([zubersoft forum](https://www.zubersoft.com/mobilesheets/forum/thread-7704.html)) | The camera runs **only** while a profile-enabled app is in the foreground **and** the screen is on and unlocked. Low resolution (~320×240), capped at 15 fps. Drops to an idle scan rate when no face is seen. Only the model the active profile needs is loaded. Thermal/battery-saver aware (§6). |
| 4 | **Ads, paywalls, timer limits** | "loads of ads", "limited timer for scrolling on the free version" ([Play](https://play.google.com/store/apps/details?id=com.moumen.autoPlay&hl=en_IN)) | Free, no ads, no IAP, open source (hard constraint). |
| 5 | **Can't stop it / runs everywhere** | "continues to scroll in every app after I turn it off" ([Play](https://play.google.com/store/apps/details?id=com.autoscroll.handsfreeswipe&hl=en)) | **Per-app scoping is the default.** Anything outside a profile does nothing. **One-tap kill switch** from the QS tile, notification, floating bubble and home screen. Auto-scroll stops when the app changes, the screen turns off, or the face turns away. |
| 6 | **Privacy fear** (an accessibility service plus a camera is a scary combination) | Accessibility is "high-risk" ([AppBlock](https://appblock.app/my-accessibility-keeps-turning-off-what-can-i-do-2/)); Android 13 restrictions exist because malware abuses this API ([Esper](https://www.esper.io/blog/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis)) | A **privacy promise screen** in onboarding. Frames never leave RAM: nothing is saved or uploaded. **The only network call is the GitHub update check, and it can be turned off.** The system camera indicator plus Flick's own badge are visible whenever the camera is on. The app is open source. Flick doesn't read screen text beyond what profile matching needs: the package name only. |
| 7 | **Painful setup on Android 13+** ("Allow restricted settings" is hidden; the toggle is greyed out) | F-Droid issue #2680, F-Droid forum threads ([gitlab](https://gitlab.com/fdroid/fdroidclient/-/issues/2680), [forum](https://forum.f-droid.org/t/cant-activate-permissions-for-digipaws-on-android-14/30539)) | A dedicated **step-by-step visual walkthrough** with vector illustrations that mimic Settings. Deep-links to App Info. Detects success on return and auto-advances (§7). |
| 8 | **Breaks after app/OS updates**: scrapers rely on view IDs and progress bars | Auto Scroll's "Android 15/16 auto-scrolling bug" fix; progress-bar fallback | Primary actions are **coordinate-relative gestures** (swipe from 70%→30% of the screen height) and **global actions**, so they don't depend on app internals. Node-based scrolling (`ACTION_SCROLL_FORWARD`) is an optional enhancement with a gesture fallback. Profiles are user-editable data. |
| 9 | **Fatigue and slowness**: scanning UIs, a required neutral reset between gestures | Camera Switches needs scanning and a return to neutral ([MakeUseOf](https://www.makeuseof.com/how-to-control-android-with-facial-gestures/)) | Gestures map **directly to actions** with no scanning. Low-effort defaults: head-down = next, plus an **auto-scroll mode** for long reading. The neutral gate is short and adaptive. |
| 10 | **No feedback**: "did it see me? is it working?" | Camera Switches' tiny blue-square indicator | **Edge glow + haptic** on every fired gesture, an optional mini camera preview with a tracking overlay, a live calibration meter, and a "try a gesture" sandbox on the home screen. |

Further pitfalls seen: volume buttons being hijacked (Flick includes a "keep normal volume" toggle), required language-pack downloads (Flick ships its models in the APK), and the Voice Access "can't scroll Reels by voice" gap.

---

## 2. AccessibilityService rules (target API 36/37, minSdk 26)

Sources: [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [AccessibilityServiceInfo](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo), [GestureDescription](https://developer.android.com/reference/android/accessibilityservice/GestureDescription), [service guide](https://developer.android.com/guide/topics/ui/accessibility/service).

**Gestures**
- `dispatchGesture(GestureDescription, GestureResultCallback, Handler)` dispatches a gesture to the touch screen. `GestureDescription` was added in API 24, so it is available at our minSdk. **[Verified]**
- XML `android:canPerformGestures="true"` is required. **[Verified]**
- A gesture is one or more strokes, timed in ms. `getMaxStrokeCount()` and `getMaxGestureDuration()` are static and must be checked at runtime, because the docs give no numbers. Continued strokes use `StrokeDescription(path, start, dur, willContinue=true)` + `continueStroke()`; the guide shows this. **[Verified]** The `willContinue` API level is 26 **[Unverified, from memory]**, which still fits minSdk.
- **Design implications:**
  - Swipe = 1 stroke of ~250–350 ms (too fast reads as a fling with unpredictable distance; too slow reads as a drag).
  - Tap = 1 stroke of ~50 ms.
  - Double tap = two strokes 50 ms long, 100 ms apart, inside one GestureDescription.
  - Long press = 1 stroke of ~600 ms.
  - Macros = sequential `dispatchGesture` calls chained in `onCompleted`.
  - A new dispatch may cancel an in-flight gesture **[Unverified]**, so the Action layer serialises dispatches through a queue.

**Global actions** (`performGlobalAction(int)`). Only the constant names were verified; the API levels below come from page summaries **[Unverified]**:

| API | Constants |
|---|---|
| 16 | BACK, HOME, RECENTS, NOTIFICATIONS |
| 17 | QUICK_SETTINGS |
| 21 | POWER_DIALOG |
| 24 | TOGGLE_SPLIT_SCREEN |
| 28 | LOCK_SCREEN, TAKE_SCREENSHOT |
| 31 | KEYCODE_HEADSETHOOK (play/pause via headset hook), ACCESSIBILITY_BUTTON(_CHOOSER), ACCESSIBILITY_SHORTCUT, ACCESSIBILITY_ALL_APPS, DISMISS_NOTIFICATION_SHADE |
| 33 | DPAD_UP/DOWN/LEFT/RIGHT/CENTER |
| 36 | MENU, MEDIA_PLAY_PAUSE |

- Every constant above API 26 needs an SDK guard, and the UI must hide actions the device lacks.
- `getSystemActions()` returns the actions currently available. **[Verified]**
- **Media controls on older APIs:** use `AudioManager.dispatchMediaKeyEvent()` with play/pause, next and previous keycodes. This needs no permission. `AudioManager.adjustStreamVolume` handles volume.

**Key-event filtering**
- Set `android:canRequestFilterKeyEvents="true"` in XML **and** add `FLAG_REQUEST_FILTER_KEY_EVENTS`. **[Verified]**
- `onKeyEvent(KeyEvent)` sees keys "before they are passed to the rest of the system". **[Verified]**
- Return `true` to consume a key; this is the standard behaviour from `AccessibilityService` javadoc conventions. **[Unverified in the fetched text]**
- Volume keys are delivered while the screen is on **[Inferred; widely used by "volume button remapper" apps]**.
- **Design:** consume only when bound to an action in the current profile, and let long-presses through so normal volume still works. A **"Keep normal volume"** toggle passes everything through.

**Overlays**
- `TYPE_ACCESSIBILITY_OVERLAY` (API 22): windows overlaid only by a connected accessibility service, which do **not** change the windows the service can introspect. **[Verified]**
- It is added through the service context's `WindowManager` and needs **no SYSTEM_ALERT_WINDOW**. Gameface does exactly this from its service (`ServiceUiManager.java`). **[Verified in Gameface source]**
- Flick uses it for the bubble, edge glow, camera preview and privacy badge.
- API 34+ also has `attachAccessibilityOverlayToDisplay/Window(SurfaceControl)`, which isn't needed for Flick. **[Verified that it exists]**

**Foreground app detection**
- Listen for `TYPE_WINDOW_STATE_CHANGED` and read `event.packageName`.
- Optional: `flagRetrieveInteractiveWindows` + `getWindows()`, then pick the active application window. **[Verified]**
- **Filter:** ignore our own package, `com.android.systemui`, IMEs (from `InputMethodManager.getEnabledInputMethodList`) and the launcher's transient windows. Debounce about 300 ms so notification shades and dialogs don't flip profiles.

**Scrolling by node**
- `ACTION_SCROLL_FORWARD/BACKWARD` (API 16) and the directional `AccessibilityAction.ACTION_SCROLL_UP/DOWN/...` (API 23). **[Partly verified]**
- Requires `canRetrieveWindowContent="true"`. Find the largest visible scrollable node.
- Feeds such as Reels and Shorts are full-screen ViewPagers or RecyclerViews where swipe gestures are more reliable. Node scrolling works best for readers and Chrome.

**Other**
- `takeScreenshot()` exists with `canTakeScreenshot`. `disableSelf()`. **[Verified]**
- The service starts only when the user enables it in Settings and stops when the user disables it or it calls `disableSelf()`. **[Verified]**
- **`isAccessibilityTool`** (API 31): "indicates if the service is used to assist users with disabilities". When false, the system periodically shows a privacy notification about the service. **[Verified attribute; notification behaviour from summary]** This is a policy decision; see PLAN.
- Android 16 deprecates `announceForAccessibility`. Use `View.setStateDescription` or live regions instead. **[Verified]**
- Play policy is relevant only as best practice, since Flick isn't on Play. Show an in-app **prominent disclosure** before sending users to Settings. The service description string doesn't count as that disclosure. [Play policy](https://support.google.com/googleplay/android-developer/answer/10964491), [disclosure rules](https://support.google.com/googleplay/android-developer/answer/11150561)

---

## 3. Running the camera while other apps are in the foreground

**The docs** (all **[Verified]**):
- Any camera use with no visible activity normally requires a foreground service of type `camera`, the `FOREGROUND_SERVICE_CAMERA` permission (normal, auto-granted) and the **runtime `CAMERA` permission granted first**. [service-types](https://developer.android.com/develop/background-work/services/fgs/service-types), [A14 FGS types](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- "You cannot create a camera foreground service while your app is in the background", and this holds **even if the app falls into one of the background-start exemptions**. Pre-A14: "the system would let you create the service, but the service wouldn't have access" to the camera. [restrictions-bg-start](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- Neither the background-start exemption list nor the while-in-use exemption list mentions accessibility services.
- **Android 14:** `startForeground` without a declared type throws `MissingForegroundServiceTypeException`. A missing type permission or unmet runtime prerequisites throw `SecurityException`.
- **Android 15:** `BOOT_COMPLETED` receivers can't launch `camera` FGS. The SAW exemption now requires a visible overlay. Only `dataSync` and `mediaProcessing` FGS have timeouts; `camera` has none. [A15](https://developer.android.com/about/versions/15/behavior-changes-15)
- **Android 16:** no FGS, camera or accessibility-service changes. Jobs running alongside an FGS now obey runtime quotas. [A16](https://developer.android.com/about/versions/16/behavior-changes-16)

**How accessibility services actually get camera access:**
- AOSP binds every accessibility service with these flags **[Verified in source]**: `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE_WHILE_AWAKE | BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS | BIND_INCLUDE_CAPABILITIES` ([AccessibilityServiceConnection.java](https://android.googlesource.com/platform/frameworks/base/+/master/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java))
- `BIND_INCLUDE_CAPABILITIES` (API 29) lets the bound app "get the same capabilities" as the binder, "such as location, camera, microphone from background" **[Verified]** ([Context](https://developer.android.com/reference/android/content/Context#BIND_INCLUDE_CAPABILITIES)). The binder is `system_server`, which always holds these capabilities.
- **Google's Project Gameface** (targetSdk 34) opens CameraX **inside its AccessibilityService** and runs it over other apps and Settings. **[Verified in source]**
  - The service implements `LifecycleOwner`, sets its own `LifecycleRegistry` to STARTED and calls `cameraProvider.bindToLifecycle(service, FRONT, preview, imageAnalysis)`.
  - It declares **no foreground service** and no `FOREGROUND_SERVICE*` permissions in its manifest.
- Gameface also matches the architecture used by EVA Facial Mouse and MotionScroll.

**Conclusion [Inferred]:** a running, user-enabled AccessibilityService process gets while-in-use camera capability through the system binding, so **no camera foreground service is needed**. Starting one from the service could even fail, because an FGS start from the background is disallowed. This explains why Gameface works.

**Not stated in any doc.** Flick therefore:
1. Opens the camera **from the AccessibilityService process**, bound to a service-owned `LifecycleOwner`, as Gameface does.
2. Treats this as the **first thing to prove on a real device in M2**, on your phone and Android version, logging any `CameraAccessException` / `ERROR_CAMERA_DISABLED`.
3. Has a **fallback** if a future OS or OEM build blocks it: a `camera`-type FGS started **from a visible Flick activity or a notification action**, which is an allowed while-in-use path. Once started, it continues to access the camera from the background.
4. Releases the camera immediately when leaving a profiled app or when the screen turns off. That respects other apps, and the system green privacy dot only shows while we really use the camera.
5. Handles **"camera in use by another app"** (`CameraState.ERROR_CAMERA_IN_USE`, `MAX_CAMERAS_IN_USE`) by pausing and retrying with backoff. Camera-based triggers show "paused: camera busy" and non-camera triggers keep working.

**Microphone (voice trigger):** the same `BIND_INCLUDE_CAPABILITIES` reasoning applies **[Inferred]**. On-device `SpeechRecognizer.createOnDeviceSpeechRecognizer()` requires API 31+ **[Unverified, from memory]**.

---

## 4. Restricted settings and developer verification (GitHub distribution)

### Restricted settings (Android 13+)

- **Official steps [Verified]** ([support 12623953](https://support.google.com/android/answer/12623953)): Settings → Apps → *Flick* → ⋮ → **Allow restricted settings** → follow the prompts, then enable the accessibility service. Google's own warning text: "With access to accessibility settings, the app can read content on your screen and interact with apps on your behalf."
- **Which installs are restricted [Unverified, consistent third-party reports]:** apps installed by a **non-session installer**. That covers most browsers' download "open" flows and many file managers. Session-based installers such as F-Droid's client, adb and Play are exempt.
  - Android 15 extended the gate ("enhanced confirmation mode") to more settings: accessibility, notification listener and device admin. ([Android Authority](https://androidauthority.com/android-15-restricted-settings-sideloading-3481098), [9to5Google](https://9to5google.com/2024/09/12/android-15-sideloaded-apps-restrictions/))
  - On some builds, ⋮ → "Allow restricted settings" appears only **after** the user has tried and failed to enable the accessibility toggle once **[Unverified]**. Our walkthrough tells users to try the toggle first.
- **Updates installed by Flick's own PackageInstaller session** don't retrigger the restriction. **[Inferred]**

### Developer verification (2026–2027)

All **[Verified]** from [developer.android.com/developer-verification](https://developer.android.com/developer-verification) and its [FAQ](https://developer.android.com/developer-verification/guides/faq), which was last updated in July 2026.

- **From 30 Sep 2026:** protections are live for users in **Brazil, Indonesia, Singapore and Thailand**, for installs from **participating stores**: Play, Galaxy Store, Xiaomi GetApps, OPPO, vivo, HONOR, Palm Store. The **global rollout is announced for 2027** ("2027 and beyond").
- **GitHub APKs are not covered yet.** FAQ: "If users sideload your app directly, these new verification requirements won't apply to your app yet." It applies only to certified devices.
- **Developer side:**
  - Sign up for the Android Developer Console, verify your identity, and register package names by proving ownership of the signing key (SHA-256 fingerprint).
  - **Full Distribution** costs a $25 fee and requires a government ID.
  - **Limited Distribution** is **free** with no ID, but is capped at **20 devices** (one page says 50, which is inconsistent). It is for sharing via invitation, not public download.
  - There is an **open-source app registration** guide: "you don't have to register… but doing so will let your users continue with the same install experience they have today". Whether a GitHub Releases APK qualifies is not stated.
- **User side for unregistered apps:**
  - **adb installs are exempt.**
  - **"Advanced flow":** Developer options → **Allow apps from unverified developers**, a **one-time 24-hour security delay**, then keep it on for 7 days or indefinitely. After that, each install shows "Install anyway". ([support 17588095](https://support.google.com/android/answer/17588095))
  - Updates to unregistered apps also need the flow enabled.
- **Not confirmed:** whether the 30 Sep enforcement went live as scheduled. I found no post-launch announcement.

**What Flick's README must say:**
1. Download only from the official GitHub Releases page. Verify the SHA-256 checksum and the signing-certificate fingerprint, which the README publishes.
2. Android 8–12: allow "Install unknown apps" for your browser or file manager.
3. Android 13+: after installing, try enabling Flick in Accessibility. If it's blocked, go to App info → ⋮ → Allow restricted settings, and explain why it is needed.
4. Developer verification: for 2026 it doesn't affect GitHub installs; from 2027 on certified devices, an unregistered Flick needs the "advanced flow" or adb. State Flick's registration status honestly.
5. Explain that in-app updates install through Android's installer with a confirmation prompt.

**Decision needed (Phase 2):** register the package via the open-source registration route or a $25 Full Distribution account before the 2027 global rollout. The fee conflicts with "100% free", and the choice is yours. It isn't needed to build or ship now.

---

## 5. Self-updating sideloaded apps

**GitHub Releases API [Verified]** ([releases](https://docs.github.com/en/rest/releases/releases), [rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api))
- `GET https://api.github.com/repos/{owner}/{repo}/releases/latest` returns the newest **non-draft, non-prerelease** release.
  - Release fields: `tag_name`, `name`, `body` (Markdown notes), `published_at`.
  - Asset fields: `assets[].name / size / browser_download_url / content_type / state / digest`.
  - `digest` is "string or null". Its format isn't documented, so Flick won't rely on it and uses its own `.sha256` asset.
- Headers: `Accept: application/vnd.github+json`, `X-GitHub-Api-Version`. A `User-Agent` is customary (generic `Flick-Android/<version>`).
- **Unauthenticated limit: 60 requests/hour per IP.**
  - On a 403/429 with `x-ratelimit-remaining: 0`, wait until `x-ratelimit-reset` (epoch seconds). Respect `retry-after`.
  - Flick checks once per day plus on app open, throttled to ≥6 h, and caches the ETag. Whether a 304 counts against the limit isn't documented.
- `browser_download_url` redirects to a CDN. Follow the redirect and stream to the app's cache dir.

**PackageInstaller session flow [Verified]** ([PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller), [Session](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session), [SessionParams](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams))
1. `createSession(SessionParams(MODE_FULL_INSTALL))`, then `openSession`, then `openWrite("base.apk", 0, size)` and copy, then `fsync`, then close all streams.
2. **Verify** the SHA-256 before committing. `Session.openRead()` "may be used to calculate a MessageDigest of a written APK before committing". Flick also hashes the downloaded file before writing it into the session.
3. `commit(statusReceiver.intentSender)`. On targetSdk ≥35 an **immutable PendingIntent throws IllegalArgumentException**, so the status PendingIntent must be `FLAG_MUTABLE`.
4. Status results:
   - `STATUS_PENDING_USER_ACTION` → launch `EXTRA_INTENT`
   - `STATUS_SUCCESS`
   - `STATUS_FAILURE_INVALID` / `_CONFLICT` → "incompatible certificates" (signature mismatch)
   - `_STORAGE`, `_INCOMPATIBLE`, `_ABORTED` (user cancelled), `_BLOCKED`, `_TIMEOUT` (API 34)
   - Show `EXTRA_STATUS_MESSAGE` in a details pane.

**User confirmation [Verified]**
- `REQUEST_INSTALL_PACKAGES` is declared in the manifest. The user must enable **Install unknown apps** for Flick once. Check `packageManager.canRequestPackageInstalls()` and deep-link to `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:` data.
- **Silent self-update (API 31+)** is possible when all of these hold:
  - `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` is set.
  - The manifest declares `UPDATE_PACKAGES_WITHOUT_USER_ACTION`.
  - The installer holds `REQUEST_INSTALL_PACKAGES`.
  - The installer is **"updating itself"** (or is the update owner or installer of record).
  - The new APK targets a recent API level (targetSdk 36 satisfies every row listed).
- It "must always be prepared to handle STATUS_PENDING_USER_ACTION". In practice, the first update or an OEM policy may still ask.
- **Unavoidable:** granting "Install unknown apps" once. Below API 31, a confirm dialog on every update. Possibly a confirmation on 31+ depending on OEM or update ownership.
- **Android 14 update ownership:** if the original installer claimed ownership (browsers don't), another installer needs user approval. Not relevant unless the user installed Flick via an app store such as Obtainium or F-Droid. Then Flick's updater still works but shows the system prompt. ([source.android.com](https://source.android.com/docs/setup/create/app-ownership))

**Signing:** "All APKs must have the exact same package name … and signing certificates." A mismatch fails with CONFLICT or INVALID. **Losing the keystore makes future updates impossible**: users would have to uninstall, which also loses their settings. RELEASING.md must stress offline backups. APK Signature Scheme v3 key rotation exists but needs the *old* key to sign the lineage.

**Download security:** the app is checked three ways.
1. HTTPS only.
2. SHA-256 from a separate `.sha256` asset.
3. Before committing, Flick compares the downloaded APK's signing certificate with its own using `PackageManager.getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)` on API 28+ (`GET_SIGNATURES` below). That catches a mismatch with a friendly message before the OS rejects it.

The `.sha256` alone only guards against corruption, because an attacker able to replace the APK on GitHub could replace the checksum too. The signature check is the real authenticity guarantee.

---

## 6. Battery-efficient real-time vision on mid-range phones

**MediaPipe facts [Verified]** ([Face Landmarker Android](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/android), [Gesture Recognizer](https://developers.google.com/edge/mediapipe/solutions/vision/gesture_recognizer), [Hand Landmarker](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker))
- Artifact: `com.google.mediapipe:tasks-vision`, latest **1.2.2** per Google Maven metadata (updated 2026-11-03).
- `LIVE_STREAM` mode with `detectAsync(mpImage, SystemClock.uptimeMillis())` "automatically drops incoming frames when busy".
- Face Landmarker options:
  - Defaults: `numFaces=1` (enables temporal smoothing), detection/presence/tracking confidence 0.5.
  - `outputFaceBlendshapes=true` gives 52 coefficients: `eyeBlinkLeft/Right`, `jawOpen`, `mouthSmileLeft/Right`, `browInnerUp`, `browOuterUpLeft/Right`, etc.
  - `outputFacialTransformationMatrixes=true` gives a 4×4 matrix, from which Flick derives head **pitch/yaw/roll** for tilt, nod and turn.
- `*Left/*Right` blendshapes refer to the **subject's** face; mirrored input flips them.
- Gesture Recognizer canned labels: `None, Closed_Fist, Open_Palm, Pointing_Up, Thumb_Down, Thumb_Up, Victory, ILoveYou`. Latency on Pixel 6: **CPU 16.8 ms / GPU 20.9 ms**. Hand Landmarker latency: CPU 17.1 / GPU 12.3 ms.
- Face Landmarker latency was not published on the page I read.
- GPU isn't always faster: one report shows ~15 ms GPU vs ~88 ms CPU, another shows no gain ([#5872](https://github-users.yhajoa.workers.dev/google-ai-edge/mediapipe/issues/5872), [#6041](https://github.com/google-ai-edge/mediapipe/issues/6041)). GPU init can fail on some devices without a graceful fallback, so Flick must catch it and retry on CPU.

**What Gameface ships [Verified in source]:** ImageAnalysis at ~300×400 in RGBA_8888, `STRATEGY_KEEP_ONLY_LATEST`, MediaPipe input downscaled to 213×160 ("this highly affects the performance"), GPU delegate, at most one frame in flight, ≥30 ms between frames (~33 fps). Gameface never pauses on screen-off, which is a battery gap Flick fixes.

**CameraX [Verified]** ([analyze](https://developer.android.com/media/camera/camerax/analyze), [releases](https://developer.android.com/jetpack/androidx/releases/camera)): latest stable **1.6.2**. `STRATEGY_KEEP_ONLY_LATEST` keeps a queue of depth 1. Always `imageProxy.close()`. RGBA output costs an internal YUV→RGBA conversion, but MediaPipe needs RGBA bitmaps anyway. Explicit frame-rate control for ImageAnalysis isn't documented, so Flick throttles in the analyzer.

**Flick's power budget plan:**
| Lever | Setting |
|---|---|
| When the camera runs | Profile app in foreground **and** screen interactive **and** keyguard unlocked **and** master switch on **and** a camera trigger is mapped in that profile. Otherwise the camera is unbound (released). |
| Resolution | ImageAnalysis ~320×240 (4:3) target via `ResolutionSelector`, front lens |
| Frame rate | Active: 15 fps (head/face triggers need ≤66 ms latency). Hand air-swipe: 20 fps. **Idle scan: 4 fps** after 5 s with no face/hand, back to active on detection. |
| Models | Load only what the active profile maps: Face Landmarker, *or* Gesture Recognizer, *or* both. The face model is warm-loaded in the service and closed after 60 s unused. |
| Delegate | GPU first, CPU fallback on init error. A user setting can force CPU. |
| Pipeline | One frame in flight. Analysis on a single background executor. No Preview use case unless the user enables the mini preview (it costs extra). |
| Throttle | `PowerManager.addThermalStatusListener` (API 29): ≥MODERATE → halve fps; ≥SEVERE → pause camera triggers and notify. Battery Saver (`isPowerSaveMode`) → 10 fps cap. |
| Sensors | Proximity and accelerometer at `SENSOR_DELAY_GAME`, registered only while needed. Back-knock needs ~100–200 Hz; >200 Hz on API 31+ requires `HIGH_SAMPLING_RATE_SENSORS`, which Flick avoids. |

Proximity "wave": many 2024+ phones use virtual or under-display proximity sensors that may only report during calls or report coarsely **[Unverified, common OEM behaviour]**. The calibration screen detects this and suggests another trigger.

---

## 7. Onboarding and permission-flow design for accessibility apps

Principles drawn from Google's Play disclosure policy ([answer 11150561](https://support.google.com/googleplay/android-developer/answer/11150561)), [Android accessibility principles](https://developer.android.com/guide/topics/ui/accessibility/principles), and the patterns the apps above get right or wrong:

1. **Value before permission.** Show what Flick does (an animated demo) before asking for anything.
2. **One ask per screen, in context, with the "why".** A prominent disclosure explains what the accessibility service can do, what Flick uses (gestures, global actions, foreground package name) and what it never does (read your messages, upload frames). It must look distinct from system dialogs.
3. **Show, don't tell, for Settings mazes.** Animated vector mock-ups of the exact Settings path (OEM wording varies, so show the generic path with "may be called…" hints). Deep-link as far as possible:
   - `ACTION_APPLICATION_DETAILS_SETTINGS` for restricted settings
   - `ACTION_ACCESSIBILITY_SETTINGS`; on API 33+ try `ACTION_ACCESSIBILITY_DETAILS_SETTINGS` with the component name
   - `ACTION_MANAGE_UNKNOWN_APP_SOURCES`
4. **Auto-detect and auto-advance.** On `ON_RESUME`, re-check state (service enabled? camera granted?) and move forward with a success animation. Never make users press "I did it".
5. **Graceful partial use.** Each permission is optional where possible. Without the camera, Flick still offers volume, sensor and knock triggers. Skip leads to a home screen card ("Finish setup").
6. **Immediate "aha" moment.** End onboarding with a **live try-it**: pick one gesture, perform it, and see the glow and haptic fire in a sandbox. This works before the user leaves the app.
7. **Accessible itself.** TalkBack labels, 48 dp+ targets, 200% font scaling, reduced-motion respect (`ANIMATOR_DURATION_SCALE`).
8. **Recoverable.** Revoked permissions and a dead service surface as clear repair cards on home, not crashes.
9. **Keep it short.** 4–5 screens. Advanced options (battery exemption, OEM tips) appear later, when relevant.

---

## Toolchain snapshot (for PLAN)

From official release pages, read 2026-11-09:

| Item | Version |
|---|---|
| Gradle | 9.9.0 (Nov 6 2026; runs on JDK 17–27) |
| AGP | 9.5.x (min Gradle 9.6, JDK 17, max API 37). Has **built-in Kotlin support**, so no separate kotlin-android plugin is needed. |
| Kotlin | 2.4.21. **No KSP release for 2.4 verified**, and the latest KSP has a regression. This favours manual DI over Hilt. |
| Compose BOM | 2026.09.00 |
| material3 | **1.4.0 stable**. Material 3 Expressive APIs live in **1.5.0, still RC** (rc01, Nov 4 2026). |
| Navigation 3 | 1.2.1 stable |
| Lifecycle | 2.11.0 |
| Activity | 1.13.0 |
| Core | 1.19.1 (core-ktx merged into core) |
| WorkManager | 2.12.0 |
| DataStore | 1.3.0 |
| CameraX | 1.6.2 |
| MediaPipe tasks-vision | 1.2.2 |
| Local machine | SDK platforms 34–37 + build-tools 37.0.0 installed. JDK 26 on PATH, Android Studio JBR 25 available. |

Exact patch versions are re-checked when the Gradle project is created (M1).
