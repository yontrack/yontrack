# Environments

!!! warning

    This feature is under license.

!!! note

    Documentation is in progress.

## The Environments home

`Environments` in the user menu opens a **matrix**: one row per project, one column per environment,
and in each cell what that project currently has running in that environment.

* A **cell** shows the deployed build, its top promotion and how long it has been there, with an
  overlay for a deployment still on its way, a red dot when that deployment is held up, and a
  *behind* badge when an environment earlier in the chain holds something newer. Clicking a cell
  opens the slot drawer, which shows what is deployed now, what is in flight, what could go next and
  the last few deployments.
* A project deploying more than once into the same environment — a canary, for instance — has one
  **row per qualifier**, nested under the project's own row.
* A **column** is only drawn for an environment some visible row has a slot in.
* The toolbar filters by project name, by favourites or all projects, by project label and by
  environment tag, and can keep only what has a deployment on the way. The filters are part of the
  address, so a filtered matrix can be bookmarked and shared.
* The matrix refreshes itself every 30 seconds and says when it last did.

Creating environments and slots is behind the **Setup** command, out of the way of the operational
screens — see [Setup](#setup) — and is usually not done by hand at all, see
[Configuration](#configuration).

## The slot page

Clicking **Open slot** in the drawer opens that slot's own page — "production · petclinic [canary]".
It is the home of one project's deployments into one environment.

* The **header block** is the drawer's own Now / In flight / Next, in full: what is deployed, what is
  trying to replace it and what could go next, with the one action that would move the deployment in
  flight. It refreshes itself every 30 seconds and says when it last did.
* **Deployments** is the whole history: number, build, status, who, when it started, how long it
  took, and the error if there was one. It can be filtered by status, by build and by user — *user*
  meaning anybody on the deployment's audit trail, not only whoever started it — and the filtering is
  done by the server, so the answers do not depend on how much of the history is on screen.
* **Eligible builds** lists what could go into the slot next, with *Deploy* on each. The
  *Show all eligible builds* switch widens the list from the builds the slot's rules already accept
  to every eligible one, so that "why can I not deploy this?" has somewhere to be asked.
* **Setup** holds the slot's admission rules and its workflows: adding, editing and deleting them,
  and deleting the slot itself. The whole tab is hidden from a user who may not configure the slot.

## The deployment page

Clicking a deployment — from the matrix cell's drawer, from a slot, or from a build — opens that
one deployment. It answers three questions, in three places:

* The **header** names the build, its branch and its top promotion, says when the deployment started
  and who started it, and draws where it has got to: Candidate → Running → Deployed, or → Cancelled.
  Beside the bar is the **one** action that would move it — *Start the deployment* on a candidate,
  *Finish the deployment* on a running one — with *Cancel* as a secondary action. The action is
  disabled while something is blocking, and is not shown at all to a user without the right, or on a
  deployment which is over.
* **What's blocking** lists the checks of the phase the deployment is *in*, failing ones first, with
  the fix on the failing row itself: *Answer* a rule waiting on somebody, *Override* a rule or a
  workflow which refuses. Checks which passed are folded behind "N of M checks passed". The phases
  already over are below, collapsed and read-only; a finished deployment has no current phase and
  shows all of them, expanded.
* The **timeline** down the side is the audit trail, newest first: every status change, every answer
  given to a rule, and every override — with the justification its author wrote.

The page refreshes itself every 30 seconds and says when it last did. *Force deployment* and
*Delete deployment* remain header commands, and are hidden from a user without the right.

## Setup

The **Setup** command on the Environments home opens the one page where environments and slots are
created, edited and deleted. It is deliberately out of the operational screens: configuring is not
what somebody opens the Environments home to do, and in practice it is done as code — see
[Configuration](#configuration).

* **Environments** lists every environment in order, with its icon (editable in place), its tags and
  how many slots it has. *New environment* lives here, and so does deleting one.
* **Slots** lists every slot grouped by project, with its qualifier, its environment, how many
  admission rules and workflows it carries, and a link to that slot's own Setup tab. *New slot* lives
  here.

Every control is hidden from a user without the matching right, and the Setup command itself is
hidden from a user who has none of them.

## Configuration

While environments and their slots can be configured through the UI, it's recommenced to use either:

* [CI configuration](#ci-configuration)
* [CasC](#casc)

### CI configuration

Using the [CI config](../../configuration/ci-config.md) feature, one can define the environments and slots linked to a
project directly from its Yontrack CI config file.

For example:

```yaml
version: v1
configuration:
  defaults:
    project:
      environments:
        environments:
          - name: self.yontrack.com
            description: Production environment for Yontrack itself
            order: 200
            tags:
              - yontrack
              - release
        slots:
          - project: yontrack
            environments:
              - name: self.yontrack.com
                admissionRules:
                  - ruleId: promotion
                    ruleConfig:
                      promotion: GOLD
                  - ruleId: branchPattern
                    ruleConfig:
                      includes:
                        - main
                workflows:
                  - name: Creation
                    trigger: CANDIDATE
                    nodes:
                      - id: start
                        executorId: mock
                        data:
                          text: Start
                      - id: end
                        parents:
                          - id: start
                        executorId: mock
                        data:
                          text: End
```

In this case, unless in the [CasC setup](#casc) where this behavior is configurable, the environments are not
authoritative. Only the slots are for the configured project.

A typical use case for this CI config of environments would be done at the level where the actual environment is
actually deployed, like in a GitOps repository.

### CasC

TBD
