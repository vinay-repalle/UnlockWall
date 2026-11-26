# UnlockWall: handoff

The current state of the project, for a Claude session with no earlier context.
**Keep this file up to date at the end of every phase** (and when stopping mid-phase).

Last updated: 2026-11-26.

## 1. Where things stand

| Phase | What | Status |
|---|---|---|
| Planning | Feasibility report and plan | Done |
| 0 | Toolchain, phone connection, hello app | **Done (2026-11-26)** |
| 1 | Logging "spike" app and experiments on the phone | **In progress: logging confirmed on the iQOO; session A started** (§8) |
| 2 | Core logic and unit tests | Not started |
| 3 | Service, boot, wallpaper changes | Not started |
| 4 | UI | Not started |
| 5 | Hardening and release | Not started |

The product app is only a "hello" screen. Unlock logging and wallpaper test code exist
only in the throwaway `spike` module. No test in the plan (T-01 to T-18) has been run.

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

App: ID `io.github.vinayrepalle.unlockwall`, `compileSdk`/`targetSdk`/`minSdk` 36,
version 0.1.

Decisions made by the user: Temurin 21; latest stable build tools with `compileSdk` 36
(use older libraries if the newest need more); the app ID above; USB cable.

Chosen by Claude without asking: `minSdk` 36 (only one target phone). Confirm with the
user if it matters.

## 7. Known gotchas

- **Do not bump Compose to 1.12 or later** while `compileSdk` is 36: it requires 37
  and the build fails. Upgrading the SDK is a user decision (it is a download).
- The system Java (`/usr/bin/java`) has no `javac`. Always use `scripts/contain.sh`,
  which sets `JAVA_HOME` to `.tools/jdk`.
- The build warns "Unable to strip ... libandroidx.graphics.path.so". It still succeeds.
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

## 8. Phase 1 (in progress)

Goal: find out what a normal app can really observe when a phone is unlocked.
Details: plan §3 (Phase 1), §5.2 (test matrix).

### Decided by the user (2026-11-26)

- Logging code in a separate `spike` module (own app, app ID
  `io.github.vinayrepalle.unlockwall.spike`, `minSdk` 35). Delete it after Phase 1.
- Use usage access as a second signal; log only keyguard events.
- Claude may install and update the spike over USB. The user changes permissions and
  phone settings by hand.
- Test PIN first, then switch briefly to pattern.
- Skip swipe-only lock (T-07) and Extend Unlock (T-18): record as untested.
- 20 unlocks per method.
- Survival tests: default settings first, then Autostart and battery exemption (two nights).
- The iQOO has PIN and fingerprint set up, **no face** (T-06 untested unless added).
- **A second phone must be supported:** a friend's realme P3 5G (ships with Android 15,
  realme UI 6.0, per specs the user pasted; installed version not checked).

### Scope wish stated by the user (2026-11-26, gate not yet held)

- Version 1: change the **home screen** wallpaper according to how the phone was unlocked
  (PIN, face, fingerprint). Lock screen wallpaper: later, maybe version 2.
- **Agreed (2026-11-26):** version 1 uses two home screen wallpapers, "quick unlock"
  (under 1.5 s) and "slow unlock", changed after unlock and only when the kind differs from
  the last one. Do not label them fingerprint/PIN. Lock screen is version 2.
- `setStream` is much shorter in the log than `setBitmap`, but the user saw no difference
  (F11).
- **Privacy concern raised by the user:** a personal "quick unlock" photo must not be seen
  by someone who unlocks with PIN or pattern. Agreed to try: three home wallpapers
  (normal, quick, slow), normal restored at every screen off, quick limit **1 s**, nothing
  changes when unclear. It is not a guaranteed protection (phone handed over unlocked, app
  stopped while the photo shows, a PIN entered in under 1 s).
- Tested (F12): the user never saw "Personal" after a PIN unlock. Usage access is not
  needed (F13). Not tested unplugged or with the app stopped.
- The user suggested asking "is this personal?" when picking a wallpaper. Proposed
  instead: always restore normal at screen off, no question. Awaiting their view.
- F3 (the "1.2 s late" claim) was retracted: usage-access time stamps are offset.

**Next: E4 survival, run 1 (default phone settings).** Label "Survival", unplugged: use
for an hour, swipe the app from recents, leave overnight, reboot, then plug in. Do not
reinstall the spike during the run. Run 2 repeats it with Autostart and battery
exemption. Then force stop, then the realme, then the gate.

**Design wish from the user:** no blue in the app UI (applies to the product UI in
Phase 4 too). Black and green with large buttons was tried and **disliked**. The user then
picked **light and clean** (white, black text, one filled main button, slim rows, chips),
now in the spike; top half checked by screenshot, lower half not. Their opinion of the
result is awaited. Test pictures use orange and grey instead of blue.

### Open questions

- How to test the realme (plug into this laptop, or send the APK and get the log back),
  its Android version and its unlock methods.
- The product app has `minSdk` 36 and will not install on Android 15. Proposed: lower to
  35 in Phase 2.

### Built

`spike/` has no libraries. It logs `SCREEN_ON/OFF`, `USER_PRESENT`, lock state, timings,
process exits and keyguard usage events; has test labels, in-app fingerprint and PIN
prompt buttons (replaces testing with a third-party app, E3), an optional
wallpaper-on-unlock test (E5/E6) and "Save log to Downloads".

```bash
scripts/contain.sh ./gradlew :spike:assembleDebug --offline
scripts/contain.sh adb install -r spike/build/outputs/apk/debug/spike-debug.apk
scripts/contain.sh adb shell run-as io.github.vinayrepalle.unlockwall.spike cat files/events.log
```

Verified on the iQOO (2026-11-26): builds, installs, starts; the service runs in the
foreground; `SCREEN_ON/OFF`, `USER_PRESENT` and usage keyguard events are logged (one PIN
unlock). **Not tested:** prompts, wallpaper changes, boot start, log export, the realme.
Lint not run.

### Findings so far

All results go in [EXPERIMENT_RESULTS.md](EXPERIMENT_RESULTS.md). Most important: the
receiver must be `RECEIVER_EXPORTED`, or `USER_PRESENT` never arrives (confirmed on the
iQOO; plan §2.4 corrected).

### Remaining

1. Session C (E4 survival), described above.
2. The realme tests.
3. Keep writing every result, including failures and untested items, to
   `docs/EXPERIMENT_RESULTS.md`.
4. **Gate:** show the results and agree the product scope with the user before Phase 2.
   So far on the iQOO, timing alone gives "quick unlock" (under 1.5 s, always fingerprint)
   versus "slow unlock" (PIN, pattern or a paused fingerprint); no exact method.

## 9. Git

Claude never runs `git`. The user commits. Whether earlier work has been committed is
therefore unknown to Claude. Give a commit message when work is ready.
