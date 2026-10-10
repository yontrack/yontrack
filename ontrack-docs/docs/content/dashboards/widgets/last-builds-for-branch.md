# Last builds for a branch

**Key:** `home/LastBuildsForBranch`

Shows the last builds of a branch the way the branch page does: one row per build, with its promotions, and one column per validation stamp. A validation can be run or have its status changed from the widget, as on the branch page.

## Configuration

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title` | string | | Widget header label. |
| `project` | string | | Project name. |
| `branch` | string | | Branch name. |
| `count` | int | `10` | Number of builds to display. |
| `promotions` | string[] | | Promotion levels to display for each build. All of them if empty. |
| `validations` | string[] | | Validation stamps to display as columns. All of them if empty. |

## Example

```yaml
- key: "home/LastBuildsForBranch"
  layout: {x: 0, y: 0, w: 12, h: 30}
  config:
    project: "my-project"
    branch: "main"
    count: 5
    promotions:
      - "GOLD"
    validations:
      - "unit-tests"
      - "integration-tests"
```
