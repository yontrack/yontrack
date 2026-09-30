# Project scorecard

**Key:** `extension/scorecard/ProjectScorecard`

Displays the [delivery scorecard](../../scorecard/scorecard.md) of a project: the targets it meets
in each of its [estates](../../scorecard/estates.md), and its readings.

## Configuration

| Field | Type | Description |
|-------|------|-------------|
| `project` | string | Project name. Required. |
| `set` | string | Optional. The set the widget opens on: `project` for the project on its own, or the name of an estate. |

Without a `set`, the widget opens on the first estate of the project by name, or on the project on
its own when it belongs to no estate. A configured estate the project is no longer in falls back
on that default, and the widget says so in one line.

## What it shows

* One tab per set: *Project*, then *estate · met/count* for each estate. Switching tabs is local to
  the page and is not saved into the widget. A project in no estate has no tab bar.
* For an estate: a ring of its targets met — one segment per reading judged against a target, the
  met ones first — and the judged readings, each *Met* or *Missed* with its value. This is a count
  of the targets met, not a score: the readings are never combined into one number.
* For the project on its own: its readings, never judged, with the trend of their last 90 days.
* The marker the set is read up to, when its readings were computed, and an **Open scorecard**
  link to the scorecard page of the project, on the set shown.

The widget is titled *Scorecard · project* and follows the permissions of the project. The
estates need the **Delivery scorecard** feature of the license, as on the
[project page](../../scorecard/scorecard.md#license).

```yaml
- key: "extension/scorecard/ProjectScorecard"
  layout: {x: 0, y: 0, w: 4, h: 24}
  config:
    project: "petclinic-visits"
```
