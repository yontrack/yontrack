# Widgets

Widgets are the building blocks of dashboards. Each widget displays a specific view of your delivery data and is identified by a **key** used in the [Dashboards as Code](../dashboards-as-code.md) YAML format.

## Available widgets

| Key | Description |
|-----|-------------|
| [`home/LastActiveProjects`](#homelastactiveprojects) | Most recently active projects |
| [`home/AllProjectList`](#homeallprojectlist) | All projects with pagination and filtering |
| [`home/ProjectList`](#homeprojectlist) | Fixed list of specific projects |
| [`home/FavouriteProjects`](#homefavouriteprojects) | Current user's favourite projects |
| [`home/FavouriteBranches`](#homefavouritebranches) | Current user's favourite branches |
| [`home/BranchStatuses`](#homebranchstatuses) | Promotion and validation status per branch |
| [`home/LastValidationsForBranch`](#homelastvalidationsforBranch) | Last validation results for a branch |
| [`home/ValidationsLastPromotionBuild`](#homevalidationslastpromotionbuild) | Validations of the last promoted build |
| [`home/ProjectPromotion`](#homeprojectpromotion) | Promotion status for a project with dependencies |
| [`home/BuildDependenciesTree`](#homebuilddependenciestree) | Downstream build dependency tree |
| [`home/PromotionLeadTimeChart`](#promotion-charts) | Average time from build creation to promotion |
| [`home/PromotionFrequencyChart`](#promotion-charts) | How often builds reach a promotion level |
| [`home/PromotionStabilityChart`](#promotion-charts) | Percentage of builds that reach a promotion level |
| [`home/PromotionTTRChart`](#promotion-charts) | Time-to-recovery after a promotion regression |
| [`home/ValidationMetricsChart`](#validation-charts) | Validation run results over time |
| [`home/ValidationStabilityChart`](#validation-charts) | Percentage of builds that pass a validation |
| [`home/E2ELeadTimeChart`](#homee2eleadtimechart) | End-to-end lead time across two projects |

---

## `home/LastActiveProjects`

Displays the most recently active projects.

| Config field | Type | Default | Description |
|---|---|---|---|
| `count` | integer | 10 | Number of projects to display. |

---

## `home/AllProjectList`

Lists all projects with pagination and filtering. No configuration required.

---

## `home/ProjectList`

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

---

## `home/FavouriteProjects`

Displays the current user's favourite projects. No configuration required.

---

## `home/FavouriteBranches`

Displays the current user's favourite branches.

| Config field | Type | Description |
|---|---|---|
| `project` | string | If set, restricts the list to branches of this project. Omit to show all favourite branches. |

---

## `home/BranchStatuses`

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

---

## `home/LastValidationsForBranch`

Shows the latest validation results for a specific branch.

| Config field | Type | Default | Description |
|---|---|---|---|
| `title` | string | | Widget header label. |
| `project` | string | | Project name. |
| `branch` | string | | Branch name. |
| `validations` | string[] | | Validation stamps to display. |
| `displayPromotions` | boolean | `false` | Show promotion levels alongside validations. |

---

## `home/ValidationsLastPromotionBuild`

Shows validation results for the last build promoted to a given promotion level.

| Config field | Type | Description |
|---|---|---|
| `title` | string | Widget header label. |
| `project` | string | Project name. |
| `branch` | string | Branch name. |
| `promotion` | string | Promotion level name. |
| `validations` | string[] | Validation stamps to display. |

---

## `home/ProjectPromotion`

Displays the latest promotion status for a project, including dependencies and decorations.

| Config field | Type | Description |
|---|---|---|
| `project` | string | Project name. |
| `promotions` | string[] | Promotion levels to show. |
| `depth` | integer | Dependency depth to render (default 2). |
| `label` | string | Optional label to filter the dependency graph. |

---

## `home/BuildDependenciesTree`

Shows the downstream dependency tree of the latest build promoted to a given level. See the dedicated [Build dependencies tree](build-dependencies-tree.md) page for a full description and screenshot.

| Config field | Type | Description |
|---|---|---|
| `title` | string | Widget header label. |
| `project` | string | Project name. |
| `branch` | string | Branch name. |
| `promotionLevel` | string | Promotion level to watch. |

---

## Promotion charts

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

---

## Validation charts

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

---

## `home/E2ELeadTimeChart`

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
