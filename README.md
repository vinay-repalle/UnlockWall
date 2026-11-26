# UnlockWall

A native Android app (Kotlin, Jetpack Compose, Gradle) that changes the wallpaper
when the phone is unlocked, with the goal of choosing the wallpaper by *how* it was
unlocked (PIN, pattern, fingerprint, face).

- **Target device:** iQOO Z9x 5G (model PD2353), Android 16 (OriginOS 6)
- **Constraints:** no root, no custom ROM, never delay or interfere with unlocking
- **Status (2026-11-26):** Phase 0 done. A "hello" app builds, installs and runs on the
  phone. **No unlock detection or wallpaper feature exists yet.** Next: Phase 1.

Current state: [docs/HANDOFF.md](docs/HANDOFF.md) ·
Plan: [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)

## Build and install

```bash
scripts/contain.sh ./gradlew assembleDebug
scripts/contain.sh adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The toolchain lives in `.tools/` (not in git). Versions are in
[docs/HANDOFF.md](docs/HANDOFF.md).

---

*Sections 1 to 6 are the Stage 1 report, written before Phase 0. No experiment on
unlock or wallpaper behaviour has been run since.*

## 1. Stage 1 feasibility report

### 1.1 How evidence is labelled

| Label | Meaning |
|---|---|
| **[DOC]** | Stated in official Android documentation that I read (links in §6). This says what the API *claims*, not that it behaves that way on this phone. |
| **[DEVICE]** | Plausible, but behaviour must be confirmed by an experiment on the physical iQOO Z9x. |
| **[NO]** | No public API found, or the API is not available to a normal third-party app. |

No experiment has been run, so **nothing is verified on hardware.** Every "works"
below means "documented to work", not "observed working".

### 1.2 Summary by requirement

| # | Requirement | Verdict |
|---|---|---|
| 1 | Detect locked → unlocked transition | **[DOC] + [DEVICE]** `ACTION_USER_PRESENT` via a runtime-registered receiver. Needs a live process, so a foreground service. Edge cases need device tests. |
| 2 | Distinguish PIN / pattern / fingerprint / face | **[NO]** No public API found. Only unverified heuristics are possible (§1.4). Treat as unsupported until an experiment proves otherwise. |
| 3 | Ignore in-app authentication (banking, WhatsApp) | **[DOC] + [DEVICE]** Achievable by design. We only act on the keyguard-gone signal, never on any biometric event. App-level `BiometricPrompt` is a separate mechanism. Must be confirmed that it never emits `USER_PRESENT`. |
| 4 | Never delay or interfere with unlock | **[DOC]** By design: the signal fires *after* the keyguard is gone, and we only observe. Wallpaper work is off the main thread. Needs a latency measurement. |
| 5 | ON/OFF switch | Pure app logic, fully in our control. |
| 6 | Cache selected wallpapers locally | Pure app logic, fully in our control. |
| 7 | Unknown method → keep wallpaper or fallback | Pure app logic. Given #2, this is the **default path**, not an edge case. |
| 8 | No root, no custom ROM | **[DOC]** Everything proposed uses normal or user-granted permissions. |

**Bottom line:** the part of the product that depends on knowing *which method* was
used (the headline feature) has **no documented public API** and should be assumed
infeasible until a device experiment says otherwise. Everything else is feasible.

### 1.3 Lock-state detection

| Mechanism | Finding | Label |
|---|---|---|
| `Intent.ACTION_USER_PRESENT` | "Sent when the user is present after device wakes up (e.g when the keyguard is gone)." Protected, system-only. Does not say it is skipped when no secure lock is set, and the docs don't promise delivery in any particular order relative to the wallpaper being drawn. | **[DOC]**, behaviour **[DEVICE]** |
| Manifest-declared `USER_PRESENT` receiver | Apps targeting API 26+ cannot receive implicit broadcasts via the manifest unless the broadcast is on the exemption list. I fetched the list; `USER_PRESENT` and `SCREEN_ON/OFF` are **not** on it. `ACTION_SCREEN_ON` additionally states: "You cannot receive this through components declared in manifests". So a runtime-registered receiver is required. | **[DOC]** |
| `ACTION_SCREEN_ON` / `SCREEN_OFF` | Runtime registration only. `SCREEN_ON` is documented as "device becomes interactive", which "may have nothing to do with the screen turning on". The broadcasts page says that while an app is cached the system defers less important broadcasts such as `ACTION_SCREEN_ON`. | **[DOC]** |
| `KeyguardManager.isKeyguardLocked()` | Returns whether the keyguard is *showing*, including when occluded, and does not distinguish a swipe-only lock screen from a secure one. Useful as a state query to validate transitions, not as a listener. | **[DOC]** |
| `KeyguardManager.isDeviceLocked()` | Returns true when authentication is required; "Swipe does not count as authentication". Also a point-in-time query. | **[DOC]** |
| `KeyguardManager.addKeyguardLockedStateListener` / `addDeviceLockedStateListener` | Require `SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE`, protection level `signature\|privileged\|module\|role`, "Intended for use by ROLE_ASSISTANT, VDM, and signature / privileged apps only". | **[NO]** for a normal app |
| `UsageEvents.Event.KEYGUARD_HIDDEN` / `KEYGUARD_SHOWN` (API 28) | "Keyguard has been hidden. This typically happens when the user unlocks their phone after turning it on." Read through `UsageStatsManager.queryEvents`, which needs the special-access `PACKAGE_USAGE_STATS` permission that the user grants in Settings. Pull-based (polling or query-on-wake), so a possible secondary signal. | **[DOC]**, behaviour **[DEVICE]** |

**Recommended primary signal:** `USER_PRESENT` from a runtime receiver, validated by
`isKeyguardLocked()` / `isDeviceLocked()` and a preceding `SCREEN_OFF`.
`KEYGUARD_HIDDEN` via usage stats is an optional cross-check.

### 1.4 Distinguishing the unlock method

**Documented public API: none found.**

- `KeyguardManager` exposes *whether* a secure lock exists (`isDeviceSecure`,
  `isKeyguardSecure`) and *whether* the device is locked. I found no method returning
  the credential or modality used. I searched the `KeyguardManager` reference text for
  this and found none.
- `BiometricPrompt` / `BiometricManager` / `USE_BIOMETRIC` serve *the app's own*
  authentication prompt. They say nothing about the system lock screen.
  (This is my reading of the API shape, and I did not find text stating it outright.)
- A web search found only forum and consultant commentary (B4X forum, CommonsWare
  office hours) saying standard apps cannot tell, with a plausible security rationale
  (a malicious app could otherwise exploit that knowledge). **Those are secondary
  sources, not official documentation.** I treat them as supporting, not proof.

**Heuristic ideas, all unproven [DEVICE], none to be presented as working:**

1. *Timing:* delay between `SCREEN_ON` and `USER_PRESENT`. Fingerprint and face might
   unlock faster than PIN entry. Overlaps heavily, and the timing depends on whether the
   user swipes first. May separate "biometric-like" from "typed credential" at best.
2. *Wake reason:* whether the screen woke from a power-button press, a lift, or the
   fingerprint sensor. Not exposed through a public API that I found.
3. *Accessibility service:* observing SystemUI window or view events to see whether a
   PIN pad or pattern grid appeared. It would not identify fingerprint versus face
   either. It is invasive, fragile across OEM skins, and heavily scrutinised by Play
   policy and by OriginOS. I recommend against it for v1.

**Realistic ceiling to test for:** "a keypad/pattern credential was probably entered"
versus "probably a biometric/trusted unlock". Fingerprint versus face is almost
certainly not separable. The app must be designed so the method-to-wallpaper mapping
works with `UNKNOWN` as the normal outcome.

### 1.5 Authentication inside apps

In-app prompts (`BiometricPrompt`) do not involve the keyguard, so by documented
semantics they should not produce `USER_PRESENT`, and `isKeyguardLocked()` would not
change. We keep that guarantee structurally: **the app subscribes to no biometric
event and only reacts to a keyguard-gone transition that was preceded by a
keyguard-showing state.** **[DEVICE]** test T-04 confirms this with a real banking
or WhatsApp prompt, plus a "confirm credential" screen from Settings.

### 1.6 Background execution on Android 16

- Runtime receivers only work while the process lives → a **foreground service** is
  the practical host. **[DOC]** (manifest receiver not allowed, see §1.3.)
- FGS type `specialUse` exists (permission `FOREGROUND_SERVICE_SPECIAL_USE`, normal
  protection) for uses not covered by other types, and its declared use case is
  reviewed in Play Console. **[DOC]** Needs a persistent notification. Whether
  Play would accept this use case is **uncertain**; sideloading avoids review.
- Starting a FGS from the background is restricted (API 31+). Exemptions include
  `BOOT_COMPLETED`, and the user disabling battery optimisation for the app.
  **[DOC]** → start on boot, and ask for the battery exemption.
- Android 16 applies JobScheduler runtime quotas to jobs that run alongside a
  foreground service, or that started while the app was visible. **[DOC]** → do not
  put the wallpaper change in a WorkManager job. Do it directly in the receiver
  callback on a worker thread.
- Android 16 clamps ordered-broadcast priority to within a process. **[DOC]**
  Irrelevant to us (we do not depend on priority), noted for completeness.
- Android 16 runs 4 KB-aligned apps in a 16 KB page-size compatibility mode with a
  dialog. **[DOC]** Using current AGP/NDK-free Kotlin avoids this.

### 1.7 Wallpaper changes

- `WallpaperManager.setBitmap(bitmap, cropHint, allowBackup, which)` and
  `setStream(...)`, with `FLAG_SYSTEM` (home) and `FLAG_LOCK` (lock screen), API 24+.
  Requires `SET_WALLPAPER`, a **normal** permission, install-time, no prompt.
  **[DOC]**
- `isSetWallpaperAllowed()` may be false under device policy, and then "any attempts
  to change the wallpaper will have no effect". **[DOC]** → check before applying.
- Setting is synchronous IPC with image decode/scale cost. How long it takes on this
  phone, and whether OriginOS shows a visible flash of the old wallpaper after
  unlock, is **[DEVICE]**.
- Because the change happens *after* the keyguard is gone, the user may briefly see the
  previous home wallpaper. Applying to `FLAG_LOCK` (visible next time) or
  pre-selecting is a design choice (§4 of the plan).
- OriginOS themes and "dynamic/live" wallpaper features may override or fight the
  standard API. **[DEVICE]**

### 1.8 Manufacturer-specific (iQOO Z9x, OriginOS 6)

Evidence found:

- Z9x began receiving **Android 16-based OriginOS 6** (firmware
  PD2353F_EX_A_16.2.11.0.W30 initially, then a July 2026 OTA PD2353NF_EX_A_16.2.16.0.W30
  whose changelog mentions an optimised power-saving strategy). Source: news and the
  iQOO community forum (secondary). The user's exact build must be read from the phone.
- dontkillmyapp.com documents **Vivo** (not iQOO or OriginOS 6) as killing background
  apps lacking *Autostart* permission, with further battery/background-consumption
  settings. It states Vivo's restrictions "have not been fully uncovered". Treat as
  **an indication of risk, not as a verified description of this phone.**

**Not found:** any OriginOS 6-specific documentation on auto-start, background
management, or lock-screen wallpaper behaviour. This is an evidence gap that
must be closed by experiment (plan Phase 1).

### 1.9 Permissions and limitations

| Permission / grant | Type | Purpose | Limitation |
|---|---|---|---|
| `SET_WALLPAPER` | normal | Set wallpaper | Blocked if `isSetWallpaperAllowed()` is false |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | normal | Host the receiver | Persistent notification; Play review of the declared use case; OEM may still kill it |
| `POST_NOTIFICATIONS` | runtime (API 33+) | Show the FGS notification | User can deny; the FGS still runs but the notification may be hidden |
| `RECEIVE_BOOT_COMPLETED` | normal | Restart after reboot | Doc notes it increases startup cost |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | normal | Ask for the battery exemption | The user chooses; OEM-specific Autostart and background settings are separate toggles that no public API controls |
| `PACKAGE_USAGE_STATS` (optional) | special access | `KEYGUARD_HIDDEN` cross-check | User must enable it in Settings; broad data access; optional only |
| `SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE` | signature/privileged | Keyguard listener | **Not obtainable by us** |
| Accessibility service (not planned) | special access | UI heuristics | Not recommended (§1.4) |

---

## 2. Proposed architecture (summary)

Single-module Kotlin app, Compose UI, DataStore for settings.

```
UI (Compose)  ──►  SettingsRepository (DataStore: enabled, mapping, fallback)
                        │
UnlockMonitorService (FGS, specialUse) ── registers runtime receiver
      │  SCREEN_OFF / SCREEN_ON / USER_PRESENT
      ▼
UnlockStateMachine  (pure Kotlin, unit-testable)
      │  emits UnlockEvent(timestamp, method = UNKNOWN | HEURISTIC_x, confidence)
      ▼
WallpaperPolicy  (enabled? mapping? fallback? debounce?)  ──►  WallpaperApplier
                                                         (worker thread, cached bitmaps)
WallpaperCache (app-private files, downsampled to screen size)
```

Details, interfaces, and phases are in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

---

## 3. Technical risks (top)

1. **Unlock method undetectable** (very likely). Mitigation: `UNKNOWN` is first-class; fallback wallpaper; scope the product honestly.
2. **OriginOS kills the service** or blocks autostart → no wallpaper changes. Mitigation: boot start, battery exemption, a "health" screen, device soak test.
3. **Visible flash** of old wallpaper after unlock; or OEM wallpaper engine ignoring the API.
4. **`USER_PRESENT` semantics** (fires for swipe-only locks, after trusted-device unlock, possibly after in-app flows). Mitigation: state machine + `KeyguardManager` validation.
5. **Play policy** for `specialUse` FGS, if distribution via Play is wanted.
6. ~~Tooling gap~~: resolved in Phase 0.

---

## 4. Environment inspected (this machine, before Phase 0)

*Out of date: the toolchain is now installed in `.tools/`.*

| Item | Result |
|---|---|
| OS / CPU / RAM / disk | Linux 7.0, x86_64, 12 cores, 15 GiB RAM (about 3.7 GiB free at inspection), 758 GB free |
| Java | OpenJDK 21 **runtime** present; `javac` **missing** (no JDK) |
| Kotlin, Gradle | not installed (Gradle wrapper would supply Gradle) |
| Android SDK / `adb` / `sdkmanager` / emulator | **not installed** (no `ANDROID_HOME`, no `~/Android`) |
| Android Studio | not found; VS Code present (`/snap/bin/code`) |
| `/dev/kvm` | present (emulator acceleration possible) |
| Physical device | **none detected** over USB (`lsusb`) |
| Project directory | was empty; not a git repository |

Consequence: nothing can be built or tested yet. An emulator would be useful for UI
and state-machine logic but **cannot** tell us about OriginOS keyguard or wallpaper
behaviour, which is the central uncertainty. The physical phone is required.

---

## 5. Stage 1 status

- [x] Environment inspected
- [x] Public documentation researched (reference pages downloaded and searched directly)
- [x] Requirements classified (§1.2)
- [x] `README.md` and `IMPLEMENTATION_PLAN.md` written
- [ ] **No code written; no experiment run; no feature verified.**

**Honest limits of this report:** the Android reference pages are very large; I
extracted the quoted passages from the downloaded page text rather than skimming.
Claims about OriginOS rely on secondary sources. A search tool summary was used for
the "no public API for unlock method" negative result, and a negative result cannot
be *proven* from docs, only supported, so Phase 1 experiments are required.

---

## 6. Sources

- [Intent: ACTION_USER_PRESENT, ACTION_SCREEN_ON](https://developer.android.com/reference/android/content/Intent)
- [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager)
- [Manifest.permission](https://developer.android.com/reference/android/Manifest.permission)
- [WallpaperManager](https://developer.android.com/reference/android/app/WallpaperManager)
- [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)
- [Broadcasts overview](https://developer.android.com/develop/background-work/background-tasks/broadcasts)
- [Implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)
- [Foreground service types (specialUse)](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Restrictions on starting FGS from background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Android 16 behavior changes (all apps)](https://developer.android.com/about/versions/16/behavior-changes-all)
- Secondary: [B4X thread](https://b4x.com/android/forum/threads/detecting-screen-unlock-method.136219), [CommonsWare office hours](https://commonsware.com/office-hours/2019-03-16), [dontkillmyapp: Vivo](https://dontkillmyapp.com/vivo), [Croma on OriginOS 6 for Z9/Z9x](https://www.croma.com/unboxed/android-16-based-originos-6-arrives-on-iqoo-z9-and-z9x), [iQOO community: July 2026 Z9x update](https://community.iqoo.com/in/thread/162669)
