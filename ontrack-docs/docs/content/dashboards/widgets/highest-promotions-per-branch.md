# Highest promotions per branch

**Key:** `home/HighestPromotionsPerBranch`

Shows, for a list of branches, the latest build of each branch and the highest promotion level this build reached: where each branch is right now.

* The **latest build** is the newest build of the branch, promoted or not. A latest build without any promotion shows _Not promoted yet_, even if an older build of the branch is promoted.
* The **highest promotion** is the promotion of the latest build whose level comes last in the order of the promotion levels of the branch. Promotions are not cumulative: a build can have `GOLD` without `SILVER`.

The widget has two formats:

* **Table** - one line per branch: the branch, its latest build, and its highest promotion.
* **Ladder** - one line per branch, which also shows all the promotion levels of the branch, lowest first. The levels granted to the latest build are shown in full, the ones it was not granted are greyed out, and its highest level is marked with a ring. This shows what the latest build is missing.

The `format` of the configuration is the one shown by default. A viewer can switch it in the header of the widget: the choice is kept in their browser only, needs no right to edit the dashboard, and does not change the configuration.

A row with nothing to show says so: _Branch not found_ for a branch which does not exist or which the viewer cannot see, _No build yet_ for a branch without any build, and _No branch of ... matches ..._ for a pattern matching no branch.

## Configuration

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title` | string | | Overrides the default title, _Highest promotions per branch_. |
| `format` | `TABLE` \| `LADDER` | `TABLE` | The format shown until a viewer switches it. |
| `branches` | list | `[]` | The branches to show, in this order. See below. |

Each entry of `branches` has a `project`, which is required, and **exactly one** of `branch` and `pattern`:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `project` | string | | Name of the project. |
| `branch` | string | | Exact name of the branch. |
| `pattern` | string | | Regular expression on the names of the branches of the project. Only enabled branches are shown, the most recently active first. |
| `count` | int | `5` | Maximum number of branches a `pattern` brings. Only meaningful with `pattern`. |

The rows follow the order of the entries, and the branches of a pattern the order of their activity. A branch reached twice - listed with `branch` and matched by a `pattern` - shows once, at its first position.

## Example

```yaml
- key: "home/HighestPromotionsPerBranch"
  layout: {x: 0, y: 0, w: 12, h: 30}
  config:
    format: LADDER
    branches:
      - project: "my-project"
        branch: "main"
      - project: "my-project"
        pattern: "release-.*"
        count: 3
```
