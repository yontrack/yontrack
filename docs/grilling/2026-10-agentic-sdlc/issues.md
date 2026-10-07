# Agentic SDLC — issue breakdown

This breaks [README.md](README.md) down into agent-sized issues. It was done in a second grilling session, on 2026-10-07.

- The issues in `yontrack` have base branch `main` and milestone `6.0`.
- Each one carries `initiative: agentic-sdlc`, `type: enhancement`, `status:todo` and `ready-for-agent`.
- Dependencies are recorded as native GitHub dependencies, across repositories too, and as a *Blocked by* line at the top of each body.
- The companion repositories' issues have no milestone: they follow their own release trains.

## Where the code stood

`research-v6.md` was taken on the `v6` branch before the audit trail and the events audit landed. On `main` (87aef93ca7), the facts that changed or mattered are:

- **`Actor`** (`ontrack-model/.../security/Actor.kt`, #1956) holds the account, the channel, the token name, the JWT and the system reason. It has no human/agent kind.
  - It is persisted only on audit trail entries (`BUILD_TRAIL_ENTRY.ACTOR`, licensed `extension.audit-trail`) and in queue and ingestion payloads.
  - `Signature` is still time + user name.
  - `EVENTS` carries only `EVENT_USER`.
- **Accounts** are keyed by a unique, required `EMAIL`. There is no account kind, no disable or lock, and no service account.
  - Signatures store the email in `VARCHAR(40)` columns: `CREATOR`, `EVENT_USER`, `"USER"`, `DATA_USER`, `OVERRIDE_USER`.
  - Tokens have no scope. Only the test actuator generates a token for another account.
- **Subscription keywords** (`Event.matchesKeywords`) match entity names and event values exactly. Nothing can express the actor.
- **Readiness ingredients exist:**
  - `Build.autoPromotionConditions` gives the per-stamp detail;
  - `Build.eligibleSlots` gives the rules not satisfied, with reasons;
  - promotion checks throw on the first failure.
- **The other context answers exist:** `scmChangeLog(from, to)`, `Slot.lastDeployedPipeline`, and `builds(… withPromotionLevel)`.
- **Slots** carry no properties. On promotion levels, `ci.yaml` sets only the auto-promotion and dependency properties.
- **Nothing** computes anything from the change log at build creation.
- **The ledger** is not on `main` yet: it is 6.1.

## Decisions

| # | Question | Decision |
|---|---|---|
| Q1, Q7 | What goes into 6.0 | All of the README's capabilities 1–6. **Out:** the search facet "assisted" (later), and item 7 (ledger export by actor, scorecard split), together with the README's other deferred items |
| Q2 | Re-open the README | No. It is settled; this session only resolves what the code on `main` changed |
| Q3 | Tracking | Label `initiative: agentic-sdlc`; this file |
| Q4 | Companion repositories | Their issues are opened in their own repositories (MCP, CLI, skills, licence, infra), with no milestone, and listed here |
| Q5 | Vocabulary | A first docs-only issue, AG0 |
| Q6 | Licence flag | `extension.agents` is declared by the first licensed issue (AG10). Licences get it through yontrack-license (AGL) |
| Q8 | What an agent is | An **account of kind `AGENT`**, with an owner account id. Tokens, ACLs and `lastUsed` work unchanged. It never logs in through OIDC |
| Q9 | Rights | **Derived**: the owner's rights ∩ a fixed agent policy. Nothing is assigned to the agent. Narrowing an agent further is part of the licensed "policy beyond on/off", later |
| Q10 | Persisting the actor | A nullable **`ACTOR` JSONB** on each signed table, denormalised (names survive deletion). NULL = human, no backfill. `Signature.actor` |
| Q11 | `user.name` of an agent's signature | The **agent's** identifier. So an agent cannot pass as its owner, for instance against a `manual` rule's user list |
| Q12 | Agent session | Headers `X-Yontrack-Agent-Session` (≤ 255 characters) and `X-Yontrack-Agent-Session-Link` (https), read only for agent tokens |
| Q13 | Who registers agents | Self-service for one's own agents, and an admin for any owner |
| Q14 | Owner lifecycle | Deleting the owner deletes its agents and their tokens; names stay on signatures. An admin can **transfer** an agent |
| Q15 | Actor filters | Prefixed keywords `actor:agent`, `actor:human` and `agent:<name>`, read from the event's actor. An actor filter on the admin `events` query, the page and the export |
| Q16 | Assistant conventions | A global settings page, **Agent markers**: built-ins on/off plus patterns; CasC through settings |
| Q17 | Computing `assistedChange` | An **asynchronous** listener on `new_build` and on the commit property, against the branch's previous build with a commit. A value set by CI wins |
| Q18 | Manual rule vs agent | **Refused**, with "an agent cannot approve; ask <owner>". The pipeline stays at CANDIDATE |
| Q19 | Agent identifier | **`<slug>[agent]`**, the slug matching `[a-z0-9-]{1,32}`, as GitHub's `[bot]` does. `[` is invalid in an email, so no IdP can claim it. Agents are never mail recipients |
| Q20 | Readiness | `Build.readiness(promotionLevel \| slotId)` → `{ready, missing[{kind, name, message}]}`. It reuses the auto-promotion conditions, adds a non-throwing `explainPromotionRunCreation` on checks, and uses the eligible slots |
| Q21 | "Agents admitted" | A **property** on promotion levels, and a **column** on slots (which have no properties). UI, CasC and `ci.yaml` for both |
| Q22 | Enforcement | `SecurityService`: the owner's grant ∩ an allowlist. **Every unlisted function is denied by default**, including future ones |
| Q23 | Agent tokens | The owner and admins generate them; several named tokens per agent; the usual validity rules; each value shown once |
| Q24 | Stamp restriction (licensed) | An agent's run and status change on the stamp are **refused**, never "accepted but ignored" |
| Q25 | Unknown assisted status | The property carries a basis, `COMPUTED`, `SET_BY_CI` or `UNKNOWN(reason)`. The licensed condition **fails closed**: absent or unknown counts as assisted |
| Q26 | Mobile | The assisted badge on the build screen header, and actor badges in its promotion list. Nothing else |
| Q27 | Signer columns | Widened to `VARCHAR(200)` in the actor migration |
| Q28 | Readiness UI | A *What's missing* popover on unreached levels and on eligible slots. No mobile |
| Q29 | `me` for agents | `user.account.kind/owner` and `user.agentPolicy(project)`, which is null for humans. No new root query |
| Q30 | Who sees the activity views | Per build: anyone who sees the build. Per agent 🔒: the owner and admins. Global widget and page 🔒: any user, restricted to visible projects |
| Q31 | Widget | Builds, promotions and deployments by agents, plus the assisted share. `UNKNOWN` is excluded from the share and counted apart |
| Q32 | Assistants vs registered agents | Commit assistants are agent *kinds*, never linked to a registered agent. *Assisted by* (git) and *actions by* (events) are shown separately |
| Q33 | Existing CI accounts | No conversion. A pipeline is automation, not an agent |
| Q34 | Licence on running instances | A licence carrying `extension.agents` is generated and deployed on demo, and on self.dev once it runs 6.0 |

## Issues

`🔒` = gated by `extension.agents`.

| # | GitHub | Issue | Blocked by |
|---|---|---|---|
| AG0 | [#2019](https://github.com/yontrack/yontrack/issues/2019) | `CONTEXT.md`: agent, owner, agent policy, agent session, assistant, assisted change, readiness; *Actor* amended | — |
| AG1 | [#2020](https://github.com/yontrack/yontrack/issues/2020) | Change log: commit assistants, *Agent markers* settings | — |
| AG2 | [#2021](https://github.com/yontrack/yontrack/issues/2021) | Change log UI markers and count; templating options | AG1 |
| AG3 | [#2022](https://github.com/yontrack/yontrack/issues/2022) | `Build.readiness`; `explainPromotionRunCreation` | — |
| AG4 | [#2023](https://github.com/yontrack/yontrack/issues/2023) | Readiness UI: *What's missing* | AG3 |
| AG5 | [#2024](https://github.com/yontrack/yontrack/issues/2024) | Agent accounts, owner, registration, transfer, tokens, admin page | — |
| AG6 | [#2025](https://github.com/yontrack/yontrack/issues/2025) | Actor on the record: `Actor.agent/agentSession`, `ACTOR` columns, widening, `Signature.actor` | AG5 |
| AG7 | [#2026](https://github.com/yontrack/yontrack/issues/2026) | Agent policy: allowlist, *Agents admitted*, manual rule refuses agents | AG5 |
| AG8 | [#2027](https://github.com/yontrack/yontrack/issues/2027) | `user.agentPolicy`, `AGENT_POLICY` in readiness | AG3, AG7 |
| AG9 | [#2028](https://github.com/yontrack/yontrack/issues/2028) | `assistedChange` property, async computation, `build_assisted` | AG1 |
| AG10 | [#2029](https://github.com/yontrack/yontrack/issues/2029) | 🔒 `extension.agents`, module `ontrack-extension-agents`, condition "assisted ⇒ stamps" | AG3, AG9 |
| AG11 | [#2030](https://github.com/yontrack/yontrack/issues/2030) | 🔒 Stamp restriction: evidence from non-agents only | AG7, AG10 |
| AG12 | [#2031](https://github.com/yontrack/yontrack/issues/2031) | Actor keywords; actor filter on the events audit | AG6 |
| AG13 | [#2032](https://github.com/yontrack/yontrack/issues/2032) | Badges, desktop and mobile | AG6, AG9 |
| AG14 | [#2033](https://github.com/yontrack/yontrack/issues/2033) | Per-build *Agents* section | AG6, AG9 |
| AG15 | [#2034](https://github.com/yontrack/yontrack/issues/2034) | 🔒 Per-agent feed; ACL-filtered agent-actions read | AG6, AG10 |
| AG16 | [#2035](https://github.com/yontrack/yontrack/issues/2035) | 🔒 Global widget and *Latest agent actions* page | AG15 |
| AG17 | [#2036](https://github.com/yontrack/yontrack/issues/2036) | Branch build filter: assisted, actor | AG6, AG9 |
| AG18 | [#2037](https://github.com/yontrack/yontrack/issues/2037) | Demo seed | AG2, AG4, AG7, AG8, AG10–AG17 |

### Companion repositories

| # | GitHub | Issue | Blocked by |
|---|---|---|---|
| AGL | [yontrack-license#26](https://github.com/yontrack/yontrack-license/issues/26) | Licensed feature `extension.agents` | — |
| AGM | [yontrack-mcp#1](https://github.com/yontrack/yontrack-mcp/issues/1) | Context tools and session headers | AG3, AG6 |
| AGC | [yontrack-cli#84](https://github.com/yontrack/yontrack-cli/issues/84) | Readiness and context commands, session headers, `assistedChange` from CI | AG3, AG6, AG9 |
| AGS | [yontrack-skills#7](https://github.com/yontrack/yontrack-skills/issues/7) | Conduct skill | AG3, AG7, AG8 |
| AGI | [yontrack-infra-gitops#208](https://github.com/yontrack/yontrack-infra-gitops/issues/208) | A licence carrying `extension.agents` on demo.dev and self.dev | AGL, AG10 |

## Order

1. **Start at once:** AG0, AG1, AG3, AG5 and AGL.
2. **Then:**
   - AG1 unlocks AG2 and AG9;
   - AG3 unlocks AG4;
   - AG5 unlocks AG6 and AG7.
3. The licensed part starts with AG10. AG18 comes last.
4. AGI does not block AG18: without the licence, the demo shows the licence notices. Its self.dev half waits for self.dev to run 6.0.

## Settled during the breakdown

These details were not put to the session; they are the breakdown's choices:

- **One licensed module.** Every 🔒 part lives in a new `ontrack-extension-agents`. The core parts stay in the core and in `ontrack-extension-scm`.
- **Before AG7**, an agent account has no rights at all.
- **A caller-supplied signature** (ingestion, backdated builds) never sets the actor: the actor is always the authenticated one.
- **Each issue** ships its own user docs (a new *Agents* page, grown issue by issue) and its KDSL acceptance tests. AG18 is the only demo issue.
- **The events export** gains the actor columns at the end of the CSV: a compatible change of format v1.
- **`ACTOR` columns** go on `BUILDS`, `PROMOTION_RUNS`, `VALIDATION_RUN_STATUSES`, `EVENTS` and `ENV_SLOT_PIPELINE_CHANGE`, with a partial index on `EVENTS (ACTOR->>'agent')`.
