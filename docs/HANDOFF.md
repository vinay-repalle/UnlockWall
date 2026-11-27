# UnlockWall: handoff

The current state of the project, for a Claude session with no earlier context.
**Keep this file up to date at the end of every phase** (and when stopping mid-phase).

Last updated: 2026-11-27.

## 1. Where things stand

| Phase | What | Status |
|---|---|---|
| Planning | Feasibility report and plan | Done |
| 0 | Toolchain, phone connection, hello app | **Done (2026-11-26)** |
| 1 | Logging "spike" app and experiments on the phone | **Done; gate passed 2026-11-26** (some tests deferred, §8) |
| 2 | Core logic and unit tests | **Done (2026-11-26)** (§8a) |
| 3 | Service, boot, wallpaper changes | **Done (2026-11-26)**; overnight test passed 2026-11-27 (§8d) |
| 4 | UI | **Done (2026-11-26)** (§8c) |
| 5 | Hardening and release | **In progress**: release 1.0 installed on the iQOO, retest running (§8d) |

The app (**UnlockWall**) changes the home screen wallpaper on the iQOO, with a plain first
screen. The Phase 1 test app was deleted. A1 to A6 passed on the iQOO (F21, F22), with
two limits: third-party app authentication (T-04) was not tried, and "no delay" (A3) was
judged by feel only. A7 to A9 are not done. See
[EXPERIMENT_RESULTS.md](EXPERIMENT_RESULTS.md).

## 2. Read in this order

1. [CLAUDE.md](../CLAUDE.md): rules. They override everything else.
2. This file.
3. [IMPLEMENTATION_PLAN.md](../IMPLEMENTATION_PLAN.md): phases, architecture, tests.
4. [README.md](../README.md): what the app does. Research: [FEASIBILITY.md](FEASIBILITY.md).

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

### The spike (deleted 2026-11-26, at the user's request)

The `spike` module and the UW Spike app on the phone are gone. A private, git-ignored
backup of its source and raw event log is in `.tools/spike-backup/` (this laptop only).

### Deferred tests (still untested)

1. Overnight (user will do it later), with UnlockWall: on, unplugged, not swiped away. In
   the morning, without opening the app: PIN unlock, then lock and use a finger on the
   dark screen. The Quick picture should appear.
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

**Phase 3 questions approved (2026-11-26):** basic screen with switch and three pickers;
overnight test on the real app; install over USB without asking.

## 8b. Phase 3 (done)

The user approved (2026-11-26): basic screen with switch and three pickers; overnight test
on the real app; install over USB without asking; for face (F17), judge speed by when
`isDeviceLocked()` turns false (polled every 100 ms while the lock screen is on), with
face using the Quick picture. A separate face picture is only a possible later idea.

Chosen while building (tell the user if it matters):

- A wake whose `SCREEN_ON` already shows "keyguard locked, device unlocked" is doubtful and
  keeps Normal (1 of 210 wakes in the spike log, a face try).
- The service also restarts after an app update (`MY_PACKAGE_REPLACED`), and opening the
  app restarts it if the switch is on but it is not running.
- Changing a picture while on shows Normal at once.

Code: `monitor/` (service, boot receiver, lock watcher, controller), `wallpaper/` (applier,
cache), `diagnostics/`, `ui/`, `UnlockWallApp`. Screen texts are not yet in resources.

Verified: 48 unit tests pass (state machine 33, policy 8, controller 4, settings 3); builds,
installs, starts without a crash. **Not tested on the phone:** the screen's look, pickers,
service, every unlock type, switch off, restart, power off, overnight, A1 to A6. Lint not
run.

Reading the app's log:
`scripts/contain.sh adb shell run-as io.github.vinayrepalle.unlockwall cat files/diagnostics.log`

**First device run (F18):** switching works; face gave Quick in 4 of 7 face-like wakes.
The user saw no PIN unlock show Quick and approved the fix. Added 2026-11-26 14:14: if the
lock screen is still up 0.5 s after a slow-looking device unlock, it is Quick
(`FACE_UNLOCK`); extra logging of `isDeviceLocked()` around screen off. 45 unit tests pass;
tried on the phone (F19): PIN never Quick; face 9 Quick, 3 doubtful Normal, 2 Slow. The
restart after an app update was seen to work. Approved and added: if the device was
locked at `SCREEN_OFF`, an already-unlocked `SCREEN_ON` counts as quick (**not yet tried
on the phone**). The screen warns against Extend Unlock. Screen timeout wake test done
(F20), no problem seen. 48 unit tests pass.

**Phase 3 done (2026-11-26).** Tests A to H (F21) and A3, A5, A6 (F22) passed on the
iQOO. Still untested: overnight (user will do it later), third-party app authentication
(T-04), unlock delay measurement (T-15), forced power-off, the realme, lint.

**Next, Phase 4 (UI), not started:** short plan and questions first. Includes the proper
screen design (no blue), moving screen texts to resources and a setup guide. The `spike` module is
already deleted.

## 8c. Phase 4 (done)

Approved (2026-11-26): the sketched layout (main screen, setup steps page, activity log
page), light and clean with no blue, an app icon drawn here, no separate face picture,
dark mode and the 24-hour test (A8) moved to Phase 5. **Changed by the user:** choosing a
photo opens an adjust screen to zoom, move and rotate it. Claude chose: pinch and drag,
quarter-turn rotate button, photo always fills the screen. No new libraries.

Code: `ui/` (`UnlockWallScreens`, `HomeScreen`, `EditorScreen`, `SetupScreen`,
`LogScreen`, `ActivityText`), `wallpaper/` (`CropMath`, `Pictures`, `WallpaperCache`).
Texts are in `res/values/strings.xml`.

**The user tried all seven phone tests on the first Phase 4 build and reported they passed**
(add, zoom/rotate, preview match, change then cancel, setup, log, icons).

**Then changed at the user's request (2026-11-26):** tapping a filled picture opens the
adjust screen directly (with "Change photo" and "Remove"; no menu); nothing changes until
Save; leaving with unsaved changes asks Save or Discard. To allow adjusting again, the
cache now also keeps a copy of the whole photo (up to 4096 px) and its placement. Pictures
saved earlier have only the cropped version until chosen again.

Verified: 61 unit tests and 15 picture tests (on the iQOO) pass; A7 manifest check; app
installs and opens without a crash. The user then tried the new tap and Save/Discard flow
and reported it passed. Not tested: large font sizes, accessibility tools, lint.

A debug APK was copied to the iQOO's `Download/UnlockWall.apk` at the user's request.
The user may share it with a friend (realme).

## 8d. Phase 5 (in progress)

Approved (2026-11-26): lint, dark mode and accessibility, error tests, signed release 1.0
with shrinking, retest, 24-hour test, realme (Android 15 or 16, not checked), "Save log to
Downloads", signing key in `.tools/keys/`, lint/shrink downloads, uninstalling the debug
app when it is time (tell the user just before). No Play Store.

**Signing key:** `.tools/keys/unlockwall-release.jks` and `keystore.properties` (holds the
password; never print or commit it). Certificate `CN=UnlockWall`, SHA-256
`c46a3f5c8955ba2caa8b69b163829ce105e830269bbc323a9824ef26020356ad`. They exist only on
this laptop: **the user must back them up**, or the installed app can never be updated.

Done: dark mode (follows the phone), headings and labels for screen readers, wrapping
buttons for large text, Save log, backup and device-transfer exclusion, version 1.0
(code 2), release build with R8 (2 MB; debug is 31 MB). Release copy and R8 mapping in
`.tools/release/1.0/`.

```bash
scripts/contain.sh ./gradlew :app:assembleRelease :app:lintDebug :app:testDebugUnitTest
```

Lint: 0 errors, 8 warnings, left on purpose (newer SDK, plugin and Compose versions;
battery-exemption Play policy; an AndroidX lint check that cannot load; `-v26` icon folder,
because removing the suffix broke resource linking; a KTX style hint).

Verified: 61 unit tests; 17 tests on the iQOO with the debug build; release signature and
manifest checked (not debuggable). **Not tested:** the release build on a phone at all,
dark mode, large text, screen reader, Save log, notification denied, force stop, forced
power-off, 24 hours, the realme. TalkBack users cannot zoom or move a photo.

**Release installed on the iQOO (2026-11-26, after about 16:02):** starts without a crash,
not debuggable. `Download/UnlockWall.apk` on the phone is now this release. The old debug
log was saved to `.tools/release/debug-diagnostics-before-1.0.log`. A release log can only
be read after the user taps "Save log to Downloads":
`scripts/contain.sh adb shell 'ls /sdcard/Download/unlockwall-log-*'`.

**Release retest (user's report "all done", no problem mentioned; log not read, phone not
plugged in):** unlocks by finger, PIN and face; adjust screen with Save/Discard; pictures
and setup kept after reopening; dark and light mode; largest font; Save log to Downloads.

A copy of the release is at `release/UnlockWall-1.0.apk` (git-ignored) and in the phone's
Downloads. Copying it to the laptop's own Downloads was not done (outside the project).

**Overnight on the release: passed (2026-11-27, log read;** details in
[EXPERIMENT_RESULTS.md](EXPERIMENT_RESULTS.md)). The log also shows restart or power off,
switch off/on and notifications denied working. Open point: 5 low-memory kills on 11-26;
twice the app was not running for about 14 minutes, cause of the restart unknown.

**Still untested:** on the release: force stop. Anywhere: forced power-off, a full 24 hours
(A8; the user asked to leave this out of the README, it is **not** tested), the realme,
T-04, T-15 measurement, screen reader.

**Next:** decide with the user which of the above to run before calling Phase 5 done.

## 9. Git

Claude never runs `git`. The user commits. Whether earlier work has been committed is
therefore unknown to Claude. Give a commit message when work is ready.
