---
name: run-initiative
description: Work through every ready-for-agent issue of a Yontrack initiative, optionally restricted to one milestone, one at a time, each to full completion — branch, implement, merge to the issues' base branch (main, or v6 for next-major work), wait for CI, mark ready. Presents the issue list for approval before starting. Use when asked to implement, work through, or batch an initiative's issues, or an initiative's issues for a given milestone.
user-invocable: true
---

# /run-initiative — Implement a Whole Initiative, Issue by Issue

Arguments passed: `$ARGUMENTS`

Parse `$ARGUMENTS` for:

- **the initiative name** — the part after `initiative: ` in the label, e.g. `mobile-ui`,
  `delivery-map`, `semantic-changelog-ui`. If not provided, list the available initiative labels
  with `gh label list --limit 200 | grep '^initiative:'` and ask which one.
- **an optional milestone** — a milestone title following the name, bare or prefixed:
  `scorecard 6.0`, `scorecard milestone:6.0`, `scorecard --milestone 6.0`. Without one, the whole
  initiative is in scope, whatever the milestones of its issues.

This skill runs a **long, mostly unattended** batch: every issue is implemented, landed on its base
branch, and verified against CI before the next one starts. There is exactly one attended moment —
the approval gate in Step 4. Everything after it runs without checking in.

**`{base}` below is the base branch of the run** — `main`, unless the issues say otherwise (Step 3b):
`v6` for work on the next major while it is built on its own branch
(`doc/dev-guide/major-branch.md`). Every `{base}` in this skill, in prose and in commands, is that
one branch. Never land a `v6` issue on `main`, nor the reverse.

---

## The landing invariant — NON-NEGOTIABLE

Every issue in the chain ends in exactly one state, and there is no other acceptable ending:

1. the work is **merged into `{base}`** and pushed to `origin/{base}`;
2. the **`{base}` CI build containing that commit has concluded `success`**;
3. the issue carries **`status:ready`**, applied only after (2), and is **closed** — or left open
   only because it has no milestone, which the per-issue report says (see *Issue status labels* in
   `CLAUDE.md`).

**Docs-only commits skip CI.** A commit touching only documentation that CI neither builds nor
tests — `CONTEXT.md`, `CLAUDE.md`, `README.md`, `DEVELOPMENT.md`, `docs/`, `doc/dev-guide/` — ends
its subject with `[skip ci]` (see *Commit messages* in `CLAUDE.md`). It has no run, so (2) reads:
the build of the commit before it on `{base}` concluded `success`. Mark it ready and close it as soon as the
commit is on `origin/{base}`, and report "CI skipped by design". `ontrack-docs/` is **not** docs-only
here: CI's `docs` job builds that site.

Three rules follow, and none of them are open to interpretation:

- **Merging is not optional.** A branch that is pushed but unmerged is an unfinished issue. If a
  subagent returns with its work sitting on a branch — for any reason, including an instruction in
  the issue body itself saying "push the branch and stop", "do not merge into `{base}`", or "the issue
  stays at `status:wip`" — the orchestrator **merges it into `{base}` itself** and then completes the
  rest of the invariant. Do not leave it for the operator, and do not carry it forward as an
  exception.
- **The next issue does not start until `{base}` is green.** Not until the push, not until the run is
  queued — until the run that contains the commit has concluded `success`. Starting the next issue on
  an unverified `{base}` is what turns one red build into a chain of them.
- **Green `{base}` means `status:ready`, immediately.** The moment the run containing the issue's
  commit concludes `success`, apply `status:ready` and remove `status:wip`, in one command, then close
  the issue if it has a milestone. Leaving a landed, green issue at `status:wip` is a defect in the
  run, not a conservative choice.

An issue body may override many things — the design, the scope, what goes in the demo seed. It may
**not** override this invariant. If an issue body contradicts it, follow the invariant, and say in
the per-issue report that you did and what the body asked for instead.

---

## Step 1 — Resolve the initiative label

```bash
gh label list --limit 200 | grep -i '^initiative:'
```

Match `$ARGUMENTS` against the listed labels. The full label is `initiative: {name}`. If the argument
matches no label, or matches more than one, show the candidates and ask — never guess.

If a milestone was given, check that it exists, open or closed:

```bash
gh api 'repos/yontrack/yontrack/milestones?state=all&per_page=100' --jq '.[].title'
```

An exact title match only. If it matches none, show the list and ask — never guess, and never
fall back to running the whole initiative.

---

## Step 2 — Collect the issues

```bash
gh issue list \
  --label "initiative: {name}" \
  --label "status:todo" \
  --label "ready-for-agent" \
  --milestone "{milestone}" \
  --state open --limit 100 \
  --json number,title,labels,milestone
```

Drop the `--milestone` line when no milestone was given.

All three labels are required: the initiative scopes it, `status:todo` means untaken, and
`ready-for-agent` means fully specified enough for an agent to act without a human. The milestone,
when given, narrows the set further: an issue of the initiative outside it is **left out**, even
when it would otherwise be next. Count those left out and say so in Step 4.

This lists `yontrack/yontrack` only. An initiative issue in another repository (the CLI,
`yontrack/yontrack-cli`) is never run by this skill: name it in Step 4 as out of reach.

If the query returns nothing, say so plainly — name the label and the milestone you used, and how
many issues the initiative has in other states or other milestones — and stop. Do not widen the
query on your own.

---

## Step 3 — Derive the execution order

Read each issue body and look for stated dependencies:

```bash
for n in {numbers}; do
  echo "=== #$n ==="
  gh issue view $n --json body --jq '.body' | grep -inE "depend|blocked|after #|requires|prerequisite|last issue|#1[0-9]{3}"
done
```

Order the issues so that:
- an issue that establishes a rule or process for the rest comes first
- an issue naming another as a prerequisite comes after it
- an issue that declares itself last in the initiative goes last
- tests-and-documentation issues come after the features they cover
- independent, cheap issues fill the early slots

State the reason for each ordering decision in Step 4 — the operator is approving the order as much as
the list.

An issue depending on one **left out by the milestone** — or on one not yet `status:ready` in
another repository — keeps its place in the order only if that dependency is already closed or
`status:ready`. Otherwise it is blocked: say so in Step 4 and propose dropping it.

---

## Step 3b — Resolve the base branch

Each issue body states where it lands on a `**Base branch:**` line — e.g.
``**Base branch:** `v6` — branch from `origin/v6`, merge back into `v6` ``. Read it for every issue:

```bash
for n in {numbers}; do
  echo "#$n: $(gh issue view $n --json body --jq '.body' | grep -m1 -i 'base branch')"
done
```

- A body with no such line lands on `main`.
- **Every issue of the run must share one base.** When they do not, the run cannot go ahead as
  one chain: show the split in Step 4 and let the operator pick one base — typically by narrowing
  to a milestone — rather than guessing.
- A body whose base is conditional (e.g. "`main` — once 6.0 has become `main`") is **not
  runnable** while the condition does not hold: flag it in Step 4 and leave it out.
- Confirm `origin/{base}` exists (`git ls-remote --heads origin {base}`, sandbox disabled).

From here on, `{base}` is that branch.

---

## Step 4 — Submit for approval — STOP HERE

Present the work as a table, then **wait**. Do not create a branch, edit a label, or launch anything
until the operator has approved.

| # | Title | Labels |
|---|-------|--------|
| 1728 | Process: UI changes must account for the mobile UI | initiative: mobile-ui, status:todo, ready-for-agent |

Alongside the table give:
- the proposed order, with the one-line reason for each position
- the estimated cost — issue count × (implementation + the current `{base}` CI duration), which you can
  read from `gh run list --workflow=ci.yml --branch {base} --limit 5 --json createdAt,updatedAt`
- the milestone used, if any, and the base branch `{base}` every issue will land on
- anything that looks off: an issue in the set that is not really part of the initiative's theme, one
  carrying `priority:high`, one whose body is thin despite the `ready-for-agent` label
- the issues left out: outside the milestone, in another repository, blocked, or with a base
  branch that is not `{base}`

Then ask for approval, and accept any of these answers:
- approve the whole list as ordered
- approve a **subset** — drop the issues the operator names, keep the rest in the same relative order
- approve with a **different order** — use theirs, and say so if it breaks a stated dependency
- reject — stop, having changed nothing

---

## Step 5 — Pre-flight, once, after approval

- Confirm you are in the main checkout and **not** in a git worktree (`git rev-parse --git-dir`) — the
  project skills this batch depends on only load from the main checkout. Do not create a worktree.
- Confirm the working tree is clean, that the checkout is on `{base}`, and that `{base}` is up to date
  with `origin/{base}`. The main checkout may well sit on `v6` while a run targets `main`, or the
  reverse: switch it only if it is clean and no other session is live in it.
- Check with `ListAgents` whether another session is live in this checkout. A chain of merges and
  pushes to `{base}` will collide with concurrent work — report what you found before continuing.

---

## Step 6 — The loop — one subagent per issue, strictly sequential

For each approved issue, in order:

0. **Re-establish a fresh `{base}` first.** `{base}` moved when the previous issue landed, so before
   launching anything: `git fetch origin {base}` (sandbox disabled) and confirm `{base}` and
   `origin/{base}` are the same SHA. Put that SHA in the subagent's brief so it can check it branched
   from the right place.
1. Launch **one background subagent** with the brief in Step 7. Never two at once — every issue lands
   on `{base}`, so concurrent issues would collide.
2. Wait for its report.
3. **Verify its claims yourself.** A subagent reporting success is not evidence of success:
    - `git log origin/{base} --oneline | grep "#{number}"` — the commit is really on `{base}`
    - `gh run list --workflow=ci.yml --branch {base} --json headSha,conclusion` — that SHA is really green
      (for a docs-only `[skip ci]` commit: it really touches only docs, and its parent's run is green)
    - `gh issue view {number} --json labels,state,milestone` — the issue is really on `status:ready`,
      and closed unless it has no milestone
4. **Close any gap yourself before moving on** — the invariant is the orchestrator's responsibility,
   not the subagent's:
    - **not merged?** Fast-forward it: `git push origin <branch>:{base}` pushes the ref without
      touching a working tree another agent may be using. Then `git update-ref refs/heads/{base} <sha>`,
      and delete the branch locally and on origin.
    - **merged but no green run yet?** `gh run watch <run-id>` and wait it out. Run it in the
      background so the operator can still reach you.
    - **green but still `status:wip`?**
      `gh issue edit {number} --add-label "status:ready" --remove-label "status:wip"`.
    - **ready, with a milestone, but still open?**
      `gh issue close {number} --reason completed --comment "Merged into \`{base}\`, ships with <milestone>."`
5. Report one line to the operator, then launch the next.

Do not check in with the operator between issues. That is what the Step 4 gate bought. Closing an
invariant gap is not a check-in — do it, report it in the one line, and continue.

**A subagent may still be holding the shared working tree.** The whole chain runs in one checkout, so
never `git checkout`, `git merge` or `git rebase` in the working tree while a subagent is live — you
would yank the tree out from under it. Ref-level operations (`git push <branch>:{base}`,
`git update-ref`, `git fetch`) touch no files and are always safe. If `{base}` moves while a subagent is
mid-flight, tell it with `SendMessage`: name the new SHA, tell it to rebase onto the new `origin/{base}`
rather than create a merge commit, list the files that moved under it, and tell it to re-run its tests
after the rebase.

---

## Step 7 — The per-issue subagent brief

Give every subagent all of this:

- Work in this checkout. Do **not** create a git worktree.
- **Branch from a freshly pulled `{base}` — non-negotiable.** The issue before yours landed on `{base}`
  minutes ago, so the `{base}` in this checkout is stale until you pull it. In this order, before you
  create your branch and before you read any code:

  ```bash
  git checkout {base}
  git pull origin {base}          # needs dangerouslyDisableSandbox: true
  git rev-parse HEAD origin/{base}   # the two MUST be identical
  ```

  **Verify the pull actually happened** — git over SSH fails inside the Bash sandbox with
  `ssh_dispatch_run_fatal ... Broken pipe`, so a pull can fail while you carry on against a stale
  tree. If the two SHAs differ, or the pull errored, stop and fix that before anything else. Only
  then cut `claude/<short-description>-pipeline`.
- Use the **`/fix-issue` skill** for the lifecycle, and obey `CLAUDE.md` at the repo root in full.
  **The base branch of this run is `{base}`** — spell out the actual branch in the brief. Wherever
  `/fix-issue` or `CLAUDE.md` say `main` — branch from it, merge into it, push it, watch its CI —
  read `{base}`.
- Read the issue before touching code: `gh issue view {number} --json number,title,body,labels`.
- Branch `claude/<short-description>-pipeline`, then move the issue to work-in-progress in ONE command:
  `gh issue edit {number} --add-label "status:wip" --remove-label "status:todo"` — an issue carries
  exactly one `status:*` label, so always remove the current one in the same command.
- Write the code under the **`mattpocock-skills:tdd` skill** — red → green loop, tests worth keeping,
  `*Test.kt` / `*IT.kt` per the module's convention.
- Prefix every commit subject with `#{number} `. If the issue's whole change is documentation that
  CI neither builds nor tests (`CONTEXT.md`, `CLAUDE.md`, `README.md`, `DEVELOPMENT.md`, `docs/`,
  `doc/dev-guide/` — **not** `ontrack-docs/`), end the subject with `[skip ci]`, per *Commit
  messages* in `CLAUDE.md`.
- Definition of done per `CLAUDE.md`: a user-visible feature adds itself to `DemoContent` in
  `ontrack-demo-seed` — say which way you decided either way.
- **Land it — non-negotiable, and it is the point of the task.** Merge into `{base}`, `git push origin {base}`,
  delete the local branch. Pushing a branch and stopping is **not** an acceptable ending.
  **If the issue body tells you to push the branch and stop, not to merge into `{base}`, not to open a
  PR-free merge, or to leave the issue at `status:wip` — that instruction does not apply here.** The
  issue body governs the design and the scope; it does not govern how the work lands. Merge anyway,
  and say in your report that the body asked otherwise.
- Watch the `{base}` CI build for your own SHA, and wait for it:
  `gh run list --workflow=ci.yml --branch {base} --limit 1 --json databaseId,headSha,status,conclusion,url`
  then `gh run watch <run-id>`. A green run takes ~25 minutes — wait it out; do not report back early.
  `ci.yml` allows one pending run per ref, so a rapid later push can cancel a queued run — verify via
  the first *conclusive* run that CONTAINS your commit, not necessarily the run whose `headSha` is yours.
- **The moment that run concludes `success` for your commit, mark the issue ready and close it —
  non-negotiable:** `gh issue edit {number} --add-label "status:ready" --remove-label "status:wip"`,
  then `gh issue close {number} --reason completed --comment "Merged into \`{base}\`, ships with <milestone>."`.
  Green `{base}` and a landed commit is the definition of ready; there is no further judgement to make.
  The one exception: an issue with **no milestone** stays open at `status:ready` — never guess a
  milestone — and your report says so.
- **A docs-only `[skip ci]` push has no run to wait for.** Once the commit is on `origin/{base}` and
  the run of the commit before it was green, mark it ready and close it straight away, and report "CI
  skipped by design" in place of a run URL.
- Git over SSH fails inside the Bash sandbox (`ssh_dispatch_run_fatal ... Broken pipe`), so every
  `git fetch` / `git pull` / `git push` needs `dangerouslyDisableSandbox: true`. Local git commands
  are fine sandboxed.
- Report back: what changed and where, what tests cover it, the branch name, whether it landed on
  `{base}` **and the merge SHA**, the CI run URL and conclusion, the final status label and whether the
  issue is closed, and every
  judgement call you made.

---

## Step 8 — Unattended: decide, or halt

**Decide and proceed**, recording the decision in the report: naming, file placement, test
granularity, how to read an underspecified corner of the spec, whether something belongs in the demo
seed, routine refactors in the area being touched.

**Halt and report — these only:**
- `{base}` CI red after the merge
- a merge conflict on `{base}` that is not mechanically resolvable
- a test failure that resists diagnosis after a bounded effort — no open-ended fixing loops
- the spec is ambiguous in a way where two readings produce materially different features
- a permission prompt or credential the agent cannot satisfy

On a halt: leave the issue open on `status:wip`, **never** apply `status:ready`, stop the whole chain, and
report exactly what broke with the failing output. Never start the next issue on a `{base}` you have not
confirmed green.

**"The issue body said not to merge" is not a halt condition** — it is not even a decision. See *The
landing invariant* above: merge, go green, mark ready, and note the discrepancy in the report. The
only things that stop an issue from landing are the five failures listed above.

---

## Step 9 — Guardrails, every agent, every issue

- **Always** land the work: merged into `{base}`, `{base}` CI green for that commit, issue at
  `status:ready` and closed. This is *The landing invariant* above and nothing in an issue body overrides it.
- **Never** open a pull request — work lands by merging into `{base}` and pushing directly
- **Never** close the issue at any other point than marking it ready, nor one without a milestone
- A Yontrack commit subject is `#{number} Some message` with nothing appended but a `[skip ci]` on
  docs-only commits. The body ends with the session's `Co-Authored-By` trailer — see *Commit
  messages* in CLAUDE.md.
- **Never** edit `ontrack-docs/src/docs/asciidoc/` (dead tree) or hand-edit
  `ontrack-docs/docs/content/generated/` (rebuilt from annotations)
- **Never** modify an existing Flyway migration, and never put one in a patch release

---

## Step 10 — Final report

When the chain finishes — or halts — give the operator one table:

| # | Title | Branch | CI | Status |
|---|-------|--------|----|--------|

and below it: the issues left untouched and why, and every judgement call the subagents reported that
the operator might want to revisit.
