# Yontrack V6 — Evidence & audit trail: glossary

Terms used in the client call checklist, in the order they appear.

## Audit & regulation

- **Audit trail** — Chronological, tamper-evident record of what happened to a build (validations, evidence, promotions, deployments…) that an auditor can re-verify independently.
- **Evidence** — A file produced by an external tool (scan report as PDF/HTML/JSON, test results, SBOM…) attached to a validation run as proof that a check was performed and what it found.
- **ISO 27001** — International information-security management standard. Controls A.8.15 (logging) and A.5.28 (collection of evidence) require protected, complete and retained records.
- **SOC 2** — US assurance framework (AICPA) for service organisations. Auditors ask for exportable logs whose integrity can be shown; retention typically ≥ 1 year.
- **21 CFR Part 11** — US FDA rule on electronic records in life sciences. Requires secure, computer-generated, time-stamped audit trails that cannot obscure earlier entries; EU equivalent is **Annex 11**.
- **Non-repudiation** — Property that a party cannot later deny that a record existed, in this form, at a given time, and who produced it.
- **Tamper-evident** — Any modification of past records is detectable (via hashes/chains/signatures), even if it cannot be prevented.
- **Notarization** — Having an independent third party attest that data existed at a point in time, typically via a trusted timestamp.

## Cryptographic building blocks

- **Hash (SHA-256)** — Fixed-size fingerprint of a file or record; any change to the content changes the hash. Basis of integrity checks.
- **Hash chain** — Each record includes the hash of the previous one; rewriting any record breaks every hash after it.
- **Build digest** — A single hash summarising the whole build ledger (Merkle root over all entries); what gets signed and timestamped.
- **Instance signature / instance seal** — Digital signature made with a key owned by the Yontrack instance, proving that *this* Yontrack produced the trail. Key stored in Vault/KMS.
- **TSA (Time-Stamping Authority)** — A server that signs "this hash existed at time T" — the client sends only the hash, never the data.
- **RFC 3161** — The IETF standard protocol for talking to a TSA; supported by all corporate PKIs and public TSAs (DigiCert, Sectigo, freetsa.org…).
- **PKI (Public Key Infrastructure)** — An organisation's certificate authority and related services; often already runs a TSA.
- **eIDAS** — EU regulation (910/2014, updated by eIDAS 2.0) on electronic identification and trust services.
- **Qualified timestamp** — An RFC 3161 timestamp issued by an eIDAS *Qualified Trust Service Provider*. Carries a legal presumption of accuracy and integrity across the EU (Art. 41).
- **OIDC identity (CI identity)** — The identity of the pipeline/job that called Yontrack, taken from an OpenID Connect token issued by Jenkins, GitHub Actions, GitLab…; recorded on each ledger entry it produced.

## Storage & retention

- **S3-compatible storage** — Object storage speaking the Amazon S3 API (AWS S3, MinIO, Ceph, cloud equivalents). Chosen for evidence blobs and sealed packages.
- **MinIO** — Open-source S3-compatible server; used as a container/pod for development and testing.
- **Object Lock / WORM** — "Write Once, Read Many": storage-level immutability for a retention window. *Compliance mode* means even administrators cannot delete or alter the object.
- **Data residency** — Requirement that data stays in a given country/region.
- **Encryption at rest** — Data encrypted while stored (bucket-level or KMS keys).
- **Live trail** — The ledger of a build that still exists in Yontrack; grows with the build and follows its lifecycle.
- **Sealing** — Freezing the trail: computing the build digest, signing it, optionally timestamping it, and writing a self-contained package to S3. A build can be sealed several times; seals chain to each other.
- **Sealed trail / audit package** — The immutable output of sealing; depends on nothing left in the database, so it survives build purge.
- **Build purge / build retention** — Yontrack's existing deletion of old builds. With this feature, qualifying builds are sealed before being purged.
- **Audit retention** — Separate setting (per project, in years, default indefinite) governing how long sealed packages are kept.

## Export & verification formats

- **Manifest** — JSON file listing every item in a package with its hash, metadata and chain position.
- **in-toto attestation** — Standard JSON *statement* linking subjects (artifacts + digests) to a typed *predicate* (e.g. test result, vulnerability scan). Common in software supply-chain tooling.
- **DSSE (Dead Simple Signing Envelope)** — The signed wrapper around an in-toto statement.
- **ASiC-E** — ETSI standard ZIP container (`META-INF/` with signatures and timestamps) that off-the-shelf eIDAS validators can verify without Yontrack code. Optional, EU-notarial grade.
- **Offline verification** — Checking a package (hashes, chain, signature, timestamp) without access to the Yontrack instance, e.g. with `sha256sum` or a small CLI.

## Yontrack-specific

- **Validation run** — A recorded execution of a *validation stamp* (a check) on a build, with a status; evidence is attached here.
- **Promotion** — Marking a build as having reached a *promotion level* (e.g. RELEASE); a possible trigger for automatic sealing.
- **KDSL** — Yontrack's Kotlin DSL/client used from pipelines to call the API.
- **Instance-level audit / instance ledger** — A small separate ledger recording changes to the audit feature itself: settings changes, key rollovers, deletions of sealed packages.
- **Auditor role** — Proposed read-only role allowed to browse, verify and download audit trails but not create or delete anything.
- **Licensed feature** — The whole audit-trail capability would be enabled by licence; S3 configuration is only required when the feature is on.
- **Design partner** — A client who tests early versions (e.g. with MinIO in staging) and gives feedback that shapes the feature.
