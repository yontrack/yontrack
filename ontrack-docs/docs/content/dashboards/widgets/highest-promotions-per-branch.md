# Highest promotions per branch

**Key:** `home/HighestPromotionsPerBranch`

Shows, for a list of branches, the latest build of each branch and the highest promotion level this build reached: where each branch is right now.

* The **latest build** is the newest build of the branch, promoted or not. A latest build without any promotion shows _Not promoted yet_, even if an older build of the branch is promoted.
* The **highest promotion** is the promotion of the latest build whose level comes last in the order of the promotion levels of the branch. Promotions are not cumulative: a build can have `GOLD` without `SILVER`.

The widget has two formats:

* **Table** - one line per branch: the branch, its latest build, and its highest promotion.
* **Ladder** - one line per branch, which also shows all the promotion levels of the branch, lowest first. The levels granted to the latest build are shown in full, the ones it was not granted are greyed out, and its highest level is marked with a ring. This shows what the latest build is missing.

In the ladder, hovering over a level opens a popover: for a level granted to the latest build, its name, when the build was promoted, and the description of the level if it has one; for a level not granted, its name and _not granted to this build_. Clicking a granted level opens its promotion run.

The `format` of the configuration is the one shown by default. A viewer can switch it in the header of the widget: the choice is kept in their browser only, needs no right to edit the dashboard, and does not change the configuration.

A row with nothing to show says so: _Branch not found_ for a branch which does not exist or which the viewer cannot see, _No build yet_ for a branch without any build, and _No branch of ... matches ..._ for a pattern matching no branch.

## Configuration

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title` | string | | Overrides the default title, _Highest promotions per branch_. |
| `format` | `TABLE` \| `LADDER` | `TABLE` | The format shown until a viewer switches it. |
| `showProject` | boolean | `true` | Whether the branches are shown with the name of their project. |
| `sort` | `LAST_ACTIVITY` \| `NAME` | `LAST_ACTIVITY` | How the branches are sorted. See below. |
| `branches` | list | `[]` | The branches to show. See below. |

Each entry of `branches` has a `project`, which is required, and **exactly one** of `branch` and `pattern`:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `project` | string | | Name of the project. |
| `branch` | string | | Exact name of the branch. |
| `pattern` | string | | Regular expression on the names of the branches of the project. Only enabled branches are shown, the most recently active first. |
| `count` | int | `5` | Maximum number of branches a `pattern` brings. Only meaningful with `pattern`. |

A branch reached twice - listed with `branch` and matched by a `pattern` - shows once.

A configuration without `showProject` or `sort` takes their defaults.

### Sorting the branches

The branches are sorted according to `sort`, set in the configuration only - the format is the only thing a viewer can switch in the widget:

* `LAST_ACTIVITY` (the default) - by the creation time of the latest build of each branch, the most recent first.
* `NAME` - by the name of the branch, ignoring the case and comparing the numbers as numbers (`release-5.10` comes after `release-5.9`). Two branches of the same name are sorted by the name of their project.

With both sorts, the branches with a build come first. The branches without any build (_No build yet_) come after them - by name with `NAME`, in the order they were looked up with `LAST_ACTIVITY`. The entries with nothing to show (_Branch not found_, _No branch of ... matches ..._) come last, in the order of the configuration.

The sort only orders the branches which were selected: a `pattern` still brings the `count` most recently active branches it matches. With `NAME`, these branches are listed by name.

!!! note "Existing widgets"

    From the 5.5 release which ships the `sort` option, the order of the entries in the configuration is no longer used: existing widgets are sorted by last activity, the default. The drag-and-drop of the entries in the form of the widget is gone with it.

### Hiding the project

With `showProject: false`, a branch is shown with its own name only, without the name of its project. A row with nothing to show - _Branch not found_ or _No branch of ... matches ..._ - keeps the project in any case, since it has no branch to show instead.

!!! warning

    Hide the project only when all the branches come from one project: two branches of the same name in two projects would look alike.

## Example

```yaml
- key: "home/HighestPromotionsPerBranch"
  layout: {x: 0, y: 0, w: 12, h: 30}
  config:
    format: LADDER
    showProject: false
    sort: NAME
    branches:
      - project: "my-project"
        branch: "main"
      - project: "my-project"
        pattern: "release-.*"
        count: 3
```
