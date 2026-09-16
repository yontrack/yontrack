# Scanning `/graphql` without ever sending a mutation

The demo instance is shared: it gates SILVER, it carries the seeded dataset the smoke test
asserts, and a release is decided on it. A scanner that sent one `deleteProject` there would
break the pipeline it is supposed to be watching. So the passive scan's hardest constraint is not
"do not attack" — it is **do not write**.

## Why the add-on alone is not the answer

ZAP's GraphQL add-on takes a schema and generates one request per field of each **root type** it
finds. All of them: `Query`, `Mutation` and `Subscription`. It has options for depth, for
argument style, for how queries are split — and none for leaving an operation type out. There is
no "queries only" switch to turn on, and `docs/grilling/2026-09-dast.md` anticipated that: *"If
the add-on cannot exclude mutations, feed a curated list of read queries kept in
`security/dast/graphql/`."*

## What is done instead, and why it is stronger than a curated list

The add-on is fed a **query-only schema**.

`scripts/security-dast.sh query-schema` derives it from `ontrack-web-core/ontrack.graphql` — the
schema committed in this repository, the same one the UI is generated from — by removing the
`mutation:` and `subscription:` entries from the `schema { … }` block and the `type Mutation` and
`type Subscription` definitions themselves. What the ZAP container reads is that file:
`/zap/wrk/graphql/query-only.graphql`.

A mutation is then not filtered out, not blocked, not discouraged: it is **not expressible**. The
add-on generates operations by walking the root types of the schema it was given, and there is no
mutation root in that schema to walk.

This is better than a hand-written list of read queries on three counts:

- **It cannot go stale.** A curated list is a copy of a schema that moves every release; a new
  root query added in `main` would go unscanned, silently, for as long as nobody updated the list.
  The derivation reads the real schema on every run.
- **It covers the whole read surface**, not the fraction someone thought to write down. Yontrack
  has hundreds of root query fields.
- **It is checkable.** A list is only as safe as its reviewer's eyes. A derivation has a
  post-condition, and `query-schema` enforces it: it refuses to write a file that still declares a
  mutation or subscription root, and the workflow fails there rather than scanning.

## The belt and the braces

Three independent things would each have to fail for a mutation to reach the demo:

1. the derived schema would have to contain a mutation root — `query-schema` fails the run if it
   does, before ZAP starts;
2. the ZAP plan would have to run something that sends requests of its own devising — it does not:
   `security/dast/zap/passive.yaml` has no `activeScan` job, its `spider` has `postForm: false`
   and `processForm: false`, and its `requestor` job lists nine URLs, all `GET`;
3. `scripts/security-dast.sh assert-no-mutations` re-reads ZAP's own record of every request it
   sent, after the scan, and fails the workflow if any `POST /graphql` body carries a `mutation`
   or `subscription` operation. It is the only one of the three that observes what actually went
   over the wire, which is why it exists even though the first two should make it unreachable.

## Nothing is committed here

The query-only schema is derived at scan time and lives in the runner's temporary directory. It is
8,000 lines of an artefact that is already in this repository, and a committed copy would be one
more thing to keep in step with `ontrack-web-core/ontrack.graphql` — which is exactly the failure
mode this approach was chosen to avoid.
