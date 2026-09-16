# Harvesting delivery data into Yontrack — session handover

Carry-over brief from a design conversation held 2026-09-16. Planning only: no code was written,
no files added to the repo, no GitHub issues created or labelled. Grounded in a read of
`yontrack/yontrack` at `main` = `5.4.1-54-g5818176e96`.

Blocks 3 and 4 below were grilled the same day; the outcome is
[2026-09-scorecard.md](2026-09-scorecard.md). Block 2 was grilled the same day too; the outcome
is [2026-09-ledger.md](2026-09-ledger.md), which also records where block 1 was left. Block 1
was then grilled from there; the outcome is [2026-09-harvester.md](2026-09-harvester.md).

## The problem

Report on the state of software delivery in an organization whose CI/CD ecosystem cannot push
anything into Yontrack, and where Yontrack itself may not be installable locally. Target metrics:
lead time to deployment, test stability, security-scan maturity, lead time to remediating
high-severity CVEs — for present and past data.

Constraints set by Damien:

- Tooling built in-house, as generic as possible but configurable per organization.
- The Yontrack instance may be unreachable. Outbound HTTPS may exist; fully disconnected must also
  work.
- Sources include messy human ones (spreadsheets, wikis, release emails) as well as SCM, CI,
  trackers and scanners.
- AI agents are available to help.
- Stay high level. Harvester lives in its own repo, not this one.

## Decided so far

**Approach: the portable ledger.** Collectors run inside the organization, normalize what they
find into an append-only, versioned bundle of delivery facts with provenance, and a separate
importer replays it into Yontrack. Transport is deliberately dumb, so "outbound HTTPS" and "USB
key" are two shippers over one artifact. Two alternatives were considered and rejected as primary:
an in-org push gateway (presumes egress, nothing to inspect when numbers look wrong) and an edge
Yontrack instance that federates (real product work; held as the strategic direction if the
pattern recurs).

**Yontrack owns the ledger contract, not the harvester.** CasC is the precedent: publish a JSON
schema from the instance, accept a bundle upload. CasC is configuration-as-code; this is
history-as-code. If the format drifts to the harvester side, Yontrack ends up accepting whatever
it is sent and loses the second producer — and there will be a second producer, because
Yontrack's own CI is one.

**Agents produce configuration and rules, never numbers.** They inventory the estate, draft the
mapping from each team's local vocabulary to Yontrack structure, mine the messy human sources, and
act as reconciliation reviewers. Their output is reviewable config in git, executed
deterministically; anything not reducible to a rule enters as an explicitly estimated fact with
confidence and evidence, so every metric can be read as measured-only or measured-plus-estimated.

**Harvest by source durability, not by ease of access.** Git history, tags and PRs are close to
permanent; CI build history, test results and scanner reports are pruned in weeks or months;
deployment records frequently never existed. Historical data is decaying during the planning, so
snapshot the perishable sources early and coarsely, and reconstruct from durable ones later.

## The four blocks

| # | Block | Depends on | Release home |
|---|---|---|---|
| 1 | Ledger creation at the organization | the published format only | own repo (`yontrack-harvester`), own cadence |
| 2 | Ledger import into Yontrack (+ API changes) | nothing | 5.x minors, additive |
| 3 | CVE / vulnerability modelling | nothing | 5.x, must ship with its own reader (*) |
| 4 | Reading across the estate (indicators rewrite) | 3 for its first real data | 6.x |

(*) Overridden by the grilling session: block 3 ships in 6.x too.

Block 3 is worth building even if no organization is ever harvested, because Yontrack's own CI
already produces exactly that data and throws all but four integers away. Block 4 is the block
that decides whether the other three were worth doing. The coupling to watch is the door, not the
model: block 3 needs a way to be fed, and if built first it will either define the plumbing block
2 generalises or duplicate it — which is how GitHub ingestion ended up beside the hook framework
instead of on top of it.

## Code facts that shape the design (so they need not be rediscovered)

### Already present and reusable

- `createPromotionRun(dateTime:)` and `createValidationRun(dateTime:, dataTypeId:, data:)`
  already honour a past timestamp. `VALIDATION_RUNS` has no creation column — a run's time is
  its first status's time.
- Build names unique per branch (DB constraint) → a natural idempotency key, plus
  `createBuildOrGet`.
- Build links idempotent (unique constraint + `ON CONFLICT DO NOTHING`); run info upserts per
  entity; `getOrCreate` for promotion levels and validation stamps; `IngestionModelAccessService`
  has `getOrCreateProject` / `getOrCreateBranch` and the external-key-as-property pattern.
- `QueueProcessor` framework: routing identifier preserves per-stream ordering, sync mode when
  async is off (free dry-run and deterministic tests), `QueueRecord` audit.
- `RecordingsExtension` gives an import-run audit trail almost free; `StorageService` is the right
  home for watermarks (recordings get cleaned up).
- `ontrack-demo-seed`'s `DemoTarget` is already, nearly line for line, the ledger's operation
  set. Good validation test: can `DemoTarget` be re-expressed as "read a ledger, apply it"?

### Gaps, small and unambiguous

- `createBuild` overwrites the signature with `currentSignature`, though the service layer stores
  whatever it is given (proof: the GitHub workflow-run processor creates backdated builds with an
  arbitrary creator). Hence demo-seed's two-call backdating.
- Validation run status changes cannot be backdated → no multi-status history replay. Suggested
  resolution: the ledger asserts one terminal status per run.
- Run info's creation time is always now, but `runTime` (the duration that matters) is settable.
  Accept.
- Queue layer has no retry, backoff or dead letter: failures are logged, acked and lost.
  `specificConfiguration` is the designated hook nobody has used for this.

### Gaps that are real decisions

- Promotion runs and validation runs have no unique constraint — a re-run duplicates them.
- Promotion creation runs through `PromotionRunCheckService`, whose dependency and
  previous-promotion checks can reject an out-of-order backfill. Ordering becomes a contract.

### The genuine hole — deployments

- `SlotPipeline.start` defaults to `Time.now`; the repository's update writes only `END` and
  `STATUS`, so `START` is immutable after insert; there is no `withStart`.
- `startPipeline` cancels every active pipeline in the slot first, so chronological replay works
  and out-of-order replay silently produces cancellations.
- `forceDone` can assert a completed deployment in one call, but `isBuildEligible` still throws,
  so admission rules must be satisfiable for history that predates them.
- Nothing to query it with: environments has no metrics, no chart providers, and `findPipelines`
  has no time range.

### The hazard

Every build, promotion run and validation run posts an event; events drive notifications,
subscriptions, workflows, auto-promotion, auto-versioning, slot workflows and ES indexing. There
is no quiet mode in `StructureServiceImpl`. Backfilling years of history into a live instance
fires years of notifications. This is the failure that gets the initiative banned.

### On the CVE side

- Vulnerabilities are not a domain concept anywhere in the backend. The pattern is count → CHML →
  forget, about to be repeated a fourth and fifth time by the DAST track.
- Yontrack knows "5.3.1 had 3 HIGH" and "5.3.4 had 1 HIGH" and cannot say which two were fixed
  or when. `.trivyignore.yaml` is the only place in the repo where a CVE id and a package
  coordinate appear together — a YAML file read by bash.
- CHML's four values are the codebase's entire severity vocabulary, with no slot for UNKNOWN
  (which the image scan script therefore discards). The image scan counts one entry per
  (vulnerability, package), so the same CVE in two packages counts twice.
- Suggested model: a *finding* keyed by scanner, external id and affected component coordinate;
  an *observation* per scan of a build carrying time and scanner-asserted severity. Severity
  belongs on the observation (CVEs get re-scored, scanners disagree). Remediation time is then a
  first-seen / first-absent query — the same scan shape `PromotionLevelTTRMetrics` already
  implements for promotions, generalised off promotions.
- Storage: real tables (environments extension is the template). Explicitly not `ENTITY_DATA`
  (one overwritten blob per entity+key — the SonarQube precedent, and exactly why nothing can
  answer a question about a measurement over time) and not `ENTITY_DATA_STORE` (closer, but
  cross-project aggregation wants columns). Migrations live centrally in `ontrack-database` and
  never ship in a patch.

## The shape of 6.x — the last conclusion, and the one to carry forward

6.x is the release where Yontrack reads its own history: across projects, over time, with the
honesty to say where it cannot see. Not "the release where indicators were deleted".

**The old `ontrack-extension-indicators` module is removed completely.** It was born and died of
not being used, and it was too generic to understand. No migration is needed because there are no
users, so it can be deleted outright rather than deprecated — which removes roughly thirty
GraphQL types and a metrics exporter from the published schema, a breaking change that wants a
major version. Two diagnostic symptoms of why it died, worth not repeating: its computers
interrogate external systems (GitHub, SonarQube, Jenkins) rather than reading the delivery history
Yontrack alone owns, and `AbstractBranchIndicatorComputer` is hardcoded to a branch literally
named `master`.

**The removal is decoupled from the replacement.** The moment 6.x opens the old module can go,
whether or not the new reading surface is designed. Coupling them makes the rewrite a hostage and
guarantees the old module survives another year "until we have something to replace it with". Two
separate decisions.

**Rewritten from scratch, keeping the ideas and none of the machinery.** What survives
conceptually: append-on-update storage so the history is the time series, grouping, roll-up,
trend. What is deliberately dropped: the value-type framework (configurable value types,
configurable thresholds, configurable indicator attributes, all normalised to 0–100 compliance and
an A–F rating — a rating is a presentation choice, and making it a model primitive forces every
measure to pretend it is comparable to every other), and the imports API that provisions
categories and types from an external source, which is the purest expression of the genericity
problem: an endpoint for declaring measures nobody defined.

Rule for avoiding the same death: ship a fixed, named, opinionated set of measures — the four
actually wanted — and treat extensibility as a later concession earned by a second use case, not
the founding abstraction. Start concrete, generalise once.

### The one architectural commitment

**Readings are derived, never pushed.** v1's indicators arrived by REST update or by bespoke
computers interrogating other systems. The new module computes from the builds, promotions,
validations, deployments and findings already in the instance. That inversion is what makes 6.x
pay off the harvesting work: import an organization's history and its scorecard exists, with
nobody feeding a second system. It is also what makes the feature impossible to leave empty.

Corollary: a reading must be able to say "I don't know." v1 normalised everything to a compliance
percentage, so an unmeasured project had the same shape as a failing one — fatal for an estate
report built on harvested data, where coverage is patchy by construction and half the value is
knowing where the blind spots are. So a reading carries what it was computed from and over what
period; "unknown" is a first-class value distinct from "bad"; and the estate view can be read
measured-only or measured-plus-estimated. This is the ledger's provenance idea arriving where it
is finally visible.

### Proposed vocabulary

The old module's problem was partly that one word did four jobs — the type, the value, the
category and the roll-up were all "indicators", so nobody could say what an indicator was. Name
three things instead, the way `CONTEXT.md` layers checkpoint / delivery map / delivery map view.
Each would earn a `CONTEXT.md` entry with its own _Avoid_ list.

- **Reading** — one measurement of one project at one moment, taken by Yontrack from its own
  data. Instruments take readings; no implication of grading; the tense is built in, so a history
  of readings needs no second concept. Avoid: indicator (the dead word), metric (Micrometer's, and
  held by `MetricsChart` and `MetricsValidationDataType`), gauge (Micrometer's), score (the
  presentation).
- **Scorecard** — the set of readings for one project, and what a project page shows. Avoid:
  dashboard (Yontrack already has those; the demo reset deletes them).
- **Estate** — the group of projects read together. Avoid: portfolio (the dead module's word, and
  would import its reputation), group (belongs to accounts), label (the project-labelling
  feature).

Module: `ontrack-extension-scorecard`, beside the established delivery prefix (delivery map,
delivery metrics) without colliding with either. Feature name "delivery scorecard" reads correctly
if the family framing is wanted.

### Extended CVE support at estate scale

Block 3 gives findings identity; 6.x makes identity answer organizational questions.

- **Fan-out.** One CVE, forty projects, thirty fixed — who is left, and for how long. Today
  unaskable, because finding and component both dissolve into a count. Fan-out turns remediation
  from a per-team statistic into a coordination tool.
- **Acceptance as a modelled state, not a file.** Trivy's ignore file and the planned DAST
  suppressions already carry a statement and an expiry — right instinct, wrong place. In the
  model, "open", "accepted until March", "fixed" and "gone because the component was dropped"
  become distinguishable, expiry becomes a clock that can lapse, and the remediation reading
  stops flattering itself by counting accepted findings as closed.
- **A target.** Remediation time is only a health signal against an expectation (high severity
  closed within N days), and that expectation is the bridge into the scorecard: it turns a
  duration into a reading. Declarable per estate, not globally — a regulated group and an
  internal-tools group will not share a number.

### The other thing 6.x should resolve

There are two half-dead reporting features, not one. `ontrack-extension-delivery-metrics` computes
lead time, frequency, success rate and time-to-recovery — the right measures — but only over
promotions, and its export path goes to InfluxDB and is disabled by default, so from inside
Yontrack the numbers barely exist. It owns no tables, so it is cheap to move. If 6.x deletes
indicators and introduces readings while leaving delivery-metrics beside it exporting a parallel
set of numbers to a TSDB nobody in the organization queries, one confusion has replaced another.
Suggested resolution: delivery-metrics becomes the first family of readings, and its InfluxDB
exporter survives only as an output, not as the place the data lives.

## Open questions, in the order to grill them

Those marked ✔ were settled in [2026-09-scorecard.md](2026-09-scorecard.md); those marked ✔✔
in [2026-09-ledger.md](2026-09-ledger.md).

1. ✔✔ **Import mode.** Does a ledger import suppress events entirely, post them with a flag
   subscriptions can filter, or is import restricted to instances where the noise is accepted?
2. ✔✔ **Ledger semantics.** Does the ledger assert a build's full state at a point in time, or a
   stream of individual facts? Decides idempotency, ordering and whether corrections are
   expressible.
3. ✔✔ **Ordering contract.** Chronological order guaranteed per project and per slot, or must the
   importer tolerate arbitrary order? Promotion checks and slot cancel-actives both push toward
   guaranteeing it.
4. ✔✔ **Dedupe.** Promotion and validation runs by new unique constraints, by an external fact id, or
   not at all?
5. ✔✔ **Promotion checks on import** — do they apply, or does the ledger write below them?
6. ✔✔ **Deployments** — new backdatable recording path on `SlotService`, or model historical
   deployments as something other than slot pipelines?
7. ✔ **Severity vocabulary.** Adopt CHML, or a scanner-neutral scale with a mapping, and where
   does UNKNOWN go?
8. ✔ **Component identity.** Opaque coordinate (purl) or an entity — and how firmly is an SBOM
   refused for now? Note the backend has no third-party component notion at all; build links and
   auto-versioning relate only Yontrack projects.
9. ✔ **Suppression and acceptance** in the model, or left in per-scanner files?
10. ✔ **Reading before or after the ledger** — the ledger without a reading surface produces data
    nobody can read.
11. ✔ **6.x new:** does `ontrack-extension-delivery-metrics` fold into the scorecard module?
12. ✔ **6.x new:** is the removal of `ontrack-extension-indicators` its own item, landed as soon
    as 6.x opens, independent of the rewrite?
13. ✔ **6.x new:** are reading / scorecard / estate the words, and do they get `CONTEXT.md`
    entries before implementation starts?

## Process notes for the next session

- Suggested next move: one grilling doc per block rather than one for the whole thing — they have
  different audiences, and only blocks 3 and 4 share a decision surface.
- Nothing above has been written into the repo. Sequencing and issue breakdown were deliberately
  deferred until the block shape is agreed.
