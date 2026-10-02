# Audit trail — Yontrack 6.0

Outcome of the grilling session of 2026-10-02 on [v6-scope.md](v6-scope.md), which fixed Levels 0
(evidence) and 1 (trail) for 6.0. That document settled *what* ships; this one settles what an
agent needs to build it. Where the two disagree, this one wins.

The issue breakdown is in [issues.md](issues.md), under the `initiative: v6-audit-trail` label.

## Where we start from

Facts read from the code on `v6` (c2f4b21af5) before the decisions were taken.

- **Events are synchronous and in the caller's transaction.** `EventPostServiceImpl.post` inserts the
  event and calls every `EventListener` inline; there is no after-commit or async dispatch anywhere.
  Search indexing already relies on it.
- **Seven build mutations post no event:** build link add/remove (only `BuildLinkListener`), run info
  set/delete, `deleteValidationRun` (GitHub ingestion deletes and re-creates runs),
  `updateValidationRunData` (SonarQube overwrites run data), `deleteDeployment`, the slot workflow
  override, and workflow outcomes (run in their own `REQUIRES_NEW` transactions, with no build FK).
- **Cascades rewrite other builds silently.** Deleting a validation stamp or a promotion level wipes
  runs on every build of the branch; deleting a build removes the `BUILD_LINKS` rows of *other*
  builds pointing at it.
- **Callers can back-date.** `ValidationRunRequest.signature`, build `creation` through `saveBuild`,
  slot `dateTime` parameters; GitHub ingestion uses the claimed `sender.login`.
- **There is no actor beyond an account name.** `Signature` is `(time, user name)`; the API token's
  name is discarded by `TokenSecurityFilter`; JWT claims other than email/name/groups are dropped,
  and a JWT without an email (a GitHub Actions OIDC token) cannot authenticate. `event.signature` is
  often the *build's* signature, not the caller's.
- **`@UserTransaction` does not roll back on `UserException`**, and the GraphQL layer opens no
  transaction: multi-step mutations (`createBuild` + run info, `linksBuild`) are several transactions.
- **No build purge job and no ledger import exist.** Builds go only through `deleteBuild` and the
  stale-branch job. `v6-scope.md` §3.2 and §4.5 assumed both.
- **Infrastructure:** no S3 client, no BouncyCastle, no JCS library; JDK 25 has Ed25519 built in.
  `ConfidentialStore` (file / jdbc / vault / read-only secret) already stores instance keys. ITs and
  KDSL run on compose stacks, not Testcontainers. The Helm chart is `yontrack/yontrack-chart`, the
  deployment values `yontrack/yontrack-infra-gitops`, the DigitalOcean resources (Spaces included,
  through OpenTofu) `yontrack/yontrack-infra-bootstrap`.
- **Global messages:** a `GlobalMessageExtension` returns `Message(type, content)` shown to every
  user; `LicenseMessage` is the template.
- **Licensing:** `LicensedFeatureProvider` + `LicenseControlService.isFeatureEnabled`; the UI never
  checks the licence itself, the server folds it into authorizations (`EnvironmentsLicense`,
  `EnvironmentsAuthorizationContributor`).
- **UI:** the build page has no tabs — a sub-page next to `/build/[id]/links` is the pattern. The
  validation run page adds cells conditionally (Findings). The mobile UI has no validation run page
  and routes `/validationRun/` to the desktop interstitial; `mobileRoutes.js` is the single source of
  the split.
- **Demo seed** works only through the API (`doc/dev-guide/demo-seed.md`): it cannot write a row
  directly nor switch a licence off.
- **`yontrack-cli` is in Go.**

## Vocabulary

As in `v6-scope.md` §1, with one change: **Checkpoint becomes Endorsement**. "Checkpoint" is already
a delivery-map term (`CONTEXT.md`), and "signature" collides with `model.structure.Signature`.

| Term | Meaning | Avoid |
|---|---|---|
| **Trail** | The append-only, hash-chained record of one build's story | ledger, log, history, journal |
| **Entry** | One element of a trail | event, fact, record |
| **Endorsement** | The instance key's Ed25519 signature over one entry's hash | checkpoint, signature (alone) |
| **Evidence** | A file attached to a validation run, referenced by an entry | attachment, artifact, document |

Seal and Instance trail stay reserved for 6.x. Code names: `BuildTrail`, `TrailEntry`,
`TrailEndorsement`, `Evidence`; module `ontrack-extension-audit-trail`; tables `build_trail_entry`,
`build_trail_endorsement`, `evidence`.

## Licence

- One boolean feature, id `extension.audit-trail`, no licence data (no quota, no count).
- **Entries are written only while the licence is on.** A build whose first entry is written after
  it was created (it predates the feature, or the licence was off) opens its trail with a genesis
  entry `trail.opened {buildCreatedAt, partial: true}`; verification reports the trail as *partial,
  starting at seq 1 on date X* — never as broken. Gaps from licence-off periods are not detected in
  6.0 and are documented as not covered (the instance trail, 6.x, will record licence transitions).
- **Licence lapsed after being on:** writing stops; existing trails and evidence stay readable,
  verifiable and downloadable; uploads and deletions are refused.
- The dev profile and the test stacks run with the feature on.

## Trail

### Format

- `build_trail_entry`: `id`, `build_id` (FK, **`ON DELETE CASCADE`** in 6.0), `seq` (1..n per build,
  unique with `build_id`), `schema_version`, `type`, `payload` (canonical JSON), `actor` (canonical
  JSON), `time`, `prev_hash`, `hash`.
- **Hashed bytes**: the RFC 8785 canonical form of the envelope
  `{schemaVersion, seq, type, time, actor, prevHash, payload}`, hashed with SHA-256, lowercase hex.
  `time` is ISO-8601 UTC with millisecond precision; `prevHash` is `null` for seq 1. This replaces
  the `‖` concatenation of `v6-scope.md` §3.2, which is ambiguous without delimiters.
- Seq 1 is `build.created` or `trail.opened`; its payload names the build (project, branch, build
  name, id), so an exported trail is bound to its build.
- `schema_version` starts at `1`. Any change to what is hashed bumps it, and verification supports
  every past version. **The format is a compatibility contract from the first release.**
- **Canonical JSON** is our own Kotlin canonicaliser over Jackson trees, strictly RFC 8785 on the
  subset we accept: strings, objects, arrays, booleans, null, and integers within ±2⁵³. Any other
  number is **rejected** — payloads carry decimals as strings. It is tested against the RFC 8785
  vectors of that subset and a shared fixture, `audit-trail/test-vectors/*.json`, that the CLI
  tests reuse. (`io.github.erdtman:java-json-canonicalization` is unmaintained; vendoring the
  reference code would only buy ES number formatting we do not use.)
- Payloads hold stable identifiers (names, ids, digests) and small values, never entity dumps.

### Appending

- Same transaction as the change, under `pg_advisory_xact_lock(<audit-trail class id>, build_id)` so
  `seq` and the chain never fork.
- **Seam:** a synchronous `EventListener` maps events to entries. The seven holes above are closed by
  **new core events** (which notifications gain too): build link added/deleted, run info set/deleted,
  validation run deleted, validation run data updated, deployment deleted, slot workflow overridden.
  Names follow the existing event conventions.
- **Cascades** go through a narrow core extension point called just before deleting a validation
  stamp, a promotion level or a build: the trail appends `validation.deleted`, `promotion.removed` or
  `link.removed` with `reason: cascade/<cause>` to each affected build, in the same transaction.
  Branch and project deletions need nothing — the trails go with the builds.
- **Actor** always comes from the security context at append time, never from `event.signature`.
- **Time** is the server clock at append time and is authoritative. Caller-supplied times and users
  (validation run signature, build creation, slot `dateTime`, GitHub `sender.login`) go into the
  payload as `claimed: {time, user}`.
- Accepted consequences: a GraphQL multi-step mutation writes several entries; a workflow node
  appends in its own transaction; under `@UserTransaction`, a change committed despite a
  `UserException` has its entry committed with it — the trail records what was committed.

### Entry types (closed list, schema version 1)

| Type | Produced by |
|---|---|
| `trail.opened` | First entry of a build that predates its trail |
| `build.created` | `NEW_BUILD` |
| `build.updated` | `UPDATE_BUILD` — old and new name, description, creation, creator (renames and back-dating are visible) |
| `property.set`, `property.deleted` | Build property events — property type and canonical value |
| `link.added`, `link.removed` | New link events; cascade on target build deletion |
| `validation.run` | `NEW_VALIDATION_RUN` — stamp, run id, status, data type and SHA-256 of the canonical data, `claimed` |
| `validation.status` | `NEW_VALIDATION_RUN_STATUS` |
| `validation.comment` | `UPDATE_VALIDATION_RUN_STATUS_COMMENT` |
| `validation.data` | New validation run data event — new data hash |
| `validation.deleted` | New validation run deletion event; cascade on stamp deletion |
| `evidence.attached`, `evidence.deleted` | Evidence service |
| `promotion.added`, `promotion.removed` | `NEW_PROMOTION_RUN`, `DELETE_PROMOTION_RUN`; cascade on promotion level deletion |
| `runinfo.set`, `runinfo.deleted` | New run info events (build or validation run of the build) |
| `deployment.created`, `deployment.cancelled`, `deployment.running`, `deployment.done`, `deployment.failed`, `deployment.rule-data`, `deployment.rule-overridden`, `deployment.workflow-overridden`, `deployment.deleted` | Environments pipeline events and the new ones |

Dropped from `v6-scope.md`: `workflow.completed` (workflow instances are not attached to builds;
slot workflow results arrive as `deployment.*`) and `build.deleted` (comes back with the instance
trail).

### Actor

A structured, canonical JSON object:
`{account, via: ui|token|jwt|webhook|system, tokenName?, jwt?: {iss, sub}, system?, onBehalfOf?}`.

- `tokenName` requires `TokenSecurityFilter` to keep the token's name in the security context.
- `system: <reason>` marks `asAdmin` paths (auto-promotion, SonarQube, ingestion), `onBehalfOf` keeps
  the original user.
- **In 6.0, "CI identity" means the named API token.** Accepting CI OIDC tokens (GitHub Actions,
  GitLab) without an email, under a trust policy, is a 6.x feature of its own.

## Endorsement and instance key

- **Every entry is endorsed**: Ed25519 over the entry's 32-byte hash, stored in
  `build_trail_endorsement` (`entry_id`, `key_id`, `signature`, `time`). Kept as its own table so
  batching or rollover can come later without touching the hashed format.
- The **instance key** lives in the existing `ConfidentialStore` under `audit-trail.ed25519` — which
  covers Vault, file, jdbc and Kubernetes secrets; no separate PEM option. Generated on first use if
  absent. `key_id` = first 16 hex characters of SHA-256 over the public key. Public key on
  `GET /rest/extension/audit-trail/keys` and in GraphQL.
- **Key unavailable** (read-only `secret` store with no key): entries are still chained and written
  but **unendorsed**; verification reports `unendorsedFromSeq`; an ERROR global message stays until
  the key is provisioned. Nothing is endorsed retroactively. The trail never stops CI.

## Evidence

### Storage

- S3-compatible through the AWS SDK v2, configured by **config properties**
  `ontrack.extension.audit-trail.storage.*`: endpoint, bucket, region, path-style, credentials, size
  cap (default 50 MB, instance-wide, no per-project override). Credentials never in the database.
- The client is built with `requestChecksumCalculation = WHEN_REQUIRED` and
  `responseChecksumValidation = WHEN_REQUIRED`: recent SDKs send CRC checksums by default, which some
  S3-compatible stores reject.
- Verified against **MinIO** (dev, IT, KDSL) and **DigitalOcean Spaces** (v6.dev and demo, endpoint
  `https://<region>.digitaloceanspaces.com`, virtual-hosted addressing).
- Layout: content-addressed `blobs/<sha256>`. Metadata in Postgres. Bucket content is not in
  `pg_dump`; customers back the bucket up themselves.

### Licence on, no storage

The trail works; evidence does not.

- Evidence endpoints answer a typed error, `audit-trail.evidence.storage-not-configured` (or
  `…-unreachable`).
- A probe checks reachability every minute and caches the result; a Spring health contributor reports
  storage `DEGRADED`, never `DOWN`, so a missing bucket cannot take pods out.
- **Global messages**, shown to everybody, only while the licence is on: storage not configured →
  WARNING ("Audit trail is enabled but no evidence storage is configured: evidence cannot be
  attached."); storage unreachable → ERROR; instance key not provisioned → ERROR; demo tampering
  switch on → ERROR (see *Demo*).
- An **Audit trail status** admin page (read-only) shows licence on/off, storage state (not
  configured / unreachable / OK) and the key id with its public key.
- The evidence UI shows "Evidence storage is not configured" instead of upload and list.
- No Postgres fallback.

### Model and upload

- `evidence`: `validation_run_id` (FK cascade), `file_name`, `media_type`, `size`, `sha256`,
  `collected_at` (server time), `collected_by` (actor), `source` (tool, version, origin URL),
  `external_digest` (optional, claimed; mismatch → reject), `deleted_at`.
- 0..n per validation run, immutable: a new upload is a new evidence. Each attachment writes an
  `evidence.attached` entry carrying the metadata and `sha256`.
- REST, two-step: multipart `POST` on the validation run, file plus metadata. Streamed to S3 while
  hashing; the server's hash is authoritative; the cap aborts the upload and deletes the partial
  object. Spring's multipart limits are raised for that endpoint.
- No pre-signed URLs, no pull from tools in 6.0.

### Display

- Inline only for an allow-list — PDF, JSON, plain text, PNG, JPEG, GIF, WebP — and only when the
  content's magic bytes agree with the declared type. Everything else, **HTML and SVG included**, is
  served with `Content-Disposition: attachment`. `X-Content-Type-Options: nosniff` always.
- Sandboxed HTML serving is 6.x.

### Deletion and permissions

- Evidence rows cascade with their validation run and build.
- Blobs are collected by a **daily sweep**: a `blobs/<sha>` object with no referencing row and older
  than 24 h is deleted (the delay avoids racing in-flight uploads). This replaces the queued deletes
  of `v6-scope.md` §4.5.
- Single deletion keeps the row with `deleted_at`, removes the blob only when no other row
  references it, and writes `evidence.deleted`.
- Read trail and evidence: project view. Upload: validation-run creation. **Delete: a new project
  function `EvidenceDelete`**, granted to project owner and above — not to validation-run writers,
  or a CI token could erase evidence. Status page: global settings.

## Verification

- `build.auditTrail { entries, endorsements, verification(includeEvidence: Boolean = false) }`.
  Verification reports `chainIntact`, `endorsementsValid`, `firstBrokenSeq`, `partial`,
  `unendorsedFromSeq`, and with `includeEvidence` also `missingEvidence` and `alteredEvidence` (blob
  absent, or re-hash differs).
- **JSON export** is self-sufficient: entries, endorsements, public key(s), `schema_version`. The
  algorithm is documented in the user docs, and `yontrack audit-trail verify <file>` in the CLI
  verifies it offline — without it, "integrity and origin" rests on trusting the server.
- A **daily job** verifies the trails of builds that gained entries since its last run (evidence not
  re-hashed), emits `trail.verification.failed` and counts failures in a metric.

## API, UI, KDSL, CLI

- **UI**: a build sub-page `/build/[id]/audit-trail`, reached from an "Audit trail" command in the
  build command bar (like Links), with a verification badge in its header, the entries table
  (seq, time, type, actor, summary) and a JSON download. On the validation run page, a conditional
  **Evidence** cell: list, inline preview for allow-listed types, download, upload for those with
  validation-run creation, delete for `EvidenceDelete`.
- **Mobile**: no mobile impact in 6.0 — the mobile UI has no validation run page, and the new desktop
  route is registered in `mobileRoutes.js` as desktop-only.
- **KDSL**: read and verify a trail, upload evidence, and a convenience that validates then attaches.
  Throws when evidence is refused.
- **CLI** (`yontrack-cli`): `validate … --evidence <file>` fails the step when the upload is refused
  (a missing evidence is an audit gap); `--evidence-optional` turns that into a warning.
  `audit-trail verify <file.json>` uses the `cyberphone` reference JCS implementation and the shared
  test vectors.

## Events and metrics

- Notifiable: `evidence.attached` and `trail.verification.failed`, plus the core events closing the
  holes.
- Metrics: append timer, verification failures, evidence count and size per project.

## Infrastructure

- **`yontrack`**: MinIO in the dev stack, the IT stack and the KDSL stack, on slot-offset ports
  through `buildSrc` (`.claude/rules/buildsrc.md`), not Testcontainers. The MinIO image must be one
  that is still maintained at implementation time.
- **`yontrack-chart`**: `auditTrail.storage.*` values for external S3 (credentials from an existing
  secret), and an optional MinIO subchart with a PVC, **off by default**.
- **`yontrack-infra-bootstrap`**: one private DO Spaces bucket for v6.dev and one for demo, created
  now (demo runs 5.x until the cutover, but preparing its bucket costs nothing), a Spaces key scoped
  to those buckets, credentials in Vault KV — the `modules/pg-backups` pattern.
- **`yontrack-infra-gitops`**: v6.dev reads the credentials through an ExternalSecret, points at its
  Space, runs with a licence carrying the feature and with the demo tampering switch on. Demo is
  wired the same way at the 6.0 cutover.

## Demo

Two projects, seeded through the API.

- **`audit-trail-demo`** — a release build with its full story: created from CI through a token
  (actor `token:ci-demo`), commit and release properties, links to two dependency builds, six
  validations (one FAILED → PASSED with a comment), promotions BRONZE → SILVER → GOLD, a deployment
  through two slots with one overridden admission rule. Evidence of every kind: a Trivy PDF, a
  CycloneDX JSON SBOM, an HTML ZAP report (download-only), a JUnit text summary, a PNG screenshot,
  and one deleted evidence. A validation stamp deleted to show `validation.deleted reason: cascade`
  on two builds.
- **`audit-trail-tampered`** (smaller) — one build, about six entries, one PDF, entry 4 tampered:
  the badge reads "chain broken at seq 4" and `trail.verification.failed` fires.
- Tampering goes through **`ontrack.extension.audit-trail.demo-tampering.enabled`**, off by default,
  documented as never for production. It enables a global-admin endpoint rewriting one entry's
  payload, and while it is on a permanent ERROR global message reads "This instance allows trail
  tampering for demonstration: its trails prove nothing."
- The *partial trail* case is not in the demo (the seed cannot switch the licence off); tests cover
  it and the docs show it.
- Without storage, the seed skips evidence and says so.

## What 6.0 does not give

Integrity and origin only: "this Yontrack produced this history and it was not rewritten". No
third-party proof of time (no TSA), no sealing, no audit retention independent of builds (trails
cascade with them), no detection of licence-off gaps, no CI OIDC federation, no Object Lock on DO
Spaces. Release notes and sales conversations say so.

## Deferred to 6.x

Placeholder issues on milestone `6.1`, `status:tospec`: sealing, retention and export; TSA and
Rekor; instance trail and key rollover; CI OIDC federation; sandboxed HTML evidence; auditor role;
pre-signed uploads and pull from tools.

## Delivery order and cut line

Trail core → actor and events → listener → verification → storage → evidence → KDSL → UI → demo
and docs. If time runs short, 6.0 ships the trail and the evidence API without the UI (the two UI
issues are marked *cuttable*), rather than cutting the trail.
