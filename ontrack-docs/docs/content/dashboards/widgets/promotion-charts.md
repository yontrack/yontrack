# Promotion charts

Four widgets chart the delivery of one promotion level on one branch. They all share the same
configuration shape, and the same charts are shown on the page of the promotion level.

| Key | Chart | What it charts |
|-----|-------|----------------|
| `home/PromotionLeadTimeChart` | Lead time to promotion | The time from the creation of a build to its first promotion at the level. |
| `home/PromotionFrequencyChart` | Promotion frequency | The number of promotions at the level. |
| `home/PromotionStabilityChart` | Promotion success rate | The percentage of the builds which were promoted at the level. |
| `home/PromotionTTRChart` | Promotion time to restore | How long it takes to promote again once builds stop being promoted. |

## How the charts are computed

The charts are computed from the same samples as the readings of the
[delivery scorecard](../../scorecard/scorecard.md), with the promotion level as marker and its
branch as scope, and then bucketed by period. A chart over the window of a reading holds the
reading's samples.

**Lead time to promotion**
:   From the creation of a build to its **first** promotion run at the level. A build is counted in
    the period where it was first promoted. Each period shows the mean, the 90th percentile and the
    maximum — where the reading gives the median.

**Promotion frequency**
:   Every promotion run at the level, counted in the period where it happened.

**Promotion success rate**
:   The builds of the branch, counted in the period of their creation: 100% when promoted at the
    level, 0% when not. The builds **in flight** at the end of the charted interval — created within
    the median lead time of the interval before its end — are left out: they have not had the time
    to be promoted yet.

**Promotion time to restore**
:   An outage starts with the **first** unpromoted build following a promoted one, and ends with the
    next promotion. For B1 promoted, then B2, B3 and B4 not promoted, then B5 promoted, the time to
    restore goes from the creation of B2 to the promotion of B5. An outage is counted in the period
    where it was restored; an outage still going on is not charted. Each period shows the mean, the
    90th percentile and the maximum.

!!! note "Changed in 6.0"

    * The time to restore used to start at the **last** unpromoted build before the promotion — B4
      in the example above. It now starts at the first one, so an outage longer than one broken
      build reads longer than it did.
    * The lead time used to be counted in the period of the build's creation, and for every
      promotion run of the build. It is now counted in the period of its first promotion.
    * The success rate used to count every build of the interval, including the ones just created.
      It now leaves out the builds in flight.

    See [Migration to V6](../../appendix/migration-to-v6.md#delivery-metrics-and-the-delivery-scorecard).

## Configuration

| Field | Type | Description |
|-------|------|-------------|
| `project` | string | Project name. |
| `branch` | string | Branch name. |
| `promotionLevel` | string | Promotion level name. |
| `interval` | string | Time window to display, back from now: a number and a unit, `d`, `w`, `m` (months) or `y` (e.g. `"3m"`, the default). |
| `period` | string | Bucket size for the chart, with the same units (e.g. `"1w"`, the default, or `"1d"`). |

## When the promotion level is missing

The widget resolves the promotion level by name every time it is displayed. If the configured
project, branch or promotion level does not exist any more, the widget title still names the
configuration, marked as `(not found)`, and the widget body says what is missing instead of a
chart. Edit the dashboard and reconfigure the widget to point it at an existing promotion level.

## Example

```yaml
- key: "home/PromotionLeadTimeChart"
  layout: {x: 0, y: 0, w: 6, h: 30}
  config:
    project: "Backend"
    branch: "main"
    promotionLevel: "PRODUCTION"
    interval: "3m"
    period: "1w"
```
