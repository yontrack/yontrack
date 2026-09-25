# Yontrack

Yontrack monitors continuous delivery: it tracks what has been built on each
branch of each project, how far each build has progressed towards release, and
what was checked along the way.

## Language

### Structure

**Branch view**:
The page at `/branch/{branchId}`, answering "what is the state of this branch?".
_Avoid_: branch page, branch screen

**Branch content view**:
One interchangeable rendering of a branch's builds, filling the content region of
the branch view. Content views are peers on a single axis of choice — a content
view never offers a sub-selector for other content views.
_Avoid_: view mode, layout, tab

**Pipeline view**:
The branch content view that reads a branch as its promotion pipeline — what is
release-ready right now — with the full build list one click away in the view
menu. Named for what it reads, not for how it draws.
_Avoid_: timeline view, card view, Option C

**Build timeline**:
The strip of build cards in the pipeline view, most recent first. It shows the
same builds, under the same filter and the same "load more", as the builds view's
table.
_Avoid_: build strip, build feed

**Build inspector**:
The pair of panels in the pipeline view showing the promotions and validations of
the selected build, so a build can be read without leaving the branch. It shares
display primitives with the build view's widgets, not compositions.
_Avoid_: build detail, build preview, build panel

**Delivery map**:
What a build on this branch has to pass through on its way to an environment: the
branch's promotion levels and validation stamps, the project's slots, and the
configured dependencies between them. It is configuration with progress painted
onto it, not a history.
_Avoid_: graph, dependency graph, pipeline graph. *Graph* is already taken twice
over, by the slot graph service and by the planned build dependency query engine.

**Delivery map view**:
The branch content view that renders the delivery map, a peer of the *pipeline
view* and the builds view.
_Avoid_: graph view

**Checkpoint**:
One node of a delivery map, being a promotion level, a validation stamp or a slot.
Every checkpoint names the latest build to have arrived at it and says what became
of it there: a promotion level is arrived at by being promoted, a validation stamp
by a run of any outcome whose status the checkpoint shows, a slot by a deployment.
Arriving is therefore not the same as succeeding, and only a validation stamp can
show a build that arrived and failed. On a promotion level and a validation stamp
that build is always of this branch; on a slot it is the most recently deployed
build, which may belong to another branch.
The two workflow kinds are the exception nothing arrives at: a *workflow checkpoint*
and a *slot workflow checkpoint* name no build, because theirs would always be the
one the checkpoint beside them already names.
_Avoid_: stage, node, step. *Stage* is already refused for both promotion level and
environment, and the pipeline view uses it for its promotion band.

**Aggregate checkpoint**:
One checkpoint standing for every validation stamp matched by an auto promotion
pattern, rather than one checkpoint per stamp. It is labelled with the pattern,
because that is what the configuration actually says: everything matching this,
not these forty named things. A promotion whose stamps are named explicitly gets
one checkpoint each instead.
_Avoid_: group node, collapsed node, stamp group

**Workflow checkpoint**:
The checkpoint standing for a workflow a promotion set off, drawn as a consequence
of its promotion level. It names the workflow, says where the run got to and how
long it took, and links to that run. It names no build - its build would always be
the one its promotion level already names. It exists only where a run left a
notification record behind, so a promotion level never yet promoted has none, even
when subscriptions exist.
_Avoid_: notification checkpoint, subscription checkpoint. A workflow is one channel
among several and the map draws no other.

**Slot workflow checkpoint**:
The checkpoint standing for a workflow configured on a slot, for one of the three
moments of a deployment. Unlike a *workflow checkpoint* it is configuration, so it
is drawn whether or not it has ever run - a `CANDIDATE` workflow which never ran is
not dormant, it is why nothing has ever deployed there. Its trigger decides which
edge it gets: `CANDIDATE` and `RUNNING` are hard gates and are *required by* their
slot, while `DONE` is *emitted* by it.
_Avoid_: deployment workflow, slot gate

**Branch head**:
The branch's latest build, stated once in the delivery map view's header and
never drawn as a checkpoint: edges on that map mean *unlocks* or *requires*, and
an edge from the latest build to every checkpoint would mean neither while
fanning out across the whole map at once. Each checkpoint carries its own
*checkpoint lag* instead. The view labels it "Latest build", which is what the
pipeline view's own stat calls it.
_Avoid_: tip, HEAD, latest. *HEAD* is git's, and this is a build rather than a
commit.

**Checkpoint lag**:
How many builds of the branch are more recent than the one a checkpoint names: a
checkpoint at the *branch head* is behind by none, and is marked as being at the
head rather than as behind by zero. It is unknown rather than zero on a slot
naming another branch's build (ADR 0009) - where counting against this branch's
head answers a question nobody asked - and on a branch with no head, and nothing
is drawn in either case.
_Avoid_: staleness, drift, age. *Age* is the arrival's timestamp, which is a
different reading and is drawn beside it.

**Map topology**:
The shape of a delivery map: which checkpoints it holds and which edges join
them, and nothing else - not which build has arrived where, not the order the
server listed them in, and not the members of an aggregate. The delivery map
view refreshes itself and lays itself out again only when its topology changes,
because almost nothing else on it changes minute to minute and a map which
reshuffles under the cursor once a minute is worse than one which does not
refresh.
_Avoid_: layout, shape, structure. *Layout* is what elk computes FROM the
topology, and the two must not be used for each other.

**Unreachable slot**:
A slot checkpoint no build of the branch being read can ever be deployed to,
because an admission rule excludes the branch outright. It is drawn, marked as
such, and names no build - not even the one actually deployed in it. See ADR 0009.
_Avoid_: blocked slot, forbidden slot, excluded slot. *Blocked* is what a build
waiting on an admission rule is; this is about the branch, and is permanent.

**Unresolved checkpoint**:
The checkpoint standing for a name a configuration asked for and which matches
nothing - a slot admission rule naming a promotion level the branch does not have,
or an environment the project has no slot in. It carries that name, is marked as
matching nothing, and names no build, there being nothing behind it to arrive at.
It is never what a checkpoint hidden by permissions looks like: that one is left
out of the map entirely, together with its edges.
_Avoid_: missing checkpoint, broken checkpoint, orphan, dangling reference.
*Missing* is what a hidden checkpoint is, and the two must not sound alike.

**Unlocks**:
The delivery map edge meaning that reaching one checkpoint grants another by
itself, as auto promotion does.
_Avoid_: triggers, leads to

**Requires**:
The delivery map edge meaning that a checkpoint cannot be reached until another
has been, as promotion dependencies, slot admission rules and the previous
promotion condition do. It constrains; it does not act. It is drawn labelled
*required by*, because the label is read along the arrow and the arrow runs from
the prerequisite: the line for "GOLD requires SILVER" reads *SILVER required by
GOLD*. That is this word rendered for the direction it is read in, not a second
term. A requires edge says a constraint holds; it does not say where the
constraint was configured, and two sources naming the same directed pair draw one
edge. See ADR 0010 for the requires which is not drawn at all.
_Avoid_: depends on, blocks

**Emits**:
The delivery map edge meaning that reaching one checkpoint sets another off, with
nothing waiting for the result: a promotion firing its notification workflows, a
slot firing the workflows configured for the end of a deployment. It runs from the
checkpoint to its consequence, like every other edge running from what happens
first. It neither grants nor constrains, which is why it could not be either of the
other two. See ADR 0011.
_Avoid_: triggers, fires, notifies. *Triggers* is already refused for *unlocks*.

**Previous promotion condition**:
The rule that a promotion cannot be granted before the promotion level
immediately below it in the branch's order. Unlike the other sources of a
requires, it is resolved through a cascade - promotion level, then branch, then
project, then global settings - where the first level carrying it decides,
whichever way it answers.
_Avoid_: promotion ordering, sequential promotions

**Promotion level**:
A named, ordered rung a build can reach on a branch. The set is configured per
branch and its size varies widely between projects; levels carry an ordinal
position and no tier.
_Avoid_: stage, gate, tier

**Promotion run**:
The record that a given build reached a given promotion level.
_Avoid_: promotion event

**Validation stamp**:
A named check configured on a branch, such as "unit tests" or "security scan".
_Avoid_: validation type, check definition

**Validation run**:
The record of one execution of a validation stamp against a build, carrying a
status and optionally typed validation data.
_Avoid_: validation result

**Validation data type**:
The shape of the data a validation run carries — test counts, a percentage, a
CHML severity breakdown. It is the closest thing the domain has to a validation's
"kind".
_Avoid_: validation kind, validation category

**Build display name**:
The name to show a human for a build. It is the build's release label when one
exists and the build's own name otherwise, so every build always has one.
_Avoid_: version, release, build label

**Entity image**:
An optional picture uploaded against a promotion level or validation stamp. An
uploaded image always takes precedence over the generated icon.
_Avoid_: logo, medal image

**Generated icon**:
The visual identity drawn for a promotion level or validation stamp that has no
entity image, derived deterministically from its name.
_Avoid_: placeholder, default icon, fallback image

**Range selection**:
Picking two builds on a branch in order to see what changed between them.
_Avoid_: build comparison, diff selection

**Change log**:
What happened between two builds: the commits, the issues they reference, and the
dependency links that moved. It is computed from the project's SCM, not stored.
_Avoid_: diff, delta, release notes

**Change log view**:
One interchangeable rendering of a change log, filling the change log page.
Change log views are peers on a single axis of choice, exactly as *branch content
views* are — a change log view never offers a sub-selector for other views.
_Avoid_: view mode, display mode, tab

**Semantic change log**:
The change log view that groups commits into sections by their conventional-commit
type. It shows only commits carrying a type; commits without one are absent rather
than collected into an "other" section.
_Avoid_: conventional change log, grouped change log, formatted change log

**Issue export**:
Rendering a change log's *issues*, grouped by issue type, into a format meant to be
pasted elsewhere. It is a distinct thing from the semantic change log, which groups
*commits* by commit type, and it belongs to the classic change log view.
_Avoid_: change log export, issue report

### Deployment

**Environment**:
A named, ordered place a build can be deployed to — `staging`, `production`, a
demo instance. Environments are global rather than per-project, carry tags, and
their order is how far through delivery they sit, not a ranking.
_Avoid_: stage, target, tier

**Slot**:
The junction of one project and one environment: where builds of that project
are deployed into that environment. A slot is what carries the admission rules
deciding which builds are eligible and the workflows run when one is deployed. A
project may have more than one slot in an environment, told apart by a qualifier.
_Avoid_: deployment target, environment slot, deployment config

**Slot pipeline**:
One build's passage through one slot — the record of a single deployment,
numbered within its slot and moving from candidate through running to done or
cancelled. A slot has many pipelines over time; each names exactly one build.
_Avoid_: deployment, release pipeline, and above all the bare *pipeline*, which
already means the branch's promotion pipeline in *pipeline view*

**What the environments UI calls them**:
The screens do not use these names. The terms above are the domain's, and the
environments UI redesign deliberately renames two of them for the people reading
it, who are release managers rather than modellers. The mapping is one way — code,
GraphQL fields and this document keep the domain's terms — and it is the only
place the two vocabularies are allowed to disagree:

| Domain term     | On screen                                            |
|-----------------|------------------------------------------------------|
| Slot pipeline   | **Deployment** — "Deployment #3", the deployment page |
| Slot            | **"production · petclinic [canary]"** — environment, then project, then the qualifier. The word *slot* appears only in Setup |
| Environment     | *environment*, unchanged                              |
| Admission rule  | *rule*, and in "What's blocking" simply a **check**, counted beside the workflows as "2 of 3 checks passed" |

Two adjectives are new on screen and have no domain term behind them, being
readings composed of several: a slot is **blocked** when its in-flight deployment
is held up by a failing, non-overridden check of the phase it is in, and **behind**
when a slot upstream of it in the project's slot graph holds a newer build.

**Matrix**:
The Environments home: projects down, environments across, a slot in each cell. A
**row** is a project *and a qualifier* — the default qualifier's row is the
project's own, and the others nest under it — and a **column** is an environment,
drawn only where some visible row has a slot in it. The matrix is a reading and
not a thing: no domain object is a matrix, and the server query answering it
composes slots, projects and environments into the shape the screen draws.
_Avoid_: dashboard, grid, overview. *Dashboard* is already Yontrack's home page of
widgets, one of which happens to render this matrix.

### Notifications

**Notification record**:
The outcome of one notification fired by a subscription on an entity, resolving
to a success, an in-flight, or an error state.
_Avoid_: notification result, notification run

**Notification channel**:
The medium a notification is delivered through: mail, Slack, webhook, workflow,
and others.
_Avoid_: notification target, transport

**Workflow**:
One notification channel among many, in which a notification triggers a graph of
executable nodes. A workflow is a *kind of* notification, so a count of an
entity's notifications is never a count of its workflows.
_Avoid_: pipeline, automation

### Filtering

**Build filter**:
A named, reusable, shareable filter over a branch's builds, stored per user and
per branch and expressible as a permalink. This is the domain's only concept for
a saved way of looking at a branch's builds.
_Avoid_: saved view, saved search

**Validation stamp filter**:
A named selection of which validation stamps to display on a branch.
_Avoid_: stamp selection, column filter

### Findings

**Finding**:
One known weakness — a vulnerability, a code issue, a secret, a DAST alert — at
one location of one project, identified by `(scanner, externalId, location)`.
_Avoid_: vulnerability (secrets and code issues are not), CVE (one kind of
external id), issue (the tracker's), alert

**Observation**:
One sighting of a finding by one scan of one build.
_Avoid_: occurrence, detection

**Exposure**:
A finding is exposed on a branch while the latest scan of the same stamp on that
branch reports it.
_Avoid_: open (the project-level roll-up), affected

**Acceptance**:
A decision recorded outside Yontrack, read by it, that a finding is tolerated,
possibly until an expiry.
_Avoid_: suppression (the scanner's mechanism), waiver, exception

### Search

**Search document**:
What an indexer writes about one findable thing - a project, a build, a commit -
so that search can match it and show it without reading the thing itself again: a
title, the identifiers it answers to, optional free text, and what its result needs
to be rendered and linked. It belongs to exactly one search result type and is
unique by its key within that type.
_Avoid_: index entry, ES document. There is one search index for every type, and
Elasticsearch no longer holds it.

**Search result type**:
The kind of thing a search result is - project, build, scm-commit, and so on. Each
type owns how its results are rendered, and whether a user may see one that belongs
to no project. Types are peers: none outranks another in a search's ranking, and
grouping by type is a matter of presentation only.
_Avoid_: search provider, index. A *search provider* is an indexer, which writes
the documents of a type rather than being one, and a type is a slice of the one
search index, not an index of its own.
