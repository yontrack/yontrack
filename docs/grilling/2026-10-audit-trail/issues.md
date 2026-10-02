# Audit trail — issue breakdown

Breakdown of [README.md](README.md) into agent-sized issues, created on 2026-10-02.

The 6.0 issues carry `initiative: v6-audit-trail`, `status:todo` and `ready-for-agent` (plus `type: enhancement`
in `yontrack`, `enhancement` elsewhere; AT12 also `security`). AT24–AT30 are 6.x placeholders on `6.1` with
`status:tospec` only, to be specified in their own grilling sessions. Dependencies are recorded as native GitHub
dependencies, across repositories too, and as a *Depends on* line at the top of each body.

Milestone `6.0` exists in `yontrack` only; the issues in the other repositories have none (as F17 had none).

| # | GitHub | Issue | Repo / base | Milestone | Depends on |
|---|--------|-------|-------------|-----------|------------|
| AT1 | [#1953](https://github.com/yontrack/yontrack/issues/1953) | CONTEXT.md: trail, entry, endorsement, evidence | `yontrack` / `v6` | 6.0 | — |
| AT2 | [#1954](https://github.com/yontrack/yontrack/issues/1954) | Audit trail module: licence, entry table, canonical JSON, hash format v1, append | `yontrack` / `v6` | 6.0 | AT1 |
| AT3 | [#1955](https://github.com/yontrack/yontrack/issues/1955) | Audit trail: instance key and per-entry endorsement | `yontrack` / `v6` | 6.0 | AT2 |
| AT4 | [#1956](https://github.com/yontrack/yontrack/issues/1956) | Core: structured actor in the security context (token name, JWT iss/sub, system reason) | `yontrack` / `v6` | 6.0 | — |
| AT5 | [#1957](https://github.com/yontrack/yontrack/issues/1957) | Core: events for build mutations that post none | `yontrack` / `v6` | 6.0 | — |
| AT6 | [#1958](https://github.com/yontrack/yontrack/issues/1958) | Audit trail: event listener writing every entry type | `yontrack` / `v6` | 6.0 | AT3, AT4, AT5 |
| AT7 | [#1959](https://github.com/yontrack/yontrack/issues/1959) | Audit trail: entries for cascading deletions | `yontrack` / `v6` | 6.0 | AT6 |
| AT8 | [#1960](https://github.com/yontrack/yontrack/issues/1960) | Audit trail: verification, GraphQL and JSON export | `yontrack` / `v6` | 6.0 | AT6 |
| AT9 | [#1961](https://github.com/yontrack/yontrack/issues/1961) | Audit trail: daily verification job and trail.verification.failed | `yontrack` / `v6` | 6.0 | AT8 |
| AT10 | [#1962](https://github.com/yontrack/yontrack/issues/1962) | MinIO in the dev, integration-test and KDSL stacks | `yontrack` / `v6` | 6.0 | — |
| AT11 | [#1963](https://github.com/yontrack/yontrack/issues/1963) | Evidence storage: S3 configuration, status, health and global messages | `yontrack` / `v6` | 6.0 | AT3, AT10 |
| AT12 | [#1964](https://github.com/yontrack/yontrack/issues/1964) | Evidence: model, upload, evidence.attached, listing and safe download | `yontrack` / `v6` | 6.0 | AT6, AT11 |
| AT13 | [#1965](https://github.com/yontrack/yontrack/issues/1965) | Evidence deletion: EvidenceDelete, evidence.deleted, blob sweep, metrics | `yontrack` / `v6` | 6.0 | AT12 |
| AT14 | [#1966](https://github.com/yontrack/yontrack/issues/1966) | KDSL: audit trail and evidence, acceptance tests | `yontrack` / `v6` | 6.0 | AT8, AT13 |
| AT15 | [#1967](https://github.com/yontrack/yontrack/issues/1967) | UI: build audit trail page (cuttable) | `yontrack` / `v6` | 6.0 | AT8 |
| AT16 | [#1968](https://github.com/yontrack/yontrack/issues/1968) | UI: evidence on the validation run page (cuttable) | `yontrack` / `v6` | 6.0 | AT13 |
| AT17 | [#1969](https://github.com/yontrack/yontrack/issues/1969) | Audit trail: demo tampering switch | `yontrack` / `v6` | 6.0 | AT8, AT11 |
| AT18 | [#1970](https://github.com/yontrack/yontrack/issues/1970) | Demo seed: audit-trail-demo and audit-trail-tampered | `yontrack` / `v6` | 6.0 | AT14, AT15, AT16, AT17 |
| AT19 | [#1971](https://github.com/yontrack/yontrack/issues/1971) | User documentation: audit trail and evidence | `yontrack` / `v6` | 6.0 | AT9, AT13, AT17 |
| AT20 | [yontrack-cli#83](https://github.com/yontrack/yontrack-cli/issues/83) | CLI: validate --evidence and audit-trail verify | `yontrack-cli` / `main` | — | AT8, AT12 |
| AT21 | [yontrack-chart#121](https://github.com/yontrack/yontrack-chart/issues/121) | Chart: audit trail storage values and optional MinIO | `yontrack-chart` / `main` | — | — |
| AT22 | [yontrack-infra-gitops#170](https://github.com/yontrack/yontrack-infra-gitops/issues/170) | v6.dev: audit trail storage on Spaces, licence and demo tampering | `yontrack-infra-gitops` / `main` | — | AT21, AT23, AT17 |
| AT23 | [yontrack-infra-bootstrap#13](https://github.com/yontrack/yontrack-infra-bootstrap/issues/13) | Spaces buckets for audit trail evidence (v6 and demo) | `yontrack-infra-bootstrap` / `main` | — | — |
| AT24 | [#1972](https://github.com/yontrack/yontrack/issues/1972) | Audit trail: sealing, audit retention and export | `yontrack` / `v6` | 6.1 | — |
| AT25 | [#1973](https://github.com/yontrack/yontrack/issues/1973) | Audit trail: RFC 3161 timestamping and transparency logs | `yontrack` / `v6` | 6.1 | — |
| AT26 | [#1974](https://github.com/yontrack/yontrack/issues/1974) | Audit trail: instance trail and key rollover | `yontrack` / `v6` | 6.1 | — |
| AT27 | [#1975](https://github.com/yontrack/yontrack/issues/1975) | CI OIDC federation: authenticate CI tokens and record their identity | `yontrack` / `v6` | 6.1 | — |
| AT28 | [#1976](https://github.com/yontrack/yontrack/issues/1976) | Evidence: sandboxed HTML serving | `yontrack` / `v6` | 6.1 | — |
| AT29 | [#1977](https://github.com/yontrack/yontrack/issues/1977) | Audit trail: auditor role | `yontrack` / `v6` | 6.1 | — |
| AT30 | [#1978](https://github.com/yontrack/yontrack/issues/1978) | Evidence: pre-signed uploads and pull from external tools | `yontrack` / `v6` | 6.1 | — |
| AT31 | [yontrack-infra-gitops#171](https://github.com/yontrack/yontrack-infra-gitops/issues/171) | Demo: audit trail at the 6.0 cutover | `yontrack-infra-gitops` / `main` | — | AT22, AT18 |

## Cut line

AT15 and AT16 (UI) are *cuttable*: if time runs short, 6.0 ships the trail and the evidence API without them
(`v6-scope.md` §7). AT18 (demo seed) depends on them and would then show the trail through the API only.

## Prerequisites outside the breakdown

- [yontrack-license#2](https://github.com/yontrack/yontrack-license/issues/2) makes the licensed feature `extension.audit-trail` available; it blocks AT22 and AT31 (native dependency), which then need a licence carrying it for v6.dev and for demo.
- AT23 is applied through the bootstrap repository's OpenTofu workflow before AT22.

---

## AT1 ([#1953](https://github.com/yontrack/yontrack/issues/1953)) — CONTEXT.md: trail, entry, endorsement, evidence

Add the four terms to `CONTEXT.md` before any code names them, each with its _Avoid_ list:

- **Trail** — the append-only, hash-chained record of one build's story. Avoid: ledger (the import format), log, history, journal.
- **Entry** — one element of a trail. Avoid: event, fact, record.
- **Endorsement** — the instance key's Ed25519 signature over one entry's hash. Avoid: checkpoint (a delivery-map term), signature alone (`model.structure.Signature`).
- **Evidence** — a file attached to a validation run, referenced by an entry. Avoid: attachment, artifact, document.

Mention that *Seal* and *Instance trail* are reserved for 6.x.

- Docs-only commit, `[skip ci]`.
- Done when `CONTEXT.md` carries the four entries.

## AT2 ([#1954](https://github.com/yontrack/yontrack/issues/1954)) — Audit trail module: licence, entry table, canonical JSON, hash format v1, append

**Depends on:** AT1 ([#1953](https://github.com/yontrack/yontrack/issues/1953))

The foundation: no listener yet, no endorsement yet.

- New module `ontrack-extension-audit-trail` with its `ExtensionFeature`.
- Licensed feature `extension.audit-trail` (`LicensedFeatureProvider`, an `AuditTrailLicense` modelled on `EnvironmentsLicense`), boolean, no licence data. On in the dev profile and the test stacks.
- Migration creating `build_trail_entry` (`id`, `build_id` FK `ON DELETE CASCADE`, `seq` unique per build, `schema_version`, `type`, `payload`, `actor`, `time`, `prev_hash`, `hash`).
- **Canonical JSON**: our own Kotlin canonicaliser over Jackson trees, strictly RFC 8785 on strings, objects, arrays, booleans, null and integers within ±2⁵³; any other number is rejected with a clear error. No third-party JCS library (`io.github.erdtman:java-json-canonicalization` is unmaintained).
- **Hash format v1**: SHA-256 (lowercase hex) of the canonical envelope `{schemaVersion, seq, type, time, actor, prevHash, payload}`; `time` ISO-8601 UTC, millisecond precision; `prevHash` null for seq 1. The format is a compatibility contract: document it in KDoc on the hashing class.
- `TrailService.append(build, type, payload)`: same transaction as the caller, under `pg_advisory_xact_lock(<audit-trail class id>, build_id)`; computes `seq`, `prev_hash`, `hash`; writes nothing when the licence is off; writes `trail.opened {build…, buildCreatedAt, partial: true}` as seq 1 when the build predates its trail. The actor is passed in by the caller for now (structured actor arrives with AT4).
- Shared test-vector fixture `ontrack-extension-audit-trail/src/test/resources/audit-trail/test-vectors/*.json` (inputs, canonical bytes, hashes), referenced by the CLI (AT20).
- Metric: append timer.
- Tests: canonicaliser unit tests against the RFC 8785 vectors of the accepted subset plus rejection cases; hashing unit tests against the fixture; IT for concurrent appends on one build (no fork, contiguous `seq`), for the licence-off no-op, for `trail.opened`, and for the cascade on build deletion.
- No UI, no mobile impact. No demo yet (AT18).

## AT3 ([#1955](https://github.com/yontrack/yontrack/issues/1955)) — Audit trail: instance key and per-entry endorsement

**Depends on:** AT2 ([#1954](https://github.com/yontrack/yontrack/issues/1954))

- Instance Ed25519 key (JDK built-in) stored in `ConfidentialStore` under `audit-trail.ed25519`; generated on first use if absent. `key_id` = first 16 hex characters of SHA-256 over the public key.
- Migration creating `build_trail_endorsement` (`entry_id`, `key_id`, `signature`, `time`).
- `TrailService.append` endorses every entry: Ed25519 over the entry's 32-byte hash.
- **Key unavailable** (read-only `secret` store with no key): entries are still written, unendorsed. Expose a `keyStatus` (OK / not provisioned) for the status page and global message (AT11).
- `GET /rest/extension/audit-trail/keys` and a GraphQL root field returning `{keyId, algorithm, publicKey (PEM)}`.
- Tests: unit tests for signing and `key_id`; IT with a writable store (key generated once, reused across restarts of the service bean), and with a read-only store (entries unendorsed, status reported).
- No UI, no mobile impact.

## AT4 ([#1956](https://github.com/yontrack/yontrack/issues/1956)) — Core: structured actor in the security context (token name, JWT iss/sub, system reason)

The audit trail records who did what; today only an account name survives authentication.

- A structured actor, available from `SecurityService` at any time: `{account, via: ui|token|jwt|webhook|system, tokenName?, jwt?: {iss, sub}, system?, onBehalfOf?}`.
- `TokenSecurityFilter` / `TokensServiceImpl.useTokenForSecurityContext` keep the token's **name** (never its value) in the context.
- `WebSecurityFilter.accountFromJwt` keeps `iss` and `sub`.
- `asAdmin` / run-as paths carry a `system` reason (auto-promotion, SonarQube, GitHub ingestion, …) and keep the original user as `onBehalfOf`; the GitHub webhook sets `via: webhook`.
- Queue dispatch (`QueueDispatcherImpl`, `AuthenticationStorageServiceImpl`) carries the structured actor across, or degrades explicitly to `{account, via: system}`.
- Accepting CI OIDC tokens without an email is **out of scope** (6.x).
- Tests: unit and IT per authentication path (UI session, token, JWT, run-as, queue).
- No UI, no mobile impact.

## AT5 ([#1957](https://github.com/yontrack/yontrack/issues/1957)) — Core: events for build mutations that post none

Seven mutations change a build's story without posting an event. Add a notifiable event for each, following the existing naming conventions (`NEW_…`, `DELETE_…`, `UPDATE_…`):

- build link added and deleted — `createBuildLink`, `deleteBuildLink`, bulk `editBuildLinks`, GraphQL `linksBuild`/`deleteBuildLinks`, GitHub links ingestion (one event per link);
- run info set and deleted — `RunInfoServiceImpl.setRunInfo` / `deleteRunInfo`, on builds and on validation runs;
- validation run deleted — `deleteValidationRun` (used by GitHub "validate data" ingestion);
- validation run data updated — `ValidationRunServiceImpl.updateValidationRunData` (SonarQube);
- deployment deleted — `SlotServiceImpl.deleteDeployment`;
- slot workflow overridden — `SlotWorkflowServiceImpl.overrideSlotWorkflowInstance`.

Each event carries enough context for a listener to identify the build and the change (ids, names, and for run info and data the new values). Events are posted in the transaction of the change, like the others. Their docs are generated (`EventTypesDocumentationIT`).

- Tests: one IT per event, asserting it is posted with its context.
- No UI, no mobile impact.

## AT6 ([#1958](https://github.com/yontrack/yontrack/issues/1958)) — Audit trail: event listener writing every entry type

**Depends on:** AT3 ([#1955](https://github.com/yontrack/yontrack/issues/1955)), AT4 ([#1956](https://github.com/yontrack/yontrack/issues/1956)), AT5 ([#1957](https://github.com/yontrack/yontrack/issues/1957))

- A synchronous `EventListener` mapping events to entries for the closed list of schema version 1 in the README (all types except `evidence.*`, which come with AT12/AT13, and the cascade reasons, AT7).
- Payloads: stable identifiers and small values, never entity dumps. `validation.run` / `validation.data` carry the SHA-256 of the canonical run data, not the data. `build.updated` carries old and new values. `property.*` carry the property type and canonical value.
- **Actor** from the structured actor of AT4 at append time, never `event.signature`.
- **Time** from the server clock. Caller-supplied times and users go into the payload as `claimed: {time, user}` (validation run signature, build creation, slot `dateTime`, GitHub `sender.login`).
- Deployment entries for every pipeline transition, rule data, rule override, workflow override and deletion.
- Tests: **one IT per entry type**, driving the real mutation and asserting type, payload, actor, `claimed` and chain; an IT for a multi-step GraphQL mutation (several entries); an IT for a workflow-node-driven deployment transition (entry written in the node's own transaction); an IT showing nothing is written with the licence off.
- No UI, no mobile impact.

## AT7 ([#1959](https://github.com/yontrack/yontrack/issues/1959)) — Audit trail: entries for cascading deletions

**Depends on:** AT6 ([#1958](https://github.com/yontrack/yontrack/issues/1958))

Deleting a validation stamp or a promotion level wipes runs on every build of the branch; deleting a build removes the links *other* builds hold to it. None of that is visible per build today.

- A narrow core extension point called just before those three deletions, in their transaction, listing the affected builds and items.
- The trail appends, to each affected build, `validation.deleted`, `promotion.removed` or `link.removed` with `reason: cascade/<cause>` (`validation-stamp-deleted`, `promotion-level-deleted`, `target-build-deleted`).
- Branch and project deletions need nothing.
- Tests: IT per cascade, with several builds; a timing check with a few thousand runs to keep the cost visible.
- No UI, no mobile impact.

## AT8 ([#1960](https://github.com/yontrack/yontrack/issues/1960)) — Audit trail: verification, GraphQL and JSON export

**Depends on:** AT6 ([#1958](https://github.com/yontrack/yontrack/issues/1958))

- Verification of a build's trail, for every supported `schema_version`: recompute each hash and the chain, check each endorsement against its `key_id`. Result: `chainIntact`, `endorsementsValid`, `firstBrokenSeq`, `partial`, `unendorsedFromSeq`; with `includeEvidence: true` also `missingEvidence` and `alteredEvidence` (wired once AT12 exists — return empty lists until then, and add the check in AT12).
- GraphQL: `build.auditTrail { entries, endorsements, verification(includeEvidence: Boolean = false) { … } }`, gated by project view and the licence (readable after the licence lapses — see README *Licence*).
- **JSON export**, self-sufficient: entries, endorsements, public key(s), `schema_version`; REST download endpoint. Its shape is documented (AT19) and verified offline by the CLI (AT20). Add an exported trail to the shared test-vector fixture.
- Tests: unit tests on a tampered payload, a tampered hash, a reordered entry, a bad endorsement, an unendorsed tail, a partial trail; IT through GraphQL and the export.
- No UI here (AT15), no mobile impact.

## AT9 ([#1961](https://github.com/yontrack/yontrack/issues/1961)) — Audit trail: daily verification job and trail.verification.failed

**Depends on:** AT8 ([#1960](https://github.com/yontrack/yontrack/issues/1960))

- A daily job (`JobProvider`, modelled on `WebhookDeliveriesCleanupJob`) verifying the trails of builds that gained entries since its last run; evidence is not re-hashed.
- Notifiable event `trail.verification.failed` (project, branch, build, `firstBrokenSeq`, reason), registered like `FindingsEvents`.
- Metric: verification failures counter, documented via `@MetricsDocumentation`.
- Tests: IT with a tampered row (event posted, metric incremented), and an IT showing untouched builds are not re-verified.
- No UI, no mobile impact.

## AT10 ([#1962](https://github.com/yontrack/yontrack/issues/1962)) — MinIO in the dev, integration-test and KDSL stacks

- A MinIO service, with its bucket created at start, in the dev stack (`scripts/dev-stack.sh`), the IT stack and the KDSL stack, on slot-offset ports recorded in each stack's `instance.env`, wired through `buildSrc` as `.claude/rules/buildsrc.md` describes. Not Testcontainers.
- The application in each stack is configured with the `ontrack.extension.audit-trail.storage.*` properties pointing at it (the properties themselves are declared in AT11 — until then, only the service and its ports).
- Pick a MinIO image that is still maintained and published at implementation time, and pin it; say which in the commit.
- Update `DEVELOPMENT.md` for the new service and port.
- Done when the three stacks start with MinIO reachable on their recorded port, from two worktrees at once.
- No UI, no mobile impact.

## AT11 ([#1963](https://github.com/yontrack/yontrack/issues/1963)) — Evidence storage: S3 configuration, status, health and global messages

**Depends on:** AT3 ([#1955](https://github.com/yontrack/yontrack/issues/1955)), AT10 ([#1962](https://github.com/yontrack/yontrack/issues/1962))

- Config properties `ontrack.extension.audit-trail.storage.*`: endpoint, bucket, region, path-style, access key, secret key, size cap (default 50 MB). Documented through the generated config docs.
- S3 client on the AWS SDK v2, built with `requestChecksumCalculation = WHEN_REQUIRED` and `responseChecksumValidation = WHEN_REQUIRED`; must work against MinIO (path-style) and DigitalOcean Spaces (virtual-hosted).
- Storage status: not configured / unreachable / OK, from a probe every minute, cached.
- Spring health contributor: `DEGRADED` when the licence is on and storage is not OK — never `DOWN`.
- `GlobalMessageExtension` (template `LicenseMessage`), only while the licence is on: storage not configured → WARNING "Audit trail is enabled but no evidence storage is configured: evidence cannot be attached."; storage unreachable → ERROR; instance key not provisioned (AT3) → ERROR.
- **Audit trail status** admin page, read-only, gated by global settings through an authorization contributor: licence on/off, storage state, key id and public key. Entry in the user menu like the other admin pages.
- Tests: unit tests for the messages; IT against the IT stack's MinIO (OK), a wrong endpoint (unreachable) and no configuration; UI test for the status page.
- Mobile: no mobile impact — admin page, registered in `mobileRoutes.js` as desktop-only.

## AT12 ([#1964](https://github.com/yontrack/yontrack/issues/1964)) — Evidence: model, upload, evidence.attached, listing and safe download

**Depends on:** AT6 ([#1958](https://github.com/yontrack/yontrack/issues/1958)), AT11 ([#1963](https://github.com/yontrack/yontrack/issues/1963))

- Migration creating `evidence` (`validation_run_id` FK cascade, `file_name`, `media_type`, `size`, `sha256`, `collected_at` server time, `collected_by` actor, `source` {tool, version, url}, `external_digest`, `deleted_at`).
- REST multipart `POST` on a validation run (file + metadata), gated by validation-run creation and the licence; streamed to `blobs/<sha256>` while hashing; server hash authoritative; `external_digest` mismatch → reject and delete the object; size cap aborts and deletes; Spring multipart limits raised for this endpoint. Refusals use typed errors (`audit-trail.evidence.storage-not-configured`, `…-unreachable`, `…-too-large`, `…-digest-mismatch`).
- Appends `evidence.attached` (metadata + `sha256`) to the build's trail in the same transaction; notifiable event `evidence.attached`.
- GraphQL: `validationRun.evidence` list (metadata, download URL), readable after the licence lapses.
- Download endpoint: inline **only** for PDF, JSON, plain text, PNG, JPEG, GIF, WebP **and** when the magic bytes agree with the type; everything else — HTML and SVG included — `Content-Disposition: attachment`; `X-Content-Type-Options: nosniff` always.
- Wire `includeEvidence` into verification (AT8): blob existence and re-hash.
- **Security review** (this issue carries `security`): stored XSS through HTML/SVG, content-type spoofing, path traversal in `file_name`, size exhaustion, and the demo tampering switch (AT17) being off by default. Record the review in the commit message.
- Tests: IT upload/list/download against MinIO, each refusal, spoofed media types, verification with a missing and an altered blob.
- No UI here (AT16), no mobile impact.

## AT13 ([#1965](https://github.com/yontrack/yontrack/issues/1965)) — Evidence deletion: EvidenceDelete, evidence.deleted, blob sweep, metrics

**Depends on:** AT12 ([#1964](https://github.com/yontrack/yontrack/issues/1964))

- Project function `EvidenceDelete`, granted to project owner and above (not to validation-run writers); role grants test using `Roles.*`.
- Single deletion: row kept with `deleted_at`, blob removed only when no other row references the digest, `evidence.deleted` appended.
- Daily sweep job: deletes `blobs/<sha>` objects with no referencing row and older than 24 h.
- Metrics: evidence count and size per project.
- Tests: IT for deletion (entry written, blob kept when shared, removed otherwise), permission refusals, sweep (orphan removed, recent orphan kept, referenced kept), cascade with run and build deletion.
- No UI here (AT16), no mobile impact.

## AT14 ([#1966](https://github.com/yontrack/yontrack/issues/1966)) — KDSL: audit trail and evidence, acceptance tests

**Depends on:** AT8 ([#1960](https://github.com/yontrack/yontrack/issues/1960)), AT13 ([#1965](https://github.com/yontrack/yontrack/issues/1965))

- KDSL: read a build's trail, verify it (with and without evidence), download the JSON export, upload evidence to a validation run (`Connector.uploadFile`), and a convenience that validates then attaches. Throws when evidence is refused.
- Acceptance tests (`ontrack-kdsl-acceptance`, KDSL stack with MinIO from AT10): full build story → trail verifies; evidence upload, download, deletion; a refused upload with storage unconfigured; reading after the licence lapses if the stack allows switching it.
- No UI, no mobile impact.

## AT15 ([#1967](https://github.com/yontrack/yontrack/issues/1967)) — UI: build audit trail page (cuttable)

**Depends on:** AT8 ([#1960](https://github.com/yontrack/yontrack/issues/1960))

*Cuttable*: if time runs short, 6.0 ships without it.

- Sub-page `/build/[id]/audit-trail`, reached from an "Audit trail" command in the build command bar (like Links), shown only when authorized (licence folded into authorizations server-side).
- Header: verification badge (intact / partial / unendorsed from seq N / broken at seq N) with a "verify including evidence" action.
- Table of entries (seq, time, type, actor, summary), expandable payload; JSON export download.
- **Mobile**: register the route in `mobileRoutes.js` as desktop-only (+ its test); no mobile screen in 6.0, because the mobile UI has no validation run page and the trail is an audit tool, not a phone one.
- Tests: Jest for the view model, Playwright UI test on an intact and a broken trail.

## AT16 ([#1968](https://github.com/yontrack/yontrack/issues/1968)) — UI: evidence on the validation run page (cuttable)

**Depends on:** AT13 ([#1965](https://github.com/yontrack/yontrack/issues/1965))

*Cuttable*: if time runs short, 6.0 ships without it.

- Conditional **Evidence** cell on the validation run page (as Findings does, with its own stored-layout id when shown): list with name, type, size, SHA-256, collected at/by, source; inline preview for allow-listed types; download; upload for validation-run creators; delete for `EvidenceDelete`.
- "Evidence storage is not configured" (or unreachable) in place of upload and list.
- Mobile: no mobile impact — the mobile UI has no validation run page and sends `/validationRun/` to the desktop interstitial.
- Tests: Jest for the cell, Playwright upload/preview/download/delete.

## AT17 ([#1969](https://github.com/yontrack/yontrack/issues/1969)) — Audit trail: demo tampering switch

**Depends on:** AT8 ([#1960](https://github.com/yontrack/yontrack/issues/1960)), AT11 ([#1963](https://github.com/yontrack/yontrack/issues/1963))

The demo needs a tampered trail and its seed only uses the API.

- `ontrack.extension.audit-trail.demo-tampering.enabled`, default `false`, documented as never for production.
- When on: a global-admin REST endpoint rewriting one entry's payload (build, seq, new payload) without recomputing anything; and a permanent ERROR global message: "This instance allows trail tampering for demonstration: its trails prove nothing."
- When off: the endpoint does not exist (404), not merely forbidden.
- Tests: IT with the switch off (404) and on (rewrite, verification broken at that seq, message shown).
- No UI beyond the global message, no mobile impact.

## AT18 ([#1970](https://github.com/yontrack/yontrack/issues/1970)) — Demo seed: audit-trail-demo and audit-trail-tampered

**Depends on:** AT14 ([#1966](https://github.com/yontrack/yontrack/issues/1966)), AT15 ([#1967](https://github.com/yontrack/yontrack/issues/1967)), AT16 ([#1968](https://github.com/yontrack/yontrack/issues/1968)), AT17 ([#1969](https://github.com/yontrack/yontrack/issues/1969))

- **`audit-trail-demo`**: a release build created through a token named `ci-demo`; commit and release properties; links to two dependency builds; six validations (one FAILED → PASSED with a comment); promotions BRONZE → SILVER → GOLD; a deployment through two slots with one overridden admission rule. Evidence: a Trivy PDF, a CycloneDX JSON SBOM, an HTML ZAP report (download-only), a JUnit text summary, a PNG screenshot, one deleted evidence. A validation stamp deleted to show `validation.deleted reason: cascade` on two builds.
- **`audit-trail-tampered`**: one build, about six entries, one PDF, entry 4 tampered through the switch (AT17); description says it is deliberately tampered.
- Without storage, skip evidence and log it; without the tampering switch, skip the second project and log it.
- Update `doc/dev-guide/demo-seed.md` ("What the target instance must have").
- Mobile: no mobile impact (data only).

## AT19 ([#1971](https://github.com/yontrack/yontrack/issues/1971)) — User documentation: audit trail and evidence

**Depends on:** AT9 ([#1961](https://github.com/yontrack/yontrack/issues/1961)), AT13 ([#1965](https://github.com/yontrack/yontrack/issues/1965)), AT17 ([#1969](https://github.com/yontrack/yontrack/issues/1969))

A page in `ontrack-docs` (and its nav entry), covering:

- what the feature gives (integrity and origin) and **what 6.0 does not give** (no TSA, no sealing, no retention independent of builds, licence-off gaps not detected, no CI OIDC federation) — with a release-note paragraph saying the same;
- licence behaviour, including partial trails and lapsed licences;
- storage configuration, S3-compatible providers (AWS S3, MinIO, DigitalOcean Spaces — no Object Lock there), backing the bucket up, the global messages and the status page;
- the instance key in `ConfidentialStore` and the unendorsed mode;
- the entry types, the actor object, `claimed`;
- the hash format v1 and the verification algorithm, precise enough to re-implement, and the JSON export shape;
- evidence upload (REST, KDSL, CLI), display rules, deletion and `EvidenceDelete`;
- the demo tampering switch and why it must stay off.

Not `[skip ci]`: the `docs` job builds the site.

## AT20 ([yontrack-cli#83](https://github.com/yontrack/yontrack-cli/issues/83)) — CLI: validate --evidence and audit-trail verify

**Depends on:** AT8 ([#1960](https://github.com/yontrack/yontrack/issues/1960)), AT12 ([#1964](https://github.com/yontrack/yontrack/issues/1964))

- `validate … --evidence <file>` (repeatable; optional tool, tool version and source URL): uploads after validating. Fails the step when the upload is refused (missing evidence is an audit gap); `--evidence-optional` turns that into a warning.
- `audit-trail verify <file.json>`: verifies a JSON export offline — canonical JSON with the `cyberphone` reference Go implementation, SHA-256 chain, Ed25519 endorsements against the embedded public key(s). Human-readable report, non-zero exit on any break.
- Tests reuse the shared test vectors and exported trail of `yontrack` (`ontrack-extension-audit-trail/src/test/resources/audit-trail/test-vectors/`), copied with their source commit noted.
- Docs in the CLI README.

## AT21 ([yontrack-chart#121](https://github.com/yontrack/yontrack-chart/issues/121)) — Chart: audit trail storage values and optional MinIO

- Values `auditTrail.storage.{endpoint, bucket, region, pathStyle, maxSize, existingSecret, accessKeyKey, secretKeyKey}` mapped to the `ontrack.extension.audit-trail.storage.*` properties; credentials only from an existing secret.
- Optional MinIO (subchart or templates) with a PVC and its bucket created, **off by default**; when on, the storage values default to it.
- Pick a MinIO image that is still maintained and published at implementation time.
- Docs in the chart README.

## AT22 ([yontrack-infra-gitops#170](https://github.com/yontrack/yontrack-infra-gitops/issues/170)) — v6.dev: audit trail storage on Spaces, licence and demo tampering

**Depends on:** AT21 ([yontrack-chart#121](https://github.com/yontrack/yontrack-chart/issues/121)), AT23 ([yontrack-infra-bootstrap#13](https://github.com/yontrack/yontrack-infra-bootstrap/issues/13)), AT17 ([#1969](https://github.com/yontrack/yontrack/issues/1969))

- v6.dev reads the Spaces credentials through an ExternalSecret from the Vault path of the bootstrap issue, and sets the chart's `auditTrail.storage.*` values to its bucket (virtual-hosted, DO region endpoint). No MinIO.
- `ontrack.extension.audit-trail.demo-tampering.enabled=true` on v6.dev only.
- **Prerequisite:** the licensed feature from [yontrack-license#2](https://github.com/yontrack/yontrack-license/issues/2), and a licence carrying `extension.audit-trail` for v6.dev.
- Done when v6.dev's audit trail status page shows storage OK and the seeded demo shows both projects.

## AT23 ([yontrack-infra-bootstrap#13](https://github.com/yontrack/yontrack-infra-bootstrap/issues/13)) — Spaces buckets for audit trail evidence (v6 and demo)

Following the `modules/pg-backups` pattern:

- Two private `digitalocean_spaces_bucket`s, one for v6.dev and one for demo (demo runs 5.x until the 6.0 cutover, but preparing its bucket costs nothing). No ACL, no CORS, `force_destroy` left at `false` (application data, like `rpg-storage`).
- A `digitalocean_spaces_key` **scoped to those two buckets** (`grant` with `readwrite`), not account-wide.
- Credentials in Vault KV for an ExternalSecret to project.
- Outputs: bucket names, endpoint URL, Vault path.

## AT24 ([#1972](https://github.com/yontrack/yontrack/issues/1972)) — Audit trail: sealing, audit retention and export

Seals in S3 surviving build deletion, `audit_seal` table, audit retention per project, seal on demand / promotion / purge, export ZIP with manifest and DSSE statements, ASiC-E option, project-level "Audit trails" page (`research.md` §4 Level 3, §6).

## AT25 ([#1973](https://github.com/yontrack/yontrack/issues/1973)) — Audit trail: RFC 3161 timestamping and transparency logs

Optional configurable TSA on endorsements or seals, Rekor / in-toto publishing, ERS-style re-timestamping (`research.md` §4 Level 2).

## AT26 ([#1974](https://github.com/yontrack/yontrack/issues/1974)) — Audit trail: instance trail and key rollover

Instance-level trail of audit settings changes, licence transitions, key rollovers and deletions (`build.deleted`); key rollover procedure, new key endorsed by the old one.

## AT27 ([#1975](https://github.com/yontrack/yontrack/issues/1975)) — CI OIDC federation: authenticate CI tokens and record their identity

Accept GitHub Actions / GitLab OIDC tokens without an email under a trust policy (issuer, repository, workflow claims → account), and record the CI identity on trail entries.

## AT28 ([#1976](https://github.com/yontrack/yontrack/issues/1976)) — Evidence: sandboxed HTML serving

Serve HTML evidence from a dedicated origin or path with `Content-Security-Policy: sandbox`, instead of download-only.

## AT29 ([#1977](https://github.com/yontrack/yontrack/issues/1977)) — Audit trail: auditor role

A read-only role to browse, verify and download trails and evidence, without other rights.

## AT30 ([#1978](https://github.com/yontrack/yontrack/issues/1978)) — Evidence: pre-signed uploads and pull from external tools

Pre-signed S3 upload URLs for large evidence; collecting evidence from tools instead of pipelines pushing it.

## AT31 ([yontrack-infra-gitops#171](https://github.com/yontrack/yontrack-infra-gitops/issues/171)) — Demo: audit trail at the 6.0 cutover

**Depends on:** AT22 ([yontrack-infra-gitops#170](https://github.com/yontrack/yontrack-infra-gitops/issues/170)), AT18 ([#1970](https://github.com/yontrack/yontrack/issues/1970))

Applied once the demo environment runs 6.0 (it runs `main`, 5.x, until the cutover).

- Demo reads its Spaces bucket's credentials (created by the bootstrap issue) through an ExternalSecret and sets `auditTrail.storage.*`.
- `ontrack.extension.audit-trail.demo-tampering.enabled=true` on demo.
- **Prerequisite:** the licensed feature from [yontrack-license#2](https://github.com/yontrack/yontrack-license/issues/2), and a licence carrying `extension.audit-trail` for demo.
- Done when the demo's seed shows `audit-trail-demo` and `audit-trail-tampered` with evidence.
