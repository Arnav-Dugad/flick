# Releasing Flick

Releases are built and published by GitHub Actions (`.github/workflows/release.yml`) whenever a
tag like `v1.2.3` is pushed. Each release has three assets:

| Asset | Purpose |
|---|---|
| `flick-1.2.3.apk` | The signed app |
| `flick-1.2.3.apk.sha256` | Checksum for people verifying by hand |
| `version.json` | Read by Flick's in-app updater: `versionCode`, `versionName`, APK name, SHA-256, `minSdk` |

`versionCode` is derived from the tag: `MAJOR × 10000 + MINOR × 100 + PATCH`. Minor and patch
must stay below 100.

---

## ⚠️ The signing key: read this first

Every Flick release **must** be signed with the same key. Android only installs an update over an
existing app if both are signed with the same certificate.

**If the key is lost, nobody who installed Flick can ever update it.** They would have to
uninstall, which also deletes their profiles and settings. There is no recovery: Google can't
help, and GitHub doesn't keep a copy you can download.

The key was created once, on 2026-11-09:

- File: `C:\Users\rduga\FlickSigning\flick-release.jks` (PKCS12, alias `flick`, RSA 4096, valid 100 years)
- Password: in `C:\Users\rduga\FlickSigning\KEYSTORE-PASSWORD.txt`
- Certificate SHA-256: `D0:66:22:AF:2E:42:B3:07:ED:BB:6B:76:91:00:2E:4C:45:6B:4A:D4:18:27:78:E8:0D:99:5E:31:2D:CF:42:2E`

**Do this now:**

1. Copy the whole `FlickSigning` folder to **two** places that aren't this laptop, such as an
   encrypted USB stick and a private cloud folder.
2. Put the password in a password manager.
3. Never commit the `.jks` file or the password to git. `.gitignore` already blocks `*.jks`.

The release workflow refuses to publish an APK whose certificate doesn't match the fingerprint
above (`EXPECTED_CERT_SHA256`).

### Creating a key from scratch (only for a brand-new fork)

```sh
keytool -genkeypair -keystore flick-release.jks -storetype PKCS12 -alias flick \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Flick, O=Your Name"
```

Then update `EXPECTED_CERT_SHA256` in `release.yml`. The value is the output of
`keytool -list -v -keystore flick-release.jks`, lowercased and without colons.

---

## GitHub Secrets (one-time)

Repository → **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `FLICK_KEYSTORE_BASE64` | The keystore file, base64-encoded (see below) |
| `FLICK_KEYSTORE_PASSWORD` | Keystore password |
| `FLICK_KEY_ALIAS` | `flick` |
| `FLICK_KEY_PASSWORD` | Key password (same as the keystore password) |

To base64-encode the keystore:

- PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\rduga\FlickSigning\flick-release.jks")) | Set-Clipboard`
- macOS/Linux: `base64 -w0 flick-release.jks`

Or with the GitHub CLI (from Git Bash):

```sh
base64 -w0 /c/Users/rduga/FlickSigning/flick-release.jks | gh secret set FLICK_KEYSTORE_BASE64
gh secret set FLICK_KEYSTORE_PASSWORD   # paste the password when prompted
gh secret set FLICK_KEY_ALIAS --body flick
gh secret set FLICK_KEY_PASSWORD        # same password
```

---

## Cutting a release

1. Add a section to `CHANGELOG.md`:
   ```markdown
   ## [1.2.3] - 2026-12-01
   - What changed, in plain words.
   ```
   That section becomes the release notes and appears in Flick's update sheet.
2. Commit, then tag and push:
   ```sh
   git tag v1.2.3
   git push origin main v1.2.3
   ```
3. Watch **Actions → Release**. In about 10 minutes the release appears under **Releases**,
   with the APK, the checksum and `version.json`.
4. Installed copies of Flick find the update on their next daily check, or straight away from
   **Settings → Updates → Check now**.

To retract a bad release, delete it on GitHub (and the tag). Phones that already updated keep
that version, so fix forward with a higher version number. Never reuse a version.

---

## Building a signed release locally

```sh
export FLICK_KEYSTORE_PATH=/c/Users/rduga/FlickSigning/flick-release.jks
export FLICK_KEYSTORE_PASSWORD=...  FLICK_KEY_ALIAS=flick  FLICK_KEY_PASSWORD=...
./gradlew :app:assembleRelease -Pflick.versionName=1.2.3 -Pflick.versionCode=10203
```

Without these variables a release build is unsigned and can't be installed. Debug builds use the
separate package `io.github.arnavdugad.flick.debug` and never update from GitHub.

---

## Size and shrinking

Release builds use R8 (`isMinifyEnabled`, `isShrinkResources`) and ship only `arm64-v8a` and
`armeabi-v7a` native code, which keeps the APK at about 42 MB: MediaPipe's runtime plus two
bundled models of about 12 MB. Keep rules for MediaPipe, protobuf and Flogger live in
`app/proguard-rules.pro`. If a release crashes when creating a camera gesture task, check those
rules first.
