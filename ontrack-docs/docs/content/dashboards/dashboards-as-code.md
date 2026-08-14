# Dashboards as Code

Shared dashboards can be defined as YAML and applied to Yontrack via the UI or the GraphQL API. This lets you version-control dashboard definitions and distribute them across teams or environments.

## YAML format

A dashboard YAML file is a **list** of dashboard definitions. Each definition describes one dashboard and its widgets.

```yaml
- name: "CI/CD Pipeline Status"
  widgets:
    - key: "home/BranchStatuses"
      layout: {x: 0, y: 0, w: 12, h: 30}
      config:
        branches:
          - project: "MyProject"
            branch: "main"
        promotionConfigs:
          - promotionLevel: "STAGING"
          - promotionLevel: "PRODUCTION"
        displayValidationResults: true

    - key: "home/LastActiveProjects"
      layout: {x: 0, y: 30, w: 6, h: 25}
      config:
        count: 10
```

### Dashboard fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Display name. Used as the upsert key when no UUID is provided. |
| `uuid` | No | Stable identifier. Generated deterministically from the name if absent. Preserved across updates. |
| `widgets` | Yes | List of widget definitions (may be empty). |

### Widget fields

| Field | Required | Description |
|-------|----------|-------------|
| `key` | Yes | Widget type identifier (see [Widget reference](#widget-reference) below). |
| `layout` | Yes | Grid position and size. |
| `layout.x` | Yes | Column start (0-based, 12-column grid). |
| `layout.y` | Yes | Row start (0-based). |
| `layout.w` | Yes | Width in columns. `x + w` should not exceed 12. |
| `layout.h` | Yes | Height in row units (1 unit = 10 px). |
| `uuid` | No | Stable widget instance identifier. Generated from the dashboard UUID + widget key if absent. |
| `config` | No | Widget-specific configuration. See the [Widgets](widgets/index.md) reference for all available keys and their config options. |

---

## Layout grid

Widgets are placed on a **12-column** grid. Each row unit is 10 px tall.

- `x` ranges from 0 to 11. `x + w` should not exceed 12.
- `y` controls vertical position. Widgets with a higher `y` appear lower on the page.
- A widget with `w: 12` spans the full width.
- A comfortable default height for most widgets is `h: 25` to `h: 30`.

---

## Importing dashboards

### Via the UI

1. Open the **Dashboard** dropdown in the top bar.
2. Click **Import dashboards as YAML**.
3. Paste your YAML and click **Import**.

Dashboards that do not exist yet are created as **shared** dashboards. Dashboards that already exist (matched by UUID or name) are updated in place.

### Via the GraphQL API

```graphql
mutation ApplyDashboards($yaml: String!) {
  applyDashboards(input: { yaml: $yaml }) {
    dashboards {
      uuid
      name
    }
    errors {
      message
    }
  }
}
```

Pass the YAML string as the `$yaml` variable. The mutation returns the list of created or updated dashboards.

---

## Exporting dashboards

The exported YAML is **ready to use**: it is a list containing the one exported dashboard, in exactly the format described above, and can be pasted as-is into a `dashboards.yml` file or fed straight back into `applyDashboards`.

```yaml
- name: "CI/CD Pipeline Status"
  widgets:
  - key: "home/LastActiveProjects"
    layout:
      x: 0
      "y": 0
      w: 6
      h: 25
    config:
      count: 10
```

The `y` key is quoted because a bare `y` is a boolean in YAML 1.1; you can write it unquoted in your own files.

UUIDs are deliberately **omitted** from the export. They identify dashboards and widgets within a single Yontrack installation, so leaving them out keeps the file portable across environments. Re-applying an export matches the existing shared dashboard by `name` (see [Upsert semantics](#upsert-semantics)) and creates it, with a deterministic UUID, where it does not exist yet.

### Via the UI

Select any non-built-in dashboard, then open the **Dashboard** menu → **Export as YAML**. The dialog shows the YAML representation, which can be copied and stored in version control.

### Via the GraphQL API

The `asYaml` field is available on any dashboard object:

```graphql
{
  userDashboards {
    name
    asYaml
  }
}
```

---

## Upsert semantics

Applying a YAML file is **idempotent** and **non-destructive**:

- **Match by UUID** — if the definition includes a `uuid` and a dashboard with that UUID exists, it is updated.
- **Match by name** — if no UUID is given (or no UUID match found), Yontrack looks for a shared dashboard with the same `name`.
- **Create** — if neither match is found, a new shared dashboard is created with a deterministic UUID derived from the name.
- **No deletions** — dashboards not mentioned in the YAML are left untouched.

This means the same YAML file can be applied multiple times or from CI pipelines without risk of creating duplicates.

---

## Permissions

The `applyDashboards` mutation requires the **`DashboardSharing`** global function, which implies `DashboardEdition`. This permission is typically granted to administrators or DevOps roles.
