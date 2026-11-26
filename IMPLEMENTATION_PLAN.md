# UnlockWall: Implementation Plan

Status (2026-11-26): **Phase 0 done; Phase 1 in progress.** The product app is still a
"hello" screen. Experiment results are in
[docs/EXPERIMENT_RESULTS.md](docs/EXPERIMENT_RESULTS.md); the direction they point to, and
what this plan must change after the Phase 1 gate, is in
[docs/HANDOFF.md](docs/HANDOFF.md) §8. See [README.md](README.md) for the feasibility
evidence behind this plan.

## 1. Guiding decisions

1. **`UNKNOWN` is a first-class unlock method.** No public API for the method was
   found, so the app must be fully useful with only "unlocked" + a fallback wallpaper.
2. **Observe, never intervene.** No overlay, no keyguard dismissal, no accessibility
   interception. The unlock path is never touched.
3. **Experiment before building.** A throwaway spike app (Phase 1) establishes what is
   observable on the iQOO Z9x before any product code is written.
4. **Pure-Kotlin core.** The state machine and policy have no Android dependencies so they
   are unit-testable on this machine without a device.
5. **Fail safe:** any error → leave the current wallpaper unchanged.

## 2. Architecture

### 2.1 Modules / packages (single Gradle module `app`)

| Package | Responsibility |
|---|---|
| `ui` | Compose screens: Home (ON/OFF, status/health), Mapping (per-method wallpaper picker), Fallback, Diagnostics log |
| `settings` | `SettingsRepository` over Jetpack DataStore: `enabled`, `mapping[method]→WallpaperRef`, `fallback`, `target` (home/lock/both) |
| `monitor` | `UnlockMonitorService` (FGS, `specialUse`), runtime `BroadcastReceiver`, `BootReceiver` |
| `core` | `UnlockStateMachine`, `UnlockMethodDetector` interface, `WallpaperPolicy`; **no Android imports** |
| `wallpaper` | `WallpaperApplier` (WallpaperManager wrapper), `WallpaperCache` |
| `diagnostics` | Ring-buffer event log (timestamps, states) shown in-app and exportable |

### 2.2 Core types

```kotlin
enum class UnlockMethod { PIN_OR_PATTERN_LIKELY, BIOMETRIC_LIKELY, PIN, PATTERN, FINGERPRINT, FACE, UNKNOWN }
data class UnlockEvent(val atElapsedMs: Long, val method: UnlockMethod, val confidence: Float)

interface UnlockMethodDetector {            // v1 ships UnknownDetector only
    fun classify(signals: UnlockSignals): UnlockMethod
}
```

The fine-grained values (`PIN`, `FACE`…) exist in the type so the mapping UI can be
built, but are only produced if Phase 1 proves a technique works. Otherwise they are
hidden in the UI.

### 2.3 State machine (pure Kotlin)

States: `UNLOCKED_INTERACTIVE`, `SCREEN_OFF`, `LOCKED_SCREEN_ON`.

- `SCREEN_OFF` → `SCREEN_OFF` state (record time). Keyguard is *showing* per docs.
- `SCREEN_ON` → `LOCKED_SCREEN_ON` (record time). The state is validated with
  `isKeyguardLocked()` at that moment.
- `USER_PRESENT` while in `LOCKED_SCREEN_ON` → emit `UnlockEvent`. A `USER_PRESENT`
  received in any other state is logged and **ignored**. This is the structural guard
  against confusing in-app auth with device unlock.
- Debounce duplicate `USER_PRESENT` within N ms.
- Re-sync on service start via `isKeyguardLocked()` / `PowerManager.isInteractive`,
  because broadcasts can be missed while the process was dead.

### 2.4 Service and receiver

- `UnlockMonitorService`: FGS type `specialUse` (manifest `<property>` declaring the
  use case), low-importance ongoing notification, registers the receiver with
  `RECEIVER_EXPORTED` (with `NOT_EXPORTED`, `USER_PRESENT` never arrives on the iQOO; see
  [docs/EXPERIMENT_RESULTS.md](docs/EXPERIMENT_RESULTS.md) F1) and checks the action;
  unregisters in `onDestroy`. Started from the UI when the switch turns ON and from `BootReceiver`
  (`BOOT_COMPLETED`) if enabled.
- **Switch OFF** = service stopped, receiver unregistered. The policy also checks
  `enabled` on every event, so a race cannot apply a wallpaper while OFF.
- The receiver's `onReceive` only forwards the event to the state machine and returns
  immediately (no I/O, no decode), so it cannot delay anything.

### 2.5 Wallpaper cache and applier

- On selection, copy the picked image (Photo Picker / `GetContent`; no storage
  permission) into app-private `filesDir/wallpapers/`, decoded once and **downsampled
  to the screen's pixel size**, stored as WebP/JPEG, keyed by method.
- On event, a single-thread dispatcher loads the file, calls
  `setStream`/`setBitmap(..., which)` with `FLAG_SYSTEM`/`FLAG_LOCK` per the setting,
  after checking `isWallpaperSupported()` and `isSetWallpaperAllowed()`.
- Skip when the target equals the last-applied wallpaper id, to avoid pointless work and
  flicker.
- Catch every exception; on failure keep the current wallpaper and log.

### 2.6 Fallback behaviour (requirement 7)

Setting `fallbackMode ∈ { KEEP_CURRENT, USE_FALLBACK_IMAGE }`. Policy:
`method == UNKNOWN` or no mapping for the method → apply fallback mode.

## 3. Phases

### Phase 0: Toolchain (done 2026-11-26; results in docs/HANDOFF.md)
- Install a full JDK (17 or 21 with `javac`), Android command-line tools,
  `platform-tools` (adb), `platforms;android-36`, `build-tools`.
- Enable USB debugging on the phone (or wireless debugging); confirm `adb devices`.
- Record exact firmware (`getprop ro.build.version.*`, OriginOS build).
- **Exit:** `adb devices` lists the phone; a Gradle "hello" app installs and runs.

### Phase 1: Spike / experiments (throwaway app; the decisive phase)
A minimal logging app (FGS + runtime receiver) that records, with
`SystemClock.elapsedRealtimeNanos`, every of: `SCREEN_ON/OFF`, `USER_PRESENT`,
`isKeyguardLocked`, `isDeviceLocked`, `PowerManager.isInteractive`, display state,
and (if granted) `UsageEvents` keyguard events. Run the scenario matrix in §5 and
inspect logs. Also `adb logcat` / `dumpsys` captures during unlocks.

Questions it must answer:
- E1: Does `USER_PRESENT` fire reliably for every unlock type, and with what latency?
- E2: Is there *any* signal that differs between PIN, pattern, fingerprint, face?
  (timing distributions, `dumpsys` fields visible to adb but NOT to apps are out of scope).
- E3: Do in-app biometric prompts and credential confirmations emit anything?
- E4: Does the service survive screen-off, an hour, overnight, reboot, force-stop from
  recents, with and without Autostart/battery exemption?
- E5: How long does `setBitmap` take with cached 1080p images, and is there a visible
  flash of the old wallpaper? Does `FLAG_LOCK` work and show on the AOD/lock screen?
- E6: Does OriginOS theming override the API-set wallpaper?

**Exit gate (go/no-go):** document in `docs/EXPERIMENT_RESULTS.md`. If E2 finds no
usable discriminator (expected), product scope = "change wallpaper on every unlock",
with the method-mapping UI hidden or marked experimental.

### Phase 2: Core logic (device-independent)
`core` module + unit tests for state machine and policy; `settings` repository;
`WallpaperCache` with instrumented tests. **Exit:** all unit tests pass.

### Phase 3: Service, boot, wallpaper integration
Service, notification, boot receiver, applier. Health screen showing: service running,
battery exemption state, notification permission, last event, last result.
**Exit:** acceptance tests A1-A6 on device.

### Phase 4: UI
Compose: ON/OFF, wallpaper pickers, fallback option, target (home/lock/both),
diagnostics log, guided setup for battery/autostart. **Exit:** A7-A8.

### Phase 5: Hardening
Soak test (24 h), error injection (deleted cache file, no permission, policy block),
accessibility and dark-mode pass, release signing, `lint`, optional Play-policy check.

## 4. Open design question for the user (decide after Phase 1)

Because the wallpaper changes *after* the keyguard is gone, the home screen may flash
the old wallpaper. Options: (a) accept the flash; (b) set `FLAG_LOCK` only, so the
change is seen at the next lock screen; (c) rotate based on the previous unlock.
The choice depends on E5 results.

## 5. Test plan

### 5.1 Unit tests (host JVM, no device)
- State machine: ordering, ignore stray `USER_PRESENT`, debounce, re-sync.
- Policy: enabled/disabled, mapped/unmapped, UNKNOWN + each fallback mode.
- Settings round-trip; cache keying and eviction.

### 5.2 Device test matrix (iQOO Z9x, physical)

| ID | Scenario | Expected (to be confirmed) |
|---|---|---|
| T-01 | Unlock via PIN | One `UnlockEvent`, one wallpaper action |
| T-02 | Unlock via pattern | same |
| T-03 | Unlock via fingerprint | same |
| T-04 | Open banking app / WhatsApp, authenticate with biometric or PIN | **Zero** events, **zero** wallpaper changes |
| T-05 | Settings → "confirm device credential" screen | zero events |
| T-06 | Unlock via face | same as T-01 |
| T-07 | Swipe-only lock (temporary test config) | Documented behaviour of `USER_PRESENT`, recorded |
| T-08 | Switch OFF, then unlock all ways | Zero wallpaper changes; service not running |
| T-09 | Switch ON → OFF → ON repeatedly | No duplicate receivers (one event per unlock) |
| T-10 | Reboot with switch ON | Service up after first unlock, per OriginOS rules |
| T-11 | Swipe app from recents / force stop | Recorded survival behaviour |
| T-12 | 24 h soak, screen-off idle, with battery exemption | Event counts equal unlock counts |
| T-13 | Missing/corrupt cached file | Wallpaper unchanged, error logged, no crash |
| T-14 | Policy: `isSetWallpaperAllowed()` false (if testable) | Graceful no-op |
| T-15 | Unlock latency: stopwatch/high-speed video with the app ON versus uninstalled | No perceptible difference; log timing |
| T-16 | Rapid lock/unlock cycles (10 in 20 s) | No crash, no queue build-up |
| T-17 | Unknown method path | Fallback applied per setting |
| T-18 | Trusted device / on-body detection (if configured) | Behaviour recorded |

### 5.3 Measurement of "never delay unlock" (req. 4)
The receiver runs after unlock completion by documentation, so it cannot gate it.
Evidence required anyway: T-15 comparison plus `onReceive` duration logged
(target: < 5 ms) and wallpaper work confirmed off the main thread.

## 6. Acceptance criteria

| ID | Criterion | Evidence needed |
|---|---|---|
| A1 | With switch ON, each real device unlock produces exactly one wallpaper update (or fallback no-op) within 2 s | T-01..T-03, T-06 logs |
| A2 | Authentication inside other apps never triggers a change | T-04, T-05 |
| A3 | No perceptible unlock delay | T-15 |
| A4 | With switch OFF, no automatic change ever occurs and the service is not running | T-08, `dumpsys activity services` |
| A5 | Selected wallpapers are cached app-privately; the app works after the source image is deleted | T-13 + manual |
| A6 | Unknown method → keeps current or applies fallback per setting | T-17 |
| A7 | No root; no custom ROM; only permissions listed in README §1.9 | manifest review |
| A8 | Survives 24 h soak with documented OEM settings applied | T-12 |
| A9 | The documentation states plainly which unlock methods are detected on this device, backed by Phase 1 results | `docs/EXPERIMENT_RESULTS.md` |

A feature is "working" only when its row has logged device evidence.

## 7. Risk register

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Unlock method not detectable by a normal app | High | Headline feature lost | Phase 1 gate; UNKNOWN-first design; honest scoping |
| R2 | OriginOS kills FGS / blocks autostart | Medium-High | No changes occur | Boot start, battery exemption, health screen, setup guide; soak test |
| R3 | Flash of old wallpaper / OEM wallpaper engine override | Medium | Poor UX | E5/E6; `FLAG_LOCK` option |
| R4 | `USER_PRESENT` semantics differ (swipe lock, trust agents) | Medium | Spurious changes | State machine + `KeyguardManager` validation |
| R5 | Play policy rejects `specialUse` use case | Medium | Distribution only | Sideload/internal track first |
| R6 | Android 16 background changes affect job-based designs | Low (we avoid jobs) | n/a | Keep the work in the receiver/worker thread |
| R7 | Accessibility-based detection tempts scope creep | Low | Policy, fragility | Explicitly out of scope for v1 |
| R8 | No toolchain/device available | Resolved (Phase 0) | n/a | Setup recorded in docs/HANDOFF.md |
| R9 | OriginOS 6 docs absent; behaviour differs from Funtouch evidence | Medium | Surprises | All OEM claims treated as unverified until tested |

## 8. Out of scope (v1)

Root, custom ROM, accessibility interception, overlays, device-admin / Device Owner
modes, any attempt to read or infer credentials, any network features.

## 9. Recommended next step

Do **Phase 1**: build the logging spike app; run the unlock matrix; write
`docs/EXPERIMENT_RESULTS.md`. Only then commit to the final product scope.
