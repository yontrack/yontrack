# Issue lifecycle

The `status:*` labels an issue goes through, the `gh` commands that move it, and how to check the
base branch's build before calling it ready. `CLAUDE.md` carries the summary and the hard rules;
this is the detail behind them.

These labels are the issue *lifecycle*; they are distinct from the triage labels in
[`triage-labels.md`](triage-labels.md) and must never be substituted for them.

## Status labels

Issues carry exactly one `status:*` label at a time. The five the agent workflow drives are:

| Label             | When to apply                                                              | Issue  |
|-------------------|----------------------------------------------------------------------------|--------|
| `status:tospec`   | The issue is being specified — a grilling session has started on it        | open   |
| `status:todo`     | The spec is settled and the issue is ready to be picked up                 | open   |
| `status:wip`      | Work has started on the issue (right after creating the branch)            | open   |
| `status:ready`    | Merged into `main` **and** the CI build on `main` succeeded — not released | closed |
| `status:released` | The milestone has shipped — applied by `/release-milestone`, not by hand   | closed |

Apply them with `gh`, always removing the previous status label in the same command:

```bash
# Starting to specify an issue (see *Specifying an issue* below)
gh issue edit <number> --add-label "status:tospec" --remove-label "status:todo"

# The spec is settled
gh issue edit <number> --add-label "status:todo" --remove-label "status:tospec" --add-label "ready-for-agent"

# Starting work (check the issue's actual label first)
gh issue edit <number> --add-label "status:wip" --remove-label "status:todo"

# After the merge lands and CI on main is green: check the milestone, then mark ready and close
gh issue view <number> --json milestone --jq '.milestone.title'
gh issue edit <number> --add-label "status:ready" --remove-label "status:wip"
gh issue close <number> --reason completed --comment "Merged into \`<base>\`, ships with <milestone>."
```

`ready-for-agent` is **not** a status label and is not exclusive with them: it says the issue
is specified well enough to be handed to an agent, and it travels with the issue from
`status:todo` onwards rather than being swapped out.

## A ready issue is closed

Open means there is still work to do; a change waiting for its release is not work, and keeping it
open buried the real backlog under finished issues. What says it has not shipped yet is the
`status:ready` label **and the milestone** — `/release-milestone` finds it by both, swaps
`status:ready` for `status:released` and comments the version it shipped in. So:

- **Check the milestone before closing.** A closed `status:ready` issue with no milestone drops out of
  every release query and is never marked released. If the issue has none, apply `status:ready`,
  leave it **open**, and say so — Damien sets the milestone and closes it. Never guess a milestone.
- Close with the default reason (`completed`), never `not planned`, and name the base branch
  (`main`, `main` cherry-picked to `release/5.5`, or `release/5.5` for a 5.x-only fix) and the
  milestone in the comment.
- A ready issue that turns out not to work is **reopened** and moved back to `status:wip`, in the
  same step: `gh issue reopen <number>`, then the `status:wip` edit.

## Checking the build

Check the base branch's build before applying `status:ready` — the workflow is `CI`
(`.github/workflows/ci.yml`), which runs on every push:

```bash
# The CI run for the commit that was just pushed. Match on the SHA, never on "the latest run":
# runs on main go in parallel now (ADR 0014), so another session's newer run can finish first.
gh run list --workflow=ci.yml --branch main --limit 10 \
  --json databaseId,headSha,status,conclusion,url \
  --jq ".[] | select(.headSha == \"$(git rev-parse HEAD)\")"

# Block until it finishes (use the databaseId from the command above)
gh run watch <run-id>
```

Only a `conclusion` of `success` for the pushed commit earns `status:ready`. If the build fails, leave
the issue at `status:wip`, report the failure, and fix it before moving on. If the build is still running
and waiting is impractical, say so explicitly — never apply `status:ready` on an unverified build.

The one exception is a **docs-only push** carrying `[skip ci]` (see *Commit messages* in `CLAUDE.md`):
it has no run to wait for. Once its commit is on the base branch, and the commit before it on that
branch had a green build, mark it ready and close it straight away, and say that CI was skipped by
design.

## Specifying an issue

An issue whose body is a placeholder — "to be specified", an open question, a scope left
undecided — is specified through a **grilling session** (`/grilling`, or `/grill-me`), not by
writing a plausible spec in one pass. The session interviews Damien round by round until every
branch of the decision tree has been visited, and the outcome is a rewritten issue body.

Two label moves bracket it, and they are the agent's job, not Damien's:

1. **At the start**, before the first round of questions, set `status:tospec` if the issue does
   not already carry it. It is what tells anyone else looking at the board that the issue is
   being decided right now and is not free to pick up.
2. **When the rewritten body is published**, move `status:tospec` → `status:todo` and add
   `ready-for-agent`, in one command.

Neither move happens for a grilling session that is not about a GitHub issue — grilling a
design, a migration plan or an approach touches no labels.

Two rules carry over from the rest of the workflow and are worth repeating because a spec session
is where they are easiest to forget:

- **Never** close the issue, and never start implementing during the session. Specifying and
  building are separate; the session ends at a published body.
- A decision that depends on another issue is recorded as a **native GitHub dependency**, not
  only as prose — [`issue-tracker.md`](issue-tracker.md) has the incantation, including that it
  takes the blocker's numeric database id. Keep the prose line too: the relationship is the
  machine-readable half, and the sentence is the one an agent picking up the issue reads.
