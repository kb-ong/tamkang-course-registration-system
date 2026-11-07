---
name: build-feature
description: Run the multi-agent pipeline (planner → coder + tester → test/fix loop → reviewer → human approval → git-publisher) for a Course Registration requirement such as "FR1" or "FR3 + FR6". Use when the user asks to build, implement or deliver a feature or requirement of the course registration system.
argument-hint: <requirement ids or description, e.g. "FR3 + FR6">
---

# /build-feature: multi-agent delivery pipeline

Requirement to build: **$ARGUMENTS**

You are the **orchestrator**. You don't write code or tests yourself. You call the subagents (`planner`, `coder`, `tester`, `reviewer`, `git-publisher`) with the Agent tool, pass results between them, and keep the human informed with one short line per stage.

Pick a feature id from the requirement, e.g. `FR3-FR6-register`. Working files go in `.pipeline/<feature-id>/`.

## Stage 0: Pre-flight
- If `$ARGUMENTS` is empty, ask which requirement to build (see the build order in `docs/requirements/phase1.md`).
- Run `git status`. If there are uncommitted changes unrelated to this feature, tell the human and ask before continuing.
- Run `git ls-remote --heads origin main`. If the remote has no `main` yet, tell the human that the repo needs an initial commit on main first, and stop.
- Create `.pipeline/<feature-id>/`.

## Stage 1: Plan
Call **planner** with the requirement, the feature id, and the instruction to write `docs/specs/<feature-id>.md`.
Show the human the summary and assumptions in a few lines. Don't wait for approval here; the human approves everything at Stage 5. If the planner says the feature is too big, ask the human whether to accept its proposed split.

## Stage 2: Build (in parallel)
Send **both** calls in one message so they run at the same time:
- **coder**: "Implement `docs/specs/<feature-id>.md`."
- **tester** (Mode A): "Write tests for `docs/specs/<feature-id>.md`. Do not read production code (src/main/ or non-test files in frontend/)."

## Stage 3: Test and fix loop (max 3 rounds)
1. Call **tester** (Mode B): "Run the tests and triage. Write `.pipeline/<feature-id>/test-report.md`."
2. If `ALL PASS`, go to Stage 4.
3. Otherwise route each item:
   - CODE BUG → **coder**: "Fix these failures: <items>. Report: `.pipeline/<feature-id>/test-report.md`."
   - SPEC GAP → you decide whether it is minor (choose the reading closer to `docs/requirements/phase1.md`, add it to the spec's Assumptions, and tell both sides). If it changes behaviour the human would care about, ask the human.
   - A coder claim that a test is wrong → send it to **tester** to check against the spec.
4. Repeat. After **3 rounds** with failures remaining, stop and show the human the remaining failures and the report path. Ask whether to keep going, change the spec, or abort.

## Stage 4: Review (max 2 rounds)
Call **reviewer**: "Review feature `<feature-id>` against `docs/specs/<feature-id>.md`."
- `CHANGES REQUESTED` → send `[coder]` items to **coder** and `[tester]` items to **tester**, re-run Stage 3 (tester Mode B), then review again.
- After 2 review rounds still not approved → hand over to the human with the open issues.

## Stage 5: Human approval gate (mandatory)
Present a concise summary:
- What was built (requirement IDs, key files)
- Test results: total and passing, and the criterion → test coverage table (from `test-report.md`)
- The review verdict, plus non-blocking suggestions and "manual checks" from `review.md`
- The spec's assumptions the human should confirm
- How to try it: `mvn spring-boot:run`, then http://localhost:8080 and a seed login from the spec

Then use **AskUserQuestion** with these options:
- **Approve & publish** → Stage 6
- **Request changes** → get the details, send them to the right agent(s), and go back to Stage 3
- **Stop here** → leave everything uncommitted and end

**Never go to Stage 6 without an explicit "Approve" from the human in this run.** Silence, "looks good so far", or an earlier approval for a different feature don't count.

## Stage 6: Publish
Call **git-publisher** with the feature id, the spec path, a summary, the test count, the review verdict, the assumptions, and the statement "Human approved on <date>".
Give the human the branch name and the PR link (or compare URL) it returns.

## Rules for you as orchestrator
- Don't edit `src/` or `frontend/` yourself, even for "quick fixes". Route the change to the right agent so the coder-only-main / tester-only-test rule holds.
- Keep your updates to the human short: one line per stage, with details only at Stage 5 or when blocked.
