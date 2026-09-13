---
name: release-milestone
description: Mark every status:ready issue of a GitHub milestone as released — swap status:ready for status:released, comment "Available in <version>", close the issue. Presents the list for approval first. Use when asked to release, close out, or mark as released the issues of a milestone.
user-invocable: true
---

# /release-milestone — Close Out a Milestone's Ready Issues

Arguments passed: `$ARGUMENTS`

Two arguments, in this order:

1. **milestone** — the GitHub milestone title, e.g. `5.4`
2. **version** — the version the issues ship in, e.g. `5.4.0` or `5.4.1`

If either is missing, ask for it. Do not derive the version from the milestone: a milestone can ship
in a `.0` or in a patch.

All commands target `yontrack/yontrack`.

---

## Step 1 — Check the inputs

```bash
gh api repos/yontrack/yontrack/milestones?state=all --paginate --jq '.[].title'
```

If the milestone is not in the list, stop and say so. Check that `version` starts with the milestone
(`5.4.1` for `5.4`); if not, ask before going on.

## Step 2 — List the issues

```bash
gh issue list -R yontrack/yontrack --milestone "<milestone>" --label status:ready --state open --limit 500 \
  --json number,title,labels \
  --jq '.[] | "#\(.number)\t\(.title)\t\([.labels[].name]|join(", "))"'
```

If the list is empty, say so and stop.

## Step 3 — Approval gate

Show the list as a table (key, title, labels) with the count, and state what will happen to each
issue. **Wait for an explicit yes.** Closing issues and posting comments are outward-facing, so no
approval, no action.

## Step 4 — Release each approved issue

Only the issues shown in Step 3:

```bash
for n in <numbers>; do
  if gh issue edit $n -R yontrack/yontrack --add-label status:released --remove-label status:ready >/dev/null \
    && gh issue close $n -R yontrack/yontrack --comment "Available in <version>" >/dev/null; then
    echo "OK #$n"; else echo "FAIL #$n"; fi
done
```

The label is `status:released`. There is no `status:release`, and `gh` rejects a label that doesn't
exist. The `&&` keeps a failed relabel from closing the issue anyway.

On any `FAIL`, report the issue numbers and the error. Don't retry blindly.

## Step 5 — Re-query

Run the Step 2 query again. GitHub's issue search can lag, so issues may turn up that weren't in the
first list, and issues can gain the label while this runs. If any appear, **don't process them
silently**. Show them as a new table and go back to Step 3 for them.

## Step 6 — Report

Say how many issues were released and closed, list any failures, and confirm the Step 5 query came
back empty.

---

## Rules

- **Never** touch an issue the user hasn't approved, even when it matches the query.
- **Never** change any label other than `status:ready` → `status:released`. `ready-for-agent`,
  `initiative: …`, and the type labels stay.
- Close with the default reason (`completed`). Never use `not planned`.
