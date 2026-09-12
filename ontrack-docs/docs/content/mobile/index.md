# The mobile UI

Yontrack serves two user interfaces from the same instance: the full one you use on a
desktop, and a phone-sized one under `/mobile`. You do not have to choose between them —
open Yontrack on a phone and you get the mobile UI, open it on a laptop and you get the
desktop one.

The mobile UI is **not a smaller copy of the desktop UI**. It covers a handful of things
well rather than everything badly, and when you reach for something it does not cover it
says so and hands you the desktop version rather than pretending.

## What it does

Five things — the ones people actually do from a phone:

**Find a project and a branch.**
:   The home screen is your [favourite](#favourites) projects and branches. The full
    project list is one tap away in the bottom bar, and it — like the branch list on a
    project — is filterable by name: a phone shows a handful of rows and an instance holds
    far more than that.

**See the latest builds, with their promotions and deployments.**
:   A branch screen shows its latest builds as cards: what the build is called, when it
    happened, how far it has been [promoted](../concepts/model/index.md), and which
    [environments](../integrations/environments/environments.md) it is currently deployed
    to. Each promotion is named as well as drawn, so it reads without zooming.

**Search the builds of a branch.**
:   By name and by promotion level, together or separately. The name is matched against the
    [display name](../concepts/build-filtering/index.md#filtering-on-the-build-display-name),
    which is the version you would say out loud rather than the timestamp Yontrack calls the
    build.

**Promote a build.**
:   From the build screen. Promotion levels that declare
    [fields](../concepts/model/promotion-level-fields.md) get them, required ones included,
    so no promotion level is out of reach from a phone. The date and time is collapsed —
    promoting from a phone means promoting now — with a "Promoted earlier?" link for the
    rare correction.

**Deploy a build, and unblock a deployment.**
:   The build screen lists the [environments](../integrations/environments/environments.md)
    the build may go to, and says why one is refusing it rather than hiding it. Starting a
    deployment lands on the deployment's own screen, where you answer its admission rules —
    a manual approval, say — or override one with a reason, and then run it. A deployment
    somebody else started, usually CI, is reachable from the same build screen, which is
    what makes "approve the thing that is waiting" a phone job at all.

A build screen also lists that build's validations, read-only. They are otherwise out of
scope, but whether a build is green is the question you answer before promoting or
deploying it, and having to switch to the desktop UI to check would defeat the point.

## What it deliberately does not do

Everything else, and on purpose. Notably:

- **Configuration and administration.** Settings, security, users and groups, connections
  to GitHub, Jenkins or Jira — none of it is on a phone.
- **Dashboards** and [charts](../dashboards/index.md), and the
  [branch views](../concepts/branch-views/index.md).
- **Global search**, [change logs](../integrations/changelogs/changelogs.md),
  [auto-versioning](../integrations/auto-versioning/auto-versioning.md),
  [workflows](../integrations/workflows/workflows.md) and
  [notifications](../integrations/notifications/index.md).
- **Validation detail.** A build screen says which validations ran and how they did; the
  run itself, the matrix and the per-stamp history stay on the desktop.
- **Finishing or cancelling a deployment.** Marking a deployment done is CI's job, and
  cancelling one is destructive enough to want a bigger screen.
- **Editing anything** — names, descriptions, properties, links.

This list is not a backlog. The mobile UI exists precisely because the desktop UI is not
designed to shrink, so a screen is added to it because someone decided a phone needed that
flow — not as a step towards eventual parity.

### Favourites

Because the home screen is your favourites, a phone is worth a minute of setup on the
desktop: star the projects and branches you actually watch and the app opens on them. You
can also star and unstar from the phone itself — every project and branch row carries the
toggle. It is one list of favourites shared by both UIs, not a separate one per device.

## Phones are redirected automatically

Yontrack looks at the browser's user agent and sends **phones** to `/mobile`. Nothing to
install, no separate address to remember, and a link someone shares from their desktop
session keeps its subject: a link to a project, a branch, a build or a deployment lands on
that same project, branch, build or deployment in the mobile UI.

Two things follow from the user agent being the whole input:

- **Tablets keep the desktop UI.** They have the width for it.
- A desktop browser is never redirected, however narrow its window. Resizing a laptop
  window to phone width does not show you the mobile UI.

When you follow a link to something the mobile UI does not cover, you land on a short
**Desktop only** page naming where you were going and offering both ways on: *Open the
desktop version* of that page, or *Go to the mobile home*. Neither of the silent
alternatives would do — serving you the desktop page costs you the readable UI, and dropping
you on the home screen loses what you came for.

## Switching to the desktop version

Two ways, and they do the same thing:

- **From the account screen.** Tap your name in the header, and under **This device** tap
  **Open the desktop version**. This is the one to use when you simply want the desktop UI
  — to read a change log, to open an administration page, or because you prefer it. It
  takes you to the desktop home page.
- **From the Desktop only page**, when you followed a link to something the mobile UI does
  not cover. Tap **Open the desktop version** there and you land on the page you were
  actually heading for, rather than on the home page.

From then on that browser stays on the desktop UI: every Yontrack page you open in it is
the desktop one, and the phone redirect stops applying.

!!! note

    The choice belongs to the **browser**, not to your account. Choosing the desktop
    version on your phone does not affect Yontrack on your laptop, or on a colleague's
    phone. It also lasts only as long as the browser session — close the browser and the
    next visit is back on the mobile UI.

The desktop UI is not designed for a phone screen, so expect to pan and zoom. It is the
complete UI, which is the point: it is the escape hatch, not a second mobile experience.

## Getting back to the mobile UI

Open the user menu — your avatar, at the top right of the desktop UI — and choose
**Mobile version**. That clears the choice and takes you to the mobile home screen.

That entry is tappable at phone width, deliberately: it is the only way back, and without it
a phone that once chose the desktop version would be stuck on it until the browser session
ended.

## Your account, the theme and the version

Tap your name in the header to reach the account screen. It carries who you are signed in
as, the light/dark theme, the way to
[open the desktop version](#switching-to-the-desktop-version), the Yontrack version — the
one to quote on a support thread — and the way to sign out.

The screen separates the two kinds of choice, because they behave differently.
**Appearance** is about **your account**: choosing dark on your phone means the desktop UI
is dark too, next time you open it, and **Auto** follows whatever your phone or laptop is
set to, saying which one it is currently resolving to. **This device** is about the browser
you are holding: opening the desktop version there changes nothing for your laptop or for
anyone else, and it lasts only until you close the browser.
