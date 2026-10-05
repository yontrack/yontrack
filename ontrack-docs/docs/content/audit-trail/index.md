# Audit trail

Yontrack keeps, for every build, a **trail**: an append-only, hash-chained record of its story —
its creation, its properties, its links, its validations and their **evidence**, its promotions,
its run info and its deployments. Each change is recorded in the same database transaction as the
change itself, by whom it was made and through which channel, and every entry of the trail is
signed by a key of the Yontrack instance.

The trail lets an auditor check two things about the history of a build: that **this Yontrack
instance wrote it**, and that **it was not rewritten since**. Validation runs carry **evidence** —
the scan reports, SBOMs, test summaries and screenshots which justify their status — whose content
hashes are recorded in the trail.

!!! note

    The audit trail is under license, as the feature `extension.audit-trail` ("Audit trail"). See
    [License](#license).

## What it gives, and what it does not

The audit trail of Yontrack 6.0 gives **integrity and origin**: "this Yontrack produced this
history, and it was not rewritten". Concretely:

* every entry is chained to the one before it by its hash: removing, inserting, reordering or
  editing an entry breaks the chain at that entry;
* every entry is **endorsed** — signed with Ed25519 — by the instance key: recomputing the hashes
  after an edit keeps the chain intact, but invalidates the endorsements;
* the evidence attached to a validation run is recorded by its SHA-256: a verification which reads
  the evidence back detects an altered or vanished file;
* a trail can be exported as one JSON document and verified offline, with nothing but its content
  — see [Verifying a trail offline](#verifying-a-trail-offline).

Yontrack 6.0 does **not** give:

* **third-party proof of time** — no RFC 3161 timestamping authority (TSA), no transparency log:
  the times of the entries are the server's;
* **sealing** — no frozen, self-contained package of a trail;
* **retention independent of the builds** — a trail goes with its build: deleting a build, its
  branch or its project deletes its trail and its evidence;
* **detection of the gaps while the license was off** — nothing is recorded while the license is
  off, and nothing says it was (see [License](#license));
* **CI OIDC federation** — the identity of a CI pipeline is the name of the
  [API token](../security/tokens.md) it uses, not the OIDC token of the CI engine;
* **key rollover** — the instance has one key; see [Instance key](#instance-key);
* **Object Lock on DigitalOcean Spaces** — the storage of the evidence does not make the files
  immutable; see [Evidence storage](#evidence-storage).

All but the last are planned for 6.x versions.

## The model

**Trail**
:   The append-only, hash-chained record of the story of one build. A trail belongs to exactly one
    build and goes with it.

**Entry**
:   One element of a trail: a [type](#entry-types), a payload describing the change, the
    [actor](#actor) who made it and the server time, numbered within its trail by its `seq` (from
    1) and hashed together with the hash of the entry before it.

**Actor**
:   Who made a change, and how they got in: the account, the channel (`ui`, `token`, `jwt`,
    `webhook` or `system`), the name of the API token — never its value — or the issuer and subject
    of the JWT. When Yontrack acts on its own — an auto-promotion, an ingestion, a job — the actor
    is the *system*, with its reason, on behalf of the actor which set it off.

**Endorsement**
:   The Ed25519 signature, by the instance key, of the hash of one entry: this Yontrack instance
    wrote that entry.

**Evidence**
:   A file attached to a [validation run](../concepts/model/index.md#validation-runs) — a scan
    report, an SBOM, a test summary, a screenshot — stored by the SHA-256 of its content, and
    recorded by an `evidence.attached` entry of the trail of the build. An evidence is immutable:
    a new upload is a new evidence.

## License

The audit trail is the licensed feature `extension.audit-trail`. It has no quota: it is on or off.

**While the license is on**, every change of a build is written to its trail, and evidence can be
attached and deleted.

**A build which predates its trail** — created before the feature was enabled, or while the
license was off — gets a trail at its first change after that. The trail then opens with a
`trail.opened` entry instead of `build.created`:

```json
{"build": {"id": 1042, "project": "payments", "branch": "release-2.4", "name": "2.4.7"}, "buildCreatedAt": "2026-09-14T10:02:11.418Z", "partial": true}
```

Such a trail is **partial**: its verification reports it as such, starting at seq 1 at the time of
that entry — never as broken. What happened to the build before is not in its trail.

**When the license lapses** after having been on:

* nothing more is written: the changes made while the license is off are in no trail;
* the trails already written stay readable, verifiable and exportable, and the evidence stays
  downloadable;
* uploading and deleting evidence are refused, with the error `audit-trail.evidence.not-licensed`
  — a deletion would be missing from the trail.

When the license is on again, writing resumes at the end of the existing trails. **The gap is not
detected**: Yontrack 6.0 does not record when the license was off, and a trail with such a gap
verifies as intact.

## Using the audit trail

### The audit trail of a build

The *Audit trail* command of the build page opens its audit trail, at `/build/<id>/audit-trail`. It
is offered when the build has a trail to read: the license is on, or entries were written for it
before it lapsed. Seeing the trail of a build requires seeing its project.

The page shows:

* a **verification badge**, the worst state found:

    | Badge                  | Meaning                                                                                                   |
    |------------------------|-----------------------------------------------------------------------------------------------------------|
    | *Intact*               | Every entry is chained to the one before it and endorsed by the instance.                                |
    | *Partial*              | As *Intact*, for a trail opened after its build was created, by a `trail.opened` entry.                  |
    | *Unendorsed from seq N* | The chain is intact, but the entries from seq N are endorsed by no key: see [unendorsed mode](#unendorsed-mode). |
    | *Broken at seq N*      | The chain is broken, or an endorsement is invalid, at seq N: the trail was tampered with.                |

* **Verify including evidence**, which verifies the trail again, reading every evidence back from
  the storage to check its SHA-256, and adds a second badge — *Evidence intact*, or the seqs of
  the `evidence.attached` entries whose file is missing or altered;
* the **evidence** of every validation of the build — every run of every validation stamp, deleted
  evidence included — in the order of its upload. Each evidence shows its validation run, linked to
  its page, and its state: *Active* or *Deleted*, marked *Missing* or *Altered* once **Verify
  including evidence** found its file absent or changed. The list can be filtered by name or start
  of SHA-256, by type, by validation stamp and by state — the title keeps the total. Evidence is
  previewed and downloaded here; it is uploaded and deleted on the
  [validation run page](#evidence-on-a-validation-run). When the evidence storage is not
  configured or cannot be reached, the list still shows, without preview nor download;
* the **entries**: seq, time, type, actor and a summary, each expandable to its payload and actor;
* **Export JSON**, which downloads the [export](#json-export) of the trail.

The mobile UI shows neither the audit trail nor the evidence: they are on the desktop UI only.

### Evidence on a validation run

The validation run page shows an **Evidence** section when the run has evidence, or when you may
upload some. It lists the evidence of the run — deleted ones included, marked as such — with:

* **Upload evidence**, for those who may create validation runs on the project, while the license
  is on;
* **Preview**, for the files which can be [displayed inline](#display);
* **Download**;
* **Delete**, for those granted [`EvidenceDelete`](#deletion) on the project, while the license is
  on.

When the evidence storage is not configured or cannot be reached, the section says so instead.

## Evidence

### Uploading evidence

Evidence is attached to an existing validation run. Uploading requires the right to create
validation runs on its project — what a CI token has — and the license.

#### REST

```
POST /rest/extension/audit-trail/validation-runs/{validationRunId}/evidence
Content-Type: multipart/form-data
```

| Part             | Required | Description                                                                                                                   |
|------------------|----------|-------------------------------------------------------------------------------------------------------------------------------|
| `file`           | Yes      | The content.                                                                                                                  |
| `fileName`       | No       | Name of the file, overriding the one of the `file` part. Only its last path segment is kept, without control characters, at most 255 characters — a label, never a path. Defaults to `evidence`. |
| `mediaType`      | No       | Media type, overriding the one of the `file` part. Kept as its lowercase `type/subtype`, without parameters. Defaults to `application/octet-stream`. |
| `sourceTool`     | No       | Tool which produced the evidence, like `trivy`. At most 255 characters.                                                      |
| `sourceVersion`  | No       | Version of that tool. At most 255 characters.                                                                                 |
| `sourceUrl`      | No       | Where the evidence was produced — a CI job, a report: an absolute HTTP or HTTPS URL, at most 2000 characters.                |
| `externalDigest` | No       | SHA-256 of the content as the client computed it, 64 hexadecimal characters, optionally prefixed by `sha256:`. The upload is refused when it does not match. |

For example, with an [API token](../security/tokens.md):

```bash
curl --fail-with-body \
  -H "X-Ontrack-Token: $YONTRACK_TOKEN" \
  -F "file=@trivy-report.pdf;type=application/pdf" \
  -F "sourceTool=trivy" \
  -F "sourceVersion=0.58.1" \
  -F "sourceUrl=$CI_JOB_URL" \
  -F "externalDigest=sha256:$(sha256sum trivy-report.pdf | cut -d' ' -f1)" \
  "$YONTRACK_URL/rest/extension/audit-trail/validation-runs/$RUN_ID/evidence"
```

The server answers `201 Created` with the evidence:

```json
{
  "id": 87,
  "validationRunId": 4512,
  "fileName": "trivy-report.pdf",
  "mediaType": "application/pdf",
  "size": 183204,
  "sha256": "0b1e7c…",
  "collectedAt": "2026-10-02T08:21:05.112Z",
  "collectedBy": {"account": "ci@example.com", "tokenName": "ci-pipeline", "via": "token"},
  "source": {"tool": "trivy", "version": "0.58.1", "url": "https://ci.example.com/job/1234"},
  "externalDigest": "0b1e7c…",
  "deletedAt": null,
  "downloadUrl": "/rest/extension/audit-trail/evidence/87/download"
}
```

The content is streamed to the storage while it is hashed: the SHA-256 the server computes is the
one recorded. The upload writes an `evidence.attached` entry to the trail of the build, and posts
the [`evidence.attached`](../generated/events/event-evidence.attached.md) event.

A refused upload answers a JSON body with a stable `code`:

```json
{"status": 413, "code": "audit-trail.evidence.too-large", "message": "…"}
```

| Code                                          | HTTP | Why                                                                                     |
|-----------------------------------------------|------|-----------------------------------------------------------------------------------------|
| `audit-trail.evidence.storage-not-configured` | 503  | No [evidence storage](#evidence-storage) is configured.                                 |
| `audit-trail.evidence.storage-unreachable`    | 503  | The evidence storage cannot be reached.                                                 |
| `audit-trail.evidence.too-large`              | 413  | The file is bigger than `ontrack.extension.audit-trail.storage.max-size`.               |
| `audit-trail.evidence.digest-mismatch`        | 422  | The `externalDigest` is not the SHA-256 of what was sent.                               |
| `audit-trail.evidence.not-licensed`           | 403  | The license does not allow the audit trail.                                             |
| `audit-trail.evidence.invalid`                | 400  | A part cannot be accepted: no `file` part, an unreadable or wildcard media type, a malformed digest, a source which is too long, holds control characters, or a URL which is not HTTP or HTTPS. |

There is no GraphQL mutation to upload evidence: the content goes through REST only.

!!! warning "Routing the uploads"

    CI pipelines upload evidence from outside the cluster, straight to the backend. An ingress which
    routes only `/graphql` and `/hook` to the backend — as the ingress of the Yontrack Helm chart
    does — must also route `/rest/extension/audit-trail` to it, for the uploads, the downloads and
    the exports made by API clients. The web UI does not need it: it goes through its own server.

#### KDSL

The KDSL attaches evidence to a validation run, or validates a build and attaches evidence in one
call:

```kotlin
val run = build.validateWithEvidence(
    validationStamp = "security-scan",
    status = "PASSED",
    evidence = listOf(
        EvidenceFile(
            fileName = "trivy-report.pdf",
            content = File("trivy-report.pdf").readBytes(),
            mediaType = "application/pdf",
            sourceTool = "trivy",
            sourceVersion = "0.58.1",
        )
    ),
)

// Or, on an existing run
run.attachEvidence(EvidenceFile(fileName = "sbom.json", content = sbom, mediaType = "application/vnd.cyclonedx+json"))
```

A refused evidence throws an `EvidenceRefusedException`, whose `code` is one of the codes above.
`validateWithEvidence` creates the run first: when an evidence is refused, the run stays, with the
evidence attached before the refused one, and the exception is thrown — a missing evidence is an
audit gap, and the step should fail.

The KDSL also reads a run's evidence (`ValidationRun.evidence`), downloads (`Evidence.download()`)
and deletes them (`Evidence.delete()`), and reads the trail of a build (`Build.trail`), verifies it
on the server (`Build.verifyTrail(includeEvidence)`) and exports it (`Build.exportTrail()`).
`Ontrack.auditTrailStorageState` tells whether evidence can be attached at all.

#### CLI

The `yontrack` CLI does not upload evidence nor verify trails yet: the commands are coming
([yontrack/yontrack-cli#83](https://github.com/yontrack/yontrack-cli/issues/83)). Until then, use
the REST API above.

### Display

An evidence is downloaded from `GET /rest/extension/audit-trail/evidence/{evidenceId}/download`, as
long as its validation run can be seen — whatever the license. A deleted evidence cannot be
downloaded any more.

It is displayed **inline** only when its declared media type is in an allow-list **and** its first
bytes agree with that type:

| Declared type                          | Content checked                                      |
|----------------------------------------|------------------------------------------------------|
| `application/pdf`                      | Starts with `%PDF-`                                  |
| `application/json`, `application/*+json` | UTF-8 text starting with `{` or `[`                |
| `text/plain`                           | UTF-8 text without control characters but tabs and line breaks |
| `image/png`                            | PNG signature                                        |
| `image/jpeg`                           | JPEG signature                                       |
| `image/gif`                            | `GIF87a` or `GIF89a`                                 |
| `image/webp`                           | `RIFF` … `WEBP`                                      |

**Everything else — HTML and SVG included**, or an allow-listed type whose content is something
else — is served as an `application/octet-stream` attachment: no browser renders active content
from an evidence in the origin of Yontrack. Every download carries `X-Content-Type-Options: nosniff`
and a `Content-Security-Policy` which forbids any script and any external resource, and sandboxes
the document — but for an inline PDF, which browsers refuse to display in a sandbox.

An HTML report — a ZAP scan, a coverage report — is therefore download-only: open it from your
disk.

### Deletion

Deleting an evidence requires the **`EvidenceDelete`** function on its project, granted to the
project [OWNER](../security/roles.md#project-roles) and to the administrators only. The roles which
create validation runs — and the CI tokens which upload evidence — do not have it, so that a
pipeline cannot erase the evidence it attached.

* REST: `DELETE /rest/extension/audit-trail/evidence/{evidenceId}`
* GraphQL: `deleteEvidence(input: {id: …})`

A deleted evidence is **kept**, marked as deleted, and its deletion writes an `evidence.deleted`
entry to the trail and posts the [`evidence.deleted`](../generated/events/event-evidence.deleted.md)
event. Its file is removed from the storage unless another evidence has the same content. Deleting
evidence requires the license, like uploading it.

Evidence also goes with its validation run, and therefore with its build.

The files are stored once per content (`blobs/<sha256>` in the bucket) and shared between the
evidence which has the same content. A **daily sweep** removes the files which no evidence
references any longer, and the uploads left behind, once they are more than 24 hours old — the
delay keeps it away from the uploads in progress. Nothing is swept while the storage cannot be
used.

## Setting up

### Evidence storage

The trail itself needs nothing but the database. The **evidence** is stored in an S3-compatible
bucket, through the AWS SDK. Without one, the trail works and evidence cannot be attached.

The storage is configured by these [configuration properties](../generated/configurations/net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties.md):

| Property                                          | Default     | Description                                                                 |
|---------------------------------------------------|-------------|-----------------------------------------------------------------------------|
| `ontrack.extension.audit-trail.storage.endpoint`   |             | URL of the S3-compatible service. Required.                                 |
| `ontrack.extension.audit-trail.storage.bucket`     |             | Name of the bucket, which must exist. Required.                             |
| `ontrack.extension.audit-trail.storage.region`     | `us-east-1` | Region of the bucket.                                                       |
| `ontrack.extension.audit-trail.storage.path-style` | `false`     | Path-style addressing (`<endpoint>/<bucket>`) instead of virtual-hosted (`<bucket>.<endpoint>`). |
| `ontrack.extension.audit-trail.storage.access-key` |             | Access key. Required.                                                       |
| `ontrack.extension.audit-trail.storage.secret-key` |             | Secret key. Required.                                                       |
| `ontrack.extension.audit-trail.storage.max-size`   | `50` (MB)   | Maximum size of one evidence, for the whole instance. In megabytes when no unit is given. |

The storage is configured when the endpoint, the bucket and both keys are set. The credentials are
read from the configuration only, never stored in the database: pass them as environment variables
from a secret, like `ONTRACK_EXTENSION_AUDIT_TRAIL_STORAGE_SECRET_KEY`.

| Provider               | `endpoint`                                   | `region`              | `path-style` |
|------------------------|----------------------------------------------|-----------------------|--------------|
| AWS S3                 | `https://s3.<region>.amazonaws.com`          | The bucket's region   | `false`      |
| MinIO                  | The MinIO URL, like `http://minio:9000`      | Any, like `us-east-1` | `true`       |
| DigitalOcean Spaces    | `https://<region>.digitaloceanspaces.com`    | The Space's region, like `fra1` | `false` |

The endpoint is always required, AWS S3 included. The S3 client sends checksums only when an
operation requires them, which the S3-compatible services which reject the default checksums of
recent AWS SDKs accept.

Keep the bucket **private**: Yontrack is the only one which needs to read it, and serves the files
itself. It stores:

* `blobs/<sha256>` — the content of the evidence, named by its SHA-256;
* `uploads/<uuid>` — the uploads in progress, removed once copied to their blob.

The multipart limits of the instance (`spring.servlet.multipart.max-file-size` and
`max-request-size`) are raised to let the largest evidence through; the upload enforces `max-size`
itself.

#### Backing up the evidence

The bucket is not in the database: a `pg_dump` does not hold the evidence. **Back the bucket up
yourself**, alongside the database, with the tools of your provider. A database restored without
its bucket has evidence whose file is missing: *Verify including evidence* reports them.

Yontrack 6.0 does not make the files immutable. On a provider which offers it — AWS S3, MinIO —
bucket versioning and Object Lock keep the files at the storage level; DigitalOcean Spaces has no
Object Lock.

#### Status of the storage

The storage is **probed every minute** — the bucket is read — and its state is one of:

* `NOT_CONFIGURED` — a required property is missing;
* `UNREACHABLE` — the service is down, the bucket does not exist, or the credentials are refused;
* `OK`.

While the license is on, a storage which is not `OK` shows a **global message** to every user:

| State            | Message type | Message                                                                                      |
|------------------|--------------|----------------------------------------------------------------------------------------------|
| `NOT_CONFIGURED` | Warning      | Audit trail is enabled but no evidence storage is configured: evidence cannot be attached.   |
| `UNREACHABLE`    | Error        | Audit trail is enabled but its evidence storage is unreachable: evidence cannot be attached. |

The messages do not say why: the reason, which may name hosts and buckets, is on the **Audit trail
status** page, in the system section of the user menu, for those who manage the global settings
(`/extension/audit-trail/status`). It shows whether the license is on, the state of the storage
with its configuration — never its credentials — and the reason it is not `OK`, the state of the
instance key and its public key.

The state alone is readable by every user, through the GraphQL query `auditTrailStorageState`; the
whole status, for the global settings, through `auditTrailStatus`.

The health of the instance has an **`evidenceStorage`** component: `DEGRADED` while the license is
on and the storage is not `OK`, `UP` otherwise. It is **never `DOWN`**: a missing bucket must not
take the instance out of a load balancer. `DEGRADED` is answered with HTTP 200, and makes the
overall status `DEGRADED` — see [Management port](../operations/management-port.md).

### Instance key

The **instance key** is the Ed25519 key with which the instance endorses every entry. It lives in
the [confidential store](../generated/configurations/net.nemerosa.ontrack.model.support.OntrackConfigProperties.md)
of the instance (`ontrack.config.key-store`: `file`, `jdbc`, `vault` or `secret`), next to the
encryption key of the credentials, under the name `audit-trail.ed25519`, as an Ed25519 private key
in PKCS#8.

* With a writable store — `file`, `jdbc`, `vault` — the key is **generated** on first use, and
  stored.
* With the read-only `secret` store — files mounted from a Kubernetes secret — **provision** it as
  the file `audit-trail.ed25519` of the store directory (`ontrack.config.file-key-store.directory`),
  in PEM or DER:

    ```bash
    openssl genpkey -algorithm ed25519 -out audit-trail.ed25519
    ```

    and add it to the secret which already holds the encryption key. While the key is missing,
    Yontrack looks for it again every minute: a key mounted after the start is found without a
    restart.

The public key is published, with its **key ID** — the first 16 hexadecimal characters of the
SHA-256 of its DER `SubjectPublicKeyInfo`:

* REST: `GET /rest/extension/audit-trail/keys` — `[{"keyId", "algorithm": "Ed25519", "publicKey"}]`,
  the public key in PEM;
* GraphQL: `auditTrailKeys`;
* the Audit trail status page, and every [export](#json-export).

!!! warning "Keep the key"

    Yontrack 6.0 has **no key rollover**: an instance publishes one key, and verifies the
    endorsements against it. A key which is lost or replaced makes every endorsement made by the
    previous one fail its verification, with an unknown key. Back the key up with the confidential
    store, and let every instance of a cluster share it — as the encryption key already requires.

#### Unendorsed mode

When no key is available — the `secret` store has none, the store cannot be read, or what it holds
is not a key — the instance runs **unendorsed**:

* entries are still chained and written: the trail never stops a change, nor a pipeline;
* they are not endorsed, and nothing is endorsed retroactively once the key is provisioned;
* while the license is on, an **error global message** says so to every user: *Audit trail is
  enabled but its instance key is not provisioned: entries are not endorsed.*;
* the verification reports `unendorsedFromSeq`, the first entry after the last endorsed one.

An unendorsed entry followed by an endorsed one is covered by it, through the chain: the hash the
later entry endorses covers every entry before it. Only the **unendorsed tail** of a trail — the
entries after its last endorsed one — is covered by no endorsement.

## Reference

### Entry types

The types of schema version 1 form a closed list. Payloads hold stable identifiers — names, IDs,
digests — and small values, never whole entities; a value which is not set is absent. Builds,
validation stamps, validation runs and promotion levels are referred to as
`{id, project, branch, name}`, `{id, name}`, `{id, order}` and `{id, name}`.

| Type                            | Recorded when                                                     | Payload                                                                                       |
|---------------------------------|-------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `trail.opened`                  | First entry of a build which predates its trail                   | `build`, `buildCreatedAt`, `partial: true`                                                    |
| `build.created`                 | Build created                                                      | `build`, `description`, `claimed`                                                             |
| `build.updated`                 | Build edited — renames and back-dating are visible                | `old`, `new`, each `{name, description, creation, creator}`                                   |
| `property.set`                  | Property set on the build                                          | `propertyType` (FQCN), `value` (as stored)                                                    |
| `property.deleted`              | Property deleted from the build                                    | `propertyType`                                                                                |
| `link.added`                    | Link added from the build                                          | `target` (build), `qualifier`                                                                 |
| `link.removed`                  | Link removed from the build                                        | As `link.added`, plus `reason` for a cascade                                                  |
| `validation.run`                | Build validated                                                    | `validationStamp`, `validationRun`, `status`, `data: {type, sha256}`, `claimed`               |
| `validation.status`             | New status of a validation run                                     | `validationStamp`, `validationRun`, `status`, `description`, `claimed`                        |
| `validation.comment`            | Comment of a validation run status edited                          | `validationStamp`, `validationRun`, `validationRunStatusId`, `comment`                        |
| `validation.data`               | Data of a validation run replaced                                  | `validationStamp`, `validationRun`, `data: {type, sha256}` — no `data` when removed           |
| `validation.deleted`            | Validation run deleted                                             | `validationStamp`, `validationRun`, `status` (its last one), plus `reason` for a cascade       |
| `evidence.attached`             | Evidence attached to a validation run of the build                 | `validationStamp`, `validationRun`, `evidence: {id, fileName, mediaType, size, sha256, source: {tool, version, url}, externalDigest}` |
| `evidence.deleted`              | Evidence deleted                                                   | `validationStamp`, `validationRun`, `evidence: {id, fileName, sha256}`                        |
| `promotion.added`               | Build promoted                                                     | `promotionLevel`, `promotionRun: {id}`, `description`, `claimed`                              |
| `promotion.removed`             | Promotion run deleted                                              | `promotionLevel`, `promotionRun`, plus `reason` for a cascade                                 |
| `runinfo.set`                   | Run info set on the build or one of its validation runs            | `runnable`, `runInfo: {sourceType, sourceUri, triggerType, triggerData, runTime}`             |
| `runinfo.deleted`               | Run info deleted                                                   | `runnable`                                                                                    |
| `deployment.created`            | Deployment of the build started in an environment slot             | `deployment: {id, number, environment, slot: {id, qualifier}}`, `message`, `claimed`          |
| `deployment.running`, `deployment.done`, `deployment.failed`, `deployment.cancelled` | Deployment progressed | As `deployment.created`                                                       |
| `deployment.rule-data`          | Data set for an admission rule of a deployment                     | `deployment`, `rule: {id, name, ruleId}`, `data: {sha256}`, `claimed`                         |
| `deployment.rule-overridden`    | Admission rule of a deployment overridden                          | `deployment`, `rule`, `message`, `claimed`                                                    |
| `deployment.workflow-overridden` | Result of a slot workflow overridden                              | `deployment`, `slotWorkflow: {id, instanceId, workflow}`, `message`                           |
| `deployment.deleted`            | Deployment deleted                                                 | `deployment`, `status` (when deleted)                                                         |

`runnable` is `{type: "build"}` or `{type: "validation_run", validationStamp, validationRun}`. The
data of a validation run and of an admission rule is recorded by the SHA-256 of its
[canonical form](#canonical-json), not as such; a property value which canonical JSON does not
accept — a decimal number, a very large integer — is written as a string.

**Cascades.** A deletion which reaches builds beyond the deleted entity writes an entry on each of
them, with a `reason`:

| Deleted                       | Entry on each affected build | `reason`                            |
|-------------------------------|------------------------------|-------------------------------------|
| A validation stamp            | `validation.deleted`         | `cascade/validation-stamp-deleted`  |
| A promotion level             | `promotion.removed`          | `cascade/promotion-level-deleted`   |
| A build other builds link to  | `link.removed`               | `cascade/target-build-deleted`      |

Deleting a branch or a project writes nothing: the trails go with the builds.

An entry is written in the database transaction of its change: both are committed, or neither.
A GraphQL mutation made of several steps writes several entries, and a change committed despite an
error is recorded, since it was committed.

### Actor

The `actor` of an entry is taken from the security context when the entry is written — never from
what the caller says:

```json
{"account": "ci@example.com", "via": "token", "tokenName": "ci-pipeline"}
```

| Field        | Description                                                                                                  |
|--------------|--------------------------------------------------------------------------------------------------------------|
| `account`    | Email of the account, or `system`.                                                                           |
| `via`        | `ui`, `token`, `jwt`, `webhook` or `system`.                                                                 |
| `tokenName`  | Name of the API token — never its value — for `token` and `webhook`.                                         |
| `jwt`        | `{iss, sub}` of the JWT, for `ui` and `jwt`.                                                                 |
| `system`     | Why the system acts, when it acts as administrator.                                                          |
| `onBehalfOf` | The actor whose action led the system to act.                                                                |

Absent fields are left out. The channels:

* `ui` — a JWT issued to the web UI: its `azp` claim is one of
  `ontrack.config.security.authorization.jwt.ui-clients` (default: `ontrack-client`,
  `yontrack-client`);
* `jwt` — any other JWT;
* `token` — an [API token](../security/tokens.md);
* `webhook` — a webhook authenticated by a token, like the GitHub ingestion;
* `system` — Yontrack itself, acting as administrator. The `system` field gives the reason, like
  `auto-promotion`, `github-ingestion`, `sonarqube`, `branch-auto-enable`,
  `release-validation`, `validation-notification`, or `job:<category>/<type>` for a background
  job, and `onBehalfOf` the actor which set it off:

    ```json
    {"account": "system", "via": "system", "system": "auto-promotion", "onBehalfOf": {"account": "ci@example.com", "via": "token", "tokenName": "ci-pipeline"}}
    ```

In 6.0, the identity of a CI pipeline is the API token it uses: give each pipeline its own token,
with a meaningful name.

### `claimed`

The `time` of an entry is the server clock when it is written, and its `actor` the security
context. What a **caller supplied** — the time and user of a validation run, of a build creation,
of a promotion, of a deployment transition, or the `sender` of a GitHub event — is kept apart, in
the payload, as `claimed`:

```json
"claimed": {"time": "2026-10-02T08:15:29.871Z", "user": "ci-bot"}
```

A back-dated validation run is therefore visible: its `claimed.time` is earlier than the `time` of
its entry.

### Canonical JSON

The `actor` and `payload` of an entry, and the envelope which is hashed, are written in the
canonical form of [RFC 8785](https://www.rfc-editor.org/rfc/rfc8785) (JSON Canonicalization
Scheme), on the subset of JSON a trail accepts:

* **strings**, written as ECMAScript's `JSON.stringify` writes them: `"` and `\` escaped as `\"`
  and `\\`; the characters below U+0020 escaped — `\b`, `\t`, `\n`, `\f`, `\r` in their short
  forms, the others as `\u00xx` in lowercase hexadecimal; every other character as it is, `/`,
  U+007F and U+2028 included, encoded in UTF-8;
* **objects**, their properties sorted by the **UTF-16 code units** of their names, without any
  whitespace;
* **arrays**, in their order, without any whitespace;
* `true`, `false` and `null`;
* **integers** within ±(2⁵³ − 1), in decimal.

Anything else is rejected: decimal numbers — even `1.0` or `1e3` —, integers out of that range,
strings with a lone surrogate. Payloads carry decimals as strings.

### Hash format v1

The **hash** of an entry of schema version 1 is the SHA-256, written as 64 lowercase hexadecimal
characters, of the UTF-8 bytes of the canonical form of its **envelope**:

```
{
  "schemaVersion": 1,
  "seq": <seq>,
  "type": "<type>",
  "time": "<time>",
  "actor": <actor>,
  "prevHash": "<hash of the entry seq - 1>" | null,
  "payload": <payload>
}
```

* `schemaVersion` — the integer `1`;
* `seq` — the position of the entry in the trail of its build, from 1, without gap;
* `type` — the [type](#entry-types) of the entry;
* `time` — the server time, ISO-8601 in UTC with **exactly three** digits of milliseconds and a
  `Z`, anything finer truncated: `2026-10-02T08:15:30.120Z`;
* `actor` — the [actor](#actor), a JSON object;
* `prevHash` — the hash of the entry `seq − 1`, `null` for seq 1;
* `payload` — the payload, a JSON object.

The canonical form sorts the properties of the envelope, which gives, for the first entry of a
trail:

```
{"actor":{"account":"ci-bot","tokenName":"ci-demo","via":"token"},"payload":{"build":{"branch":"release-2.4","id":1042,"name":"2.4.7","project":"payments"},"claimed":{"time":"2026-10-02T08:15:29.871Z","user":"ci-bot"}},"prevHash":null,"schemaVersion":1,"seq":1,"time":"2026-10-02T08:15:30.000Z","type":"build.created"}
```

whose SHA-256 is `4b320b3f1628780eaf5adbc9153975aa05eeb25fd082f97b111588499a6550a6`.

The first entry of a trail is `build.created` or `trail.opened`, and its `payload.build.id` names
its build: an exported trail is bound to its build.

**This format is a compatibility contract.** An entry of schema version 1 is verified with it for
as long as it exists; a change to what is hashed, or how, is a new schema version, and the
verification keeps supporting the earlier ones.

### Endorsement format

An **endorsement** of an entry is:

* `keyId` — the ID of the key which signed it: the first 16 lowercase hexadecimal characters of the
  SHA-256 of the DER `SubjectPublicKeyInfo` of its public key — what the base64 body of the PEM
  decodes to;
* `signature` — the Ed25519 ([RFC 8032](https://www.rfc-editor.org/rfc/rfc8032)) signature of the
  **32 bytes** of the hash of the entry — its 64 hexadecimal characters, decoded — in standard
  base64 with padding (88 characters);
* `time` — when it was made, in the same format as the time of an entry. It is not signed.

Nothing but the hash is signed: it covers the entry and, through `prevHash`, the trail before it.

### Verification

The verification of a trail uses its entries, their endorsements and the public keys of the
instance, and nothing else. For the entry at position *n* (from 1), in the order of the trail:

1. **Seq** — its `seq` is *n*;
2. **Schema version** — its `schemaVersion` is supported: `1`;
3. **Hash** — the hash of its envelope, computed with the format of its schema version, is its
   stored `hash`;
4. **Previous hash** — its `prevHash` is `null` for *n* = 1, and the **stored** `hash` of the entry
   *n* − 1 otherwise;
5. **First entry** — for *n* = 1, its `type` is `build.created` or `trail.opened`, and its
   `payload.build.id` is the ID of the build;
6. **Endorsements** — each of its endorsements names a known key, and its `signature` verifies
   against the stored `hash` of the entry with that key.

A key is known when it is an `Ed25519` key whose PEM can be read and whose `keyId` is the ID of its
public key; any other key is ignored, and what it endorsed is reported with an unknown key.

Checks 1 to 5 are the **chain**; check 6, the **endorsements**, is apart: a trail whose hashes were
all recomputed after an edit has an intact chain, and only its endorsements tell. An entry
**without** endorsement is not a failure.

The result:

| Field                        | Description                                                                                          |
|------------------------------|------------------------------------------------------------------------------------------------------|
| `chainIntact`                | No check 1 to 5 fails.                                                                               |
| `firstBrokenSeq`             | Position of the first entry failing a check 1 to 5 — the trail is verified up to the one before it. `null` when the chain is intact. |
| `endorsementsValid`          | No check 6 fails.                                                                                    |
| `firstInvalidEndorsementSeq` | Position of the first entry failing check 6, `null` when none does.                                  |
| `partial`                    | The first entry is `trail.opened`.                                                                   |
| `unendorsedFromSeq`          | Position of the first entry after the last endorsed one — 1 when none is endorsed. `null` when the last entry is endorsed, or the trail is empty. |
| `problems`                   | Every failed check: `seq` (position), `type` — `SEQ`, `SCHEMA_VERSION`, `HASH`, `PREVIOUS_HASH`, `FIRST_ENTRY`, `BUILD` for the chain; `ENDORSEMENT`, `UNKNOWN_KEY` for the endorsements — and a `message`. |
| `missingEvidence`            | With the evidence verified: positions of the `evidence.attached` entries whose file is absent from the storage. `null` otherwise. |
| `alteredEvidence`            | With the evidence verified: positions of the `evidence.attached` entries whose file no longer has the recorded SHA-256. `null` otherwise. |

A trail is **tampered with** when its chain is not intact or its endorsements are not valid.

When the evidence is verified, the file of every `evidence.attached` entry is read back from the
storage and hashed again, against the `payload.evidence.sha256` of the entry — what the trail says
was attached, whatever the evidence says now. The `evidence.attached` entry of an evidence which a
later `evidence.deleted` entry names is skipped: its file may be gone, as the trail says. A
deleted entry or a removed file is therefore never silent.

In GraphQL:

```graphql
query BuildTrail($id: Int!) {
  build(id: $id) {
    auditTrail {
      entries { seq time type actor payload prevHash hash }
      endorsements { seq keyId signature time }
      verification(includeEvidence: true) {
        chainIntact
        firstBrokenSeq
        endorsementsValid
        firstInvalidEndorsementSeq
        partial
        unendorsedFromSeq
        missingEvidence
        alteredEvidence
        problems { seq type message }
      }
    }
  }
}
```

`auditTrail` is `null` when the build has no trail to read: the license is off and no entry was
ever written for it.

#### Daily verification

A job verifies, **every day**, the trails of the builds which gained entries since its previous
run, chain and endorsements, without reading the evidence back. Each trail found tampered with
posts the [`trail.verification.failed`](../generated/events/event-trail.verification.failed.md)
event on its build — subscribe to it to be told — and increments the
`ontrack_audit_trail_verification_failures` [metric](../generated/metrics/net.nemerosa.ontrack.extension.audittrail.metrics.AuditTrailMetrics.md).

A trail which gains no entry is not verified again: an entry tampered with afterwards is found
when its trail next gains an entry, or by a verification on demand. The first run of the job
verifies every trail.

### JSON export

The **export** of the trail of a build is a self-sufficient JSON document: its entries, their
endorsements and the public keys of the instance.

* REST: `GET /rest/extension/audit-trail/builds/{buildId}/export`, downloaded as
  `audit-trail-<project>-<branch>-<build>.json`;
* the *Export JSON* command of the audit trail page;
* KDSL: `Build.exportTrail()`.

It is available whatever the license, as long as the build has a trail.

```json
{
  "exportVersion": 1,
  "exportedAt": "2026-10-02T09:00:00.000Z",
  "build": {"id": 1042, "project": "payments", "branch": "release-2.4", "name": "2.4.7"},
  "keys": [
    {"keyId": "06e3fd8fda29bb60", "algorithm": "Ed25519", "publicKey": "-----BEGIN PUBLIC KEY-----\n…\n-----END PUBLIC KEY-----\n"}
  ],
  "entries": [
    {
      "seq": 1,
      "schemaVersion": 1,
      "type": "build.created",
      "time": "2026-10-02T08:15:30.000Z",
      "actor": {"account": "ci-bot", "via": "token", "tokenName": "ci-demo"},
      "prevHash": null,
      "payload": {"build": {"id": 1042, "project": "payments", "branch": "release-2.4", "name": "2.4.7"}, "claimed": {"time": "2026-10-02T08:15:29.871Z", "user": "ci-bot"}},
      "hash": "4b320b3f1628780eaf5adbc9153975aa05eeb25fd082f97b111588499a6550a6",
      "endorsements": [
        {"keyId": "06e3fd8fda29bb60", "signature": "TbRpefKqc1a4K1iXhVIAYiib+udglKU1/1o7SPKi1zuGD1AyreSpjahnzcdwIooEZa+nAtteCEpVWKXMTD/OAg==", "time": "2026-10-02T08:15:30.000Z"}
      ]
    }
  ]
}
```

| Field           | Description                                                                                   |
|-----------------|-----------------------------------------------------------------------------------------------|
| `exportVersion` | Version of the shape of the document: `1`. A change to the shape is a new version.            |
| `exportedAt`    | Server time of the export.                                                                    |
| `build`         | `{id, project, branch, name}` of the build, which the first entry names.                      |
| `keys`          | Public keys of the instance: `{keyId, algorithm, publicKey}`, the public key in PEM.          |
| `entries`       | Entries, by seq. Each holds its envelope — `seq`, `schemaVersion`, `type`, `time`, `actor`, `prevHash`, `payload` — its `hash`, and its `endorsements`, none when it was written unendorsed. |

The fields of an entry but `hash` and `endorsements` are its envelope, as hashed. The server
verifies a trail the same way, from its export: one algorithm, online and offline.

### Verifying a trail offline

The export carries its own keys, so it proves that it was not altered since it was exported, and
by whom it was endorsed. To prove that it was endorsed by **your** instance, compare its keys with
the ones the instance publishes (`GET /rest/extension/audit-trail/keys`), or with a copy you kept.

To verify an export:

1. parse it with a JSON parser which keeps integers as integers;
2. keep the `keys` whose `algorithm` is `Ed25519` and whose `keyId` is the ID of their public key;
3. for each entry, in the order of the document, run the [checks](#verification), the build ID of
   check 5 being `build.id`: rebuild the envelope from the entry, write it in
   [canonical JSON](#canonical-json), hash it, compare, and verify each endorsement.

One endorsement can be checked with OpenSSL, given the public key in `key.pem`, the entry `hash`
and its `signature`:

```bash
echo -n "$HASH" | xxd -r -p > hash.bin
echo -n "$SIGNATURE" | base64 -d > signature.bin
openssl pkeyutl -verify -pubin -inkey key.pem -rawin -in hash.bin -sigfile signature.bin
```

The offline verification command of the `yontrack` CLI is coming
([yontrack/yontrack-cli#83](https://github.com/yontrack/yontrack-cli/issues/83)).

### Test vectors

The reference vectors of the hash format, the endorsements and the exports are in the Yontrack
repository, under
[`ontrack-extension-audit-trail/src/test/resources/audit-trail/test-vectors/`](https://github.com/yontrack/yontrack/tree/main/ontrack-extension-audit-trail/src/test/resources/audit-trail/test-vectors).
An implementation of the verification should pass all of them:

| File                                   | Holds                                                                                              |
|----------------------------------------|----------------------------------------------------------------------------------------------------|
| `01-build-created.json`                | A trail opened by `build.created`: for each entry, its `envelope`, its `canonical` form and its `hash`. |
| `02-trail-opened.json`                 | A partial trail, opened by `trail.opened`.                                                         |
| `03-canonical-forms.json`              | One entry exercising the canonical form: property order by UTF-16 code units, escapes, non-ASCII text, integers at the limits, nesting, empty containers. |
| `endorsements/01-rfc8032-test1.json`   | The endorsements of the entries of `01-build-created.json` by the published key of RFC 8032, section 7.1, test 1, with its `keyId`. |
| `exports/01-build-created.json`        | An intact export, validly endorsed.                                                                |
| `exports/02-tampered-payload.json`     | The same export, the payload of seq 2 edited: its chain is broken at seq 2.                        |

### API

| Operation                         | REST                                                                    | GraphQL                                        |
|-----------------------------------|-------------------------------------------------------------------------|------------------------------------------------|
| Read the trail of a build         |                                                                         | `Build.auditTrail { entries endorsements }`    |
| Verify it                         |                                                                         | `Build.auditTrail { verification(includeEvidence) }` |
| Export it                         | `GET /rest/extension/audit-trail/builds/{buildId}/export`               |                                                |
| Public keys                       | `GET /rest/extension/audit-trail/keys`                                  | `auditTrailKeys`                               |
| Evidence of a validation run      |                                                                         | `ValidationRun.evidence`                       |
| Upload evidence                   | `POST /rest/extension/audit-trail/validation-runs/{validationRunId}/evidence` |                                          |
| Download evidence                 | `GET /rest/extension/audit-trail/evidence/{evidenceId}/download`        |                                                |
| Delete evidence                   | `DELETE /rest/extension/audit-trail/evidence/{evidenceId}`              | `deleteEvidence`                               |
| State of the evidence storage     |                                                                         | `auditTrailStorageState`                       |
| Status of the audit trail         |                                                                         | `auditTrailStatus` (global settings)           |

### Events and metrics

Events, which [notifications](../integrations/notifications/index.md) can subscribe to on a build,
its branch or its project:

* [`evidence.attached`](../generated/events/event-evidence.attached.md);
* [`evidence.deleted`](../generated/events/event-evidence.deleted.md);
* [`trail.verification.failed`](../generated/events/event-trail.verification.failed.md).

[Metrics](../generated/metrics/net.nemerosa.ontrack.extension.audittrail.metrics.AuditTrailMetrics.md):

* `ontrack_audit_trail_append` — duration of the append of an entry, by `type`;
* `ontrack_audit_trail_verification_failures` — trails found tampered with by the daily
  verification;
* `ontrack_audit_trail_evidences` and `ontrack_audit_trail_evidence_bytes` — number and total size
  of the evidence of each project, refreshed every 15 minutes.

## Demo tampering switch

A demonstration needs a trail which fails its verification. The property
`ontrack.extension.audit-trail.demo-tampering.enabled` — environment variable
`ONTRACK_EXTENSION_AUDITTRAIL_DEMOTAMPERING_ENABLED` — enables an endpoint which rewrites the payload
of one entry, recomputing nothing:

```
PUT /rest/extension/audit-trail/demo-tampering/builds/{buildId}/entries/{seq}/payload
```

Its body is the new payload, a JSON object; only the global administrators may call it. The trail
then reads *Broken at seq N*.

**Never enable it in production.** While it is on, any trail of the instance can be rewritten by an
administrator without a trace: its trails prove nothing. A permanent **error global message** says
so to every user, whatever the license: *This instance allows trail tampering for demonstration:
its trails prove nothing.*

The switch is off by default. While it is off, the endpoint does not exist: it answers `404`.

## Release note for 6.0

Yontrack's release notes are written in its wiki. This is the paragraph they carry for the audit
trail:

> **Audit trail and evidence** (licensed, `extension.audit-trail`). Yontrack 6.0 records the story
> of every build — creation, properties, links, validations, promotions, run info, deployments — in
> an append-only trail, hash-chained and signed with an Ed25519 key of the instance, written in the
> same transaction as each change, with the actor who made it (account, API token name, JWT issuer
> and subject, or the system and its reason). Validation runs carry evidence — reports, SBOMs,
> screenshots — stored in an S3-compatible bucket and recorded by their SHA-256. A trail is
> verified in the UI, through the API and daily, and exported as JSON to be verified offline.
> What 6.0 gives is **integrity and origin**: this Yontrack produced this history, and it was not
> rewritten. It does **not** give third-party proof of time (no TSA), sealing, retention
> independent of the builds (a trail goes with its build), detection of the periods during which
> the license was off, CI OIDC federation, nor Object Lock on DigitalOcean Spaces.
