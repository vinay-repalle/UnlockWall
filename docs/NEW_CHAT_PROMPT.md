# Prompt for a new Claude chat

Open the `UnlockWall` folder in the editor, start a new chat, and paste this:

```text
You are continuing my project UnlockWall, in /home/vinayrepalle/Documents/UnlockWall.
It is a native Android app (Kotlin, Jetpack Compose) that changes the wallpaper when
my phone is unlocked. You have no memory of earlier chats; the files hold the context.

First, read in this order:
1. CLAUDE.md (rules)
2. docs/HANDOFF.md (current state and next step)
3. IMPLEMENTATION_PLAN.md (phases, architecture, tests)
4. README.md (feasibility findings)

Rules (always):
- Only create, edit or delete files inside the UnlockWall folder. This is an office laptop.
- Never run git or gh, not even read-only. Just give me a commit message.
- Ask me before any open decision, download or install, and include your recommendation.
- Free tools only. No sudo.
- Run all SDK and build tools through scripts/contain.sh.
- Answer short, simple and in plain words.
- Never call something working unless you tested it. Say what was not tested.

Then check the setup using the commands in docs/HANDOFF.md section 3, and tell me in a
few lines: what is done, whether the checks pass, and what is next. Do not change
anything yet; wait for my go-ahead.

Work one phase at a time, in the plan's order (0 is done, then 1, 2, 3, 4, 5):
1. Give me a short plan for the phase and ask your open questions.
2. After my approval, build it.
3. Test it against the phase's exit criteria (on my phone where needed).
4. Update docs/HANDOFF.md and the other docs with the real results.
5. Give me a commit message.
6. Stop and wait for my approval before the next phase.
Phase 1 ends with a go/no-go gate: show me the experiment results and agree the scope
with me before Phase 2.
```
