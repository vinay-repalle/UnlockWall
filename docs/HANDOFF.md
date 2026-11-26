# UnlockWall: handoff

The current state of the project, for a Claude session with no earlier context.
**Keep this file up to date at the end of every phase** (and when stopping mid-phase).

Last updated: 2026-11-26.

## 1. Where things stand

| Phase | What | Status |
|---|---|---|
| Planning | Feasibility report and plan | Done |
| 0 | Toolchain, phone connection, hello app | **Done (2026-11-26)** |
| 1 | Logging "spike" app and experiments on the phone | **Done; gate passed 2026-11-26** (some tests deferred, §8) |
| 2 | Core logic and unit tests | **Done (2026-11-26)** (§8a) |
| 3 | Service, boot, wallpaper changes | **Next, not started** |
| 4 | UI | Not started |
| 5 | Hardening and release | Not started |

The product app (**UnlockWall**) still shows only the "hello" screen: the Phase 2 logic is
tested but not connected. The unlock and wallpaper behaviour seen on the phone so far is
from the throwaway `spike` module (**UW Spike**). No acceptance test
(A1 to A9) has been passed; what the spike showed is in
[EXPERIMENT_RESULTS.md](EXPERIMENT_RESULTS.md).

## 2. Read in this order

1. [CLAUDE.md](../CLAUDE.md): rules. They override everything else.
2. This file.
3. [IMPLEMENTATION_PLAN.md](../IMPLEMENTATION_PLAN.md): phases, architecture, tests.
4. [README.md](../README.md): what Android allows and why.

## 3. Check before doing any work

Run from the project folder (`/home/vinayrepalle/Documents/UnlockWall`):

```bash
scripts/contain.sh javac -version                    # expect: javac 21.0.13.1
scripts/contain.sh ./gradlew assembleDebug --offline # expect: BUILD SUCCESSFUL
scripts/contain.sh adb devices -l                    # expect: model:I2219 ... device
```

Install and start the app on the phone:

```bash
scripts/contain.sh adb install -r app/build/outputs/apk/debug/app-debug.apk
scripts/contain.sh adb shell am start -n io.github.vinayrepalle.unlockwall/.MainActivity
```

If the phone is not listed, ask the user to plug it in with a data cable, choose
"File transfer", keep USB debugging on, and tap "Allow". `lsusb` should show `vivo`.

## 4. Verified on 2026-11-26

- `adb` lists the phone, and the debug APK installs.
- The app starts (cold start 1524 ms), stays the foreground activity, shows
  "UnlockWall is installed and running.", and logged no crash.

## 5. Phone

| Item | Value (read with `adb`) |
|---|---|
| Phone | iQOO Z9x 5G (`adb` model `I2219`, product `I2219i`) |
| Android | 16 (API 36) |
| OS | OriginOS 6 |
| Build | `PD2353BF_EX_A_16.2.17.0.W30` |
| Security patch | 2026-09-01 |
| CPU | arm64-v8a |
| Screen | 1080 x 2408, density 440 (user override 396) |

## 6. Toolchain

All of it is in `.tools/` (git-ignored, exists only on this laptop).

| Tool | Version |
|---|---|
| JDK | Eclipse Temurin 21.0.13.1+2, in `.tools/jdk` |
| Android SDK | platform 36, build-tools 36.1.0, platform-tools, cmdline-tools |
| Gradle | 9.9.2 (wrapper) |
| Android Gradle Plugin | 9.5.2 (built-in Kotlin) |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.06.01 (Compose 1.11.4) |
| activity-compose | 1.13.0 |

App: ID `io.github.vinayrepalle.unlockwall`, `compileSdk`/`targetSdk` 36, `minSdk` 35,
version 0.1. Added in Phase 2 (approved): DataStore Preferences 1.3.0, JUnit 4.13.2,
AndroidX Test runner 1.7.0 and ext-junit 1.3.0.

Decisions made by the user: Temurin 21; latest stable build tools with `compileSdk` 36
(use older libraries if the newest need more); the app ID above; USB cable.

`minSdk` was lowered to 35 (approved) for the realme.

## 7. Known gotchas

- **Do not bump Compose to 1.12 or later** while `compileSdk` is 36: it requires 37
  and the build fails. Upgrading the SDK is a user decision (it is a download).
- The system Java (`/usr/bin/java`) has no `javac`. Always use `scripts/contain.sh`,
  which sets `JAVA_HOME` to `.tools/jdk`.
- The build warns "Unable to strip ... libandroidx.graphics.path.so". It still succeeds.
- After the phone restarts, `adb` may say "no permissions". Running
  `scripts/contain.sh adb kill-server` and listing devices again fixed it.
- If a build fails instantly for no clear reason, run `scripts/contain.sh ./gradlew --stop`
  and retry. This fixed one failure, probably a stale Gradle daemon.
- Slow or unstable internet: GitHub downloads failed without an IPv4 address and ran
  at about 80 KB/s on one network. `.tools/bin/pdl.sh <url> <file> <sha256> [connections]`
  downloads in parallel, resumes, and verifies the checksum.
- If `.tools/` is lost, ask the user before downloading anything again:
  - JDK: `https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13.1_2.tar.gz`
    (SHA-256 `bb8a93f9ae6e7a693b2b721e85dbeff26ae1a77447985a8d7afcdd9c7cf6222e`),
    unpacked into `.tools/jdk` with `--strip-components=1`.
  - Android SDK: command-line tools into `.tools/android-sdk/cmdline-tools/latest`,
    then `sdkmanager` for `platform-tools`, `platforms;android-36`, `build-tools;36.1.0`.
  - Gradle: the wrapper (`./gradlew`) downloads it and checks the checksum itself.

## 8. Phase 1 (done, with deferred tests)

Details: plan §3 (Phase 1), §5.2 (test matrix). Results:
[EXPERIMENT_RESULTS.md](EXPERIMENT_RESULTS.md) (F1 to F16; F3 is retracted).

### Decided by the user (2026-11-26)

- Separate `spike` app (`io.github.vinayrepalle.unlockwall.spike`, `minSdk` 35), deleted
  after Phase 1.
- Claude may install and update the spike over USB. The user changes permissions and
  phone settings by hand.
- Skip swipe-only lock (T-07) and Extend Unlock (T-18): untested.
- The iQOO has PIN and fingerprint, **no face** (T-06 untested unless added).
- **A friend's realme P3 5G must also be supported** (shipped with Android 15 per specs
  the user pasted; installed version not checked).
- No blue in the UI, also for the product (Phase 4). Black and green was disliked; the
  spike now has a light, clean style. Product design is still to be agreed.

### Version 1 scope (agreed at the gate)

- Home screen only. Lock screen is version 2.
- Three wallpapers: **Normal** (default), **quick unlock** (`USER_PRESENT` under **1 s**
  after `SCREEN_ON`; usually fingerprint) and **slow unlock** (usually PIN or pattern).
  Do not label them as unlock methods in the app.
- Restore Normal at screen off, at `ACTION_SHUTDOWN` and at every service start. Keep it
  for 60 s after the service starts, and whenever the unlock is unclear. The user requires
  Normal after a restart or power off.
- Needs Autostart (manual) and unrestricted battery. Usage access is **not** needed.
- The app must say this is not a security feature. Known gaps: phone handed over unlocked,
  app stopped while the personal photo shows, forced power-off, a PIN entered under 1 s,
  a late `SCREEN_ON` making a slow unlock look quick (not observed).
- Use `setStream`. The user sees about 1.5 s before the picture changes; the cause is not
  known.
- Proposed, not answered: do not ask "is this personal?" when picking a wallpaper.

### The spike

No libraries. Logs screen and unlock events, lock state, timings, process exits and
keyguard usage events. Has test labels, in-app prompt buttons, wallpaper tests and the
three-wallpaper "Friend scenario".

```bash
scripts/contain.sh ./gradlew :spike:assembleDebug --offline
scripts/contain.sh adb install -r spike/build/outputs/apk/debug/spike-debug.apk  # stops the logger
scripts/contain.sh adb shell run-as io.github.vinayrepalle.unlockwall.spike cat files/events.log
```

**Not tested:** lock screen wallpaper, face, third-party in-app prompts, "Save log to
Downloads", lower half of the new screen by screenshot, unlock delay with and without the
app (T-15), the realme. Lint not run.

### Deferred tests (still untested)

1. Overnight (user will do it later): unplugged, Friend scenario on, label "Survival". In
   the morning, without opening the app: PIN unlock, then lock and use a finger on the
   dark screen. "Personal" should appear.
2. Force stop. 3. The realme (how to test it, its Android version and unlock methods are
   open). 4. Face, if set up.

## 8a. Phase 2 (done 2026-11-26)

In `app/src/main/java/io/github/vinayrepalle/unlockwall/`: `core/` (rules, no Android
imports), `settings/SettingsRepository`, `wallpaper/WallpaperCache`. Face needs no code.

Verified: 27 unit tests pass (state machine 16, policy 8, settings 3); 8 cache tests pass
on the iQOO; the app installs and starts. Not tested: lint, the realme, EXIF rotation of
real camera photos, very large or unusual image formats.

```bash
scripts/contain.sh ./gradlew :app:testDebugUnitTest
scripts/contain.sh ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
scripts/contain.sh adb install -r app/build/outputs/apk/debug/app-debug.apk
scripts/contain.sh adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
scripts/contain.sh adb shell am instrument -w io.github.vinayrepalle.unlockwall.test/androidx.test.runner.AndroidJUnitRunner
scripts/contain.sh adb uninstall io.github.vinayrepalle.unlockwall.test
```

(`connectedAndroidTest` was not used because it uninstalls the app afterwards.)

**Next, Phase 3:** short plan and questions first. Stop the spike's logger and Friend
scenario before testing the real app, or both will change the wallpaper.

## 9. Git

Claude never runs `git`. The user commits. Whether earlier work has been committed is
therefore unknown to Claude. Give a commit message when work is ready.
