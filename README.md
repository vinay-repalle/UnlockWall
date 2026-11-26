# UnlockWall

An Android app that shows a different **home screen wallpaper** depending on how you
unlock your phone.

## What it does

You choose three pictures:

| Picture | When you see it |
|---|---|
| **Normal** | While the phone is locked, and whenever the app is unsure |
| **Quick** | After a fast unlock, usually fingerprint or face |
| **Slow** | After a slower unlock, usually PIN or pattern |

- Zoom, move and rotate each photo to fit your screen.
- Normal comes back when the screen turns off, and before a restart or power off.
- One switch turns the app on or off.
- Light or dark, following your phone.
- No internet, no accounts, no ads. Your photos stay on your phone.

## How it works

Android does not tell apps whether you used a PIN, finger or face. So UnlockWall
**times the unlock**: under 1 second from the screen coming on is "quick", longer is
"slow". Face unlock is also recognised when the lock screen waits for your swipe.

## Please know

- **It is not a security lock.** Do not use a picture that must never be seen.
- It guesses from speed, so it can be wrong. A very fast PIN could count as quick.
- Face and fingerprint cannot have separate pictures.
- Do not use it with **Extend Unlock (Smart Lock)**.
- If the phone stops the app, the last picture stays on screen.
- The lock screen wallpaper is not changed.

## Requirements

- Android 15 or newer. No root.
- Tested on **iQOO Z9x 5G** (Android 16, OriginOS 6) only. Other phones may behave
  differently.

## Set up on the phone

1. Install the APK and allow installing from that source when asked.
2. Add a **Normal** picture, then the Quick and Slow pictures.
3. Turn the switch **on**.
4. Finish the **setup steps**: notification, unrestricted battery and Autostart.
5. Wait about a minute before testing.

After a restart it can take a few minutes before the wallpaper changes again. Normal
stays until then.

## Permissions

| Permission | Why |
|---|---|
| `SET_WALLPAPER` | Change the wallpaper |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep running to notice unlocks |
| `POST_NOTIFICATIONS` | Show that the app is running |
| `RECEIVE_BOOT_COMPLETED` | Start again after a restart |
| `WAKE_LOCK` | Finish a change while the screen turns off (at most 10 s) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask not to be paused to save battery |

AndroidX libraries also add one internal permission of the app's own. There is **no
internet permission**.

## Tested and not tested

On the iQOO Z9x: finger, PIN and face unlock, switching off and on, restart and power
off, and a PIN prompt inside Settings (correctly ignored). Pattern was checked in early
experiments only.

**Not yet tested:** overnight and 24-hour running, other phones, other apps' fingerprint
locks, forced power-off, large font sizes and accessibility tools.

## Status

Phases 0 to 4 are done. Phase 5 is in progress: a signed 1.0 release is being tested.

## Build

Written in Kotlin with Jetpack Compose. All tools live in `.tools/` inside the project
(not in git); versions are in [docs/HANDOFF.md](docs/HANDOFF.md).

```bash
scripts/contain.sh ./gradlew assembleDebug            # build
scripts/contain.sh ./gradlew :app:assembleRelease     # signed only if you have a key
scripts/contain.sh ./gradlew :app:testDebugUnitTest   # tests
scripts/contain.sh adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## More

- [Experiment results](docs/EXPERIMENT_RESULTS.md): what was measured on the phone
- [Feasibility report](docs/FEASIBILITY.md): the research done before building
- [Implementation plan](IMPLEMENTATION_PLAN.md): design, phases and tests
- [Handoff](docs/HANDOFF.md): current state
