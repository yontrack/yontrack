# Environments UI redesign

Outcome of the grilling session of 2026-09-17 on the screens of the Environments feature
(`ontrack-extension-environments`, frontend under `ontrack-web-core/components/extension/environments/`).
The session settled 35 questions. This document records the decisions and their reasons, not the
questions. No issue has been created yet; the phasing at the end is the intended breakdown.

Two static mockups sit beside this file:

- [`matrix-drawer.html`](matrix-drawer.html) — the new home, with the slot drawer open, drawn with
  the demo seed data plus the `canary` slot proposed in [Demo seed](#demo-seed).
- [`deployment.html`](deployment.html) — the new deployment page, on a *hypothetical* candidate to
  that `canary` slot, with a manual approval and an overridden workflow so that "What's blocking"
  has something to show. The seed has no blocked candidate.

## Where we start from

The feature has four desktop pages, one mobile screen and a scattering of panels elsewhere. Every
one of them is a representation of the data model — environment, then slot, then slot pipeline —
rather than of what somebody came to do.

| Screen | Route | Today |
|---|---|---|
| Environments list | `/extension/environments/environments` | One card per environment, a small card per slot inside it, a tags/project filter. "New environment" and "New slot" commands. |
| Slot | `/extension/environments/slot/[id]` | Details (8 cols) and eligible builds (5 per page) beside a deployments table (16 cols), then admission rules and workflows as two full-width tables. |
| Deployment | `/extension/environments/pipeline/[id]` | A `Descriptions` summary and one `List` whose shape changes with the status: rules, workflows, run/finish/cancel buttons. "Force" and "Delete" commands. |
| Project environments | `/extension/environments/projects/[id]` | A three-panel splitter: builds (20%), a ReactFlow slot graph (60%), actions (20%). Pick a build *and* a slot before anything can happen. |
| Elsewhere | build page, decorations, delivery map, build search, widgets | A build page "Environments" cell with an expandable list; environment icons as decorations; two dashboard widgets (`EnvironmentList`, `Environment`); a "Deployments" column in build search. |
| Mobile | `/mobile/deployment/[id]`, build screen | A deployment screen ("N of M checks passed", Checks, Workflows) and a deploy sheet listing refusal reasons. |

What that costs:

- **No screen answers "what is running where".** The environments list is the closest, but it is
  organised by environment, so comparing a project across environments means reading every card.
- **Deploying has four entry points and four UIs**: the build page menu, the slot page eligible
  builds, the project environments actions panel and the build page cell.
- **Why a deployment is blocked is not stated.** The deployment page lists every rule and workflow
  of every phase with equal weight; the failing one has to be found.
- **Starting a deployment silently cancels the active one on the slot** (`SlotServiceImpl.startPipeline`,
  "Cancelled by more recent pipeline."). No screen warns about it.
- Configuration (admission rules, workflows, slot creation) sits in the middle of the operational
  screens, while in practice it is done as code.
- The "still under experiment" banner is on every page.

## Principles

- **Users.** The release manager / ops person first ("what's in production, what's waiting, can I
  deploy X?"), the developer second ("where is my build?"). The auditor is served on the way by
  the history and the audit timeline. The platform admin configures as code and gets plain forms.
- **Jobs, in this order**: (1) see the state of every environment at a glance, (2) move a build
  forward, (3) understand why a deployment is blocked, (4) trace a build through the environments,
  (5) configure. Screens are ordered by these jobs, not by the data model.
- **Mental model.** A **project × environment matrix** is the home. The slot, the deployment and
  the build are drill-downs from it.
- **The Yontrack chrome stays**: top bar, user menu, breadcrumbs, page title and the header command
  pattern. Everything below the header is redesigned, with Ant Design v5 components only, no new UI
  library.
- **Routes and the API may change.** Every new GraphQL or backend need is listed in
  [Backend and GraphQL needs](#backend-and-graphql-needs).
- **Vocabulary on screen.** A slot pipeline is a **Deployment**. A slot is shown as
  **"production · petclinic [canary]"**; the word *slot* only appears in Setup. `CONTEXT.md` keeps
  its terms and gains a line mapping the UI labels to them.
- **Configuration is set aside, not removed.** Creating and editing environments, slots, admission
  rules and workflows moves out of the operational flow into a Setup area and a Setup tab.
- **Unauthorised actions are hidden**, everywhere — header commands, Setup edits, and the operational
  actions (Deploy, Start, Finish, Cancel, Override, Answer) in the drawer and on the deployment page.
  This follows the rest of Yontrack. The consequence is accepted: a user without the right sees the
  blocking item in "What's blocking" but no button for it.
- **Freshness.** Operational screens (matrix, drawer, deployment page, slot page header) poll every
  30 s and show "Updated 12 s ago" with a manual refresh. Push (subscriptions/SSE) is a follow-up.
- **Scale.** Designed for about 100 projects: favourites by default, grouping by project label,
  server-side paging so that 500+ projects does not need a redesign.
- **The experimental banner is removed** from the operational screens and kept once, on the Setup page.

## Shared components

Five components carry the redesign. They are built first and reused by every screen.

### Slot cell

One slot, as a cell of the matrix, a node of the project graph, or a row of a widget.

```
┌──────────────────────────┐
│ 104 · 1.4.3  ● GOLD  18d │   deployed build, top promotion, age
│ → 107 running ▸          │   in-flight deployment (CANDIDATE / RUNNING)
│ ⚠ behind                 │   the previous slot holds a newer build
└──────────────────────────┘
```

- Always: the deployed build (display name/release), its top promotion, how long ago.
- Overlay when a deployment is in flight: "→ 107 running" or "→ 107 candidate".
- **Blocked**: a red dot when the in-flight deployment is a candidate with a failing rule or workflow.
- **Behind**: a subtle badge when the slot upstream of it in `slotGraph` holds a newer build.
- **Never deployed**: a muted "Never deployed".
- No slot for that project in that environment: an empty cell, not a dash.
- Click opens the slot drawer.

### Slot drawer

The shared "slot quick view", an antd `Drawer` on the right, URL-addressable with `?slot=<id>` so it
survives a reload and can be shared. Opened from the matrix, the project graph, the build journey
strip, the widgets and the delivery map checkpoints.

```
┌─ 🟢 staging · petclinic ────────────── Open slot ▸ ─┐
│ NOW                                                 │
│   89 · 1.3.9 (release-1.3)  ● SILVER                │
│   deployed 3 d ago by admin                         │
│ IN FLIGHT                                           │
│   #3  107 · 1.4.6   Candidate ✓ ─ Running ● ─ Done  │
│   What's blocking — nothing                         │
│                                   [ Finish ]  Cancel│
│ NEXT                                                │
│   (no eligible build newer than 107)                │
│ RECENT                                              │
│   #3 107 running · #2 89 deployed · #1 104 deployed │
└─────────────────────────────────────────────────────┘
```

Sections, in order:

1. **Header** — environment icon, "staging · petclinic [qualifier]", link to the slot page.
2. **Now** — deployed build, promotions, when, by whom.
3. **In flight** — a mini `Steps` bar, the "What's blocking" list with its inline fixes, the primary
   action (Start or Finish) and Cancel. Absent when nothing is in flight.
4. **Next** — up to three eligible builds *newer* than the current one, each with **Deploy**, which
   opens the deploy dialog.
5. **Recent** — the last five deployments, `#n` links.

The top of the slot page is this same component.

### Deploy dialog

The one way to start a deployment, replacing `BuildStartDeploymentDialog`, `SlotPipelineCreateButton`,
the project environments actions panel and the build page cell's start button.

- It opens **from a build** (choose the slot) or **from a slot** (choose the build).
- Non-eligible choices are **listed, not hidden**, each with the admission rule that refuses it
  (`EligibleSlot.nonEligibleRules`), using the wording of the mobile deploy sheet.
- When the slot already has an active deployment, the implicit cancellation becomes an explicit
  warning: *"Deployment #12 (build 105, RUNNING) will be cancelled."*
- On success it opens the new deployment page (or, from the drawer, refreshes the drawer).

### What's blocking

The list that answers job 3, used by the drawer and the deployment page.

- It covers the **current phase only**: admission rules and CANDIDATE workflows for a candidate,
  RUNNING workflows for a running deployment.
- **Failing items first**, each with its inline fix: **Answer** (rule needing input), **Override**
  (rule or workflow), **Open workflow**. Overridden items show who, when and the message.
- Passed items are collapsed behind "N checks passed".
- The labels align with the mobile deployment screen ("N of M checks passed", "Passed", "Blocking").

### Journey chip

A build's state in one slot, used in the build journey strip, build decorations, the build search
"Deployments" column, `ProjectPromotionWidget` and the delivery map `SlotCheckpoint`.

States: **Deployed (current)**, **Superseded** (was here, a newer build is now), **In progress**,
**Eligible**, **Not eligible** (reason in a tooltip). Clicking opens the slot drawer.

## Screens

### Environments — the matrix (replaces the environments list)

Same route, `/extension/environments/environments`, so bookmarks, the user menu entry and the
dashboard command keep working. See [`matrix-drawer.html`](matrix-drawer.html).

```
Environments                                        [Setup]  [Close]
┌──────────────────────────────────────────────────────────────────────┐
│ 🔍 project   (●Favourites ○All)  Group by label ▾  Tags ▾  □ Activity │
│                                                      Updated 12 s ago ⟳│
├───────────────────────┬─────────────────┬────────────────────────────┤
│                       │ non-production  │ production                 │
│ Project               │ 🟢 staging      │ 🔴 production              │
├───────────────────────┼─────────────────┼────────────────────────────┤
│ ▾ petclinic           │ 89 · 1.3.9  3d  │ 104 · 1.4.3 ● GOLD  18d    │
│                       │ → 107 running   │                            │
│     [canary]          │                 │ Never deployed             │
│   petclinic-ui        │                 │ Never deployed ●           │
└───────────────────────┴─────────────────┴────────────────────────────┘
```

- **Rows**: one per project. A project with several qualifiers nests one row per qualifier under
  it, collapsible, expanded by default up to three qualifiers and collapsed beyond. The empty
  qualifier is the project row itself.
- **Columns**: environments by `order`, header with the environment icon, grouped under their first
  tag when tags exist. Columns with no slot among the visible rows are hidden.
- **Cells**: the slot cell. Click opens the drawer.
- **Toolbar**: project search; **Favourites / All** (Favourites by default, All when the user has no
  favourite); group by project label; environment tag filter; **Only with activity** (in flight or
  blocked). Filter state is in the URL, and the last choice is remembered through
  `@components/storage/local`.
- **Header commands**: **Setup** (hidden without any configuration right), Close. "New environment"
  and "New slot" move to Setup.
- **Empty states**:
  - No environment at all — an explanation of the feature, a link to the user documentation
    (configuration as code) and to Setup.
  - Environments exist but none of the favourites has a slot — a hint and a **Show all** button.

### Deployment page

Same route, `/extension/environments/pipeline/[id]`. See [`deployment.html`](deployment.html).

```
Deployment #3 — staging · petclinic               [Force] [Delete] [Close]
┌──────────────────────────────────────────────────────────────────────┐
│ 107 · 1.4.6 (main)  ● SILVER          started 2 h ago by admin        │
│  ✓ Candidate ─────── ● Running ─────── ○ Deployed           [Finish] │
├──────────────────────────────────────────────┬───────────────────────┤
│ What's blocking                              │ Timeline              │
│  ✓ Nothing is blocking the completion        │ 2h  Running — admin   │
│  ▸ 0 running workflows                       │ 2h  Candidate — admin │
│                                              │                       │
│ ▸ Candidate phase (2 checks passed)          │                       │
└──────────────────────────────────────────────┴───────────────────────┘
```

- **Top**: build, promotions, start/end, and a horizontal `Steps` bar (Candidate → Running →
  Deployed, or → Cancelled) with the current step highlighted. **One primary action** — Start or
  Finish — on the right of the bar; Cancel as a secondary action beside it.
- **Main**: "What's blocking" for the current phase. Earlier phases below, collapsed and read-only.
- **Side**: an **audit timeline** from `SlotPipeline.changes` — status changes, rule data entered,
  rule and workflow overrides with their messages, newest first.
- **Header commands**: Force deployment, Delete deployment, Close — unchanged, hidden without the right.
- A finished deployment (Deployed or Cancelled) shows all phases expanded, read-only, and no action.

### Slot page

Same route, `/extension/environments/slot/[id]`. The slot's home.

- **Title**: "production · petclinic [canary]". Breadcrumbs: Home › Environments › slot.
- **Header block**: the Now / In flight / Next sections of the drawer — the same component.
- **Tabs**:
  - **Deployments** — the full history: `#n`, build, status, who, started, duration, error. Filters
    on status, build and user. Paginated.
  - **Eligible builds** — with the "Show all eligible builds" switch, each with Deploy (deploy dialog).
  - **Setup** — admission rules and workflows. Adding, **editing** (new: today a rule can only be
    deleted and re-added) and deleting, all hidden without `slot.edit`. Delete slot moves here from
    the header.
- The 8/16 split and the four stacked sections go away.

### Setup

New route, `/extension/environments/setup`, reached from the **Setup** command on the matrix.

- **Environments** tab — a table: icon (editable), order, name, tags, number of slots, edit/delete.
  "New environment" lives here.
- **Slots** tab — grouped by project: qualifier, environment, number of admission rules and
  workflows, link to the slot page's Setup tab. "New slot" lives here.
- The "still under experiment" note, once.
- A **"managed as code"** marker on configured objects is desirable but needs the backend to record
  where a configuration came from (CasC, CI configuration). Optional, low priority.

### Project environments

Same route, `/extension/environments/projects/[id]`, still reached from the project page
"Environments" command. It becomes a project-scoped view centred on the slot graph.

- The **slot graph**, laid out left to right. Each node **is** a slot cell; clicking it opens the
  drawer. The graph stays because it shows the dependencies between slots (the `environment`
  admission rules), which a matrix cannot.
- A **Graph / Matrix** toggle; Matrix is the home matrix filtered on the project.
- A qualifier selector when the project has several.
- The builds panel and the actions panel are **removed**: the drawer and the deploy dialog replace them.
- Commands: All environments, Close.

### Build page

- The "Environments" cell becomes a **journey strip**: one journey chip per slot of the project,
  in environment order, each carrying this build's state there. Clicking a chip opens the drawer.
  The "Start deployment" user menu action opens the deploy dialog from this build.
- **Build decorations** use the same chips (compact, icon + state) instead of bare environment
  icons, so the branch pipeline view and the build page read the same way.

### Dashboard widgets

Widget ids are stored in dashboards, so both keep their ids.

- **`extension/environments/EnvironmentList`** renders the matrix. Configuration: title, projects,
  tags, row limit.
- **`extension/environments/Environment`** renders a one-column matrix (one environment) with the
  in-flight overlay. Its separate `SlotCard` UI is removed.
- In both, cells open the drawer.

### Other surfaces

- The build search "Deployments" column, `ProjectPromotionWidget` and the delivery map
  `SlotCheckpoint` use the journey chip; delivery map checkpoints open the drawer.
- The build promotion-info dots are dead code (see [Defects](#defects-noted-not-design)).

## Mobile impact

- **Deploy dialog** shares the wording of the mobile deploy sheet (`MobileDeploySheet.js`) for
  refusal reasons.
- **What's blocking** aligns its labels with the mobile deployment screen ("N of M checks passed",
  Passed, Blocking).
- The **matrix, slot page, Setup and project environments** stay desktop-only; their routes keep
  mapping to the existing desktop-only page on a phone. The deployment route keeps its redirect to
  `/mobile/deployment/[id]`.
- **Follow-up**: a mobile "Environments at a glance" screen, in a separate session.

## Demo seed

The seed already shows a RUNNING deployment (staging ← petclinic 107) and a slot with broken rules
(production ← petclinic-ui). To demonstrate the redesign it gains, alongside the implementation:

- **A qualifier** on one project, to show nested rows — the mockups use a `canary` production slot
  on petclinic.
- **A slot never deployed** — the same `canary` slot serves.
- **"Behind"** is *not* shown by the seed as it stands: staging holds 89 (1.3.9), older than
  production's 104 (1.4.3), and 107 stops at RUNNING. The implementation decides how to show it —
  for example a fifth deployment, or a `canary` slot downstream of production.

`doc/dev-guide/demo-seed.md` explains why the deployment order is load-bearing; any addition keeps it.

## Backend and GraphQL needs

Most of what the screens read exists: `Slot.currentPipeline`, `Slot.lastDeployedPipeline`,
`Slot.eligibleBuilds`, `Slot.pipelines`, `SlotPipeline.changes`, `SlotPipeline.admissionRules`,
`Build.currentDeployments`, `EligibleSlot.nonEligibleRules`, `Project.slotGraph`. New:

- **A matrix query**: projects × slots with, per slot, the last deployed pipeline, the in-flight
  pipeline, a *blocked* flag and a *behind* flag. Filters: project name, favourites, label, environment
  tags, activity. Server-side paging over projects.
- **Blocked**: whether the in-flight deployment has a failing, non-overridden rule or workflow
  (computable from `runAction`/`finishAction`, but it needs to be cheap for a whole matrix).
- **Behind**: whether the slot upstream in the slot graph holds a newer build.
- **Next**: eligible builds newer than the currently deployed one, limited.
- **Build journey**: for a build, its state in every slot of its project (deployed current,
  superseded, in progress, eligible, not eligible with rules).
- **Deploy dialog warning**: the active deployment a new one would cancel (`Slot.currentPipeline` may
  already be enough).
- **Admission rule edit**: `saveSlotAdmissionRuleConfig` on an existing config, if not already supported.
- **Optional**: configuration origin, for the "managed as code" marker.

## Phasing

Each step ships on its own.

1. **Shared components** — slot cell, slot drawer, deploy dialog, What's blocking, journey chip.
2. **Matrix home** and the two widgets.
3. **Deployment page.**
4. **Slot page** and **Setup**.
5. **Build journey strip** and decorations.
6. **Project environments** graph page.
7. **Other surfaces** — build search, `ProjectPromotionWidget`, delivery map.

## Defects noted (not design)

Found during the inventory, each a small issue of its own:

- **Permissions**:
  - "New slot" (`SlotCreateCommand`) is gated on `environment.view`, not a create right.
  - Deleting an environment (`DeleteEnvironmentButton`) is gated on `environment.create`, not `delete`.
  - Deleting an admission rule (`SlotAdmissionRulesTable`) has no permission check.
- An existing admission rule cannot be edited, only deleted and re-added.
- The manual approval rule form (`environments-slot-admission-rule/manual/Form.js`) does not expose
  the `users` and `groups` its backend config supports; its `Check.js` does not show approval details.
- A frontend `environments-slot-admission-rule/workflow/Check.js` exists with no `workflow` rule in the backend.
- `# TODO Forcing` in `deployment/steps/deploymentActions.js`.
- A `console.log("Regenerating items...")` left in `SlotPipelineSteps.js`.
- Dead code: `BuildEnvironments.js`, `EnvironmentSlots.js`, `SlotPipelineDeploymentStatusButton.js`,
  `SlotAdmissionRuleCheck.js`, `components/builds/BuildPromotionInfo.js` — and with the last one, the
  `SlotPipeline`/`EnvironmentBuildCount` promotion-info dots and the `buildDisplayOption` setting that
  drives them.
- `SlotPipelineStatus.js` is marked deprecated and still used.
- `ontrack-extension-environments/docs/model.puml` still lists the old statuses (ONGOING, DEPLOYING,
  ERROR, DEPLOYED).
- Most environments components still use the deprecated `useGraphQLClient`; the redesign's new
  components use `useQuery`/`useMutation`/`callGraphQL`.

## Follow-ups

- Push updates (subscriptions or SSE) instead of polling.
- A mobile "Environments at a glance" screen.
- The "managed as code" marker, once the backend records configuration origin.
