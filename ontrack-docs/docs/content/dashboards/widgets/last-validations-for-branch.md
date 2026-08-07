# Last validations for a branch

**Key:** `home/LastValidationsForBranch`

Shows the latest validation results for a specific branch, with one row per build and one column per validation stamp.

## Configuration

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title` | string | | Widget header label. |
| `project` | string | | Project name. |
| `branch` | string | | Branch name. |
| `validations` | string[] | | Validation stamps to display as columns. |
| `displayPromotions` | boolean | `false` | Show promotion levels alongside validation results. |
