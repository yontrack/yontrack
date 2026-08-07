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
| `config` | No | Widget-specific configuration. See [Widget reference](#widget-reference). |

## Widget reference

### `home/LastActiveProjects`

Displays the most recently active projects.

| Config field | Type | Default | Description |
|---|---|---|---|
| `count` | integer | 10 | Number of projects to display. |

### `home/AllProjectList`

Lists all projects with pagination and filtering. No configuration required.

### `home/ProjectList`

Shows a fixed, explicitly configured list of projects.

| Config field | Type | Description |
|---|---|---|
| `projectNames` | string[] | Names of the projects to display. |

**Example:**
```yaml
config:
  projectNames:
    - "Backend"
    - "Frontend"
    - "Infra"
```

### `home/FavouriteProjects`

Displays the current user's favourite projects. No configuration required.

### `home/FavouriteBranches`

Displays the current user's favourite branches.

| Config field | Type | Description |
|---|---|---|
| `project` | string | If set, restricts the list to branches of this project. Omit to show all favourite branches. |

### `home/BranchStatuses`

Shows promotion and validation statuses for a list of branches in a table.

| Config field | Type | Default | Description |
|---|---|---|---|
| `title` | string | `"Branch statuses"` | Widget header label. |
| `branches` | object[] | | Branches to monitor. Each has `project` (string) and `branch` (string). |
| `promotionConfigs` | object[] | `[]` | Promotion levels to display. Each has `promotionLevel` (string) and optional `period` (ISO duration, e.g. `"PT24H"`). |
| `validationConfigs` | object[] | `[]` | Validation stamps to display. Each has `validationStamp` (string) and optional `period`. |
| `displayValidationResults` | boolean | `false` | Show validation result statuses in cells. |
| `displayValidationRun` | boolean | `false` | Show the individual validation run link. |
| `refreshInterval` | ISO duration | `"PT0S"` | Auto-refresh interval (`"PT0S"` = disabled). |

**Example:**
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
```

### `home/LastValidationsForBranch`

Shows the latest validation results for a specific branch.

| Config field | Type | Default | Description |
|---|---|---|---|
| `title` | string | | Widget header label. |
| `project` | string | | Project name. |
| `branch` | string | | Branch name. |
| `validations` | string[] | | Validation stamps to display. |
| `displayPromotions` | boolean | `false` | Show promotion levels alongside validations. |

### `home/ValidationsLastPromotionBuild`

Shows validation results for the last build promoted to a given promotion level.

| Config field | Type | Description |
|---|---|---|
| `title` | string | Widget header label. |
| `project` | string | Project name. |
| `branch` | string | Branch name. |
| `promotion` | string | Promotion level name. |
| `validations` | string[] | Validation stamps to display. |

### `home/ProjectPromotion`

Displays the latest promotion status for a project, including dependencies and decorations.

| Config field | Type | Description |
|---|---|---|
| `project` | string | Project name. |
| `promotions` | string[] | Promotion levels to show. |
| `depth` | integer | Dependency depth to render (default 2). |
| `label` | string | Optional label to filter the dependency graph. |

### `home/BuildDependenciesTree`

Shows the downstream dependency tree of the latest build promoted to a given level.
See the [Build dependencies tree](widgets/build-dependencies-tree.md) widget reference for details.

| Config field | Type | Description |
|---|---|---|
| `title` | string | Widget header label. |
| `project` | string | Project name. |
| `branch` | string | Branch name. |
| `promotionLevel` | string | Promotion level to watch. |

### Promotion charts

The following chart widgets share the same configuration shape:

| Widget key | Description |
|---|---|
| `home/PromotionLeadTimeChart` | Average time from build creation to promotion. |
| `home/PromotionFrequencyChart` | How often builds reach the promotion level. |
| `home/PromotionStabilityChart` | Percentage of builds that reach the promotion level. |
| `home/PromotionTTRChart` | Time-to-recovery: how quickly the promotion level is restored after a regression. |

| Config field | Type | Description |
|---|---|---|
| `project` | string | Project name. |
| `branch` | string | Branch name. |
| `promotionLevel` | string | Promotion level name. |
| `interval` | string | Bucket size for the chart (e.g. `"1d"`, `"1w"`). |
| `period` | string | Time window to display (e.g. `"1M"`, `"3m"`). |

### Validation charts

| Widget key | Description |
|---|---|
| `home/ValidationMetricsChart` | Validation run results over time. |
| `home/ValidationStabilityChart` | Percentage of builds that pass the validation. |

| Config field | Type | Description |
|---|---|---|
| `project` | string | Project name. |
| `branch` | string | Branch name. |
| `validationStamp` | string | Validation stamp name. |
| `interval` | string | Bucket size (e.g. `"1d"`). |
| `period` | string | Time window (e.g. `"1M"`). |

### `home/E2ELeadTimeChart`

Measures end-to-end lead time from a source project's promotion to a target project's promotion.

| Config field | Type | Description |
|---|---|---|
| `project` | string | Source project name. |
| `branch` | string | Source branch name. |
| `promotionLevel` | string | Source promotion level. |
| `targetProject` | string | Target project name. |
| `targetBranch` | string | Target branch name. |
| `targetPromotionLevel` | string | Target promotion level. |
| `interval` | string | Bucket size. |
| `period` | string | Time window. |

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
