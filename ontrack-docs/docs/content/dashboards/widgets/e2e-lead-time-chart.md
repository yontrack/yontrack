# End-to-end lead time

**Key:** `home/E2ELeadTimeChart`

Measures end-to-end lead time from a source project's promotion to a target project's promotion. Useful for tracking how quickly a change flows from one service to another (e.g. library → application).

## Configuration

| Field | Type | Description |
|-------|------|-------------|
| `project` | string | Source project name. |
| `branch` | string | Source branch name. |
| `promotionLevel` | string | Source promotion level. |
| `targetProject` | string | Target project name. |
| `targetBranch` | string | Target branch name. |
| `targetPromotionLevel` | string | Target promotion level. |
| `interval` | string | Bucket size for the chart (e.g. `"1d"`, `"1w"`). |
| `period` | string | Time window to display (e.g. `"1M"`, `"3m"`). |
