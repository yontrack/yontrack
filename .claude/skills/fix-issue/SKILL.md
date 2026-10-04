---
name: fix-issue
description: Pick a GitHub issue, create a correctly-named branch (claude/<short-description>-pipeline), implement the fix, and summarise the changes. Use when asked to fix or work on a GitHub issue.
user-invocable: true
allowed-tools:
  - Bash(gh issue view:*)
  - Bash(gh issue list:*)
  - Bash(gh issue edit:*)
  - Bash(gh run list:*)
  - Bash(gh run view:*)
  - Bash(gh run watch:*)
  - Bash(git checkout:*)
  - Bash(git branch:*)
---

# /fix-issue — Fix a GitHub Issue

Arguments passed: `$ARGUMENTS`

Parse `$ARGUMENTS` for an issue number. If not provided, ask the user for one, or offer to list open issues with `gh issue list`.

---

## Step 1 — Fetch issue details

```bash
gh issue view {number} --json number,title,body,labels
```

Read the issue title, description, and any linked context. Understand what needs to be fixed before touching any code.

---

## Step 2 — Derive branch name

From the issue title, create a short kebab-case description (2–5 words). The branch name must follow this pattern exactly:

```
claude/{short-description}-pipeline
```

Examples:
- Issue "Fix null pointer in build validation" → `claude/fix-null-build-validation-pipeline`
- Issue "Add keepLast support for DISABLE mode" → `claude/add-keeplast-disable-mode-pipeline`

---

## Step 3 — Create the branch

The base follows the milestone:

- **Any milestone but `5.5`**, or none: the base is `main`.
- **Milestone `5.5`** — a fix for the 5.x line, maintained on `release/5.5`. The body's
  `**Base branch:**` line says which of the two it is:
  - `main, cherry-pick to release/5.5` — the base is `main`, and Step 6 cherry-picks the commit onto
    `release/5.5` once it has landed. This is the default for a 5.5 issue: a fix living only on the
    5.x branch is a regression in 6.x waiting to happen.
  - `release/5.5` — the bug exists only in 5.x, in code that 6.0 removed. The base is `release/5.5`,
    and there is nothing to cherry-pick.

  A `5.5` issue with no `**Base branch:**` line takes the first form.
  `doc/dev-guide/patch-release.md` has the rules, among them **no Flyway migration** on `release/5.5`.

Branch from the freshly fetched remote ref, never from wherever the worktree happens to stand:

```bash
git fetch origin            # outside the sandbox - a sandboxed fetch fails and leaves the ref stale
git checkout -b claude/{short-description}-pipeline origin/<base>
```

Confirm the branch was created and `git merge-base HEAD origin/<base>` equals
`git rev-parse origin/<base>` before proceeding.

---

## Step 4 — Mark the issue as in progress

As soon as the branch exists, move the issue to the work-in-progress status. An issue carries exactly
one `status:*` label at a time, so **always remove the current one in the same command** — never add
`status:wip` on its own.

Most issues start on `status:todo`, which is the usual label to drop:

```bash
gh issue edit {number} --add-label "status:wip" --remove-label "status:todo"
```

If Step 1 showed a different `status:*` label (`status:ready`, `status:tomerge`,
`status:waiting-feedback`, `status:released`), remove that one instead:

```bash
gh issue edit {number} --add-label "status:wip" --remove-label "status:<previous>"
```

The `--json ...,labels` output from Step 1 already tells you which one is set — use it rather than
guessing.

---

## Step 5 — Implement the fix

Explore the codebase to understand the affected area. Follow all patterns in CLAUDE.md:
- Use the existing service/repository layer, don't bypass it
- Apply security checks where needed
- Add or update unit tests (`*Test.kt`) and/or integration tests (`*IT.kt`) as appropriate
- Follow naming conventions for the module being changed

Commit on the branch, prefixing every subject with the issue number — one `#<number>` at the very
start, then a space:

```
#{number} Some message
```

See *Commit messages* in CLAUDE.md for what that prefix costs in the semantic change log.

---

## Step 6 — Land on the base, mark the issue ready and close it

Follow the workflow lifecycle in `CLAUDE.md`: merge the branch into `<base>`, push, and delete the local
branch. Then wait for the CI build on `<base>` for the pushed commit:

```bash
gh run list --workflow=ci.yml --branch <base> --limit 1 --json databaseId,headSha,status,conclusion,url
gh run watch <run-id>
```

**For `main, cherry-pick to release/5.5`**, once that `main` run is green, cherry-pick the commit onto
`release/5.5` and wait for that branch's build as well:

```bash
git fetch origin            # outside the sandbox
git checkout -b claude/{short-description}-5.5-pipeline origin/release/5.5
git cherry-pick -x <sha on main>
git push origin HEAD:release/5.5
git checkout - && git branch -D claude/{short-description}-5.5-pipeline
gh run list --workflow=ci.yml --branch release/5.5 --limit 1 --json databaseId,headSha,status,conclusion,url
gh run watch <run-id>
```

A cherry-pick that does not apply cleanly is not resolved by guessing: stop and report it. The base in
the close comment below is then `main`, cherry-picked to `release/5.5`.

Only when the run's `conclusion` is `success` for the commit you pushed — both runs, for a
cherry-pick — move the issue to ready and close it, provided it has a milestone:

```bash
gh issue view {number} --json milestone --jq '.milestone.title'
gh issue edit {number} --add-label "status:ready" --remove-label "status:wip"
gh issue close {number} --reason completed --comment "Merged into \`<base>\`, ships with <milestone>."
```

If the issue has no milestone, apply `status:ready` but leave it **open** and say so — Damien sets
the milestone and closes it. Never guess a milestone. *Issue status labels* in `CLAUDE.md` says why.

If the build fails, leave the issue on `status:wip`, report the failure, and fix it. If the build is
still running and waiting is impractical, leave `status:wip` and say so — never apply `status:ready`
on an unverified build.

**Never close the issue at any other point** — closing belongs to marking it ready, and nowhere else.

---

## Step 7 — Summarise

After implementing, provide a concise summary:
- What was changed and in which files
- What tests cover the fix
- The branch name, whether it landed on `<base>` (and was cherry-picked to `release/5.5`), and the
  resulting issue status label

**Never open a pull request** — leave that to the user.