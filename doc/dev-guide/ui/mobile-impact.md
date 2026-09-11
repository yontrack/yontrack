# Judging mobile impact

Yontrack serves **two** user interfaces from one instance: the desktop UI at the root, and a
phone-sized one under `/mobile`. The Definition of done in `CLAUDE.md` therefore asks every
change to the web UI to say whether it affects the mobile UI, and to say which way it decided.

This page is what makes that question answerable rather than a ritual. It is for the person
who has just finished a **desktop** change and has to answer it; `mobile-ui.md` is the
reference for building the mobile UI itself, and this page points into it wherever the detail
lives there.

**Most desktop changes have no mobile impact, and that is the expected answer.** The point of
the checklist item is not to manufacture mobile work. It is that the mobile UI is small,
young and easy to forget: the failure mode is a desktop change that quietly breaks or
outgrows it and nobody noticing until a user does. An assertion of "no impact" that somebody
actually made is the whole deliverable. Silence is not that assertion.

## Why the question exists at all

The desktop UI has no responsive design — no `useBreakpoint`, no antd responsive grid props,
and exactly one layout media query — across roughly 1260 components. That one query is
`.ot-navbar` in `styles/globals.css`: the desktop header's avatar opens the user menu, and
the user menu holds the only way back to `/mobile`, so it has to be tappable on a phone
(#1729). It is a bounded exception, not the start of a retrofit. A phone-shaped app for the four
things people actually do from a phone (check a build, promote it, deploy it, find the branch
they are on) is a smaller and safer thing to build and maintain than a retrofit of all of
that.

The price of that trade is exactly this page. Two UIs means two things to keep working, and
the second one is not the one you are looking at.

## What the two share

| Shared | Not shared, on purpose |
|---|---|
| Services, GraphQL fragments and mutations | **Every layout component** |
| Authorization helpers (`isAuthorized`, the `authorizations` field) | `MainLayout`, `MainPage`, `MainPageBar`, `NavBar`, `UserMenu` |
| Theme tokens (`styles/globals.css`) and the pre-paint theme script | The mobile shell: `MobileLayout`, `MobileHeader`, `MobileBottomNav` |
| A handful of display primitives — `ValidationChip`, `PromotionLevelImage`, `TimestampText`, `EntityIcon` | The provider stack: `/mobile` is its own App Router root and assembles its own |
| The next-auth session and the `/api/protected/graphql` proxy | Every screen, and the lists and cards they are built from |

The boundary is deliberately sharp, and the layout half of it is the load-bearing one:
sharing a layout component between the two UIs means a change made for one silently deforms
the other, in a viewport whoever made the change is not looking at. See
[The mobile UI](mobile-ui.md) for the full picture.

That table is also the shape of the impact question. The left column is where a desktop
change reaches the mobile UI; the right column is where it cannot.

## Changes that usually **do** have mobile impact

- **A GraphQL field the mobile UI reads is changed, renamed or removed.** This is the big
  one, and the only category where a desktop change can break a mobile screen outright. The
  inventory is short enough to check by eye — see below.
- **A new promotion level field type**, or any change to how a promotion level is drawn. The
  mobile build screen and every build card render the level's medal beside its name, through
  the shared `PromotionLevelImage`.
- **A new admission-rule or deployment surface.** The build screen's deploy entry point is
  gated on `slotPipeline/create`, and both the build screen and the branch screen read
  `Build.currentDeployments`.
- **A new authorization**, or a change to an existing one, on anything the mobile UI acts on.
  `MobileBuildActions` gates its two entry points off `build/promote` and
  `slotPipeline/create`, read exactly as the desktop UI reads them. A new right gating an
  action the mobile UI already offers is a mobile change too.
- **A new or changed desktop route.** The route map decides what a phone following a link to
  it sees — see [Routes](#routes-adding-a-desktop-page-is-a-mobile-decision) below.
- **A change to a shared display primitive.** `ValidationChip`, `PromotionLevelImage`,
  `TimestampText` and `EntityIcon` all render inside `/mobile` at phone width. A primitive
  that grew a desktop-sized affordance, or that started reaching for a provider the mobile
  stack does not have, breaks a mobile screen and not a desktop one.

  That last one has already happened once: `useEventForRefresh` read a context with an empty
  default, and the mobile provider stack has no `EventsContextProvider` — so every promotion
  medal would have thrown and taken a whole mobile screen with it.

## Changes that usually **do not**

- Desktop-only layout, page-bar and table work — including everything in the right-hand
  column above.
- Admin, settings, configuration and reference pages. The mobile UI serves none of them, and
  a phone following a link to one gets the interstitial by design.
- Dashboards and widgets. There is no mobile dashboard.
- The delivery map, the pipeline views and the environments pages.
- Change logs, search and the extension pages. Global search is desktop-only for 5.4.
- Anything under `ontrack-web-core/components/dashboards/`, `.../widgets/`, `.../layouts/`,
  `.../grid/`, `.../charts/`, `.../links/` or `.../views/`. The mobile UI imports from
  `@components/` sparingly, and the table above is the whole list of what it imports —
  a tree that is not in it cannot reach a mobile screen.

"Usually" is doing real work in both lists. They are a starting point for the judgement, not
a decision procedure — a rule that mechanically decided which desktop changes need a mobile
counterpart would be wrong in both directions, exactly as the demo-seed rule says of itself.

## The GraphQL the mobile UI reads

The mobile UI adds **no GraphQL of its own**: every screen reads fields the desktop UI
already had. That is what makes a backend or schema change the most likely way to break it,
and it is also what makes the check cheap — this is the whole list.

| Screen | Reads |
|---|---|
| Home | `projects(favourites: true)`, `branches(favourite: true)` |
| Projects | `projects(pattern:)` |
| Project | `project(id:)`, `Project.branches(name:, count:, order:)` |
| Branch | `branch(id:)`, `Branch.builds(filter: StandardBuildFilter, size:)` with `withDisplayName` and `withPromotionLevel`, `Branch.promotionLevels` |
| Build | `build(id:)` — `displayName`, `description`, `creation`, `branch`, `authorizations`, `promotionRuns(lastPerLevel: true)`, `validations(size:)` with its runs' `lastStatus` |
| Build and branch | `Build.currentDeployments`, in a query of its own |
| Favourites | the four `favourite`/`unfavourite` mutations |

Two things about that list are worth knowing before changing anything on it:

- **`Build.currentDeployments` is asked for separately, deliberately.** It is contributed by
  the environments extension and is *absent from the schema* on an instance without the
  licence, so naming it in the main query would fail validation and take the whole screen
  down. Any future field an extension contributes conditionally needs the same treatment —
  see `useMobileDeployments`.
- **The filter goes to the deployments query too.** Asked for under different terms, the two
  queries are pages of two different lists and the badges silently vanish.

If your change touches a row of that table, it has mobile impact. If it does not, it very
probably has none — say so and move on.

## Routes: adding a desktop page is a mobile decision

`components/mobile/mobileRoutes.js` is the single source of truth for the split. The
middleware reads it to decide, for a phone, between a mobile screen and the interstitial:

- `mobileEquivalent(pathname)` — the desktop routes that have a mobile screen. Deliberately
  short; a route earns an entry only once the screen behind it exists and does the job.
- `isRedirectExempt(pathname)` — the paths the redirect must not touch at all.
- `describeDesktopRoute(pathname)` — what to call a desktop route in the interstitial, so it
  can name where the user was going.

**Adding a desktop route means deciding what a phone following a link to it should see.** A
route added without an entry silently gets the interstitial. That may well be the right
answer — it usually is — but it should be a choice, and adding a `describeDesktopRoute`
description is what turns a raw path into a sentence the user can read. That is frequently
the entire mobile half of a desktop change, and it takes one line.

## Answering the checklist item

Say it in the same place you say the demo-seed decision — the commit, the issue, the report.
One or two sentences is the expected size:

> *No mobile impact: the change is to the dashboard widget layout, which the mobile UI does
> not serve.*

> *Mobile impact: `Build.validations` grew a required argument, so the mobile build screen's
> query was updated alongside the desktop one.*

> *Mobile impact: the new `/core/config/retention` route has no mobile screen and should not
> have one; it is described in `mobileRoutes.js` so the interstitial names it.*

When there is impact, cover it in `ontrack-web-tests/tests/core/mobile.spec.js`, whose tests
run in a phone browser context.

## See also

- [The mobile UI](mobile-ui.md) — the shell, the redirect, the route map and the screens
- [Demo seed and reset](../demo-seed.md) — the other half of the Definition of done
