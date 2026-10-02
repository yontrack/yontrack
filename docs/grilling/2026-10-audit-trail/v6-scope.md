# Audit trail — V6.0 scope (Levels 0 + 1)

_Scope note, 2026-10-02. Companion to `research.md` (research, decisions, retention proposal). This document fixes what ships in 6.0, what is deferred to 6.x, and the vocabulary._

## 1. Vocabulary

| Term | Meaning | Avoid |
|---|---|---|
| **Trail** | The append-only, hash-chained record of one build's story | ledger (import format), log (application logs), history (generic UI word), journal |
| **Entry** | One element of a trail: type, canonical payload, actor, time, previous hash, hash | event (notification system), fact (import), record (recordings extension) |
| **Checkpoint** | A signed hash of the latest entry, produced by the instance key | "signature" alone (ambiguous with per-entry or CI signatures) |
| **Evidence** | A file attached to a validation run and referenced by an entry | attachment, artifact (build artifacts), document |
| **Seal** | The frozen, self-contained package of a trail written to S3 — *6.x, not 6.0* | export, archive, snapshot |
| **Instance trail** | Instance-level trail of changes to the audit feature itself — *6.x* | audit log |

"Chain" is a technical adjective ("the trail's chain is intact"), never a noun for the thing. Code names: `BuildTrail`, `TrailEntry`, `TrailCheckpoint`, `Evidence`, `TrailSeal`; extension/module `audit-trail`.

## 2. What 6.0 delivers

A licensed feature giving, for every build:

1. **A trail** covering the whole build story — creation, links/dependencies, validation runs (status + data hash), evidence attachments, promotions, workflow outcomes, run-info, deployments — that is tamper-evident (hash chain) and attributable (instance checkpoints + caller identity).
2. **Evidence** files attached to validation runs, stored in S3-compatible object storage, hashed on receipt, immutable.
3. **Verification** on demand: an API/UI showing the trail, re-computing the chain and checkpoint signatures, and reporting any break.

What 6.0 gives an auditor: *integrity and origin* — "this Yontrack produced this history and it was not rewritten". It does **not** give third-party proof of time (no TSA), long-term preservation (no sealing, no audit retention), or exports. Release notes and sales conversations must say so.

## 3. Level 1 — Trail

### 3.1 Model
- `build_trail_entry`: `build_id`, `seq` (1..n per build), `schema_version`, `type`, `payload` (canonical JSON), `actor` (user or token; CI identity when an OIDC/API token identity is presented), `time`, `prev_hash`, `hash`.
- `build_trail_checkpoint`: `build_id`, `seq` (entry covered), `key_id`, `signature`, `time`.
- Entry types for 6.0 (closed list): `build.created`, `link.added`, `link.removed`, `validation.run`, `validation.status`, `evidence.attached`, `evidence.deleted`, `promotion.added`, `promotion.removed`, `workflow.completed`, `runinfo.set`, `deployment.*` (as emitted by environments), `build.deleted` (last entry, written to the instance trail in 6.x; in 6.0 it cascades with the build).

### 3.2 Rules
- **Append in the same transaction** as the change that produced it, under a per-build advisory lock so `seq` and the chain never fork. No asynchronous listener.
- **Canonical JSON (RFC 8785 / JCS)** for the hashed bytes: `hash = SHA-256(schemaVersion ‖ seq ‖ type ‖ time ‖ actor ‖ prev_hash ‖ canonical(payload))`. Payloads hold stable identifiers (names, ids, digests), never entity dumps.
- `schema_version` is one integer; any change to what is hashed bumps it and verification must support every past version. **This format is a compatibility contract from the first release.**
- **Checkpoint**: Ed25519 signature of the latest entry hash by the instance key, written at least on every promotion and on evidence attachment (option: on every entry). `key_id` recorded so key rollover (6.x) is representable from day one.
- **Instance key**: loaded from Vault (existing extension) or a configured PEM; generated on first start if absent and persisted; public key exposed on `GET /audit-trail/keys`.
- Every write path that mutates a build must emit an entry: audit of bulk operations, CasC, migrations and the import run before 6.0 is called complete. A mutation without an entry is a hole in the trail.
- Trail rows **cascade with the build** in 6.0 (sealing arrives in 6.x).

### 3.3 API / UI
- GraphQL: `build.auditTrail { entries, checkpoints, verification { chainIntact, checkpointsValid, firstBrokenSeq } }`; JSON export of the same.
- UI: "Audit trail" tab on the build page — table of entries with actor/time/type/summary, verification badge, download JSON.
- KDSL: read trail, verify.

## 4. Level 0 — Evidence

### 4.1 Storage
- **S3-compatible** via AWS SDK v2 (AWS S3, MinIO, Ceph…). Settings: endpoint, bucket, region, credentials (direct or Vault), path-style flag, size cap (default 50 MB).
- **Optional**: licence off or S3 unconfigured → evidence endpoints answer "feature not enabled"; nothing else in Yontrack changes.
- Layout: content-addressed `blobs/<sha256>` (dedupes re-uploads; sealing in 6.x reuses the same objects). Metadata lives in Postgres.
- Dev/test: MinIO service in the compose stack and in the Helm chart (values-gated), MinIO Testcontainer in acceptance tests.
- Documented: S3 content is not part of `pg_dump` backups; customers back up the bucket themselves (and may enable Object Lock).

### 4.2 Model
- `evidence`: `validation_run_id`, `file_name`, `media_type`, `size`, `sha256`, `collected_at`, `collected_by`, `source` (tool, version, origin URL), `external_digest` (optional, claimed by uploader; mismatch → reject), `deleted_at` (nullable).
- 0..n evidence per validation run. Immutable: no update/replace; a new upload is a new evidence. Each attachment writes an `evidence.attached` entry carrying the metadata and `sha256`.

### 4.3 Upload
- Multipart upload via REST + KDSL (pipelines push; no pull from tools in 6.0). Streamed to S3 while hashing; the hash is computed server-side and is authoritative.
- Pre-signed upload URLs: not in 6.0.

### 4.4 Display — security item
- PDF, JSON, plain text, images: inline.
- **HTML evidence is never rendered in the Yontrack origin** (stored XSS). 6.0 default: download-only for HTML. Option, if time allows: serve from a dedicated path with `Content-Security-Policy: sandbox` and `Content-Disposition: attachment` fallback. To be reviewed with the security checklist before release.

### 4.5 Deletion
- Build purge deletes evidence rows in the transaction and queues S3 object deletes through the job framework (objects shared by digest are deleted only when no other evidence references them). A weekly orphan sweep reconciles bucket vs. database.
- Admin deletion of a single evidence writes an `evidence.deleted` entry; the row is kept with `deleted_at`, the blob is removed.

## 5. Cross-cutting
- **Licence**: one feature flag `audit-trail`, off by default, gates both levels.
- **Permissions**: upload/delete evidence = validation-run write; read trail/evidence = build read; admin = key and S3 settings. An "auditor" read-only role is 6.x unless a design partner needs it.
- **Events/notifications**: `evidence.attached` and `trail.verification.failed` as new notifiable events.
- **Metrics**: evidence count/size per project, verification failures.

## 6. Deferred to 6.x (from the research doc)
- Level 2: RFC 3161 TSA (optional, configurable), Rekor/in-toto publishing, ERS-style re-timestamping.
- Level 3: seals (frozen packages in S3), audit retention independent of build retention, seal-on-promotion / seal-on-purge policies, export ZIP + manifest + DSSE, ASiC-E option, project-level "Audit trails" page, `audit_seal` table surviving build deletion.
- Instance trail (settings changes, key rollovers, deletions), key rollover procedure, auditor role, pre-signed uploads, pull from external tools.

## 7. Delivery order
1. Trail: model, canonical format, append hook, checkpoints, key handling, verification API.
2. Evidence: model, S3 settings, upload API + KDSL, `evidence.attached` entries.
3. UI: build "Audit trail" tab, evidence list/download on validation runs.
4. Compose/Helm MinIO, Testcontainers, acceptance tests.
5. Licence gate, docs, security review of evidence display.

If time runs short, 6.0 ships 1 + 2 (API only) rather than cutting the trail.

## 8. Decisions still open for 6.0
- Checkpoint frequency: every entry vs. promotions + evidence only (cost vs. granularity).
- HTML evidence: download-only vs. sandboxed serving.
- Default size cap and whether it is per-instance or per-project.
- Whether `build.deleted` needs anywhere to live before the instance trail exists (proposal: not in 6.0; purge is already recorded by the retention job logs).

## 9. Related files
- `research.md` — standards research, decisions, retention proposal.
- `glossary.md` — one-line definitions of the terms used.
- `client-checklist.pdf` — printable question list for client calls.
