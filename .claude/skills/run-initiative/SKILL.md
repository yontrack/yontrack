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

1. Launch **one background subagent** with the brief in Step 7. Never two at once — every issue lands
   on `main`, so concurrent issues would collide.
2. Wait for its report.
3. **Verify its claims yourself.** A subagent reporting success is not evidence of success:
    - `git log origin/main --oneline | grep "#{number}"` — the commit is really on `main`
    - `gh run list --workflow=ci.yml --branch main --json headSha,conclusion` — that SHA is really green
    - `gh issue view {number} --json labels` — the issue is really on `status:ready`
4. Report one line to the operator, then launch the next.

Do not check in with the operator between issues. That is what the Step 4 gate bought.

---

## Step 7 — The per-issue subagent brief

Give every subagent all of this:

- Work in this checkout. Start from an up-to-date `main`: `git checkout main && git pull origin main`.
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
- Merge into `main`, `git push origin main`, delete the local branch.
- Watch the `main` CI build for your own SHA, and wait for it:
  `gh run list --workflow=ci.yml --branch main --limit 1 --json databaseId,headSha,status,conclusion,url`
  then `gh run watch <run-id>`.
- Only when that run's conclusion is `success` for YOUR commit:
  `gh issue edit {number} --add-label "status:ready" --remove-label "status:wip"`.
- Report back: what changed and where, what tests cover it, the branch name, whether it landed on
  `main`, the CI run URL and conclusion, the final status label, and every judgement call you made.

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

---

## Step 9 — Guardrails, every agent, every issue

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
