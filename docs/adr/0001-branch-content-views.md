# Branch content views are pluggable, on one axis, remembered per user

The branch view is gaining a second way to read a branch (a promotion-pipeline
rendering) alongside the existing builds table, and we expect more later. Rather
than nesting view switches, we make the content region of `/branch/{branchId}`
pluggable on a **single axis**: the legacy table and the pipeline are peers in one
registry, chosen from one menu in the page's command bar, and no content view
contains a selector offering other content views.

The registry is a static frontend array of `{key, name, icon, component}`. The
selection is stored in the already-deployed but unused `Preferences.selectedBranchViewKey`
field — one global key per user, not per project or per branch — and a live
`?view=` query parameter makes any choice linkable.

## Considered options

The design handoff proposed the pipeline view carrying its own internal
Timeline / Table / Compare toolbar, which would have made view choice two-dimensional
and produced a second table implementation alongside the legacy one. Rejected: the
handoff's own rationale warns against maintaining divergent implementations of the
same renderers, and a single menu already gives the table the first-class status
the toolbar was there to provide.

A server-driven extension point (`BranchContentViewExtension`, contributed by
Kotlin extensions and resolved by convention on the frontend) is where Yontrack's
architecture points, and is the likely end state. Rejected for now: there are two
views and no extension asking for a third, so the contribution API would be
designed against zero real consumers. The registry entry shape is deliberately
one a server list could populate later.

Per-project or per-branch persistence was rejected because it requires migrating
`selectedBranchViewKey` from a scalar to a map, and which branch you are looking at
rarely changes how you want to read it.

## Consequences

`BranchContent` shrinks to a switch that renders the selected view and hands it
only `branch`; each content view fetches its own data, so adding a third view never
means editing a shared fetcher. State genuinely belonging to the branch rather than
to any one view — the disabled-branch banner, and the validation stamp filter
context that content views share — stays above the switch.

Existing users keep the legacy table by default; the default flips to the pipeline
in a later release once it has soaked. Silently replacing the page every user knows
is how a redesign gets rolled back.
