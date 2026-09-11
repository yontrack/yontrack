# The mobile UI

Yontrack serves two user interfaces from one instance: the desktop UI, at the root, and a
phone-sized one under `/mobile`. This page describes the shell they share the instance
through — the redirect, the boundary between them, and the route map that decides what a
phone sees. The mobile screens themselves are documented as they land.

> Working on the **desktop** UI and answering the Definition of done's mobile question?
> [Judging mobile impact](mobile-impact.md) is the shorter page for that: what the two UIs
> share, what the mobile UI reads, and which changes reach it.

> The **user-facing** page is `ontrack-docs/docs/content/mobile/index.md`, published in the
> mkdocs site as *Mobile UI*: what the mobile UI does and deliberately does not do, the
> redirect, and the two ways across the boundary. A screen that changes what a user can do
> from a phone changes that page too — this one is the reference for *building* the mobile
> UI, and the two are deliberately not copies of each other.

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
| The promotion level field mapping (`promotionLevelFields`) | The promote dialog and the promote sheet around it |
| The admission rule components themselves (`.../environments-slot-admission-rule/*`) | The rule-id lookup: `Dynamic` resolves into the wrong webpack layer under `/mobile` |
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
| Deployment | `/mobile/deployment/[id]` | One deployment waiting to run: its admission rules, and what can be done about them |
| Account | `/mobile/account` | Who is signed in, the theme, the version, and sign out |
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

**Both happen here.** Promoting is the sheet below (#1724) and deploying is the one after it
(#1725). Neither sends the device to the desktop UI any more, and the caption that used to say
so is gone with the last thing it was true of.

#### Promoting, from the phone

`MobilePromoteSheet` is a bottom sheet over the build screen, on the existing
`createPromotionRunById` mutation — the mobile UI adds no GraphQL of its own here either. Four
decisions are worth knowing:

- **The date and time is collapsed.** Someone promoting from their phone is promoting *now*,
  and a date-time picker in the way of the common case is the whole difference between this
  and the desktop dialog, which opens on one. While it stays collapsed no `dateTime` is sent
  at all: the server stamps the run when it receives it. A timestamp captured when the sheet
  *opened* would be wrong by however long its owner was interrupted, and a sheet on a phone is
  interrupted often. "Promoted earlier?" opens the picker, initialised to now, for the
  correction.

- **The promotion level's own fields are rendered through a mapping shared with the desktop
  dialog** — `components/promotionLevels/promotionLevelFields.js`, and that module is the
  *only* thing the two promote UIs share. A level can declare typed fields (`TEXT`, `NUMBER`,
  `BOOLEAN`, `CHOICE`, `LINK`) and some of them are required; the server refuses a run missing
  one. A field type the phone cannot render is therefore not a cosmetic gap but a promotion
  level nobody can use from a phone — and two copies of that `switch` would drift the first
  time a type is added, silently, because the missing field would simply not be there. The
  layout stays separate, as everywhere else in this document; the type mapping does not have
  to be. The shared module also falls back to a text box for a type it has never heard of
  rather than rendering nothing, and it carries the GraphQL fragment both UIs read the fields
  with, so the query and the mapping cannot drift either.

- **The sheet is capped at 85vh, in two places.** A level declaring five fields makes a form
  taller than an 812px screen. The drawer is fixed, so a sheet that grows past the window puts
  its own Promote button where nothing can scroll it back from — the page behind does not
  move. Capping the *wrapper* alone is not enough: its overflow is `visible`, so the content
  hangs out of the bottom of it exactly as if there were no cap. Capping the content too makes
  the drawer's body the scroller and keeps the title in place. `mobile.spec.js` pins the
  Promote button inside the viewport with a level declaring one field of every type, which is
  the case that overflows.

- **The screen refetches rather than patching its promotions.** The run's id, its signature
  and its place among the level's last runs are the server's answer, and
  `promotionRuns(lastPerLevel: true)` means a second promotion to a level already shown
  *replaces* a row rather than adding one. A counter in the query's `deps`, as the favourite
  toggles use.

**The demo seed needs nothing new for this, and that is a decision.** `petclinic/main` in
`DemoContent` already carries `BRONZE`, `SILVER`, `GOLD` and `CANARY` on branches full of
builds, so the demo demonstrates the feature as it stands: open a build on a phone, tap
Promote, pick a level. What it does not demonstrate is the promotion level *fields*, and
seeding those was considered and rejected twice over. They are a pre-existing server and
desktop feature the seed has never shown, so seeding them would be demonstrating that feature
rather than this one; and a *required* field on a demo level would break the seed itself,
since the demo's auto-promotion and workflow-driven promotions create runs with no field
values, which is exactly what `validatePromotionRunFieldValues` refuses. See
[demo-seed.md](../demo-seed.md).

#### Deploying, from the phone

Two surfaces, because the deployment lifecycle has two moments a person is in it:
`MobileDeploySheet` over the build screen starts one, and `/mobile/deployment/[id]` is where
one that is waiting gets acted on. Both are #1725.

**Where the scope stops.** The lifecycle is `CANDIDATE → RUNNING → DONE`, plus `CANCELLED`.
The phone covers starting a deployment and acting on one that is waiting, and nothing else:

- **Marking a deployment done** is driven by CI, not by a person on a phone.
- **Cancelling one** is a destructive action behind a thumb on a small screen, and stays on
  the desktop UI for 5.4.
- Workflow overrides, slot configuration, admission rule configuration, browsing a slot's
  eligible builds and the pipeline graph are all desktop surfaces.

The desktop `SlotPipelineStatusActions` carries all four buttons side by side, which is the
difference between a control surface and a decision surface.

##### The deploy sheet: a list, not a dropdown

`BuildStartDeploymentDialog` is a modal around a `Select` of slots, with the chosen slot's
details and its current pipeline underneath. The mobile sheet is a **card per slot**, each
carrying its own action or its own explanation.

That is not restyling. The desktop `Select` marks an ineligible slot `disabled`, which on a
phone is a tap that does nothing and says nothing — and the acceptance criterion is the
opposite: ineligible slots are shown **with the reason**, so a user is never left wondering
where an environment went.

**The reason had to come from the server.** `EligibleSlot` carried `eligible` and the slot,
and nothing else — so a UI could grey a row out but could not say why. It now also carries
`nonEligibleRules`: the slot's admission rules which actually refuse *this* build, not its
whole rule set. A slot with three rules of which one refuses would otherwise be explained by
listing all three, two of which are satisfied.

`eligible` and `nonEligibleRules` are computed in **one** walk over the rules
(`SlotServiceImpl.eligibleSlot`), because a slot is eligible exactly when nothing refuses:
computing the two separately would walk the rules twice and let the answers disagree.

The cards are ordered by the environment's own order and then by qualifier, so the list reads
the way a pipeline runs and two slots of one project in one environment come out stably. A
badge names the environment **and** the qualifier, through the desktop UI's own
`slotNameWithoutProject` — the rule that applies everywhere a slot is named (#1731).

##### The deployment screen: the waiting room

A deployment starts as a `CANDIDATE`: nothing has happened to the environment yet, and
everything that still has to happen is on its own screen. Starting one therefore **navigates**
there rather than dropping the user back on the build with a new row to notice.

It is reached two ways, and the second is the one that matters: the build screen carries a
**Waiting to deploy** section listing this build's `CANDIDATE` deployments, which is the only
thing on a phone that names a deployment *somebody else* started — CI, usually, sitting on a
manual approval nobody has given. That section is absent rather than empty when nothing is
waiting: unlike the three sections under it, it is not a facet of the build, and "no
deployment is waiting" on every build screen would be a line nobody reads.

The screen itself is the status, the run button, and one row per admission rule: its verdict
as a glyph **and** in words — `Passed` / `Blocking`, so the state survives greyscale, as the
validation chips do — plus the two things that can be done to a blocking one.

**The run button reads `runAction.ok` and not a count of its own.** A rule can be satisfied by
an override as well as by passing, and the server computes the answer over every check at once,
workflow checks included. When it says no, the caption says how far it got (`1 of 3 checks
passed`) and points at the list below.

**Every action refetches rather than patching.** Whether a rule now passes, and whether the
deployment can now run, are answers only the server has. A counter in the query's `deps`, as
the build screen's promotion uses — the mobile provider stack has no `EventsContextProvider`.

##### `Dynamic` does not work under `/mobile`, and that is load-bearing

The desktop UI chooses a rule's components **at runtime**: `SlotAdmissionRuleSummary` and
`SlotAdmissionRuleDataForm` both go through `components/common/Dynamic.js`, which is
``lazy(() => import(`../${path}`))``. That template literal makes webpack build a *context
module* over `components/`, and the context it builds belongs to the **Pages Router** layer —
which is where the whole desktop UI lives. `/mobile` is an App Router root, compiled in a
separate layer with its own React copy, so a component pulled in through that context renders
against a React whose dispatcher is null:

```
TypeError: Cannot read properties of null (reading 'useMemo')
    at Text (antd/lib/typography/Text.js)
```

`Dynamic`'s own `ErrorBoundary` catches it and draws "Error" where the content should be, so
the failure is quiet: nothing throws out of the screen, and the reason a deployment is refused
is simply replaced by a red cross. Nothing about the rule, the data or the screen is wrong —
the module arrives from the wrong compilation.

**This is true of every use of `Dynamic`, not only these.** Properties, widgets and
auto-versioning post-processing all resolve the same way, so any future mobile screen needing a
component chosen at runtime needs a static map instead.

##### What is shared: the rule components, and a table that cannot drift

`components/mobile/deployments/admissionRuleComponents.js` is that static map. It imports the
**desktop's own** rule components — the same files `Dynamic` would have resolved — so how a rule
is phrased and which fields it asks for stay in one place; `CheckIcon` is shared outright. What
is duplicated is only the *lookup table*, and only because a static import is the one thing that
resolves in the right layer.

A duplicated table drifts, which is exactly why the promote sheet shares its field mapping
rather than copying it. So it is pinned: `admissionRuleComponents.test.js` reads
`components/framework/environments-slot-admission-rule/` **off disk** and fails when a rule
there has a `Summary.js` or a `DataForm.js` the mobile table does not name. A rule type added
for the desktop becomes a failing test rather than a phone that silently cannot explain itself.
Both lookups also fall back rather than rendering nothing — the rule's configured name for a
summary, and "this rule cannot be answered from a phone" for a form.

The input sheet leaves the **value shape** to those components too. Each rule's form names its
fields under the rule config's id, so the form's own values are already `{[configId]: {…}}` and
go to `updatePipelineData` untouched. Reshaping them here would mean knowing what each rule's
data looks like, which is what using the rule's own form avoids.

##### Authorization

Off the slot's own `authorizations`, as everywhere else: `pipeline/create` gates the run and
the input, `pipeline/override` and the rule's own `canBeOverridden` gate the override. A user
without the right sees no button rather than one that fails, and reading is never gated —
someone who may not deploy can still need to know why a deployment is stuck.

`pipeline/create` and not a right of its own is a deliberate approximation: running a
deployment needs `SlotPipelineStart` and providing input needs `SlotPipelineData`, neither of
which the slot publishes as an authorization, and every role that grants `SlotPipelineCreate`
grants both. The server checks each of them for real; the gate only decides what is worth
offering.

##### The override keeps its warning

An override bypasses a control somebody configured on purpose and is recorded against the user
who made it, so the sheet says so as plainly as the desktop dialog does and the reason stays
**required**. A smaller screen is a reason to be shorter, not a reason to be quieter about the
one irreversible thing on it.

##### Route map

`/extension/environments/pipeline/[id]` maps to `/mobile/deployment/[id]` — the only entity
pattern whose id is a UUID rather than a number, and exact for the same reason the others are.
The mobile screen covers what a phone user does with a deployment and not the desktop pipeline
page's history, workflows or graph, which is the bar the map sets: the screen exists and does
the job.

##### Testing it, and the licence

Environments are a **licensed** feature, and the mobile deploy journeys run in CI anyway: the
Playwright stack (`compose/docker-compose-kdsl.yml`) runs the backend under the `dev` profile,
and `DevLicenseService` enables every licensed feature. That is why the whole of
`tests/extensions/environments` already runs there. `mobile.spec.js` drives both journeys end
to end — an eligible slot and an ineligible one with its reason, the approval, the run, and the
override with its reason — against real slots and real rules, which is also where the shared
mapping is exercised for real. The component tests stub it, and cover the half a UI test
running as the suite's admin account cannot: what an unauthorized user does *not* see.

**The demo seed needs nothing new for this, and that is a decision.** The dataset already
demonstrates the feature as it stands: `service` has a staging slot requiring `SILVER` and a
production slot requiring `GOLD` plus a staging deployment plus the `main` branch, and `ui`
has a production slot whose rules name things that do not exist. Open one of those builds on a
phone, tap Deploy, and the list shows an eligible slot and ineligible ones each naming the rule
that refuses — four of the six acceptance criteria on data that is already there.

What it does not demonstrate is **input and override**, and both need a `manual` admission
rule. Seeding one was considered and rejected on the same two grounds as the promotion level
fields for #1724. Manual approval is a pre-existing slot-configuration feature the seed has
never shown, so seeding it would be demonstrating *that* feature rather than this one; and it
cannot be bolted onto an existing slot, because `DemoSlot.deploy` runs each pipeline all the
way to `DONE` and a rule waiting on a person fails the reset. Showing it would mean a **new**
slot with no deployment — either a qualifier the seed's dataset model does not carry, or a
third environment — and the demo's two environments are a composed picture that the delivery
map reads and `DemoSeedTest` pins. Adding a third to show one rule would rewrite that picture
as a side effect. See [demo-seed.md](../demo-seed.md).

### The account screen

`/mobile/account` is who is signed in, the **appearance**, the version, and **sign out** — and
nothing else. It is reached by tapping the signed-in name in the header, and from nowhere else.

Until it landed there was no way to sign out of the mobile UI at all: `signOut` appeared
nowhere under `components/mobile/` or `app/mobile/`, the only deliberate call being the
desktop `UserMenu`, which the mobile shell shares no layout component with. On a phone the
session ended when it expired and not before — and that matters more here than on the desktop,
because a phone session is long-lived and, once the mobile UI is installed as a PWA, there is
no address bar to reach `/api/auth/signout` with by hand.

**A screen, not a drawer and not a bare header button.** A drawer is the desktop pattern, and
importing `UserMenu` would cross the boundary above. A screen is what every other mobile
surface already is — `MobileScreen` needs no new shape for it — it has a URL a UI test can
address, and a mis-tap costs a navigation rather than a session. It is **not** a fourth
bottom-nav tab either: two thumb-level destinations are what the bar carries, and a tab is
earned by a screen someone returns to rather than by a settings page. `activeMobileNavKey`
answers `null` for it, exactly as for the interstitial, because it belongs to neither
destination.

**Reaching it.** `.ot-mobile-user` was 13px at 0.85 opacity with no affordance: it did not read
as tappable and was well under a 44px target. It is now the control — a `Link` padded to the
full header height, so the tap target is the header row rather than the glyph, with a chevron
marking it as a door. The name stays in the header; on a shared or long-lived phone session it
is still the one thing worth the space.

**What it holds.** The identity — full name and username as the screen's head, email as
context, all three already on `UserContext` — the **Appearance** section below, and the version
(`useRefData().version`) as secondary text at the foot. The version earns its row because of the PWA: no address bar, no
user menu, and "what version are you on?" is the first question on any support thread. It is
also where the desktop user menu puts it. The desktop's own user-profile page
(`/core/admin/userProfile`) is API tokens and groups; neither belongs on a phone and neither
comes here.

**Sign out fires immediately**, as the desktop's does. Two taps and a full screen of context is
already the confirmation; a modal on a screen the user deliberately navigated to is friction
that makes a phone app feel like a form.

**It lands them on `/mobile`** — `signOut({callbackUrl: MOBILE_HOME})`, not the default.
`signOut()` with no argument defaults `callbackUrl` to the *current* URL, so signing out of
`/mobile/build/12` would leave that build as the callback and signing back in would return to
it: on a shared phone, the wrong souvenir. `/mobile` is redirect-exempt so the middleware
leaves it alone, `AuthProvider` sends the unauthenticated visitor to the sign-in page on its
own, and signing back in lands on the mobile home. `mobile.spec.js` asserts that last hop
rather than restating the default, which is the only way the decision stays made.

**No route-map entry.** `/mobile/account` stands in for no desktop route, and
`/core/admin/userProfile` is deliberately *not* mapped to it: the two share a name and nothing
else, and redirecting a phone there would answer a link about API tokens with a sign-out
button. It keeps falling through to the interstitial as "an administration page".

#### Sign-out is local, deliberately

`signOut` drops Yontrack's own session and **leaves the identity provider's alone** — there is
no `events.signOut` in `authOptions` and no `end_session_endpoint` call, so the Keycloak SSO
cookie survives and the next sign-in is silent. That is exactly as true of the desktop UI
today.

Making it a real sign-out is **#1734**, and it stays there: it is a shared auth change touching
both UIs, both provider configurations and the 401 handler, with a back-channel/front-channel
fork that deserves its own decision rather than riding in on a phone screen.

**Nothing is said to the user about it here.** The desktop makes no such statement, and a
caveat the user can do nothing about reads as a malfunction. It is recorded here and in #1734,
where it is actionable. The one place it shows up in code is `mobile.spec.js`: signing back in
after a sign-out may never be asked for credentials, so the helper that does it copes with
both.

The `yontrack-ui=desktop` cookie is left entirely alone by sign out. It is a *device* choice
and signing out is a *user* action; clearing it would move the next person to pick up the phone
between UIs as a side effect of someone else's logout.

#### Appearance: the theme is a user preference, not a device one

The **Appearance** section sits between the identity head and sign out (#1732). Sign out stays
the last thing on the screen and the only destructive one: a preference placed after it would
sit on the path a thumb travels past. The section title costs one line and is what makes the
screen read as *account and preferences* rather than as a sign-out button with something bolted
above it.

Until it landed, the mobile UI had the whole theme machinery and no affordance at all —
`ThemeProvider`, `themeMode.js`, the cookie mirror and `ThemePreferenceSync` are all in
`MobileProviders`, and `themeInitScript` resolves the theme before the first paint — so a phone
user got whatever the device decided and could not change it. The desktop offers `ThemeSwitch`
in the user menu; the mobile shell deliberately carries no user menu.

**One `themeMode`, shared.** The mirror cookie is set at `path: '/'` and the server preference is
a single field on the account, so choosing Dark on the phone darkens that account's desktop UI,
in every browser — and a user who set Dark on their desktop already found the phone dark. That
is the intent. The alternative reading — a phone in bed at night is not a desk at noon — would
mean a second cookie, a second GraphQL field and a migration, to serve a preference nobody has
asked for. It is also what settles *where* the control belongs: this screen draws a line twice
already, declining the desktop-version opt-out and leaving `yontrack-ui=desktop` alone on sign
out, because those are **device** choices. The theme is on the user's side of that line.
`mobile.spec.js` asserts it directly — a choice made on the phone, read back from a fresh
desktop context — because otherwise "one preference" is a sentence nothing enforces.

**A new component, `MobileThemeSwitch`, not `ThemeSwitch`.** The desktop one hard-codes
`id="theme-switch"`, which `theme.spec.js` locates by, and carries a `stopPropagation` whose
only purpose is keeping the desktop drawer open. Reusing it would cross the boundary above.
What *is* shared is the **write**: `useThemeModeSetter` in `components/theme/` performs the dual
write both UIs need — apply locally, then save to the profile, then warn if only the first
landed — and is called by `ThemeSwitch` and by the mobile row alike. Those two halves are
precisely the thing that must never drift now that they are one value, and extracting the hook
carries the desktop's failure-path coverage to the mobile control instead of leaving it
desktop-only.

**Three modes, not a toggle.** Light / Dark / Auto, as an antd `Segmented`, the same vocabulary
as the desktop — round-tripping one shared value through two vocabularies cannot work, and a
user who picked Auto on the desktop would find the phone control showing something untrue.
`system` is also `DEFAULT_THEME_MODE`, the state every user who has never picked is in, so a
binary switch could express neither it nor the way back to it.

**A caption, because a phone has no hover.** `ThemeSwitch` puts the only explanation any mode
ever gets inside a `Tooltip` — "Follow the theme of your operating system" is the one place Auto
is defined — and on a touch screen that text is unreachable. The tooltips are dropped here and
replaced by one line of secondary text: `Always light`, `Always dark`, or
`Auto — currently dark` / `Auto — currently light`. It is present in **every** mode, not only in
Auto: a caption that appeared and disappeared would change the row's height as the user taps
across it, which on this screen means sign out moves under their thumb as a side effect of
choosing a theme. The Auto half tracks the OS live — `ThemeProvider` subscribes to
`matchMedia('(prefers-color-scheme: dark)')`, so `resolvedTheme` and the caption with it change
without a reload.

**The failure path is unchanged**: the choice applies locally first and stands even if the
mutation fails, because reverting a theme under the user mid-tap would be worse — but it does
not fail silently, since the cookie now disagrees with the server and on the next sign-in the
server wins. The wording changed for **both** UIs: "Theme applied, but not saved to your profile
— it may not follow you to other devices." The previous text said "another browser", which was
accurate when the switch was a desktop control and is not accurate now that what the user loses
is the choice following them to their phone.

**Test isolation.** The theme is server-side state on the shared account and Playwright runs
`workers: 1, fullyParallel: false`, so a spec leaving `DARK` behind drives every spec after it
in the dark theme. `mobile.spec.js` used to only *read* the mode and relied on `theme.spec.js`
resetting it; this row is what makes it a writer, so the reset moved into `tests/core/theme.js`
and **both** specs call it in `beforeEach` and `afterEach`. One shared value means neither file
can rely on the other cleaning up.

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
