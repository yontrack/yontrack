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
screens — and is usually not done by hand at all, see below.

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
