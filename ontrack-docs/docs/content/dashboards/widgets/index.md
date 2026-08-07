# Widgets

Widgets are the building blocks of dashboards. Each widget displays a specific view of your delivery data and is identified by a **key** used in the [Dashboards as Code](../dashboards-as-code.md) YAML format.

| Widget | Key | Description |
|--------|-----|-------------|
| [Last active projects](last-active-projects.md) | `home/LastActiveProjects` | Most recently active projects |
| [All projects](all-project-list.md) | `home/AllProjectList` | All projects with pagination and filtering |
| [Project list](project-list.md) | `home/ProjectList` | Fixed list of specific projects |
| [Favourite projects](favourite-projects.md) | `home/FavouriteProjects` | Current user's favourite projects |
| [Favourite branches](favourite-branches.md) | `home/FavouriteBranches` | Current user's favourite branches |
| [Branch statuses](branch-statuses.md) | `home/BranchStatuses` | Promotion and validation status per branch |
| [Last validations for a branch](last-validations-for-branch.md) | `home/LastValidationsForBranch` | Last validation results for a branch |
| [Validations of last promoted build](validations-last-promotion-build.md) | `home/ValidationsLastPromotionBuild` | Validations of the last promoted build |
| [Project promotion](project-promotion.md) | `home/ProjectPromotion` | Promotion status for a project with dependencies |
| [Build dependencies tree](build-dependencies-tree.md) | `home/BuildDependenciesTree` | Downstream build dependency tree |
| [Promotion charts](promotion-charts.md) | `home/PromotionLeadTimeChart` · `home/PromotionFrequencyChart` · `home/PromotionStabilityChart` · `home/PromotionTTRChart` | Delivery metrics charts for promotion levels |
| [Validation charts](validation-charts.md) | `home/ValidationMetricsChart` · `home/ValidationStabilityChart` | Metrics charts for validation stamps |
| [End-to-end lead time](e2e-lead-time-chart.md) | `home/E2ELeadTimeChart` | Lead time across two projects |
