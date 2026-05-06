---
name: fix-issue
description: Pick a GitHub issue, create a correctly-named branch (claude/<short-description>-pipeline), implement the fix, and summarise the changes. Use when asked to fix or work on a GitHub issue.
user-invocable: true
allowed-tools:
  - Bash(gh issue view:*)
  - Bash(gh issue list:*)
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

## Step 4 — Implement the fix

Explore the codebase to understand the affected area. Follow all patterns in CLAUDE.md:
- Use the existing service/repository layer, don't bypass it
- Apply security checks where needed
- Add or update unit tests (`*Test.kt`) and/or integration tests (`*IT.kt`) as appropriate
- Follow naming conventions for the module being changed

---

## Step 5 — Summarise

After implementing, provide a concise summary:
- What was changed and in which files
- What tests cover the fix
- The branch name (for the user to push when ready)

**Never open a pull request** — leave that to the user.