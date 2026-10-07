# Yontrack in the agentic SDLC

Outcome of the grilling session of 2026-10-02 on what Yontrack contributes when AI coding agents
become actors in software delivery. The outcome is this document and
[`executive-summary.md`](executive-summary.md). The research that fed it
is in [`research-web.md`](research-web.md) (the landscape, with sources) and
[`research-v6.md`](research-v6.md) (what the `v6` branch already has).

The issue breakdown, for milestone `6.0` under the `initiative: agentic-sdlc` label, is in
[`issues.md`](issues.md), with the decisions of the second session (2026-10-07) that settled what
the code on `main` had changed since.

## Where we start from

- **The demand is real and takes four shapes**, heard from users of Yontrack and from people who
  do not use it: *can agents push to Yontrack*, *how do I know which builds an agent made*, *can
  an agent promote*, *how do we audit it*.
- **The website already promises it.** `yontrack.com` says "Agents, same gates: agents read and
  write the record through the MCP server, and earn levels like everyone else", and "API-first,
  agent-ready". `yontrack/yontrack-mcp` exposes the GraphQL API as MCP tools, read-only by default
  with opt-in mutations; `yontrack/yontrack-skills` teaches agents how to report CI to Yontrack.
- **The product itself knows nothing about agents.** `Signature` is a time and a bare user name;
  a token always belongs to a person's account; `AUTOMATION` is a role, not a kind of actor;
  change logs read commit trailers for issue keys only; the single agent-aware line of code
  truncates commit messages "because those written by coding agents run to dozens of lines".
- **Nobody else fills the gap.** No vendor or standard records, at build or promotion level,
  which agent session produced an artefact. No delivery tool's MCP server answers "is this build
  promotable" or "what changed between what is deployed and this candidate". Governance of agent
  *pull requests* is moving into GitHub (Agent HQ, rulesets requiring an extra approval for
  unattributed agent commits) and into identity vendors (Entra Agent ID, Okta, Auth0); the
  build-and-promotion level is open.
- **The conventions are converging.** The Linux kernel and Fedora require an `Assisted-by:`
  trailer and forbid agents from signing off; Claude Code adds `Co-Authored-By` and a
  `Claude-Session:` URL; Codex adds `Co-authored-by: Codex`. Bot-account lookup finds 3% of agent
  commits in a 180M-repository census; trailers find the rest.
- **This repository is already an agentic SDLC** that Yontrack tracks: about a third of the commits
  since August 2026 carry an agent co-author trailer, and the issue lifecycle is driven by agents.
- **The 6.x direction fits.** The ledger (6.1) serialises facts with a producer, a basis and
  evidence; the harvester session settled that "agents produce configuration and rules, never
  numbers"; the scorecard is the release "where Yontrack reads its own history".

## Thesis

**Same record, same gates.** An agent is one more actor on the delivery record: it records
evidence, it earns levels, it is held to the same promotions and admission rules as everyone
else. Nothing in Yontrack changes for that to be true, except one thing: **the record must be
able to tell an agent from a human**, and name the human who answers for it.

That single capability is what turns the principle into a referee. Once every signature says who
acted and how, Yontrack becomes the place where agent-driven delivery is **auditable** (what did
agents do, on which builds, under whose responsibility), **gate-able** (which levels and slots
admit an agent, which never will), and **readable by agents** (what is deployed, what changed,
what a build still lacks) before they act.

## Scope

Three roles are in, one is out.

| Role | Yontrack's question | In |
|---|---|---|
| **(a) Agents as producers of change** — commits, branches, PRs | What did the agent change, did it earn its gates, who is accountable? | yes, first |
| **(b) Agents as operators of delivery** — triage, re-run, promote, deploy | Is the agent allowed to do this, and is the decision recorded? | yes |
| **(c) Agents as consumers of delivery data** — "what is in production, is this promotable?" | What context does an agent need before it acts? | yes |
| **(d) Yontrack using AI itself** — generated release notes, failure summaries | — | no, a later and distinct story |

**Hard boundaries.** Yontrack is not an agent runner or orchestrator, not an LLM host, not an
agent-trace observability platform, not a code reviewer, not a CI engine. It *records and gates*.
The one soft edge: Yontrack may *record* a review verdict as a validation; it never produces one.

**Persona.** The buyer is the **platform / DevEx team**, as the servants of release managers,
product owners and compliance teams, who are the ones asking the four questions above. The agent
is a user of the API, never the buyer.

## Vocabulary

To be added to `CONTEXT.md` when the first issue is opened.

- **Agent**: a registered non-human principal that acts on Yontrack with its own tokens, owned
  by an account. _Avoid_: bot, service account, automation user.
- **Owner**: the account accountable for an agent. An agent's rights are a subset of its
  owner's. _Avoid_: creator, sponsor.
- **Actor**: who signed an action, human or agent, carried on the signature. _Avoid_: user
  (already the name on a signature), principal.
- **Agent session**: the opaque reference an agent gives for the conversation or run behind an
  action, stored and linked, never fetched. _Avoid_: session (collides with login and HTTP
  sessions), trace, conversation.
- **Assisted change**: the fact that a build's commits since the previous build on the branch
  carry agent authorship markers. _Avoid_: AI-generated, agent-made (both overclaim authorship).
- **Readiness**: what a build still lacks to reach a promotion level or a slot. _Avoid_:
  promotability, eligibility.

## Decisions

### Identity

- **A registered agent is the principal.** A new `Agent` principal with a name, a vendor or
  tool, an **owner account**, its own tokens and its own permissions. It appears in the admin
  screens, is revoked as a unit, and carries policy. A bare token attribute would have been
  invisible to administrators; IdP-issued identities would have worked only for customers on
  those products.
- **IdP-issued agent identities are a future mapping source**, not a competing model: the
  document states that Yontrack's agent model is designed to accept Entra Agent ID, Okta or
  similar identities mapped onto registered agents, with no commitment on which first. This is a
  sale argument for enterprises, and the registered agent remains the canonical object.
- **An agent never holds rights its owner does not have.** Its permissions are a declared subset
  of the owner's, so demoting or revoking the owner bounds every agent they own. This is what
  makes "same gates" true rather than a slogan.
- **The signature is extended, not shadowed.** `Signature` gains the actor (kind human or agent,
  the agent, the owner) and an optional agent session (opaque id plus link). Existing rows
  default to human. It is a migration, hence a minor release, never a patch. A side "actor
  record" was rejected as a second place to look for "who"; the ledger is about to serialise
  signatures and should carry the actor from the start.
- **Agent sessions are opaque.** Yontrack stores the reference and renders the link; it never
  fetches or interprets the session.

### Provenance of the change (role a)

- **Git stays the truth; Yontrack computes and stores a summary.** Trailers and author
  identities are parsed when a change log is computed, giving each `SCMCommit` its assistants.
  The value is derived, so nothing stored can contradict a re-read of git.
- **Recognised conventions**: built-in defaults for `Co-Authored-By` with known agent addresses
  (`noreply@anthropic.com`, `codex@openai.com`, `copilot@github.com`), the kernel's
  `Assisted-by:`, `Claude-Session:`, and known bot logins (`copilot-swe-agent[bot]`,
  `devin-ai-integration[bot]`), **plus a configurable list of patterns** in CasC, because the
  conventions are still moving and enterprises run internal agents with their own addresses.
- **The fact lands on the build as a property**, `assistedChange` (agents involved, commit count,
  agent session links), computed by Yontrack at build creation from the change log since the
  branch's previous build when the SCM is configured, and settable by CI through the CLI when it
  is not. A property is a fact about the build; a validation is a judgement, and "assisted" is
  not pass or fail.
- **A dedicated event, `build_assisted`**, is posted once when the property is set with at least
  one agent, carrying the agents, the commit count and the session links as event values. No
  clearing event: a build that was assisted stays assisted; a recomputation that disagrees is a
  configuration problem, not a lifecycle. It sits in `EventFactory` beside `new_build`.
- **What drives on it**, in the usual way: notifications subscribe to `build_assisted` and
  templates expose the summary; a new **promotion condition** (`PromotionRunCheckExtension`)
  says "if the build is assisted, require validation stamp X" (a human review, a security scan),
  configured per promotion level (licensed, see below). Workflows and auto-promotion need nothing new, since the
  condition feeds them.
- **What the user sees**: a marker on each assisted commit in the change log, a count on the
  change log header, the same in the templated change log for notifications and PR bodies. A
  build filter and a search facet come later.

### Gates (role b)

- **Defaults for a registered agent:**

  | Action | Default |
  |---|---|
  | Read everything its owner can | yes |
  | Create builds, validation runs, links, properties (record evidence) | yes |
  | Promote | no, unless the promotion level admits agents |
  | Start or finish a deployment | no, unless the slot admits agents |
  | Satisfy a manual admission rule | **never** |
  | Change configuration (stamps, levels, subscriptions, CasC) | no |

  Evidence is cheap to record and safe to audit; gates are where accountability lives. "Never
  satisfy a manual approval" is GitHub's CODEOWNERS rule transposed.
- **"Agent asks, human approves"** exists for deployments only in this story: a slot pipeline
  started by an agent sits at CANDIDATE behind a manual admission rule that only a human can
  resolve, which the model already supports. Promotions stay human-triggered, the agent notifies.
  A pending *promotion request* state is deferred (see below). Delegating the approval to the
  identity provider (CIBA-style asynchronous authorization) was rejected because it moves the
  record of who approved what out of Yontrack.
- **Agent-recorded evidence counts the same by default**, with an **opt-in restriction on a
  validation stamp**: "evidence must come from a non-agent actor" (licensed, see below). The harvester principle
  ("agents produce configuration and rules, never numbers") is why the restriction exists;
  defaulting it on would break "same gates".
- **Actor is a filter on events**: subscriptions and workflows can select on the actor of the
  event (`actor:agent`, `agent:<name>`) through the existing keyword mechanism, so "notify me
  when an agent promotes" and "a human approved a deployment" need no new field.

### Context (role c)

- **Four questions nobody else answers, as core GraphQL queries** exposed as dedicated MCP tools
  and reachable from the CLI and the UI: *is this build ready for level X or slot Y, and what is
  missing* (the only new query, **readiness**); *what changed between what is in production and
  this candidate*; *what is deployed where*; *which builds of my dependency are at a given
  level*. Core rather than composed in the MCP server, because the CLI and the UI want the same
  answers.
- **An agent can read its own permissions**: a `me`-style query returns the agent identity, its
  owner, and the levels and slots that admit agents, so the conduct skill can say "ask before
  acting" instead of "try and fail". A permission the agent can read is one it respects; one it
  discovers by a 403 is one it retries.
- **A conduct skill ships in `yontrack-skills`**, agent-neutral like the harvester playbook:
  identify yourself with your agent token and session, read readiness before proposing a merge,
  record evidence as validation runs, never promote on a level that does not admit agents, start
  a deployment and stop at the manual rule rather than override it.

### Visibility

- **Badges everywhere an actor shows**: build (assisted badge, agents, session links),
  validation run and promotion run (actor in the signature, "by Claude Code, owned by damien"),
  deployment (actor in the pipeline change history), change log (per-commit marker and count),
  admin (the agents list with owner, tokens, last used, permissions).
- **Mobile UI**: badges on build and promotion only; the mobile UI reads status, not
  administration.
- **Agent activity views**, in order of value: a **per-build** section ("agents": the assisted
  change summary and every action an agent took on this build), read before GOLD; a
  **per-agent** feed under the agents admin page ("what did this agent do in the last 7 days",
  with session links), what compliance asks for; a **global** dashboard widget with counts over a
  window (builds, promotions, deployments by agents; assisted share of builds) and a page of the
  latest agent actions across projects; a **per-project** build filter on "assisted" and "actor".
- **The event log is the data source.** Events already carry a signature; the actor on it is the
  index. No dedicated agent-activity table, no derivation from the ledger. Retention of agent
  activity therefore follows event retention, and the document says so.

### Reporting and compliance

- **Scorecard readings split by actor** (lead time, frequency, success rate by human-driven versus
  agent-driven change; an "agent share" reading) fall out of the actor on the signature, but are a
  scorecard decision: a separate issue, later.
- **The ledger is the compliance trail.** Auditors following ISO 42001 or SOC 2 ask for a
  tamper-evident, exportable record of agent activity. With the actor on the fact's signature,
  "every fact produced by agent X between two dates" is a ledger *export*, a direction the ledger
  does not have yet. No second audit format.

### Licensing

Open core, with **one new licence flag** gating every paid part of the story. V6 has four flags
today (`extension.environments`, `extension.findings.native-formats`, `extension.scorecard`,
`extension.configuration`) and the ledger import is decided as a fifth; this story adds
`extension.agents`, "Agent governance", checked the way the others are.

| Core | Under `extension.agents` |
|---|---|
| Actor and agent session on the signature, registered agents, owner, agent tokens | Agent activity views: per-agent feed, global widget and page |
| Trailer parsing, change log markers, `assistedChange` and `build_assisted` | The promotion condition: "if the build is assisted, require validation stamp X" |
| Gate defaults: a level or slot admits agents or not, manual rules never | The stamp restriction: "evidence on this stamp must come from a non-agent actor" |
| Readiness and the other three queries, MCP tools, conduct skill | Ledger export by actor |
| | Scorecard readings split by actor |
| | Agent policy beyond on/off per level and slot |

The free product **records** agents in full and holds them to the same gates as everyone else:
"same gates" is not a paywall. **Ruling on** agents (conditioning a level on agent involvement,
refusing agent-recorded evidence on a stamp), reporting on them and exporting their trail are what
the platform team's sponsors pay for.

### Companion repositories

- **The MCP server is never merged into the main repository.** The main repository owns the
  contract: the agent principal, the actor on the signature, the readiness queries, the events.
  `yontrack-mcp` and `yontrack-skills` consume it on their own release trains.

## Candidate capabilities, ranked

Cheapest-and-most-visible first. Items 1 and 2 ship value without touching identity, which lets
the identity model be refined before it is built.

1. **Change log reads agent trailers**: assistants on each commit, markers and counts in the UI
   and in templates. No schema.
2. **Readiness queries and the conduct skill**: one new core query, three existing ones exposed
   as MCP tools, one skill in `yontrack-skills`.
3. **Registered agent, owner, agent tokens, actor and agent session on the signature.** The
   schema change, and the agents admin page.
4. **`assistedChange` property, `build_assisted` event, and the promotion condition** (the
   condition under `extension.agents`).
5. **Gate defaults**: levels and slots admit agents or not; manual rules never; `me` for
   agents; the stamp restriction on evidence (under `extension.agents`).
6. **Actor filters on subscriptions, badges, activity views** (per build, per agent, global
   widget, project filter).
7. **Ledger export by actor and scorecard split**, in their own sessions.

## Deferred to later sessions

1. A pending **promotion request** state: agent asks, human approves, on promotions.
2. **Scorecard readings split by actor**.
3. The **ledger export** direction and its actor filter.
4. **IdP-issued agent identities** (Entra Agent ID, Okta, SPIFFE) mapped onto registered agents.
5. **Yontrack using AI itself** (role d): generated release notes, failure summaries,
   explanations.

## What was decided against

- Agent as a token attribute only, or as an IdP-only concept.
- A side "actor record" instead of extending the signature.
- Storing "assisted" as a validation run.
- A clearing event for `build_assisted`.
- Approval of agent actions delegated to the identity provider.
- A dedicated agent-activity table or a second audit format.
- Agent-recorded evidence distrusted by default.
- Merging the MCP server into the main repository.
