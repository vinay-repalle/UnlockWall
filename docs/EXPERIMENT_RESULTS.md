# UnlockWall: experiment results (Phase 1)

Only what was observed on a real phone is written here.

## Phones

| Phone | Android | Build | Lock methods set up |
|---|---|---|---|
| iQOO Z9x 5G (`I2219`) | 16 (API 36) | `PD2353BF_EX_A_16.2.17.0.W30` | PIN, fingerprint (no face) |
| realme P3 5G (friend's) | not checked | not checked | not checked; **not tested** |

## Status

| ID | Question | Status |
|---|---|---|
| E1 | Does `USER_PRESENT` fire for every unlock, and how fast? | PIN (F2) and fingerprint, dark screen (F6) and lock screen (F7), pattern (F9): every unlock detected |
| E2 | Does any signal differ between unlock methods? | Timing only: separates quick fingerprint unlocks from PIN, not slow ones from PIN or pattern (F6, F7, F9) |
| E3 | Do in-app prompts emit anything? | Our own app's prompts: nothing (F8). Third-party apps not run |
| E4 | Does the service survive? | Default settings: survives swipe from recents, does not restart after reboot by itself (F14). With exemption and Autostart it starts after reboot (F16). Overnight, force stop not run |
| E5 | Wallpaper speed, flash, lock screen | Home screen (F10, F11, F12). Lock screen: version 2, not run |
| E6 | Does OriginOS theming override the wallpaper? | Home screen: no override seen (F10). Reboot, themes, lock screen not run |

## Gate decision (2026-11-26)

**Go.** The user agreed: version 1 changes the home screen only, with Normal, Quick-unlock
and Slow-unlock pictures (rules in the plan §2.3). Face is included only in the sense that
a fast face unlock would count as quick; it cannot have its own picture.

Not tested when the gate was passed: overnight or 1 hour survival, force stop, the realme,
face, forced power-off, lock screen, third-party in-app prompts.

## Findings

### F1: the receiver must be `RECEIVER_EXPORTED` (iQOO, 2026-11-26)

- With `RECEIVER_NOT_EXPORTED`, `SCREEN_ON` and `SCREEN_OFF` arrived but `USER_PRESENT`
  never did.
- `adb shell dumpsys activity broadcasts history` showed the unlock's `USER_PRESENT`
  (10:16:18) was sent by `com.android.systemui` (uid 10095, not the system uid) to 94
  receivers; the logging app was not among them.
- After switching to `RECEIVER_EXPORTED`, `USER_PRESENT` arrived (10:20:48).

### F2: PIN, 20 unlocks (iQOO, 2026-11-26, USB plugged in)

- 20 `SCREEN_ON` while locked, 20 `USER_PRESENT`, exactly one per unlock. No duplicates,
  none missed.
- `SCREEN_ON` to `USER_PRESENT`: median 2458 ms, range 2059 to 4380 ms. This includes
  typing the PIN.

### F3: RETRACTED. Usage-access time stamps are about 1.2 s early on this phone

An earlier version of this file said `USER_PRESENT` is sent about 1.2 s after the keyguard
goes. That relied on usage-access time stamps, which turned out to be offset:

- `KEYGUARD_SHOWN` is stamped a median 1297 ms before the app receives `SCREEN_OFF` with
  the phone already locked (111 of 113).
- For dark-screen fingerprint, `KEYGUARD_HIDDEN` is stamped a median 1039 ms before
  `SCREEN_ON` (21 of 21), while the phone still reported locked at `SCREEN_ON`.
- `isKeyguardLocked()` changes together with `USER_PRESENT` (F5).

So there is **no evidence that `USER_PRESENT` is late**. Usage time stamps must not be
compared with the app's clock. The direct check (polling usage access, app's own clock) agrees
(F13).

What stays true: `dumpsys` showed 16 to 22 ms from queuing the broadcast to delivery
(n=4).

### F4: receiver time (iQOO, 20 of each)

| Event | `onReceive` median | max | over the 5 ms target |
|---|---|---|---|
| `USER_PRESENT` | 1.1 ms | 3.8 ms | 0 of 20 |
| `SCREEN_ON` | 6.5 ms | 65.0 ms | 14 of 20 |
| `SCREEN_OFF` | 6.8 ms | 69.8 ms | 11 of 20 |

The spike reads three lock and screen states inside `onReceive`; the product should avoid
unneeded calls there. Effect on unlocking has not been measured (T-15).

### F5: asking "still locked?" every 100 ms gives no earlier signal (iQOO, PIN, n=5)

The spike checked `isKeyguardLocked()` and `isDeviceLocked()` every 100 ms from
`SCREEN_ON` while locked.

| Unlock | Usage `KEYGUARD_HIDDEN` | Poll saw unlocked | `USER_PRESENT` received |
|---|---|---|---|
| 1 | 10:33:42.154 | 10:33:43.404 | 10:33:43.348 |
| 2 | 10:33:54.707 | 10:33:55.885 | 10:33:55.906 |
| 3 | 10:34:20.046 | 10:34:21.220 | 10:34:21.257 |
| 4 | 10:34:34.115 | 10:34:35.315 | 10:34:35.317 |
| 5 | 10:34:49.813 | 10:34:51.014 | 10:34:51.010 |

- The poll saw the change between 37 ms before and 57 ms after `USER_PRESENT`. Both lock
  values changed at the same check in 4 of these 5; in one, only the keyguard value had
  changed when polling stopped.
- So polling is not worth using for PIN. Fingerprint: see F6.
- The checks took about 1.3 ms each (125 checks, 159 ms in total). Battery not measured.
- All 5 unlocks gave exactly one `USER_PRESENT` (26 PIN `USER_PRESENT` logged in total:
  the run of 20, one extra, and these 5).

### F6: fingerprint from a dark screen, 20 unlocks (iQOO, 2026-11-26, USB plugged in)

- 20 `SCREEN_ON`, 20 `USER_PRESENT`, one per unlock. At `SCREEN_ON` the phone still
  reported locked in all 20.
- `SCREEN_ON` to `USER_PRESENT`:

| Method | n | min | median | max |
|---|---|---|---|---|
| Fingerprint, dark screen | 20 | 103 ms | 335 ms | 527 ms |
| PIN (all PIN unlocks logged) | 27 | 1874 ms | 2387 ms | 4380 ms |

- The ranges do not overlap in this data, so a time limit (for example 1 s) would have
  separated them. This is a timing guess, not a reported unlock method. It says nothing
  yet about fingerprint on a visible lock screen, face, pattern, or another phone.
- Polling saw the keyguard gone a median of about 0.13 s (at most 0.42 s) before
  `USER_PRESENT`, and in 8 of 20 while `isDeviceLocked()` was still true.

### F7: fingerprint with the lock screen showing, 20 unlocks (iQOO, 2026-11-26)

The user was asked to vary the pause before touching the sensor.

- 21 `SCREEN_ON` (all locked), 20 `USER_PRESENT`. The one without was a wake of about 7 s
  followed by screen off, with no unlock event: correct if no unlock was made then.
- `SCREEN_ON` to `USER_PRESENT`: min 171 ms, median 1225 ms, max 3007 ms.

Timing compared across all logged unlocks:

| `SCREEN_ON` to `USER_PRESENT` | Fingerprint, dark (20) | Fingerprint, lock screen (20) | PIN (27) |
|---|---|---|---|
| under 1000 ms | 20 | 4 | 0 |
| under 1500 ms | 20 | 16 | 0 |
| under 1874 ms (fastest PIN) | 20 | 19 | 0 |

- In this data a quick unlock (under 1.5 s) was always fingerprint. A slow one can be
  either. The limits were picked after seeing the data and one user was measured, so a
  faster PIN entry could fall under them.

### F8: in-app prompts emit no unlock signal (iQOO, 2026-11-26)

- 7 fingerprint and 6 PIN `BiometricPrompt`s from the logging app, all successful.
- No `USER_PRESENT`, `SCREEN_ON/OFF` or polling event was logged during them.
- Not covered: third-party apps (banking, WhatsApp), the Settings "confirm credential"
  screen.
- Usage access also recorded no keyguard event during the prompts (10:46:31 to 10:47:40).

### F9: pattern, 20 unlocks (iQOO, 2026-11-26)

- 20 `SCREEN_ON` (all locked), 20 `USER_PRESENT`, one per unlock.
- `SCREEN_ON` to `USER_PRESENT`: min 2329 ms, median 2579 ms, max 6748 ms. None under
  1500 ms.
- The label was left on "in-app" by mistake, so these are the 20 unlocks logged with that
  label after 10:50:36. The user confirmed they were all pattern.
- Quick (under 1.5 s) unlocks over all runs: 36 of 40 fingerprint, 0 of 27 PIN, 0 of 20
  pattern.

### F10: changing the home screen wallpaper (iQOO, 2026-11-26, USB plugged in)

Target: home only. Two generated 1080x2408 JPEG test images, decoded then `setBitmap`.

| When | n | `setBitmap` min / median / max | Trigger to `setBitmap` returning, median |
|---|---|---|---|
| After unlock (`USER_PRESENT`) | 5 | 2162 / 2212 / 2597 ms | 2594 ms |
| Screen off | 12 | 1152 / 1522 / 3288 ms | 1629 ms |

- No errors; 17 changes for 17 triggers. Decoding took 22 to 436 ms.
- Plan A1 wants the change within 2 s. After unlock, this missed it in all 5.
- What the user saw (estimates, not measured):
  - After unlock: the old picture first, new one about 1 to 2 s after unlocking; possibly
    a zoom-out animation.
  - Screen off: felt much faster; nothing seen changing at unlock.
  - Pictures appeared properly each time. The lock screen did not change.
- When the new picture first becomes visible compared with `setBitmap` returning is not
  known. Real photos may take a different time.

### F11: `setStream` with the JPEG file versus `setBitmap` (iQOO, home, after unlock)

| Method | n | Set call median (range) | `USER_PRESENT` to call returning, median |
|---|---|---|---|
| `setStream` (file as is) | 6 | 206 ms (183 to 253) | 208 ms |
| `setBitmap` (decoded first) | 5 | 1170 ms (1132 to 1195) | 1221 ms |

- No errors. 12 unlocks were asked for; 11 changes are in the log.
- **The user saw no difference:** about 1.5 s every time. So the log time is not the time
  the picture becomes visible. Where the visible delay comes from is not known; the earlier
  explanation (late `USER_PRESENT`) is retracted (F3).
- Use `setStream` in the product anyway: the call is much shorter. Visible gain not shown.

### F12: friend scenario, three home wallpapers (iQOO, 2026-11-26, USB plugged in)

Normal restored at every screen off; quick (under 1000 ms) shows "Personal"; slow shows
"Slow"; unclear keeps Normal. The user ran: owner finger, friend PIN, fast friend PIN,
finger after a pause.

- **The user never saw Personal in a PIN test.** A finger used within about 1 s of waking
  the lock screen gave Personal, which is what the rule does.
- Log: 27 unlocks: 12 quick, 14 slow, 1 unclear. At no timed screen wake was Personal
  still the current wallpaper.
- Normal was restored 179 to 482 ms after the app got `SCREEN_OFF` (median 268, n=25).
- The log does not record which method was used; the PIN result rests on what was seen.
- Not tested: unplugged, app stopped by the phone, reboot while Personal is showing, real
  photos, a second person.

Two things to handle in the product:

- **`USER_PRESENT` can arrive before `SCREEN_ON`** (once, 0.4 s before). It was treated as
  unclear. The plan's state machine (§2.3) would ignore such an unlock.
- **`USER_PRESENT` can be late** (once): at 11:48:03 the keyguard was gone 335 ms after
  `SCREEN_ON`, but `USER_PRESENT` came at 1033 ms, so a probably quick unlock counted as
  slow. This erred on the safe side.

### F13: polling usage access gives no real gain (iQOO)

- Polled every 100 ms from `SCREEN_ON`, the app first saw `KEYGUARD_HIDDEN` a median 10 ms
  before `USER_PRESENT` (n=21). 20 were within about 120 ms; one was 766 ms early (the
  late case above).
- When first seen, the event's stamp was already about 1.2 s old, which confirms the
  offset in F3.
- The product does not need usage access.

### F14: swipe from recents and reboot, default phone settings (iQOO, 2026-11-26)

- **Swipe from recents:** `TASK_REMOVED` logged, same process kept running, unlocks and
  wallpaper changes continued.
- **Reboot while "Personal" was showing (twice): the failure expected.** After the PIN,
  Personal stayed visible.
- **The app did not start after reboot until the user opened it** (user's observation).
  `BOOT_COMPLETED` reached the app about 5 minutes and about 80 seconds after boot, each
  time within 1 s of the process starting. Normal was restored then.
- Autostart and battery exemption had not been turned on by us; battery exemption was off.

### F15: broadcasts arrive late and out of order shortly after a reboot (iQOO)

For about 45 s after the first late start, `USER_PRESENT` arrived without a preceding
`SCREEN_ON`, once 19 ms after `SCREEN_OFF` with the phone reporting locked. The rule
"unclear keeps Normal" covered these. **Risk not observed but possible:** a late
`SCREEN_ON` just before `USER_PRESENT` would make a slow unlock look quick and show the
personal photo. The product must handle this.

### F16: restart and power off with the safeguards (iQOO, 2026-11-26)

User's observations, confirmed by the log:

- Restart and power off with Personal showing: **Normal** after the PIN. The app was not
  opened; its notification appeared.
- Both times `ACTION_SHUTDOWN` arrived and Normal was set before power off (310 and
  342 ms).
- Quick and slow unlocks kept Normal for a while, then changes resumed.

| After switching on | Restart | Power off |
|---|---|---|
| App started (`BOOT_COMPLETED`), seconds since boot | 98 s | 140 s |
| 60 s hold ends, seconds since boot | about 159 s | about 200 s |

- The unlock signal was not logged before the app started.
- Battery exemption confirmed with `dumpsys deviceidle`. Autostart cannot be read; the app
  did start by itself both times.
- Not covered: forced power-off.

### F17: face unlock waits for a swipe (iQOO, 2026-11-26, after the gate)

The user set up face unlock and saw it give the "Slow" picture. Confirmed in the log
(3 face-style wakes; `dumpsys face` showed 1 enrolled face):

| Wake | `isDeviceLocked()` false after | Lock screen gone after | `USER_PRESENT` after |
|---|---|---|---|
| 13:02:02 | 264 ms | 3638 ms | 4143 ms (set Slow) |
| 13:05:47 | 186 ms | 3285 ms | 3689 ms (set Slow) |
| 13:08:44 | 408 ms | not (screen went off) | none |

- Face authenticates quickly but the lock screen stays until a swipe, and `USER_PRESENT`
  comes only after the swipe. Judging speed by `USER_PRESENT` is wrong for face.
- No other cycle in the log showed the device unlocked while the lock screen stayed for
  over 1 s (the other long gaps were right after a reboot, where both changed together).
- Also seen: after 12:00, `USER_PRESENT` came a median 474 ms after the device unlocked
  (n=27), against 29 ms before 12:00 (n=97). Cause unknown. This changes F5's conclusion:
  polling can now be about half a second earlier than `USER_PRESENT`.

### F18: first run of the real app (iQOO, 2026-11-26, 14:04 to 14:07)

Log only; the user's own observations were not given in detail. 13 unlocks, no crash, no
failed change. Normal was restored after every screen off (136 to 896 ms to set).

| Kind of wake | n | Quick | Slow | Normal (unclear) |
|---|---|---|---|---|
| Lock screen stayed after unlocking (face-like) | 7 | 4 | 1 (recognised after 1846 ms) | 2 (already unlocked at `SCREEN_ON`) |
| Home screen straight away (finger or PIN) | 6 | 2 | 4 (1070 to 4291 ms) | 0 |

The method used in each try was not recorded. Both "already unlocked" wakes came under
1 s after screen off, so they may also be the phone not having locked yet.

In the older spike data, the lock screen was still up when the device was first seen
unlocked in 8 non-face-era unlocks, but only for 30 to 182 ms. With face it stayed for
about 3 s. So "still up 0.5 s later" would separate them in this data.

### F19: after the face fix (iQOO, 2026-11-26, 14:16 to 14:22)

Which tries were which is taken from the order of the requested tests, so the group edges
are approximate. No failed changes.

- PIN: 7 unlocks, all Slow, none Quick. None left the lock screen up.
- Face (about 14): 9 Quick (3 of them only thanks to the 0.5 s check), 2 Slow, 3 Normal
  (already unlocked at `SCREEN_ON`).
- `isDeviceLocked()` was true at `SCREEN_OFF` in 35 of 37 and still true 0.5 s later in 32
  of 35. Before the doubtful wakes it was true at `SCREEN_OFF`, so the phone was
  authenticated again after locking (consistent with face).

### F20: screen turning off by itself (iQOO, 2026-11-26, 14:24 to 14:27)

- After a timeout the phone was not yet locked 0.5 s after `SCREEN_OFF`. In the 5 logged
  wakes (4.9 to 23 s later) it asked for authentication each time.
- User's observations: where it asked, face and finger gave Quick and PIN gave Slow. When
  they touched the dark screen quickly it needed no unlock. The log has no screen on/off for
  that case, so it was probably the dimmed screen before turning off (not confirmed).
- No case was reported or logged of Quick after getting in without authentication.

### F21: Phase 3 tests of the real app (iQOO, 2026-11-26, 14:30 to 14:53)

The user reported all of A to H passed. The log agrees:

| Test | Log evidence |
|---|---|
| Face (A) | 3 `FACE_UNLOCK`; the one already-unlocked wake showed Quick; no unclear results |
| Settings PIN screen, T-05 (B) | No unlock signal without a screen wake |
| Rapid lock/unlock, T-16 (C) | No error, no crash |
| Switch off and rapid toggling, T-08, T-09 (D, E) | Normal set at each switch off; no repeated signals; running at the end |
| Restart with Quick showing, T-10 (F, G) | Normal set at `SHUTDOWN`; app started by itself; Quick after the 60 s hold |
| Power off with Quick showing (H) | The same |

The app started about 3 minutes after the shutdown signal both times (includes the time off
and the first PIN). Not tested: third-party app authentication (T-04), original photo
deleted (A5 manual), missing Slow picture on the phone (A6), unlock delay compared with
the app absent (T-15), overnight, forced power-off.

### F22: last Phase 3 checks (iQOO, 2026-11-26, user's report; log not read)

- A5: the original photo deleted from the gallery, the wallpaper still showed. Passed.
- A6: Slow picture removed, a PIN unlock showed Normal. Passed.
- A3: unlocking does not feel slower. Passed by feel only; no measurement (T-15).

### A7: manifest check (2026-11-26, Phase 4 build)

`aapt2 dump permissions`: `SET_WALLPAPER`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`,
`WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and the app's own
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (added by AndroidX). No internet permission.
Exported: the launcher activity and AndroidX's `ProfileInstallReceiver`. No root needed.

### Phase 4 screens, first build (2026-11-26, user's report)

Adding a landscape photo, zoom, drag and rotate, the home screen matching the preview,
cancel keeping the old picture, setup steps, activity log and icons: reported as passed.
Log not read.

### Phase 5 build checks (2026-11-26)

- T-13: a missing and a damaged cached picture are reported and nothing is set (tests on
  the iQOO, debug build).
- T-14 (`isSetWallpaperAllowed()` false): not testable on this phone; not run.
- Lint: 0 errors, 8 warnings. Release 1.0: 2.0 MB, signed, not debuggable.

### Release 1.0 retest (iQOO, 2026-11-26, user's report; log not read)

The user reported done, with no problem mentioned: finger, PIN and face unlocks; adjust
screen with Save/Discard; pictures and setup kept after reopening; dark and light mode;
largest font size; saving the log. Not repeated on the release: restart, power off, force
stop, notifications off. **A8 (24-hour soak) and overnight: not run.**

### Release 1.0 overnight (iQOO, 2026-11-26 16:10 to 2026-11-27 11:36, log read)

The user left the phone idle overnight with UnlockWall cleared from recent apps. Whether
it was unplugged was not recorded. Log copy: `.tools/release/logs/` (this laptop only).

- No screen events from 01:01 to 07:23. The process had run since about 17:52 on 11-26
  and was still running at 11:32.
- First morning unlock (07:23:20): 3.2 s after screen on, Slow. Next (07:23:41): 386 ms,
  Quick.
- Whole log: 163 wakes, 52 Quick, 46 Slow, 12 kept Normal (just started), 53 without an
  unlock. Every wake with `USER_PRESENT` had a result. No "picture could not be set" line.
- Device unlock time: Quick at most 846 ms, Slow at least 1007 ms. The method used for
  each unlock is not logged, so correctness per method is not verified by the log.
- Picture change: median 224 ms, maximum 1059 ms.
- 3 times the screen came back on within about 1 s of going off, before Normal was set.
  Normal was set 0.02 to 0.5 s after that wake.
- Android killed the process 5 times for low memory (`dumpsys activity exit-info`: 16:28,
  17:21, 17:24, 17:26, 17:37 on 11-26). It was back within 2 s three times, and after
  about 14 minutes twice (whether by itself or by opening the app is not known). Normal
  was showing at each kill.
- Seen in the log: restart or power off (`SHUTDOWN`, `BOOT_COMPLETED`, Normal restored),
  switch off and on. Notification permission was denied on 11-27 and switching worked.
- **Not run:** force stop, forced power-off. **A8:** not yet 24 hours without a restart
  at this point (done below).

### Release 1.0, 24-hour test, A8 / T-12: passed (iQOO, 2026-11-28, log and `adb` read)

Phone state read with `adb` at about 10:19 on 11-28:

- The same app process had run since 11-26 17:52:30, **40 h 26 min**. The service was in
  the foreground and the battery exemption was on.
- No process exit since 11-26 17:37:49 and no phone restart since 11-26 16:24:44.
- Whether the phone was charging during the period was not recorded.

Logs (copies in `.tools/release/logs/`, this laptop only):

| Period | Length | Wakes | Unlocks | Quick | Slow | Normal kept |
|---|---|---|---|---|---|---|
| 11-26 17:52 to 11-27 11:36 | 17 h 44 min | 128 | 83 | 40 | 41 | 2 (just started) |
| 11-27 11:54 to 11-28 10:20 | 22 h 26 min | 184 | 92 | 43 | 48 | 1 (unclear) |

- **Every logged unlock (175) had one result.** No unlock had two. No "picture could not
  be set" line.
- Every screen off was followed by Normal being set or already showing.
- Second period: device unlock time was at most 974 ms for Quick and at least 1015 ms for
  Slow. From unlock to the set call returning: median 406 ms, maximum 1504 ms (not the
  time the picture became visible). Setting a picture: median 268 ms, maximum 623 ms.
- The unclear result (11-27 18:20:06): `USER_PRESENT` arrived 0.6 s after the screen went
  off again. Normal was kept, as designed.
- Twice in the second period the screen came back on before Normal was set; Normal was set
  111 ms and 53 ms after that wake.
- **Gap:** 11-27 11:36:53 to 11:54:35 is in neither saved log, because "Save log to
  Downloads" saves only the last 1000 lines. The process ran through it; unlocks in it
  were not checked.
- No face result (`FACE_UNLOCK`) appears in either period. The method used for each unlock
  is not logged.

### Lock does not always engage at screen off

Twice the screen was woken about 2 s after turning off and the phone was not locked
(`SCREEN_ON` with `kgLocked=false`). No `USER_PRESENT` is expected then. The lock-delay
setting on the phone has not been checked.
