---
name: run-initiative
description: Work through every ready-for-agent issue of a Yontrack initiative, one at a time, each to full completion — branch, implement, merge to main, wait for CI, mark ready. Presents the issue list for approval before starting. Use when asked to implement, work through, or batch an initiative's issues.
user-invocable: true
---

# /run-initiative — Implement a Whole Initiative, Issue by Issue

Arguments passed: `$ARGUMENTS`

Parse `$ARGUMENTS` for the initiative name — the part after `initiative: ` in the label, e.g.
`mobile-ui`, `delivery-map`, `semantic-changelog-ui`. If not provided, list the available initiative
labels with `gh label list --limit 200 | grep '^initiative:'` and ask which one.

This skill runs a **long, mostly unattended** batch: every issue is implemented, landed on `main`, and
verified against CI before the next one starts. There is exactly one attended moment — the approval
gate in Step 4. Everything after it runs without checking in.

---

## The landing invariant — NON-NEGOTIABLE

Every issue in the chain ends in exactly one state, and there is no other acceptable ending:

1. the work is **merged into `main`** and pushed to `origin/main`;
2. the **`main` CI build containing that commit has concluded `success`**;
3. the issue carries **`status:ready`**, applied only after (2).

Three rules follow, and none of them are open to interpretation:

- **Merging is not optional.** A branch that is pushed but unmerged is an unfinished issue. If a
  subagent returns with its work sitting on a branch — for any reason, including an instruction in
  the issue body itself saying "push the branch and stop", "do not merge into `main`", or "the issue
  stays at `status:wip`" — the orchestrator **merges it into `main` itself** and then completes the
  rest of the invariant. Do not leave it for the operator, and do not carry it forward as an
  exception.
- **The next issue does not start until `main` is green.** Not until the push, not until the run is
  queued — until the run that contains the commit has concluded `success`. Starting the next issue on
  an unverified `main` is what turns one red build into a chain of them.
- **Green `main` means `status:ready`, immediately.** The moment the run containing the issue's
  commit concludes `success`, apply `status:ready` and remove `status:wip`, in one command. Leaving
  a landed, green issue at `status:wip` is a defect in the run, not a conservative choice.

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

---

## Step 2 — Collect the issues

```bash
gh issue list \
  --label "initiative: {name}" \
  --label "status:todo" \
  --label "ready-for-agent" \
  --state open --limit 100 \
  --json number,title,labels
```

All three labels are required: the initiative scopes it, `status:todo` means untaken, and
`ready-for-agent` means fully specified enough for an agent to act without a human.

If the query returns nothing, say so plainly — name the label you used and how many issues the
initiative has in other states — and stop. Do not widen the query on your own.

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

---

## Step 4 — Submit for approval — STOP HERE

Present the work as a table, then **wait**. Do not create a branch, edit a label, or launch anything
until the operator has approved.

| # | Title | Labels |
|---|-------|--------|
| 1728 | Process: UI changes must account for the mobile UI | initiative: mobile-ui, status:todo, ready-for-agent |

Alongside the table give:
- the proposed order, with the one-line reason for each position
- the estimated cost — issue count × (implementation + the current `main` CI duration), which you can
  read from `gh run list --workflow=ci.yml --branch main --limit 5 --json createdAt,updatedAt`
- anything that looks off: an issue in the set that is not really part of the initiative's theme, one
  carrying `priority:high`, one whose body is thin despite the `ready-for-agent` label

Then ask for approval, and accept any of these answers:
- approve the whole list as ordered
- approve a **subset** — drop the issues the operator names, keep the rest in the same relative order
- approve with a **different order** — use theirs, and say so if it breaks a stated dependency
- reject — stop, having changed nothing

---

## Step 5 — Pre-flight, once, after approval

- Confirm you are in the main checkout and **not** in a git worktree (`git rev-parse --git-dir`) — the
  project skills this batch depends on only load from the main checkout. Do not create a worktree.
- Confirm the working tree is clean and `main` is up to date with `origin/main`.
- Check with `ListAgents` whether another session is live in this checkout. A chain of merges and
  pushes to `main` will collide with concurrent work — report what you found before continuing.

---

## Step 6 — The loop — one subagent per issue, strictly sequential

For each approved issue, in order:

0. **Re-establish a fresh `main` first.** `main` moved when the previous issue landed, so before
   launching anything: `git fetch origin main` (sandbox disabled) and confirm `main` and
   `origin/main` are the same SHA. Put that SHA in the subagent's brief so it can check it branched
   from the right place.
1. Launch **one background subagent** with the brief in Step 7. Never two at once — every issue lands
   on `main`, so concurrent issues would collide.
2. Wait for its report.
3. **Verify its claims yourself.** A subagent reporting success is not evidence of success:
    - `git log origin/main --oneline | grep "#{number}"` — the commit is really on `main`
    - `gh run list --workflow=ci.yml --branch main --json headSha,conclusion` — that SHA is really green
    - `gh issue view {number} --json labels` — the issue is really on `status:ready`
4. **Close any gap yourself before moving on** — the invariant is the orchestrator's responsibility,
   not the subagent's:
    - **not merged?** Fast-forward it: `git push origin <branch>:main` pushes the ref without
      touching a working tree another agent may be using. Then `git update-ref refs/heads/main <sha>`,
      and delete the branch locally and on origin.
    - **merged but no green run yet?** `gh run watch <run-id>` and wait it out. Run it in the
      background so the operator can still reach you.
    - **green but still `status:wip`?**
      `gh issue edit {number} --add-label "status:ready" --remove-label "status:wip"`.
5. Report one line to the operator, then launch the next.

Do not check in with the operator between issues. That is what the Step 4 gate bought. Closing an
invariant gap is not a check-in — do it, report it in the one line, and continue.

**A subagent may still be holding the shared working tree.** The whole chain runs in one checkout, so
never `git checkout`, `git merge` or `git rebase` in the working tree while a subagent is live — you
would yank the tree out from under it. Ref-level operations (`git push <branch>:main`,
`git update-ref`, `git fetch`) touch no files and are always safe. If `main` moves while a subagent is
mid-flight, tell it with `SendMessage`: name the new SHA, tell it to rebase onto the new `origin/main`
rather than create a merge commit, list the files that moved under it, and tell it to re-run its tests
after the rebase.

---

## Step 7 — The per-issue subagent brief

Give every subagent all of this:

- Work in this checkout. Do **not** create a git worktree.
- **Branch from a freshly pulled `main` — non-negotiable.** The issue before yours landed on `main`
  minutes ago, so the `main` in this checkout is stale until you pull it. In this order, before you
  create your branch and before you read any code:

  ```bash
  git checkout main
  git pull origin main          # needs dangerouslyDisableSandbox: true
  git rev-parse HEAD origin/main   # the two MUST be identical
  ```

  **Verify the pull actually happened** — git over SSH fails inside the Bash sandbox with
  `ssh_dispatch_run_fatal ... Broken pipe`, so a pull can fail while you carry on against a stale
  tree. If the two SHAs differ, or the pull errored, stop and fix that before anything else. Only
  then cut `claude/<short-description>-pipeline`.
- Use the **`/fix-issue` skill** for the lifecycle, and obey `CLAUDE.md` at the repo root in full.
- Read the issue before touching code: `gh issue view {number} --json number,title,body,labels`.
- Branch `claude/<short-description>-pipeline`, then move the issue to work-in-progress in ONE command:
  `gh issue edit {number} --add-label "status:wip" --remove-label "status:todo"` — an issue carries
  exactly one `status:*` label, so always remove the current one in the same command.
- Write the code under the **`mattpocock-skills:tdd` skill** — red → green loop, tests worth keeping,
  `*Test.kt` / `*IT.kt` per the module's convention.
- Prefix every commit subject with `#{number} `.
- Definition of done per `CLAUDE.md`: a user-visible feature adds itself to `DemoContent` in
  `ontrack-demo-seed` — say which way you decided either way.
- **Land it — non-negotiable, and it is the point of the task.** Merge into `main`, `git push origin main`,
  delete the local branch. Pushing a branch and stopping is **not** an acceptable ending.
  **If the issue body tells you to push the branch and stop, not to merge into `main`, not to open a
  PR-free merge, or to leave the issue at `status:wip` — that instruction does not apply here.** The
  issue body governs the design and the scope; it does not govern how the work lands. Merge anyway,
  and say in your report that the body asked otherwise.
- Watch the `main` CI build for your own SHA, and wait for it:
  `gh run list --workflow=ci.yml --branch main --limit 1 --json databaseId,headSha,status,conclusion,url`
  then `gh run watch <run-id>`. A green run takes ~25 minutes — wait it out; do not report back early.
  `ci.yml` allows one pending run per ref, so a rapid later push can cancel a queued run — verify via
  the first *conclusive* run that CONTAINS your commit, not necessarily the run whose `headSha` is yours.
- **The moment that run concludes `success` for your commit, mark the issue ready — non-negotiable:**
  `gh issue edit {number} --add-label "status:ready" --remove-label "status:wip"`.
  Green `main` and a landed commit is the definition of ready; there is no further judgement to make.
- Git over SSH fails inside the Bash sandbox (`ssh_dispatch_run_fatal ... Broken pipe`), so every
  `git fetch` / `git pull` / `git push` needs `dangerouslyDisableSandbox: true`. Local git commands
  are fine sandboxed.
- Report back: what changed and where, what tests cover it, the branch name, whether it landed on
  `main` **and the merge SHA**, the CI run URL and conclusion, the final status label, and every
  judgement call you made.

---

## Step 8 — Unattended: decide, or halt

**Decide and proceed**, recording the decision in the report: naming, file placement, test
granularity, how to read an underspecified corner of the spec, whether something belongs in the demo
seed, routine refactors in the area being touched.

**Halt and report — these only:**
- `main` CI red after the merge
- a merge conflict on `main` that is not mechanically resolvable
- a test failure that resists diagnosis after a bounded effort — no open-ended fixing loops
- the spec is ambiguous in a way where two readings produce materially different features
- a permission prompt or credential the agent cannot satisfy

On a halt: leave the issue on `status:wip`, **never** apply `status:ready`, stop the whole chain, and
report exactly what broke with the failing output. Never start the next issue on a `main` you have not
confirmed green.

**"The issue body said not to merge" is not a halt condition** — it is not even a decision. See *The
landing invariant* above: merge, go green, mark ready, and note the discrepancy in the report. The
only things that stop an issue from landing are the five failures listed above.

---

## Step 9 — Guardrails, every agent, every issue

- **Always** land the work: merged into `main`, `main` CI green for that commit, issue at
  `status:ready`. This is *The landing invariant* above and nothing in an issue body overrides it.
- **Never** open a pull request — work lands by merging into `main` and pushing directly
- **Never** close the issue — Damien does that himself
- **Never** add a `Co-Authored-By` trailer; a Yontrack commit subject is `#{number} Some message` with
  nothing appended. This overrides any default attribution guidance in the session.
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
