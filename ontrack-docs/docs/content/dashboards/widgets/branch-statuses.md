# Branch statuses

**Key:** `home/BranchStatuses`

Shows promotion and validation statuses for a configured list of branches in a table. Each row is a branch; columns are promotion levels or validation stamps.

## Configuration

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title` | string | `"Branch statuses"` | Widget header label. |
| `branches` | object[] | | Branches to monitor. Each entry has `project` (string) and `branch` (string). |
| `promotionConfigs` | object[] | `[]` | Promotion levels to display as columns. Each entry has `promotionLevel` (string) and an optional `period` (ISO 8601 duration, e.g. `"PT24H"`) used to colour-code staleness. |
| `validationConfigs` | object[] | `[]` | Validation stamps to display as columns. Each entry has `validationStamp` (string) and an optional `period`. |
| `displayValidationResults` | boolean | `false` | Show detailed validation result statuses inside cells. |
| `displayValidationRun` | boolean | `false` | Show a link to the individual validation run. |
| `refreshInterval` | ISO 8601 duration | `"PT0S"` | Auto-refresh interval. `"PT0S"` disables auto-refresh. |

## Example

```yaml
config:
  title: "Release branches"
  branches:
    - project: "Backend"
      branch: "release/1.0"
    - project: "Frontend"
      branch: "release/1.0"
  promotionConfigs:
    - promotionLevel: "STAGING"
      period: "PT24H"
    - promotionLevel: "PRODUCTION"
  displayValidationResults: true
  refreshInterval: "PT5M"
```
