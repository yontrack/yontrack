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
