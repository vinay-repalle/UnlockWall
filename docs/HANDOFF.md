# UnlockWall: handoff

The current state of the project, for a Claude session with no earlier context.
**Keep this file up to date at the end of every phase** (and when stopping mid-phase).

Last updated: 2026-11-26.

## 1. Where things stand

| Phase | What | Status |
|---|---|---|
| Planning | Feasibility report and plan | Done |
| 0 | Toolchain, phone connection, hello app | **Done (2026-11-26)** |
| 1 | Logging "spike" app and experiments on the phone | **Next, not started** |
| 2 | Core logic and unit tests | Not started |
| 3 | Service, boot, wallpaper changes | Not started |
| 4 | UI | Not started |
| 5 | Hardening and release | Not started |

The app is only a "hello" screen. No unlock detection or wallpaper code exists, and no
test in the plan (T-01 to T-18) has been run.

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

## 8. Next: Phase 1 (the decisive phase)

Goal: find out what a normal app can really observe when this phone is unlocked.
Details: plan §3 (Phase 1), §5.2 (test matrix).

Suggested order:

1. Ask the user the open decisions, with a recommendation for each. For example:
   - Where the throwaway logging app lives. Recommended: a separate `spike` module, so
     it can be deleted without touching `app`.
   - Whether to also use usage-access (`PACKAGE_USAGE_STATS`) as a second signal.
     It needs a manual permission on the phone.
   - Any new library or download.
2. Build the logger: foreground service, runtime receiver for `SCREEN_ON`, `SCREEN_OFF`
   and `USER_PRESENT`, recording the fields listed in the plan, with a log that can be
   read through `adb`.
3. Run the experiments with the user, one scenario at a time. The user does the unlocks;
   Claude reads the logs. Cover E1 to E4 first.
4. E5 and E6 change the phone's wallpaper. Warn the user first so they can save their
   current one.
5. Write every result, including failures and untested items, to
   `docs/EXPERIMENT_RESULTS.md`.
6. **Gate:** show the results and agree the product scope with the user before Phase 2.
   If no signal separates unlock methods (expected), the scope becomes "change the
   wallpaper on every unlock".

## 9. Git

Claude never runs `git`. The user commits. Whether earlier work has been committed is
therefore unknown to Claude. Give a commit message when work is ready.
