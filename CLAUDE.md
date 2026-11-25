# UnlockWall: rules for Claude

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

## Project

- Native Android app: Kotlin, Jetpack Compose, Gradle. See `README.md` and `IMPLEMENTATION_PLAN.md`.
- Target phone: iQOO Z9x 5G (PD2353), Android 16 (OriginOS 6).
- GitHub (personal): `git@github-personal:vinay-repalle/UnlockWall.git`. The user manages it.

## Local toolchain (all inside this folder)

| What | Where |
|---|---|
| Java | system JDK 21 (`/usr/bin/java`), used as is |
| Android SDK | `.tools/android-sdk` (`ANDROID_HOME`) |
| Gradle cache | `.tools/gradle` (`GRADLE_USER_HOME`) |
| Android user data | `.tools/android-user` (`ANDROID_USER_HOME`) |

These variables are set in `.claude/settings.json` under `env`. `.tools/` is git-ignored.
