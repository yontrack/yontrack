# Project labels

An instance which holds three projects needs no help organising them. One which holds three hundred
does, and the hierarchy of the [model](index.md) is not where that help comes from: a project belongs
to no folder, and the questions people actually ask - *which projects does my team own?*, *which ones
are written in Go?*, *which ones are in the payment chain?* - cut across any tree you could build.

**Labels** answer those questions. A label is a small coloured tag attached to a project, a project
carries as many of them as it needs, and a label is carried by as many projects as it applies to.
They are shown wherever a project is named, and every project list can be filtered on them.

## What a label is made of

| Part            | Rules |
|-----------------|-------|
| **Name**        | Required. Letters, digits, `.`, `-` and `_`. |
| **Category**    | Optional, same format as the name. It groups labels which answer the same question - `team`, `language`, `domain`. |
| **Description** | Optional. Shown as the tooltip of the label wherever it appears. |
| **Colour**      | Required, as `#RRGGBB`. The text colour is computed from it - black or white, whichever reads - so no combination is unreadable. |

A label is written, and searched for, as `category:name` - `team:payments`, `language:kotlin` - or as
its name alone when it has no category. That display string is what the filters take; the category is
not a namespace, just the first half of a name.

The tag itself is drawn the same way everywhere - the Labels page, the project page, every project
list - and on a project or in a project list, clicking it opens
[the label's own page](#the-page-of-a-label).

!!! note "Labels are assigned by people, not computed"

    Earlier versions of Yontrack could also compute labels from *label providers*, a job which set
    labels on projects on its own. That mechanism is gone: every label is created and assigned
    deliberately. Computed labels are deleted on upgrade, and the `label-provider-job` key is no
    longer known to [Configuration as Code](../../configuration/casc.md) - a casc file still
    carrying it must have it removed, or Yontrack refuses to start.

## Managing the labels

Labels are defined once for the whole instance, not per project. The **Labels** page, in the
*Configurations* group of the user menu - your avatar, at the top right - lists them all:

* the label itself, as it will appear on a project,
* its category, name and description,
* the number of projects carrying it, which links to [its page](#the-page-of-a-label).

A single text box filters the list on the category and on the name, which is all a list of this size
needs.

The **New label** command opens the creation dialog: category, name, description and a colour picker.
The same dialog, reached from the pencil on a row, edits an existing label - renaming a label or
changing its colour keeps it on the projects which carry it.

Deleting a label asks for confirmation first, and the confirmation says how many projects carry it,
because deleting a label removes it from all of them. There is no undo: the label has to be created
again, and assigned again.

The **Labels** entry is only offered to the holders of the `LabelManagement` function, and creating,
editing and deleting a label is refused to anybody else - see [Permissions](#permissions) below.

## Assigning labels to a project

A project's labels are shown on its page, next to its name. To change them, use the **Labels**
command in the project's command bar: it opens a dialog listing every label of the instance as a
checkbox, with a filter over the list, and ticking is assigning. What you save is the whole selection
- the labels you leave unticked are removed from the project.

The dialog assigns existing labels; it does not create them. Creating a label needs a function most
project owners do not hold, and a label invented on the spot in one project is how two spellings of
the same idea end up side by side. Ask for the label to be created on the
[Labels page](#managing-the-labels) first.

The command is only there for the users who may change that project's labels - see
[Permissions](#permissions).

## Finding projects by their labels

Labels are shown on every project list - the **All projects** and **Favourite projects**
[widgets](../../dashboards/widgets/index.md) among them - so a list of projects reads as a list of
what those projects *are*, not only of what they are called.

### Filtering a project list

The [All projects widget](../../dashboards/widgets/all-project-list.md) carries a label selector next
to its project name box. Pick one or more labels and the list narrows to the projects carrying **all**
of them; combined with a name, both criteria apply together.

That selection is transient, exactly like the name filter: it is not saved in the widget's
configuration and not kept in the address, so a dashboard always opens unfiltered and the filter is a
way to look, not a way to configure.

### The page of a label

Every label has a page of its own, reached by clicking that label on a project or in a project list,
or from the project count on the [Labels page](#managing-the-labels). It shows the label, its
description, and the projects carrying it.

It is open to every user, and it lists only the projects that user is allowed to see - so it is a
safe link to send to anyone.

### On a phone

The [mobile UI](../../mobile/index.md) has its own label search: the project list filters by name and
by label together, the labels being picked from a list rather than typed. Several labels narrow the
list the same way they do on the desktop.

That is all the mobile UI does with labels - project rows do not carry them, for want of width, and
creating, editing or assigning labels stays on the desktop.

## Permissions

| Who | Sees labels | Assigns labels to a project | Creates, edits and deletes labels |
|---|---|---|---|
| Any user who can see the project | yes | no | no |
| Project **OWNER**, project **PROJECT_MANAGER** | yes | on that project | no |
| Global **CREATOR**, global **AUTOMATION** | yes | on all projects | **CREATOR** only |
| Global **ADMINISTRATOR** | yes | on all projects | yes |

Two functions are behind that table, and they are independent:

`LabelManagement`
:   A global function, held by administrators and creators. It gives access to the **Labels** page and
    to the creation, edition and deletion of labels.

`ProjectLabelManagement`
:   A project function, held by the project owner, the project manager, and by everyone holding
    `ProjectConfig` on the project - which the global *creator* and *automation*
    [roles](../../security/roles.md) do, on every project. It gives the **Labels** command on that
    project. Extensions may contribute it to roles of their own.

Labels themselves are never secret: a user who can see a project sees its labels, its labels'
descriptions, and the label pages they lead to.

## From automation

Labels are read and written through the [GraphQL API](../../api/graphql.md), with `createLabel`,
`updateLabel`, `deleteLabel` and `setProjectLabels` on the writing side - `setProjectLabels` replaces
the whole set of labels of a project, so read `Project.labels` first if you mean to add one. On the
reading side, `labels` lists them, `label(id:)` gets one, and both `projects(labels:)` and
`paginatedProjects(labels:)` filter projects on the `category:name` display strings, ANDed together.
