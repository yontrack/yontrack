# The mobile UI

Yontrack serves two user interfaces from one instance: the desktop UI, at the root, and a
phone-sized one under `/mobile`. This page describes the shell they share the instance
through — the redirect, the boundary between them, and the route map that decides what a
phone sees. The mobile screens themselves are documented as they land.

> Working on the **desktop** UI and answering the Definition of done's mobile question?
> [Judging mobile impact](mobile-impact.md) is the shorter page for that: what the two UIs
> share, what the mobile UI reads, and which changes reach it.

## Why two UIs rather than one responsive one

The desktop UI has no responsive design at all: no `useBreakpoint`, no layout media queries,
no antd responsive grid props across roughly 1260 components and 62k lines. Retrofitting that
is a much larger and riskier change than building a small, purpose-built app for the handful
of things people actually do from a phone — check a build, promote it, deploy it.

The trade is that the mobile UI is deliberately **incomplete**. It covers a few flows well
rather than everything badly, and it says so when a user arrives somewhere it does not cover.

## What is shared, and what is not

| Shared | Not shared |
|---|---|
| Services, GraphQL fragments and mutations | Every layout component |
| Authorization helpers | `MainLayout`, `MainPage`, `MainPageBar`, `NavBar`, `UserMenu` |
| Theme tokens (`styles/globals.css`) and the pre-paint theme script | The mobile shell: `MobileLayout`, `MobileHeader`, `MobileBottomNav` |
| The next-auth session and the `/api/protected/graphql` proxy | The provider stack — see below |

Keep that boundary sharp. Sharing a layout component between the two UIs means a change made
for one silently deforms the other, in a viewport whoever made the change is not looking at.

`/mobile` is its own App Router root, with its own `<html>` and `<body>` — exactly like the
sign-in page under `app/(auth)`. It is therefore covered by neither `pages/_document.js` nor
the provider stack in `pages/_app.js`, and assembles its own in `app/mobile/MobileProviders.js`.
Theme resolution before the first paint uses the same `themeInitScript` both other roots use.

### The header's brand

The identity is the two brand marks, `yontrack-logo.svg` and `yontrack-text.svg`, and not the
word "Yontrack" set in the UI font: the wordmark is a drawn typeface in brand lilac, and
typing the name loses both the letterforms and the colour. Both marks carry their own colours
— brand green and brand lilac — which read on the header's purple in either theme, because
the header is that purple in both.

Each is drawn at **its own aspect ratio** (27×24 and 129×16). Next compares what it renders
against the `width`/`height` it was given and warns when the two disagree, which is what the
desktop `NavBar` does by putting the 8.08:1 wordmark in a 120×24 box. The wordmark is set
shorter than the mark is tall because at the mark's 24px it would be 194px wide — half of a
375px header.

`priority` is deliberately not set on either. Next reports the mark as the largest contentful
paint and suggests it, but adding it does not silence the warning and Next 13 implements it by
passing React a camelCase `fetchPriority`, which React 18.3 rejects on every render. Both marks
are inline SVGs of about a kilobyte, served straight from `/public` — `next/image` passes SVGs
through rather than sending them to the optimizer, which answers `400` for them.

## The redirect

`middleware.js` sends phones to `/mobile`:

- **User agent detection, phones only.** It is the only thing a middleware knows about the
  device; viewport width is a client-side fact and arrives far too late. Tablets keep the
  desktop UI — they have the width for it. The rules are written to fail *towards* the desktop
  UI, which is the complete one. See `components/mobile/userAgent.js`.
- **A redirect, not a rewrite.** The PWA is scoped to `/mobile`, and a scope only works if
  that path actually appears in the URL. It also makes mobile pages bookmarkable.
- **A `yontrack-ui=desktop` cookie opts out**, per device rather than per user. It is set by
  the interstitial's "open the desktop version" and cleared by the "Mobile version" entry in
  the desktop user menu. Both halves matter: without the second, a phone that once chose the
  desktop UI is stranded on it, and once the mobile UI is installed as a PWA there is no
  address bar to escape with.

  That user-menu entry **is** tappable at phone width. It was not when the cookie was first
  written: the desktop header row grew past its own 64px box and painted over the page bar
  below, so a tap on the avatar opened the home page's "New project" command instead. #1729
  fixed the header row and the page bar for that one case — the only responsive work the
  desktop UI carries, and deliberately the last. `tests/core/navBar.spec.js` pins the hit
  target at 393px and at 375px.

  It is still a **session** cookie, on purpose, and now as a second line rather than as the
  only one. The desktop UI is not responsive by design, so the escape hatch is the single
  thing between a phone user and being stranded; ending the opt-out with the browser session
  turns "stranded for good" into "stranded until the browser restarts" whatever else breaks
  around it. Making it long-lived is a decision of its own, not a consequence of #1729.

The decision itself is a pure function in `components/mobile/mobileRedirect.js`, so it can be
tested without a request or the edge runtime. `middleware.js` is the adapter over it.

## The route map

`components/mobile/mobileRoutes.js` holds three things:

- `mobileEquivalent(pathname)` — the desktop routes that have a mobile screen. Deliberately
  short: a route earns an entry only once the mobile screen behind it exists and does the job.
  Two kinds of entry: exact paths, and **entity patterns** for the parameterised routes
  (`/project/[id]`, `/branch/[id]`), which carry the id through so a shared link keeps its
  subject. The patterns are exact — `/project/abc` and `/project/12/anything` fall through to
  the interstitial rather than to a screen that would ask the server an unanswerable question.
- `isRedirectExempt(pathname)` — the paths the redirect must not touch at all: `/mobile`,
  `/auth` (redirecting the sign-in page would loop), `/api`, `/display` and static files.
- `describeDesktopRoute(pathname)` — what to call a desktop route in the interstitial.

A phone whose destination has no mobile equivalent lands on the **interstitial**
(`/mobile/desktop-only`), which names where they were going and offers both ways out. Neither
of the easy alternatives is acceptable: silently serving the desktop page costs the user the
readable UI the redirect exists to give them, and silently dropping them on the mobile home
loses what they came for.

**A route named after an entity can be swallowed by `.gitignore`.** `/mobile/build/[id]` lives
in a directory called `build`, and the repository's `.gitignore` carries an unanchored `build`
for Gradle output — so the screen's files were ignored, and silently: they exist on disk, the
app runs, the unit tests pass and `git status` shows nothing. Only a fresh checkout notices.
Each such directory has to be re-included by name in `.gitignore`, and the *directory* itself
has to be, because git never descends into an excluded one.

**Adding a desktop route means deciding what a phone following a link to it should see.** That
decision is recorded in this map — a desktop route added without one silently sends phones to
the interstitial, which may well be right, but should be a choice rather than an oversight.

## The screens

| Screen | Route | What it is |
|---|---|---|
| Home | `/mobile` | The user's favourite projects and branches |
| Projects | `/mobile/projects` | Every project, filterable by name, with the favourite toggle |
| Project | `/mobile/project/[id]` | The project's branches, limited and filterable |
| Branch | `/mobile/branch/[id]` | The branch's latest builds, as cards, searchable |
| Build | `/mobile/build/[id]` | The decision surface: promotions, deployments, validations |
| Interstitial | `/mobile/desktop-only` | A route with no mobile equivalent |

Home → project → branch → build is the path the mobile UI exists for, and all of it stays
inside `/mobile`: every row links to a mobile route, never to a desktop one. A desktop link
would bounce through the redirect and, once the app is installed as a PWA scoped to that
prefix, out of the app itself.

**Home is the favourites, not a project list.** Someone reaching for their phone is checking
something they already care about; the full list is one tap away in the bottom bar for the
times it is not. Both screens read the existing `projects(favourites: true)` and
`branches(favourite: true)` — the mobile UI adds no GraphQL of its own.

A user who has never curated favourites on the desktop UI would otherwise open a blank home
screen, which reads as a broken app rather than as an empty list. Two things stop that: the
empty state (`MobileFavouritesEmpty`), which says what favourites are and links to the
project list, and the favourite toggle on the project list, which is where those favourites
get made. The demo seed marks a couple of its own, so the demo is populated on a phone —
see `doc/dev-guide/demo-seed.md`.

### Favourites

`MobileFavourite` is **controlled**: the favourite state lives in the list that renders the
toggle, and the toggle owns only whether its own call is in flight. Its parent then refetches
rather than patching its copy of the list, so there is one answer to "is this a favourite"
and it is the server's. `useFavouriteRefresh` is that refetch: a counter in the screen's
`deps` and the `onToggled` its stars share.

It is deliberately not the desktop `Favourite`, which is a 14px icon inside a
`Typography.Text` with a click handler — neither a 44px touch target nor a control a screen
reader announces. Same four mutations, own affordance; the mapping from entity type to
mutation is a pure module, `components/mobile/favourites/favouriteMutations.js`.

The mobile provider stack has no `EventsContextProvider`, so a screen cannot refresh off the
`project.favourite` page event the desktop widgets use. A local counter in the screen's
`deps` does the same job for one screen, which is all a phone shows at a time.

That absence has one consequence worth knowing: `useEventForRefresh` reads a context whose
default value is an empty object, so a shared primitive that only ever wants the refresh —
`EntityIcon`, and so every promotion medal — would throw and take a whole mobile screen with
it. The hook now calls `subscribeToEvent?.()`. Outside a provider nothing is ever fired, so
a counter stuck at 0 is the right answer rather than a degraded one.

### The project screen

`/mobile/project/[id]` is the project's branches and nothing else. The desktop project page
carries branch boxes with their last promotions, decorations, an info drawer and a row of
commands; none of that is what someone opens on a phone for. They are on their way to a
branch, and from there to a build.

The branch list is **limited and filterable**, for the reason the project list is: a project
can hold hundreds of branches and a phone shows a handful of rows. It asks for
`MOBILE_BRANCH_LIMIT + 1` branches ordered by build activity (`branches(count:, order: true)`)
and shows the limit: `branches` answers with a plain list and no total, so the extra row is
the only way to know whether anything was left out — and it costs exactly one row. When it
was, the screen says so and points at the filter, which is how a branch beyond the limit is
reached.

Neither screen has a "no such project/branch" state of its own. `project(id:)` is a nullable
field and `branch(id:)` is not, but it makes no difference: the server never answers a bad id
with a null — it raises `ProjectNotFoundException`, or `AccessDeniedException` for one the
user cannot see — and both arrive as GraphQL *errors* carrying the reason. The error alert
already says what happened, and a null-checking branch beside it would be code that never
runs.

### The branch screen

`/mobile/branch/[id]` is the one screen that genuinely diverges rather than restyling.
`BranchBuilds` renders builds as a matrix with a column per validation stamp: wide by
construction, and no amount of narrowing turns a matrix into something readable at 375px. A
phone gets **a card per build** instead (`MobileBuildCard`) — what the build is called, when
it happened, how far it has been promoted, and where it is deployed.

- The name is `displayName`, which is already the release property when there is one. A build
  name is a timestamp-run pair, not a version.
- Promotions are `promotionRuns(lastPerLevel: true)`, drawn as the level's medal **beside its
  name**. The acceptance criterion is that promotions read without zooming, and a 16px medal
  on a phone is a coloured dot.
- Deployments are `Build.currentDeployments` — where the build is *now*, which is the question
  a phone user has; the pipeline history is a desktop surface. **Asked for in a query of its
  own** — see below.

  **A badge names the environment *and* the qualifier** — `production [demo]`, through the
  desktop UI's own `slotNameWithoutProject`. A project can have two slots in one environment,
  told apart by nothing but the qualifier, so an environment name on its own would draw the
  same badge twice and say nothing about the difference. `deploymentName` in
  `useMobileDeployments` is the one place that formats it, for the card and the build screen
  alike.

  This used to be a known gap rather than a feature: `currentDeployments` declared its
  `qualifier` argument with a default of `""`, and `findSlotsByProject` treats a qualifier as
  a *strict* filter rather than as "any", so the field could only ever answer with unqualified
  slots and a build deployed into a qualified one showed no badge at all. #1731 dropped the
  default — an omitted `qualifier` now means any qualifier, matching the sibling
  `Build.slots(qualifier:)` field. Callers that genuinely want one qualifier still pass it
  explicitly, and `qualifier: ""` still means the default one.
- Validation status is deliberately absent: per-stamp status is the build screen's job, and a
  strip of validation chips here would rebuild the matrix one card at a time.

"Load more" grows the page rather than accumulating pages in the browser. Merging pages by
hand means owning a second copy of the list and keeping it in step with the favourite
toggle's refetches; refetching a longer first page cannot drift.

#### Searching the builds

The branch screen carries **two controls and only two** (`MobileBuildFilter`): the build's
name, and one promotion level. Both map straight onto the existing `StandardBuildFilter`, so
there is no server work behind the search:

- the name becomes `withDisplayName` — matched against the release label when a build has
  one and against its own name otherwise, which is exactly what the card shows;
- the promotion becomes `withPromotionLevel`, matched by the repository as `PL.NAME = ?`.
  Exact, which is why the control is a list of the branch's own levels rather than a second
  text box — and why it is **absent** on a branch that has none, where an empty dropdown
  would read as broken rather than as inapplicable.

`StandardBuildFilter` offers a dozen more fields — dates, build links, properties, the
`since*` variants, validation stamps, named and shared filters — and `BuildFilterDialog` is
what showing all of them looks like. Reproducing that on a phone is the trap the two-control
rule exists to avoid. There is likewise no cross-project or cross-branch build search on
mobile: global search stays desktop-only for 5.4.

Three details are worth knowing:

- **`count` is deliberately not sent.** It is the filter's own cap, and on the paginated path
  `standardFilterPagination` sizes the page from the `size` argument and never reads it. The
  cap the phone needs is already there as `MOBILE_BUILD_PAGE_SIZE`; a `count` beside it would
  be a number with no effect that a later reader would trust.
- **The filter goes to the deployments query too.** `useMobileBranchDeployments` fetches a
  second page of the same list and the screen joins the two by build id. Asked for under
  different terms they are pages of two *different* lists, and every deployment badge on a
  filtered screen would silently disappear.
- **A new filter starts again at the first page.** Otherwise someone who pressed "Load more"
  five times and then typed would ask a phone network for fifty filtered builds in one
  request. The reset is done while rendering rather than in an effect: an effect would let
  one request go out for the new filter at the old size before resetting it, which is the
  request being avoided.

When nothing matches, the screen says which of the two is responsible — "No build matches
…", "No build has been promoted to …", or both — because the difference between "no build is
called that" and "no build got that far" is the difference between retyping and going
somewhere else.

**The demo seed needs nothing new for this**, and that is a decision rather than an
oversight — the definition of done in `CLAUDE.md` asks for one either way. `petclinic/main`
in `DemoContent` already carries seven builds released `1.4.0` to `1.4.6` across `BRONZE`,
`SILVER`, `GOLD` and `CANARY`: typing `1.4.3` narrows to one, picking `GOLD` narrows to
three, and the two together match nothing — all four acceptance criteria, on the dataset that
is already there. Adding builds only to exercise a filter would pad the demo rather than
demonstrate anything, and `doc/dev-guide/demo-seed.md` says how to run the seed against a
local stack if you want to see it.

### The build screen

`/mobile/build/[id]` is the **decision surface**: everything needed to answer "should I
promote or deploy this build?", and then act. Identity, promotions with their times and
authors, current deployments, validations, and the two action entry points.

**Validations are here on purpose**, though validations are otherwise out of scope for the
mobile UI. Someone about to promote or deploy from a phone needs to know whether the build
passed; leaving it out would mean switching to the desktop UI to check and switching back,
which defeats the flow the whole initiative exists to enable. They are a read-only list and
nothing more — no matrix, no filter, no drill-down into a run — drawn with the shared
`ValidationChip`, which spells the status out in words and repeats its glyph, so the state
survives greyscale.

The actions sit directly under the identity rather than at the foot of the screen. The issue
lists them last, but a validation list can be long and a user who already knows they want to
promote should not scroll past every stamp to reach the button.

#### Deployments are a separate query, deliberately

`Build.currentDeployments` is contributed by the environments extension, and
`GQLBuildSlotPipelinesFieldContributor` only registers it when
`environmentsLicense.environmentFeatureEnabled`. On an instance without that licence the
field is **absent from the schema**, not merely empty — so a query naming it fails
*validation*, and a GraphQL validation failure fails the whole document. A single query would
therefore take identity, promotions, validations and both action buttons down with it, and
the screen would read "Could not load the build" on the strength of a licence it never
mentions.

The desktop UI does not hit this because its environments panel is its own component with its
own query: only that panel breaks. `useMobileDeployments` gives the mobile screens the same
isolation — one hook for a build, one for a page of a branch's builds — and its `error` is an
expected state rather than a bug. The build screen says "Deployments are not available on this
instance", which is a different sentence from "This build is not deployed anywhere"; a build
card on the branch screen simply draws no badges, the difference not being worth a sentence
there.

Any future mobile query naming a field an extension contributes conditionally needs the same
treatment.

#### The action entry points

`MobileBuildActions` gates them off the build's own `authorizations`, exactly as the desktop
UI does: `build/promote` and `slotPipeline/create`. A user without the right sees no button
rather than one that fails — and the second is answered `false` on an instance with no
environments licence, so the deploy entry point disappears there without the component
knowing anything about licences.

**What they do today.** Promoting (#1724) and deploying (#1725) are their own issues; #1722
delivered the entry points and the gating. Until those land the buttons switch this device to
the desktop UI on the build's own page, through the same `switchToDesktopUI` cookie-then-
navigate pair the interstitial uses — and a caption under them says so. The alternative was a
button that does nothing, which is worse than one that is honest about where it goes. When
the two action issues land, each `onClick` becomes its dialog and the caption goes.

### Filtering a long list

An instance holds hundreds of projects, a project holds hundreds of branches and a branch
holds thousands of builds — all more than anyone scrolls through on a phone. Every one of
those lists filters by name, and every one filters **on the server**: the browser only holds
the answer to the last query, so a client-side filter could narrow that but never reach a row
the server had not already sent. The typing itself — the debounce, the two values, the trim —
lives once, in `useMobileFilter`.

What each screen sends differs, and the difference matters:

- `projects(pattern:)` is an `ILIKE '%…%'` ordered by name. The server refuses `pattern`
  alongside any *other* argument, and it tells "no pattern" from "empty pattern" by whether
  the argument was supplied at all — so the screen sends `null`, never `''`.
- `Project.branches(name:)` is **a regular expression**, matched with Postgres' `~`. Handing
  it the typed text raw would be wrong twice over: `release/1.0` would match `release/1x0`,
  and a lone `(` would not narrow the list but fail the whole query with an
  `INTERNAL_ERROR`. `branchNamePattern` escapes the text to a literal and prefixes `(?i)`,
  which Postgres' advanced regular expressions support — giving the same case-insensitive
  substring match the project list has, which is what a user moving between the two screens
  expects.

  It matches the branch's **name**, not its display name — the repository runs `B.NAME ~ ?`.
  A row therefore carries its name under its display name whenever the two differ, or a
  branch showing as `PRJ-1234` would look as though it had ignored a filter that in fact
  matched `feature/PRJ-1234-search`.
- `StandardBuildFilter.withDisplayName` is a regular expression too, and escaped the same
  way — a version is mostly dots, and `1.4.0` must not find `104x0`. It carries **no** `(?i)`,
  though, because the repository matches this one with `~*` rather than `~`: the flag would
  be a prefix that says nothing.

Both escapings live in `components/mobile/entities/namePatterns.js`, beside each other, so
the one asymmetry between them is visible rather than rediscovered.

### The list shape

`MobileEntityGroup` and `MobileEntityRow` are the mobile UI's one list: a name, a line of
context under it, and a single trailing action. `MobileSection` is the heading half on its
own; `MobileSectionList` is that plus "a list, or a line saying there is none" — the build
screen's three sections; `MobileEntityGroup` is the heading plus a list that its screen has
already decided is non-empty. All three share the heading, so they cannot drift apart.
`MobileEmpty` is the one-line empty state itself, in one voice, using antd's simple image
rather than the desktop-sized default illustration. Plain `ul`/`li` rather than antd's `List`,
whose paddings and split lines are sized for a desktop page — and which is a layout
component, on the wrong side of the boundary above.

A row links to the screen behind it through its **text**, not through the whole row: the
trailing action is itself a control, and nesting a button inside an anchor is invalid markup
that browsers and screen readers then resolve differently. The text block grows to fill the
row, so everything left of the star is tappable anyway.

A row with no screen behind it takes no `href` — a tap that 404s is worse than a row that
does not move.

## Adding a mobile screen

1. Add the page under `app/mobile/`. Keep the page itself to the route and put the screen in a
   client component beside it, as `app/mobile/page.js` and `app/mobile/HomeScreen.js` do.
2. Add the desktop route it stands in for to `components/mobile/mobileRoutes.js`, so phones
   stop getting the interstitial for it: `EQUIVALENTS` for a fixed path, `ENTITY_EQUIVALENTS`
   for one carrying an id.
3. If it belongs in the bottom bar, add it to `MOBILE_NAV_ITEMS` in
   `components/mobile/layout/mobileNav.js` — and think hard first: three destinations are what
   fits a thumb, and a tab is earned by a screen that works, not promised by one that does not.
   The bar shipped with a third tab, Search, over a placeholder screen; global search stays
   desktop-only for 5.4 (see #1723), so the tab was removed rather than left pointing at a dead
   end. A phone following a link to `/search` still gets the interstitial, which names the
   destination and offers the desktop page.
4. Cover it in `ontrack-web-tests/tests/core/mobile.spec.js`, whose tests run in a phone
   browser context.
