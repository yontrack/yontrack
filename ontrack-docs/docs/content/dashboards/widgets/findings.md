# Findings

Two widgets show the [security findings](../../integrations/findings/findings.md) of a project or of
a branch: their open findings by severity, each severity a tag in its colour, and the number of the
accepted and resolved ones. Each figure opens the findings page of the project, filtered.

A widget tells a user who is not granted the view of the findings of the project so, rather than
showing zeros, and says when no finding has been reported at all.

## Project findings

**Key:** `extension/findings/ProjectFindings`

The open findings of a project — open on at least one of the branches which count for the project —
and, optionally, the branches with open findings among those which count.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `project` | string | | Project name. |
| `showBranches` | boolean | `false` | Lists the branches with open findings, among the ones which count for the project. |

## Branch findings

**Key:** `extension/findings/BranchFindings`

The findings open on a branch, and the ones accepted and resolved on it. A disabled branch does not
count for the project, but the widget still shows its own findings, and says it is disabled.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `project` | string | | Project name. |
| `branch` | string | | Branch name. |
