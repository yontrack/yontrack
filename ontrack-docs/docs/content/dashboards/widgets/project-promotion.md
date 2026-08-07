# Project promotion

**Key:** `home/ProjectPromotion`

Displays the latest promotion status for a project alongside its build dependencies and decorations.

## Configuration

| Field | Type | Description |
|-------|------|-------------|
| `project` | string | Project name. |
| `promotions` | string[] | Promotion levels to show. |
| `depth` | integer | Number of dependency levels to render (default: 2). |
| `label` | string | Optional label used to filter the dependency graph. |
