# Deployments are not a branch-view concern

The pipeline content view shows a build's environments as a **decoration on the
timeline card**, and nowhere else. In particular the promotion stage band carries
one card per promotion level and no card for an environment or a slot, and it will
not grow one.

The band's contract is *what a release on this branch has to go through*: one card
per promotion level, counting builds of this branch. Deployment does not fit that
contract. Environments are global, slots are per project + qualifier, and the build
currently deployed in a slot may come from another branch entirely — so a card sitting
in a branch's band, naming another branch's build, would be a genuine surprise rather
than a detail. Scoping such a card to the branch would need backend support that does
not exist: there is no "builds of branch B deployed in slot S". Deployment is a
project-level fact, and the Environments page owns it.

The vocabulary says the same thing from the other side. `CONTEXT.md` reserves the bare
*pipeline* for the branch promotion pipeline — the thing *pipeline view* is named after —
and explicitly forbids it for the slot pipeline, the per-slot deployment. Two unrelated
senses of one word, one of them already claimed by this view's name, is not a mixture to
put on one screen.

What the user does get is the fact itself, through the core `decorations` field: which
environments a build is in, on the card for that build. That is `BuildEnvironmentsDecorations`,
an extension-side `DecorationExtension` rendered by a dynamic import, so the core view
gets environments with no core-to-extension coupling and works unchanged on an instance
with no environments extension installed.

## Considered options

**Environment cards in the promotion stage band** was the reading the first review of the
view asked for, and is rejected above.

**Environments in the build inspector** was rejected as redundant rather than wrong: the
card decoration already covers it, and you select a build by clicking the card you just read
the environments off, so a second copy four lines below states the same fact twice on one
screen. It is worth revisiting only for deployment *history*, which decorations cannot give —
`findHighestDeployedSlotPipelinesByBuildAndQualifier` answers "where is this build now", not
"where has it been" — and which is a different feature.

**Filtering the card's decorations down to the environments one** was rejected because it
would put the literal string `environments.ui.BuildEnvironmentsDecorations` in core code,
which is the coupling the decoration seam exists to avoid. All decorations render.

## Consequences

A future "Missing environments" report against the pipeline view is answered by this
document, not by a new band.

Rendering all decorations means `ReleaseDecorationExtension` prints the version the card
already prints on its first line via `buildVersion`. Accepted. If it grates in use, the fix
is dropping the card's version line, not filtering decorations.

Because a decoration is a link and an interactive descendant of a `button` is invalid, the
decoration row on `BuildTimelineCard` is a sibling of the selection button rather than a child
of it — the same reason the range selector already is. The card chrome therefore lives on the
card's container, not on the button.
