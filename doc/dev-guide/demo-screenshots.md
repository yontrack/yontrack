# Demo screenshots

The wiki release notes are hand-written end-user prose, and they lean on screenshots. The demo
runs the exact build being released against a seeded, reproducible dataset, so it is the
natural place to take them: `.github/workflows/demo-screenshots.yml` drives it with Playwright
and hands back a set of images named the way the release-notes convention expects.

The authoring agent still marks where a screenshot would help - that does not change. What
changes is where the image comes from: this takes it, instead of a human taking it by hand.

This adds nothing to `DemoContent`. The definition of done in `CLAUDE.md` asks a user-visible
feature to demonstrate itself in the demo seed; this is release tooling that *reads* the seed
and ships no Yontrack feature, so there is nothing for the demo to show.

## Deliberately outside the pipeline

This gates nothing. No validation stamp is reported, no promotion depends on it, and nothing
dispatches it - it is `workflow_dispatch` only. It is a second system, and it must not be able
to hold up a release for polish.

It also does not seed. [`demo-smoke.yml`](demo-smoke.md) already resets and seeds the demo
deterministically on the way to `SILVER`, and one system owning the demo's state is worth more
than a guarantee here. If the demo has drifted since - someone was demonstrating against it -
dispatch `demo-smoke.yml` first, then this.

And it keeps its own concurrency group rather than joining `demo-smoke`'s. Sharing that group
would serialise the two, which reads like the safer choice until you notice which way the queue
runs: a 20-minute capture would hold up a `SILVER` smoke run, and this is precisely the system
that must never do that. The cost lands the other way instead - a seed starting mid-capture
leaves some images showing the old dataset. That is visible and recoverable on a workflow
someone is watching; re-run it.

## Running it

Dispatch **Demo screenshots** with the version the demo is running, and the version to name
the files after:

```
gh workflow run demo-screenshots.yml -f version=5.3.0-rc-41 -f release=5.3.0
```

Download the `demo-screenshots-<release>` artifact and drop the images into the wiki.

Two versions, because at this point in the pipeline they differ. A build holds `5.3.0-rc-41`
from `BRONZE` and is rewritten to `5.3.0` only at `GOLD`, and the release notes are authored
*before* `GOLD` is granted - that ordering is deliberate, so the GitHub release can link to a
page worth reading the moment it is published. So the demo answers `5.3.0-rc-41` while the wiki
page, and therefore the images, must be named `5.3.0`. Omit `release` and it falls back to
`version`, which is what you want once the two have converged.

`version` is checked, not trusted: the first thing the workflow does is confirm that it is the
version actually answering, via `scripts/demo-smoke.sh poll` with a zero timeout - a single
check rather than a wait. Without it a run would happily produce a plausible set of images of
whatever happened to be deployed.

## What comes out

One PNG per catalogue entry, named `<version>-<slug>.png` - the convention the release-notes
agent in the wiki checkout uses:

```
5.3.0-dashboard.png
5.3.0-project.png
5.3.0-branch.png
5.3.0-build.png
5.3.0-environments.png
```

## Adding a screenshot

The catalogue is `ontrack-web-tests/screenshots/catalogue.js` - one entry per shot:

```javascript
{
    slug: 'my-feature',
    description: 'What the shot is for',
    path: '/display/branch/petclinic/main',
    ready: async (page) => { /* the only assertion: the data has rendered */ },
}
```

Prefer a URL that lands directly in the state worth shooting over clicking your way there. The
dashboard entry is the example: the seed pins `DemoContent.DASHBOARD_UUID` precisely so it can
be addressed, and `?dashboard=<uuid>` selects it in one navigation.

`ready` is what separates a screenshot from a screenshot of a spinner. Never a timeout, and -
less obviously - never the *absence* of a loading indicator. Yontrack renders those only while
loading, so "the spinner is hidden" is already true before the fetch starts, and the shot is of
an empty table. That is exactly how the first version of the branch entry produced a picture of
`No data`.

Section test ids are no better, and this is the trap that looks safest: `PageSection` puts
`data-testid` on its Card and swaps a Skeleton in *underneath*, so the section is visible
throughout its own load. Waiting for `promotions` proves only that the panel's frame exists.

Wait on positive evidence instead: something the page can only show once its data has arrived.
A populated row (`.ant-table-row[data-row-key]` - the `No data` placeholder is an
`.ant-table-placeholder` and matches neither), or content the seed put there, like a promotion
level's name or a link named after a seeded entity.

Every entry is backed by the demo seed, so the names in the catalogue mirror `DemoContent` in
`ontrack-demo-seed`. Renaming something there without renaming it here leaves the capture
navigating to a page that does not exist, which is the intended failure: a screenshot of the
wrong data is worse than no screenshot.

## Layout

| File | Role |
|---|---|
| `ontrack-web-tests/screenshots/catalogue.js` | What gets photographed, and how to know it is ready |
| `ontrack-web-tests/screenshots/naming.js` | The `<version>-<slug>.png` naming, and what it rejects |
| `ontrack-web-tests/screenshots/capture.spec.js` | Signs in once, then walks the catalogue |
| `ontrack-web-tests/screenshots/catalogue.spec.js` | Guards on the two above |
| `ontrack-web-tests/playwright.screenshots.config.js` | The `guard` and `capture` projects |

The config is a third one beside `playwright.config.js` and `playwright.demo.config.js` for the
same reason the second exists: `testDir` is what keeps the regular `PLAYWRIGHT` suite from
picking these specs up and running them against the local stack.

## The guards

`capture` depends on `guard`, so the pure checks - unique slugs, well-formed slugs, a rooted
path and a `ready` function on every entry, and the naming rules - run first and fail in
milliseconds. A duplicate slug would otherwise be discovered by one shot silently overwriting
another, three minutes into driving the demo.

They need no demo and no environment, so they run anywhere:

```bash
cd ontrack-web-tests && npm run screenshots-guard
```

Which is the loop to use when editing the catalogue.
