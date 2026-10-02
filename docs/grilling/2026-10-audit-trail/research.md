# V6 — Validation evidence & build audit trail: notarization / non-repudiation research

_Research note, 2026-09-25. Context: V6 track to attach evidences (scanner PDF/HTML reports, etc.) to validation runs and assemble, on demand, a build-level audit trail usable for internal and external audits. Section 5 records the decisions taken on the same day; section 6 is the retention proposal. Vocabulary follows `v6-scope.md` ("trail", never "ledger", which is the import format)._

## 1. What "non-repudiation" needs, concretely

Auditors and courts look for four properties on a piece of evidence:

| Property | Question | Mechanism |
|---|---|---|
| Integrity | Has the file changed since it was captured? | Cryptographic hash (SHA-256+) recorded at capture time |
| Existence-in-time | Did it exist, in this form, before time T? | Trusted timestamp from a third party (RFC 3161 / eIDAS qualified) |
| Origin / authenticity | Who or what produced/collected it? | Digital signature or seal by the collector (Yontrack instance, CI identity) |
| Continuity (chain of custody) | Can an outsider reconstruct every step, unbroken? | Append-only log + hash chaining / Merkle tree + documented procedure (ISO/IEC 27037: auditability, repeatability, justifiability) |

Key point from practitioners (FinQub, DEV): **"append-only is not enough"** — an examiner wants a cryptographic proof that history was not rewritten, anchored to something the system operator does not control (external TSA or transparency log).

## 2. Relevant norms and standards

### 2.1 Timestamping (existence-in-time)
- **RFC 3161 — Time-Stamp Protocol (TSP).** Client sends a hash; a Time-Stamping Authority returns a CMS-signed token binding hash + UTC time + serial. The data itself never leaves the client. Universally supported (BouncyCastle `TimeStampToken` in JVM; DigiCert, Sectigo, freetsa.org, and any eIDAS QTSP expose endpoints). ANSI X9.95 extends it for finance.
- **eIDAS (EU 910/2014, amended by eIDAS 2.0 / EU 2024/1183).** Distinguishes *electronic time stamps* (admissible, Art. 41(1)) from **qualified electronic time stamps**, which enjoy a **legal presumption of accuracy of date/time and integrity of the data** (Art. 41(2)) and are recognised across all member states (Art. 41(3)). Qualified ones must be issued by a QTSP listed in the EU Trusted List, UTC-traceable, signed with advanced signature/seal (Art. 42).
- **Qualified electronic seal** (Art. 35–38): the legal-person equivalent of a signature — "this organisation vouches for this document". Relevant if a customer wants the Yontrack instance (or the company) to seal exported audit packages.

### 2.2 Long-term integrity (evidence must outlive certificates/algorithms)
- **RFC 4998 Evidence Record Syntax (ERS)** and **RFC 6283 XMLERS**: standard structure for *archive timestamps*: Merkle hash tree over many objects → one TSA timestamp; **renewal chains** (re-timestamp before the TSA cert expires or the hash algorithm weakens). Explicitly designed for "long-term non-repudiation of existence and integrity of data".
- **ETSI TS 119 511 / 119 512**: requirements/protocol for long-term *preservation services* (the eIDAS-world profile of ERS).
- **Signature formats with LTV**: CAdES (ETSI EN 319 122), XAdES (EN 319 132), PAdES (EN 319 142, for PDF), JAdES (TS 119 182, JSON). Baseline levels B → T (timestamped) → LT (validation data embedded) → LTA (archive timestamp).
- **ASiC — Associated Signature Containers (ETSI EN 319 162-1/-2)**: a ZIP with the data objects + `META-INF/` holding CAdES/XAdES signatures, RFC 3161 tokens and/or ERS evidence records. ASiC-S = one object, ASiC-E = many. This is the *standard container for "a bundle of files + proofs"* and is what EU authorities/notaries exchange. Very good fit for an exported build audit package.

### 2.3 Evidence handling / audit-trail regulation
- **ISO/IEC 27037:2012** (identification, collection, acquisition, preservation of digital evidence) and **ISO/IEC 27050** (eDiscovery): hash at acquisition, record operator + conditions + time, document every transfer. **RFC 3227** is the older IETF guideline on the same.
- **ISO/IEC 27001 (A.8.15 logging, A.5.28 evidence collection)** and **SOC 2 (CC7.x)**: require protected, complete, retained logs; auditors ask for exports whose integrity can be shown.
- **FDA 21 CFR Part 11 / EU Annex 11** (life sciences): computer-generated, time-stamped, secure audit trails that record who/what/when and cannot obscure earlier entries; retention for the record's lifetime. Yontrack customers in regulated industries will quote these.
- **WORM / object lock** (S3 Object Lock compliance mode, Azure immutable blobs): physical immutability of the stored evidence for a retention window; commonly accepted by examiners alongside hashes.

### 2.4 Software-supply-chain attestations (the CI/CD-native vocabulary)
- **in-toto Attestation Framework v1**: `Statement` = list of *subjects* (name + digest) + `predicateType` + `predicate`; wrapped in a **DSSE** signed envelope; grouped in *bundles*. Standard predicates include **SLSA Provenance**, **Vulnerabilities (vulns)**, **Test Result**, **SCAI**, plus custom types (any URI). A scan report attached to a build maps naturally to `subject = build artifact digests, predicate = {tool, report digest, verdict}`.
- **SLSA v1.0**: attestations must be bound to artifacts (not releases), be immutable once published, and should be published to a transparency log.
- **Sigstore**: Fulcio (keyless certs from OIDC identity — e.g. a GitHub/GitLab workflow), **Rekor** (public append-only Merkle transparency log, RFC 6962/9162-style inclusion proofs), timestamp counter-signature. **GitHub Artifact Attestations / GitLab SLSA-3** produce Sigstore bundles that can be verified offline (`gh attestation verify`). Private repos use GitHub's private store, not public Rekor.
- **Certificate Transparency (RFC 6962 → RFC 9162)** / Trillian / Russ Cox's "transparent logs": the reference design for a tamper-evident append-only log with O(log n) inclusion and consistency proofs.

## 3. Practices observed in comparable products
- Compliance-automation tools (Vanta/Drata-style) and audit-log SaaS: per-record SHA-256 hash chain, periodic signed checkpoints, key-rollover signed by outgoing key, export = JSON/CSV + manifest + signature, optional RFC 3161 anchor.
- Digital-forensics tools: hash at acquisition, RFC 3161 token stored next to the object, chain-of-custody form listing operator/time/tool.
- Supply-chain tools (GitHub, GitLab, Xygeni, Chainguard): in-toto statement per artifact, DSSE-signed, logged to Rekor, verified from a bundle.

## 4. Design options for Yontrack V6

Ordered from cheapest to most "notarial". They stack — each level keeps the previous.

### Level 0 — Evidence capture with integrity (baseline, do in V6)
- New *evidence* attached to a **validation run** (0..n per run): stored blob (PDF/HTML/JSON/…), `mediaType`, `fileName`, `size`, **`sha256`** computed server-side on receipt, `collectedAt`, `collectedBy` (user/token), `source` (tool name/version, URL of the origin), optional `externalDigest` claimed by the uploader (mismatch = reject).
- Evidence is **immutable**: no update/replace; a new upload is a new evidence. Deletion only via retention policy, and the deletion itself is a recorded event.
- Store blobs in an S3-compatible object store (decision, see §5) — lets customers enable Object Lock / WORM themselves.

### Level 1 — Tamper-evident audit trail (recommended for V6)
- An **append-only trail per build**: each entry = canonical JSON of {event, previous entry hash}; entry hash = SHA-256 over it → **hash chain**. A Merkle root over all entries gives a single **build digest**. Entries cover the whole build story (decision, §5): creation, links/dependencies, validation runs and their evidence, promotions, workflow outcomes, run-info, deployments.
- Expose `GET /builds/{id}/audit-trail` (on demand) that renders the chain and verifies it; any break is reported.
- **Instance signing key** (Ed25519 or RSA-PSS, stored in Vault via existing extension or KMS): sign each checkpoint/build digest so a customer can prove the trail was produced by *this* Yontrack. Document key rollover (new key signed by old). Where the CI caller presents an identity (OIDC token, API token), that identity is recorded on the entry as well (decision, §5).

### Level 2 — External anchoring (notarization, third-party time)
- On demand ("Notarize this build" action, or on promotion), request an **RFC 3161 timestamp** on the build digest from a configurable TSA URL and store the token with the trail. Free/public TSAs suffice for internal audit; a **eIDAS-qualified TSA** gives the legal presumption for external/legal use — make the TSA URL + cert chain a settings item so the customer chooses. Optional and configurable (decision, §5).
- Alternative or complement: publish the digest as an **in-toto statement in DSSE** to **Rekor** (public) or a private Rekor instance — gives a third-party inclusion proof and speaks the CI-native language. Private-instance customers will not want the public log; keep it optional.
- For long retention (years), implement **ERS-style renewal**: re-timestamp trails before TSA cert expiry — a scheduled job over the existing job framework.

### Level 3 — Exportable audit package
- "Export audit trail" produces a **ZIP** containing: the evidence files, `manifest.json` (all metadata + hashes + chain), `trail.intoto.jsonl` (DSSE-signed statements, one per evidence, subjects = build artifact digests where known), the RFC 3161 token(s), the instance public key/cert, and a human-readable `index.html`/PDF summary for the auditor.
- If a customer needs EU-notarial grade: lay the ZIP out as **ASiC-E** (`mimetype` first, `META-INF/` with CAdES-T/LTA signature and the timestamp) so standard eIDAS validators (e.g. EU DSS, Adobe) verify it with no Yontrack code. This is an *option*, not the default — most customers will verify with `sha256sum` + a small `yontrack verify` CLI/KDSL command.

### Suggested defaults for V6
1. Level 0 + Level 1 built in (no external dependency beyond S3) — see `v6-scope.md`.
2. Level 2 RFC 3161 as an optional configured TSA (BouncyCastle, ~200 lines); Rekor/in-toto as a follow-up if supply-chain customers ask.
3. Level 3 plain ZIP + manifest + DSSE statements; ASiC-E as a later extension if eIDAS demand appears.
4. Trail lifecycle decoupled from build lifecycle via *sealing* (§6).

## 5. Decisions (2026-09-25)

| Question | Decision |
|---|---|
| Scope of the trail | **The whole build story**, not only validation evidence: dependencies/links, validations (+ evidence), promotions, workflows, run-info, deployments. |
| Signer identity | **Both**: the instance signs the trail; the CI/caller identity (OIDC / API token) is recorded on each entry it produced. |
| Storage | **S3-compatible object storage**, introduced with this feature. The whole initiative is a **licensed feature**, so the S3 configuration is **optional** (feature off → no storage needed). Dev/test: a MinIO container/pod with attached volume (compose + Helm). |
| TSA | **Optional and configurable**. Damien to ask users whether they run a corporate TSA. |
| Retention | Proposal in §6, to be confirmed. |
| 6.0 scope | Levels 0 + 1 (2026-10-02), see `v6-scope.md`. |

## 6. Retention proposal: decouple the trail from the build via sealing

The conflict: build retention prunes builds after weeks/months; an audit trail must typically be kept for years (SOC 2: ≥ 1 year, many regulated sectors: 5–10+ years). Resolving it by keeping builds longer is the wrong lever — builds are operational data. Proposal:

### 6.1 Two states for a trail
- **Live trail** — the trail of a build that still exists. Rows in Postgres (chain entries) + evidence objects in S3 under `blobs/<sha256>`. Grows as the build's story continues. Follows the build's lifecycle.
- **Sealed trail (audit package)** — a self-contained, immutable package written to S3 under `sealed/<project>/<build-uuid>/<seal-id>/` containing everything of Level 3 (evidence files, manifest, chain, DSSE statements, TSA token if configured, instance public key, HTML summary). Once written it depends on nothing in Postgres. Sealing = the notarization moment: the build digest is signed and, if a TSA is configured, timestamped.

A build can be sealed several times (a seal after GOLD promotion, then a later one after a hotfix deployment); each seal is a superset and references the previous seal's digest, so the seals themselves form a chain.

### 6.2 When sealing happens
- **On demand** — "Seal audit trail" action / API / KDSL (the stated V6 requirement).
- **On policy** (optional, per project): seal automatically on promotion to given levels (e.g. `RELEASE`), or on deployment to a given environment. This is what makes the next point safe.
- **On purge** (safety net): when build retention is about to delete a build that has a live trail and matches a per-project condition (default: *has at least one evidence or one promotion*), seal first, then delete. Builds matching nothing are deleted with their live trail, and the deletion is itself the last trail entry, recorded in the instance trail (see 6.4).

### 6.3 Retention of sealed packages, independent of build retention
- A separate **audit retention** setting, per project with an instance default, expressed in years. Default: **indefinite** (never auto-deleted). The feature must not silently destroy the thing it exists to preserve.
- Deletion of a sealed package (by policy expiry or by an admin action) is a recorded, signed entry in the instance trail, with the package's digest — so even the absence of a package is accounted for.
- When the customer enables **S3 Object Lock** on the `sealed/` prefix, Yontrack sets the object retention to the audit retention at write time (compliance mode if they want it un-deletable even by admins). Yontrack documents that Object Lock retention must be ≥ audit retention or writes will fail/lock inconsistently.
- Storage cost stays bounded: sealed packages are only created for builds that met the sealing condition, and evidence blobs are shared by digest (content-addressed `blobs/<sha256>`) so a re-seal does not duplicate a 40 MB PDF.

### 6.4 Findability after the build is gone
- A small **`audit_seal` table**: project, branch name, build name, build uuid, seal id, sealed-at, build digest, TSA time (if any), S3 key, status. Rows survive build deletion (this is the one intended exception to "nothing remembers deleted entities"; it stores a reference to an external package, not entity data).
- UI: project-level **"Audit trails"** page listing seals, with build name/date filters, "verify" (re-hash the package and check the chain/signature/timestamp) and "download".
- If the ledger/harvesting work later re-creates a purged build with the same name, the new build gets a new live trail; the old seal remains listed and is linked by name, not merged.

### 6.5 Long-term validity
- A scheduled job re-timestamps sealed packages before their TSA certificate expiry (ERS-style renewal), appending the new token to the package's `META-INF`-like folder without touching the sealed content. Only relevant when a TSA is configured.
- Algorithm agility: manifests record the hash algorithm; a future SHA-3 migration re-hashes and re-seals as a new seal referencing the old.

### 6.6 What to decide next
- Default sealing condition on purge (proposal: evidence present or promoted).
- Whether "seal on promotion" ships with sealing or the on-demand action only.
- Whether the instance trail (deletions, key rollovers, config changes to the audit feature) ships with sealing — recommended, it is small and auditors ask "who changed the audit settings".

## 7. Sources
- RFC 3161: https://datatracker.ietf.org/doc/html/rfc3161 — overview: https://evidency.io/en/rfc-3161-timestamping/
- eIDAS qualified timestamps: https://nhimg.org/glossary/qualified-electronic-timestamp/ , https://evidency.io/en/qualified-timestamping/ , seals: https://en.wikipedia.org/wiki/Electronic_seal
- RFC 6283 XMLERS: https://www.rfc-editor.org/rfc/rfc6283.html ; RFC 4998 ERS: https://dl.acm.org/doi/abs/10.17487/RFC4998
- ASiC: https://en.wikipedia.org/wiki/Associated_Signature_Containers ; ETSI EN 319 162-1: https://www.etsi.org/deliver/etsi_en/319100_319199/31916201/01.01.01_60/en_31916201v010101p.pdf ; CAdES EN 319 122-1: https://www.etsi.org/deliver/etsi_en/319100_319199/31912201/01.03.01_60/en_31912201v010301p.pdf
- ISO/IEC 27037 chain of custody: https://truescreen.io/insights/digital-chain-custody-technical-requirements/ , https://www.iso27001security.com/html/27037
- Tamper-evident audit logs (hash chain / signed exports / WORM): https://finqub.io/learn/tamper-evident-audit-trail/ , https://dev.to/gentlyding/how-we-built-a-tamper-evident-audit-log-for-soc-2-and-iso-27001-evidence-jl4
- 21 CFR Part 11 audit trails: https://simplerqms.com/21-cfr-part-11-audit-trail/
- in-toto attestation framework: https://github.com/in-toto/attestation/blob/main/spec/README.md ; predicates: test-result https://github.com/in-toto/attestation/blob/main/spec/predicates/test-result.md , vulns https://github.com/in-toto/attestation/blob/main/spec/predicates/vuln.md , SCAI https://github.com/in-toto/attestation/blob/main/spec/predicates/scai.md
- SLSA distributing provenance: https://slsa.dev/spec/v1.0/distributing-provenance
- GitHub Artifact Attestations: https://github.blog/news-insights/product-news/introducing-artifact-attestations-now-in-public-beta/ ; GitLab SLSA-3: https://handbook.gitlab.com/handbook/engineering/architecture/design-documents/slsa_level_3
- Transparency logs: RFC 9162 https://www.rfc-editor.org/rfc/rfc9162.html ; https://research.swtch.com/tlog
