# Estates

!!! warning

    This feature is under license: see [License](#license).

An **estate** is a group of projects read together in the [delivery scorecard](scorecard.md): the
projects are selected by [labels](../concepts/model/project-labels.md), and read against the
estate's own marker, windows and targets. Every project of an estate gets one more set of readings
— one more column in its Scorecard section — beside its no-estate readings, which do not change.

A project can belong to several estates, and is then read differently in each: its lead time to
the `GOLD` promotion in one, its lead time to `production` in another.

## What an estate is made of

**Name**
:   Unique. It names the estate's column on the scorecards, and the `estate` tag of the exported
    [`ontrack_reading`](scorecard.md#metrics-export) metric.

**Description**
:   Optional.

**Labels**
:   At least one. The estate selects the non-disabled projects carrying **all** of them. A project
    belongs to an estate by carrying its labels, never by being added to it.

**Marker**
:   What the delivery readings are read up to — see [Marker](#marker).

**Windows and targets**
:   Per reading, optionally: a window overriding the one of the
    [settings](scorecard.md#settings), and a target — see [Targets](#targets).

## Marker

| Marker      | The delivery readings are read up to                                                  |
|-------------|----------------------------------------------------------------------------------------|
| Default     | The **highest-ordered environment** where the project owns a slot with the default qualifier; else, for a project with no slot, the last promotion level of each branch, as with no estate. |
| Promotion   | The promotion level of that name, on each branch in scope which has one.               |
| Environment | The deployments in the project's slot in that environment — the default slot (qualifier `""`) unless the estate names a qualifier. |

With a default marker, two projects of the same estate may be read up to different events: the
reading's details always say which marker applied. The readings up to an environment are defined
in [Up to an environment](scorecard.md#up-to-an-environment); they read `NO_MARKER` for a project
with no slot there, and `NOT_LICENSED` when the license does not include the environments.

Qualifiers are never pooled: an estate reads one qualifier, so that a build deployed to a canary
and then to the main slot is not counted twice.

## Targets

A target is one threshold per reading. Which way it judges is fixed by the reading:

| Reading          | Met when the value is |
|------------------|-----------------------|
| Lead time        | ≤ target              |
| Frequency        | ≥ target              |
| Success rate     | ≥ target              |
| Time to restore  | ≤ target              |
| Test pass rate   | ≥ target              |
| Test flakiness   | ≤ target              |

A reading is then **met** or **missed** — there is no third state. A reading with no target is
shown, not judged, and an unknown reading is not judged either. A time to restore with no failure
in the window reads *No failure in window*, neither met nor missed.

Targets are in the unit of the reading: **seconds** for a duration, **per week** for a frequency,
**0 to 100** for a rate. The estates page lets you enter a duration in minutes, hours or days, and
converts it.

## The estates page

**Estates**, in the *Configurations* group of the user menu, lists the estates:

* the name and description,
* the labels,
* the marker — *Default*, *Promotion: GOLD*, *Environment: production*, with the qualifier if any,
* the windows and targets — *Lead time: ≤ 1d, over 30 days* — or *Default windows, no target*,
* the number of projects the estate selects, which lists them on click,
* when the estate's readings were last computed.

*New estate* and the pencil of a row open the estate dialog: the name, the description, the labels,
the marker — *Default*, *Promotion level* with its name, or *Environment* with its name and an
optional qualifier — and, per reading, a window in days (empty for the window of the settings) and
a target (empty for none).

*Recompute* on a row queues the recompute of the readings of every project of the estate; the row
shows it running. *Delete* asks for confirmation, then deletes the estate and its snapshots: the
projects and the labels are kept.

The estates page is on the desktop UI only.

## Security

Creating, editing, deleting and recomputing estates needs the `EstateManagement` global function,
granted to the *Administrator* and *Creator* roles — the ones managing the labels, since the labels
are what select the projects. The *Estates* menu entry is only offered to them.

Any authenticated user can see the estates, and sees the readings of the projects they can see.

## Labels used by an estate

**Deleting a label an estate uses is refused**, with a message naming the estates:

```
Label portfolio:product cannot be deleted: it selects the projects of the estate Demo products.
```

Dropping one label from an estate's list of required labels would silently widen the estate to
more projects. Remove the label from the estate — or delete the estate — first.

## Configuration as code

The estates can be configured as [code](../configuration/casc.md), under `ontrack.config.estates`:

```yaml
ontrack:
  config:
    estates:
      - name: Products
        description: The products we ship
        labels:
          - portfolio:product
        marker:
          kind: PROMOTION
          levelName: GOLD
        readings:
          - key: delivery.leadTime
            target: 86400        # 1 day, in seconds
          - key: delivery.frequency
            windowDays: 30
            target: 1            # per week
          - key: delivery.successRate
            target: 90           # percent
      - name: Production
        labels:
          - runs-in:production
        marker:
          kind: ENVIRONMENT
          environment: production
          qualifier: ""          # optional, the default slots
```

* A label is written `category:name`, or `name` for a label without a category.
* `marker` is omitted for the default marker.
* A reading is listed only for a window override or a target; `windowDays` and `target` are both
  optional.
* **The list is authoritative**: an estate it names is created or updated, and an existing estate
  it does not name is deleted, with its snapshots.
* Without the Delivery scorecard license, the list is ignored with a warning, and Yontrack starts.

## API

In GraphQL, `estates` and `estate(name)` read the estates, and the `createEstate`, `updateEstate`,
`deleteEstate` and `recomputeEstate` mutations manage them. `updateEstate` replaces the whole
estate:

```graphql
mutation {
  createEstate(input: {
    name: "Products",
    labels: ["portfolio:product"],
    marker: {kind: PROMOTION, levelName: "GOLD"},
    readings: [{key: "delivery.leadTime", target: 86400}]
  }) {
    estate { id name }
    errors { message }
  }
}
```

On a project, each estate's readings are a set of
[`Project.scorecard`](scorecard.md#api), with its `estate`, and each reading gives its `target` and
`targetMet`.

In the KDSL:

```kotlin
val estate = ontrack.estates.create(
    name = "Products",
    labels = listOf("portfolio:product"),
    marker = EstateMarker.Promotion("GOLD"),
    readings = listOf(
        EstateReadingConfig(key = ReadingKeys.DELIVERY_LEAD_TIME, target = 86400.0),
    ),
)
// Recomputes the readings of the estate's projects and waits for them, by project name
val scorecards = estate.recomputeAndWait()
val leadTime = scorecards["my-project"]?.estate("Products")?.reading(ReadingKeys.DELIVERY_LEAD_TIME)
```

An estate recompute computes the estate's readings only; `project.recomputeScorecardAndWait()`
computes every set of the project.

## License

The estates need the **Delivery scorecard** feature (`extension.scorecard`) of the license. The
GraphQL schema is the same with or without it; without it:

* the estate queries, fields and mutations fail with a `FORBIDDEN` error, *Feature not allowed by
  the license: extension.scorecard*;
* the estates are not computed, and their columns are not shown on the scorecards;
* the estates in the configuration as code are ignored;
* the stored estates and their snapshots are kept, and come back with the license.

An estate reading up to an environment also needs the **Environments** feature
(`extension.environments`).
