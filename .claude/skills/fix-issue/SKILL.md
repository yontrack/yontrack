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

```bash
git checkout -b claude/{short-description}-pipeline
```

Confirm the branch was created before proceeding.

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

---

## Step 6 — Land on `main` and mark the issue ready

Follow the workflow lifecycle in `CLAUDE.md`: merge the branch into `main`, push, and delete the local
branch. Then wait for the CI build on `main` for the pushed commit:

```bash
gh run list --workflow=ci.yml --branch main --limit 1 --json databaseId,headSha,status,conclusion,url
gh run watch <run-id>
```

Only when that run's `conclusion` is `success` for the commit you pushed, move the issue to ready:

```bash
gh issue edit {number} --add-label "status:ready" --remove-label "status:wip"
```

If the build fails, leave the issue on `status:wip`, report the failure, and fix it. If the build is
still running and waiting is impractical, leave `status:wip` and say so — never apply `status:ready`
on an unverified build.

Then close the issue, with a comment recording what landed — the commit(s), and the measured
outcome where the change claimed one:

```bash
gh issue close {number} --comment "..."
```

Leave the `status:ready` label in place; closing does not replace it.

---

## Step 7 — Summarise

After implementing, provide a concise summary:
- What was changed and in which files
- What tests cover the fix
- The branch name, whether it landed on `main`, and the resulting issue status label

**Never open a pull request** — leave that to the user.