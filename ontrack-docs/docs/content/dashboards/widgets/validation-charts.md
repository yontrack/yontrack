# Validation charts

Two widgets display metrics related to validation stamps. They share the same configuration shape.

| Key | Description |
|-----|-------------|
| `home/ValidationMetricsChart` | Validation run results over time. |
| `home/ValidationStabilityChart` | Percentage of builds that pass the validation stamp. |

## Configuration

| Field | Type | Description |
|-------|------|-------------|
| `project` | string | Project name. |
| `branch` | string | Branch name. |
| `validationStamp` | string | Validation stamp name. |
| `interval` | string | Bucket size for the chart (e.g. `"1d"`). |
| `period` | string | Time window to display (e.g. `"1M"`). |

## Example

```yaml
- key: "home/ValidationStabilityChart"
  layout: {x: 6, y: 0, w: 6, h: 30}
  config:
    project: "Backend"
    branch: "main"
    validationStamp: "Unit Tests"
    interval: "1d"
    period: "3m"
```
