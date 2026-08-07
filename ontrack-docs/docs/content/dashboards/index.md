# Dashboards

Dashboards are customisable home pages built from **widgets**. Each widget displays a specific view of your delivery data — project lists, branch promotion statuses, charts, dependency trees, and more.

## Scopes

Every dashboard has a scope that controls who can see it:

| Scope | Description |
|-------|-------------|
| **Built-in** | Shipped with Yontrack. Cannot be edited or deleted. |
| **Shared** | Visible to all users. Created and managed by users with the `DashboardSharing` permission. |
| **Private** | Visible only to the user who created it. |

## Selecting a dashboard

Use the **Dashboard** dropdown in the top bar to switch between available dashboards. The currently active dashboard is highlighted with a check mark.

## Creating and editing

From the **Dashboard** menu you can:

- **Create a new dashboard** — starts with a blank private dashboard in edit mode
- **Clone current dashboard** — duplicates the active dashboard as a new private copy
- **Edit current dashboard** — enter edit mode to add, move, resize, or remove widgets
- **Share current dashboard** — promote a private dashboard to shared (requires `DashboardSharing`)
- **Delete current dashboard** — remove the active dashboard (built-in dashboards cannot be deleted)

## Dashboards as Code

Shared dashboards can be defined and managed as YAML, enabling version-controlled, team-wide dashboard definitions. See [Dashboards as Code](dashboards-as-code.md).

## Widgets

See the [Widgets](widgets/build-dependencies-tree.md) section for documentation on individual widget types.
