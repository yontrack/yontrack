# Licensing

Yontrack is open core: most of it needs no license, and a few features are under license. Each of
them is a **licensed feature** of the license, identified by an ID, which the license enables or
not. A development instance enables all of them.

The page *License* of the user menu shows the license of the instance and, for each licensed
feature, whether it is enabled. Through the API, the same is in `licenseInfo` in GraphQL:

```graphql
{
  licenseInfo {
    license {
      licensedFeatures {
        id
        name
        enabled
      }
    }
  }
}
```

## Licensed features

| ID                                  | Name                                | What it gates |
|-------------------------------------|-------------------------------------|---------------|
| `extension.environments`            | Environments & deployment pipelines | The [environments](../integrations/environments/environments.md), their slots and deployments. |
| `extension.findings.native-formats` | Native scanner formats              | Reading the native scanner formats — SARIF, Trivy JSON — of the [findings](../integrations/findings/findings.md). The findings themselves and their neutral format are not licensed. |
| `extension.scorecard`               | Delivery scorecard                  | The [estates](../scorecard/estates.md) and the readings of the projects in them. The [scorecard](../scorecard/scorecard.md) of a project on its own is not licensed. |
| `extension.audit-trail`             | Audit trail                         | Writing the [audit trail](../audit-trail/index.md) of the builds, and their evidence. A trail already written stays readable. |
| `extension.agents`                  | Agent governance                    | The rulings on [agents](#agent-governance) and their activity. Recording them is not licensed. |

## Agent governance

The free product **records** agents in full and holds them to the same gates as everyone else:
registered [agents](../agents/index.md) and their tokens, the agent and its session on the record,
the agent policy and the *Agents admitted* setting of the promotion levels and slots, the
[assisted change](../agents/index.md#assisted-builds) of the builds and the `build_assisted` event,
and the readiness of a build. None of this needs a license.

*Agent governance* (`extension.agents`) gates **ruling on** agents, and following what they do:

* [Assisted builds require](../concepts/model/index.md#assisted-builds-require) — the promotion
  condition "if the build is assisted, these validation stamps must pass first".
* [Evidence from non-agents only](../concepts/model/index.md#evidence-from-non-agents-only) — the
  validation stamp restriction "evidence on this stamp must come from a non-agent actor".
* [Agent activity](../agents/index.md#agent-activity) — what an agent did over the last days, for
  its owner and the administrators.
* [Agent activity across the projects](../agents/index.md#agent-activity-across-the-projects) — the
  *Agent activity* dashboard widget and the *Latest agent actions* page, for any user, on the projects
  they can see.

Without the license, these rulings are kept, visible and editable, but **do nothing**: an assisted
build is promoted as any other, an agent records evidence on any stamp. They apply again as soon as the license allows them.

The activity of the agents is not shown without the license: the *Activity* tab of an agent, the
*Agent activity* widget and the *Latest agent actions* page say that the license is needed, the
*Latest agent actions* entry is not in the menu, and the API refuses the reads. What the agents do is still recorded, and comes back in the activity with the
license.
