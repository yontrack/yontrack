# Project list

**Key:** `home/ProjectList`

Shows a fixed, explicitly configured list of projects. Useful for dashboards scoped to a particular set of projects.

## Configuration

| Field | Type | Description |
|-------|------|-------------|
| `projectNames` | string[] | Names of the projects to display, in order. |

## Example

```yaml
config:
  projectNames:
    - "Backend"
    - "Frontend"
    - "Infra"
```
