# Project labels UI

Outcome of the grilling session of 2026-09-17 on bringing project labels back into the web UI.
The session settled 26 questions. This document records the decisions and their reasons, not the
questions. The work is tracked as the initiative `initiative: labels-ui` (#1800–#1808), milestone 5.5 — see
[Breakdown](#breakdown).

## Where we start from

Project labels (issue #615, 2019) attach coloured `category:name` tags to projects. The backend
survived the removal of the legacy Angular UI; the UI did not.

| Layer | State |
|---|---|
| Database | `LABEL` (`CATEGORY`, `NAME`, `DESCRIPTION`, `COLOR`, `COMPUTED_BY`) and `PROJECT_LABEL`, both from `V19__615_project_labels.sql`, cascading on project and label deletion. |
| Services | `LabelManagementService` (CRUD, global function `LabelManagement`) and `ProjectLabelManagementService` (assignment, project function `ProjectLabelManagement`). Covered by `LabelManagementServiceIT`, `ProjectLabelManagementServiceIT`, `LabelGraphQLIT`. |
| Security | `LabelManagement`: ADMINISTRATOR, CREATOR. `ProjectLabelManagement`: PROJECT_OWNER, PROJECT_MANAGER, GLOBAL_INDICATOR_MANAGER, and everybody holding `ProjectConfig` (which extends it), so CREATOR and AUTOMATION as well. |
| GraphQL reads | `labels(category, name)`, `Label.projects`, `Project.labels`, `projects(labels:)` (AND over `category:name`), `Build.usingQualified(label:)`, `IndicatorPortfolio.label`. |
| GraphQL writes | **None.** |
| REST | `LabelController` (create/update/delete) and `ProjectLabelController` (get/set/assign/unassign). No caller left, no test. |
| Automatic labels | `LabelProvider` extension point, `LabelProviderJob`, `LabelProviderJobSettings` (off by default), casc key `label-provider-job`, one real provider (`SCMCatalogEntryLabelProvider`, itself inert unless the SCM catalog is on). |
| Web UI | Only `components/labels/SelectLabel.js`, used by the Project promotions widget form. It renders a category-less label as `null:name` and uses the deprecated `useGraphQLClient`. |
| Mobile UI | Nothing. |
| KDSL, demo seed, mkdocs | Nothing (one sentence in `security/roles.md`). |

The legacy UI removed in `9bd045d837` (2025-06-09) offered a label admin page, a label chip under
the project title, an assignment dialog with create-from-typed-text, and a label filter on the home
page kept in `?label=` and localStorage.

Dead code found on the way: `MainBuildLinksFilterService` (no caller since `f55e2adfa4`),
`LabelTokenForm` (unused since `8b9e89bf48`), and an N+1 in
`ProjectLabelManagementServiceImpl.getLabelsForProject`.

## Decisions

### Scope

Restored: the admin page, chips on the project page, the assignment dialog, chips on project lists,
and filtering projects by label.

Not restored: creating a label by typing it in the assignment dialog. It duplicates the admin page
and needs the global `LabelManagement` function, which a project owner usually does not hold, so
the dialog would behave differently depending on who opens it.

### Automatic labels are removed

No user relies on them. Everything goes:

- `LabelProvider`, `LabelProviderService(Impl)`, `LabelProviderDescription`, `NOPLabelProvider`,
  `LabelProviderJob`, `LabelProviderJobSettings` with its manager and provider,
  `OntrackConfigProperties.jobLabelProviderEnabled`, `SCMCatalogEntryLabelProvider`, the settings
  form `label-provider-job-form.js`, and their tests.
- `Label.computedBy` — which removes `Label.computedBy` and `LabelProviderDescription` from the
  GraphQL schema, since `GQLTypeLabel` is derived from the Kotlin class. No client queries them.
- The computed-label paths in `LabelManagementServiceImpl`, `ProjectLabelManagementServiceImpl`,
  `LabelRecord` and `LabelJdbcRepository`, including `LabelNotEditableException` once nothing
  throws it.
- **One new Flyway migration** (the next free number at implementation time — V82 today) deletes
  the labels whose `COMPUTED_BY` is not null (their `PROJECT_LABEL` rows follow by cascade), drops
  the `COMPUTED_BY` column, and deletes the `LabelProviderJobSettings` row from `SETTINGS`. Same
  pattern as `V60__V5_ldap_oidc_cleanup.sql` and `V67__V5_main_builds_links_removal.sql`. This makes
  the initiative a minor-release change, never a patch.
- **The casc key `label-provider-job` is removed outright**, not kept as a no-op. The casc settings
  context raises "No CasC context is defined" on an unknown key, so a casc file still carrying it
  fails at startup; the release notes say so. A deprecation stub would be dead code to remember
  for a feature nobody uses.
- The asciidoc pages about label providers stay where they are: that tree is dead and not edited.

### GraphQL write API

Four mutations, written as a Kotlin `TypedMutationProvider` (the `EnvironmentsMutations` style):

| Mutation | Input | Returns |
|---|---|---|
| `createLabel` | `category?`, `name`, `description?`, `color` | `label: Label` |
| `updateLabel` | `id`, `category?`, `name`, `description?`, `color` | `label: Label` |
| `deleteLabel` | `id` | — |
| `setProjectLabels` | `projectId`, `labelIds: [Int!]!` | `project: Project` |

- **Why not Spring GraphQL SDL** (the `DashboardController` style, newer in core): the SDL schema is
  compiled on its own first (`GraphqlSchemaServiceImpl.createSchema`, `makeExecutableSchema`) and
  the programmatic `GQLType`s are merged in afterwards. A programmatic type may point to an SDL type
  through `GraphQLTypeReference`, but an SDL payload cannot declare `label: Label` when `Label` is
  programmatic. Moving `Label` to SDL does not help either: `Label.projects` returns the
  programmatic `Project`. The reads therefore stay as they are.
- **`setProjectLabels` replaces the whole set.** That is what the dialog does, and automation reads
  the current labels first. No `addProjectLabel` / `removeProjectLabel`.
- **Labels are identified by id** in mutations. Filters keep the `category:name` display string
  they already use.
- The frontend needs to know whether the user may assign labels to a project: a new `labels`
  action on the `project` authorizations, contributed through an `AuthorizationContributor` on
  `ProjectLabelManagement`.
- **The REST controllers `LabelController` and `ProjectLabelController` are deleted.** No UI or
  KDSL calls them, nothing tests them, and one write path is enough.

### Admin page

- Route `core/config/labels` (`pages/core/config/labels.js`), a `CoreUserMenuItemExtension` item in
  the `CONFIGURATIONS` group, shown only to holders of `LabelManagement` (ADMINISTRATOR, CREATOR).
  Other users see labels on projects already; a read-only page would add nothing. Icon added to
  `UserMenu.js`.
- A table: chip preview, category, name, description, project count. The project count links to
  the [label page](#label-page). A text filter over category and name; no grouping by category.
- Header commands: "New label", close.
- Create / edit dialog with the Ant Design `ColorPicker`, validated like `LabelForm`
  (`[A-Za-z0-9.\-_]+` for category and name, `#RRGGBB` for the colour).
- Delete asks for confirmation and states how many projects carry the label.
- **No warning about indicator portfolios.** A portfolio stores a label id; once the label is gone
  `getPortfolioLabel` returns null and the portfolio simply lists no project. That degrades
  gracefully, and naming portfolios would couple the admin page to the indicators extension.

### Project page

- **Chips in the title row**, after the project name and the favourite star, before the
  description (`ProjectView.js`, `MainPage title`). Labels are short identifying attributes.
- **A "Labels" header command**, placed before `ProjectEditCommand`, shown only when the `labels`
  authorization is granted. It opens a dialog with a text filter and a checkbox list of every label
  rendered as a chip, and saves through `setProjectLabels`. No inline pencil beside the chips.
- One shared chip component is used everywhere: label colour as background, the model's computed
  foreground colour, `category:name` (or `name` alone), description as tooltip, click to the label
  page.

### Project lists and filtering

- **Chips in `ProjectBox`** (All projects widget) **and `ProjectRow`** (Favourites), so every
  project list widget shows them.
- <a id="label-page"></a>**Label page `/project-labels/[id]`**, open to all users: the chip, the
  description, and the projects carrying the label as `ProjectBox` cards — only those the viewer
  may see, which `getProjectsForLabel` already enforces. It is the target of a chip click and of
  the admin page's project count, and does not depend on how anyone configured their dashboard.
- **All projects widget filter**: a label multi-select next to "Project name". `paginatedProjects`
  gains `labels: [String!]` (display strings, like `projects(labels:)`), combined with `name` by
  AND. The selection is transient, like the name filter: not saved in the widget configuration,
  not in the URL.
- **Several labels combine with AND**, consistent with `projects(labels:)`.
- `SelectLabel` is fixed on the way: a category-less label gives `name`, not `null:name`, and the
  component moves to `useQuery`.

### Mobile

**No mobile impact for the core work.** The mobile UI deliberately leaves out desktop decorations
and metadata (`ProjectScreen.js` says so), and labels are a portfolio-organisation tool rather than
a task done on a phone. The label page is a new desktop route with no mobile counterpart: nothing in
the mobile UI links to it.

One follow-up, as its own issue: **label search in the mobile project list**. `ProjectListScreen`
uses `projects(pattern:)`, which cannot be combined with other arguments; it moves to
`paginatedProjects(name, labels)` (made available by the widget filter work) and lets the user
filter on labels.

### KDSL and demo seed

- KDSL gains label support: creating a label, and reading and setting a project's labels.
- `DemoContent` puts labels on the demo projects (for instance a team and a language category),
  through `DemoTarget` / `DemoProject`, `KdslDemoTarget` and `InMemoryDemoTarget`. That is the
  demo for the whole initiative.
- A KDSL acceptance test covers the mutations end to end.

### Documentation

A new mkdocs page "Project labels" in the projects section of `ontrack-docs/docs/content/`, added to
`nav:` in `ontrack-docs/mkdocs.yml`: the concept, managing labels, assigning them, filtering, and
the permissions. It is written last, so it describes what shipped.

### Tests

Integration tests for the mutations and the authorization, a KDSL acceptance test, and Playwright
tests (`ontrack-web-tests/`) for each UI issue.

## Breakdown

Initiative label `initiative: labels-ui`, milestone 5.5. Each issue starts at `status:todo` with
`ready-for-agent`; dependencies are recorded as native GitHub issue dependencies.

| Issue | Scope | Depends on |
|---|---|---|
| #1800 | Remove automatic labels — migration, providers, job, settings, casc key, SCM provider, config property, settings form, tests, schema snapshots | — |
| #1801 | GraphQL label mutations and `labels` project authorization; delete the REST controllers | #1800 |
| #1802 | KDSL label support, demo seed labels, KDSL acceptance test | #1801 |
| #1803 | Labels admin page, including the `SelectLabel` fix | #1801 |
| #1804 | Label chips on the project page and assignment dialog | #1801 |
| #1805 | Label chips in project lists, label page, label filter in the All projects widget (`paginatedProjects(labels:)`) | #1804 |
| #1806 | Label search in the mobile project list | #1805 |
| #1807 | mkdocs "Project labels" page | #1803, #1804, #1805 |
| #1808 | Cleanup: `MainBuildLinksFilterService`, `LabelTokenForm`, N+1 in `getLabelsForProject` | #1800 |

#1805 depends on #1804 because both need the shared chip component.
