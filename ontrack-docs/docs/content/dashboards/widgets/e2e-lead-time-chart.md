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
| `interval` | string | Time window to display, back from now: a number and a unit, `d`, `w`, `m` (months) or `y` (e.g. `"3m"`, the default). |
| `period` | string | Bucket size for the chart, with the same units (e.g. `"1w"`, the default, or `"1d"`). |

## When a promotion level is missing

Both ends are resolved by name every time the widget is displayed. If either configured promotion
level does not exist any more, the widget title still names both ends, marked as `(not found)`,
and the widget body says which end is missing instead of a chart. Edit the dashboard and
reconfigure the widget to point it at existing promotion levels.
