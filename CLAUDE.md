# UnlockWall: rules for Claude

**Start here:** read [docs/HANDOFF.md](docs/HANDOFF.md) for the current state and next step.

## Hard rules (always follow)

1. **Stay inside this folder.** Only create, edit or delete files inside
   `/home/vinayrepalle/Documents/UnlockWall`. This is an office laptop: never touch
   anything outside it (no `~/.bashrc`, `~/.gitconfig`, `~/.ssh`, `~/.gradle`, `~/.android`, etc.).
   Every tool, SDK and cache goes in `.tools/` inside this folder.
2. **No git, ever.** Never run `git` or `gh` commands, not even read-only ones like
   `git status`. The user does all git work with their personal GitHub account.
   When work is ready, give a **commit message only**.
3. **Ask before open decisions.** Before any choice that has options (design, library,
   new tool, anything that downloads or installs), ask the user first and always
   include your recommendation.
4. **Free tier only.** No paid services, APIs, subscriptions or accounts. Example: no
   Google Play Console (it has a fee). Install the APK directly on the phone instead.
5. **No sudo.** The user has no admin rights on this laptop.

## How to answer

- Short, clear and simple. Plain words. Add detail only when asked.

## Work order

- One phase at a time, in the order in `IMPLEMENTATION_PLAN.md`.
- For each phase: short plan and open questions, wait for approval, build, test against
  the exit criteria, update the docs (including `docs/HANDOFF.md`), give a commit
  message, then stop until the user approves the next phase.
- Never call a feature working without test evidence. Say what was not tested.

## Project

- Native Android app: Kotlin, Jetpack Compose, Gradle. See `README.md` and `IMPLEMENTATION_PLAN.md`.
- Target phone: iQOO Z9x 5G (PD2353), Android 16 (OriginOS 6).
- GitHub (personal): `git@github-personal:vinay-repalle/UnlockWall.git`. The user manages it.

## Local toolchain (all inside this folder)

| What | Where |
|---|---|
| Java | Temurin JDK 21 in `.tools/jdk` (`JAVA_HOME`; the system Java has no `javac`) |
| Android SDK | `.tools/android-sdk` (`ANDROID_HOME`) |
| Gradle cache | `.tools/gradle` (`GRADLE_USER_HOME`) |
| Android user data | `.tools/android-user` (`ANDROID_USER_HOME`) |

`scripts/contain.sh` sets all of these. The Android and Gradle ones are also in
`.claude/settings.json` under `env`. `.tools/` is git-ignored.

**Always run SDK and build tools through `scripts/contain.sh`**, e.g.
`scripts/contain.sh ./gradlew assembleDebug` or `scripts/contain.sh adb devices`.
It uses `bwrap` (no sudo) to send all home-folder writes to `.tools/home`. Without it,
tools write to the real home (the SDK's `android` tool created `~/.java` once; it was removed).
Never run `android completion` or `android auth`: they change the user profile or sign in.
