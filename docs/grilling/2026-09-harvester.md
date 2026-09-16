# The harvester — producing a ledger inside an organization

Outcome of the grilling session of 2026-09-16 on block 1 of the delivery-harvesting brief
([2026-09-harvesting-brief.md](2026-09-harvesting-brief.md)): the tool that runs inside an
organization, reads its delivery sources, and writes a ledger in the contract decided in
[2026-09-ledger.md](2026-09-ledger.md). It lives in its **own repository, on its own cadence**, and
is not part of any Yontrack release.

The session settled 31 questions, starting from the three the ledger session left open. This
document records the decisions and the reasons, not the questions. **No issue is created yet**;
the issue breakdown comes in a following session and will be `2026-09-harvester-issues.md`.

## Where we start from

- **The ledger contract is settled** and owned by Yontrack: JSON Lines, a header with `source`,
  facts with `id`, `kind`, `time`, `basis`, `evidence`, `payload`; dedupe by `(source, id)`;
  thirteen closed fact kinds; `basis = MEASURED | ESTIMATED`; time required on every fact. The
  importer sorts by time per project, so a producer need not order across projects.
- **The `yontrack` CLI** (`yontrack/yontrack-cli`) is one Go module named plainly `yontrack`, flat
  cobra `cmd/`, no code generation: the GraphQL schema is checked in as reference only and every
  query is a hand-written string. It authenticates with a named configuration holding a URL and a
  token. Releases are a plain `MAJOR.MINOR.PATCH` tag, cross-compiled by a script and attached to
  the GitHub release, with a Homebrew tap and bottle. It has Go unit tests only, no acceptance
  suite against a running Yontrack. Nothing in it is named ledger, harvest or collect. The ledger
  session gave it `ledger import` and `ledger validate`; those are block 2's issues, not this
  block's.
- **The CLI repository already uses `initiative: <name>` labels** and the same `status:*` set as
  `yontrack/yontrack`, so the board convention carries over.
- **Yontrack's own CI** does not use the CLI to create builds: it delegates to the
  `ontrack-github-actions-cli-*` actions and calls `yontrack validate` and
  `yontrack build set-property release`. It is the ledger contract's second producer, and it
  needs no harvester to be one.

## Decisions

### What block 1 is

- **Three deliverables, one definition of done.** The generic harvester tool; a **template** of the
  per-organization *harvest repository* (mapping, facts file, runbook, agent playbook); and the
  agent playbook inside that template. Done when the target organization's harvest repository
  produces a ledger that imports cleanly. The real organization's harvest repository is private
  and lives in no Yontrack repository.
- **Own repository, `yontrack/yontrack-harvester`, Go, private.** Not a second binary in the CLI's
  repository: the licence may differ from the CLI's MIT and is decided later, once a working tool
  has informed it. Go rather than Python because the harvester does not drive agents, agents drive
  the harvester: what an agent needs is a readable config with a schema, deterministic commands
  and machine-readable reports, which no language decides, while a single static binary is what
  runs inside an estate that will not install an interpreter. Python would only win if the
  harvester called a model inline, which the brief forbids.
- **One-shot, never a daemon.** `fetch` and `collect` run and exit; the organization's own cron or
  CI schedules them. A daemon needs a host, monitoring and a restart story inside an organization
  that cannot even push to Yontrack, and "dumb transport" was the point.
- **The template lives in the harvester repository** under `template/`, versioned with the tool
  that reads it; `harvester init` copies it out.

### Inputs: three kinds, two collectors

- **Live collectors** (a CI engine, an SCM) run `fetch` to write an *export* file in a documented
  shape, then `collect` reads the export. The export is the seam.
- **Report collectors** read the same export shape when a user produced it without giving the
  harvester any access: a documented command (`git log --format=…`, `git for-each-ref`, a Jenkins
  JSON API URL saved to a file) whose output is handed over. Every live collector is therefore a
  report collector for free, and every collector is testable from a fixture.
- **Unstructured sources are not collectors.** Spreadsheets, wikis and release emails are mined by
  the agent's `mine-facts` procedure into the **facts file** (below), reviewed by a human, and
  merged by `collect`. The rule that decides the bucket: if a deterministic parser can read it, it
  is a collector; if judgement is needed, it is the agent's, and the output is `ESTIMATED`.
- **The export is also the durability snapshot.** "Snapshot perishable sources early, reconstruct
  durable ones later" becomes: fetch CI exports now, map them whenever the mapping is ready.

### Collectors, first set

- **Git first, Jenkins second, Bitbucket third.** The target organization is Jenkins and
  Bitbucket. Git is first because it is the durable source every organization has, and because
  its export mode covers the case where nobody can hand over a clone.
- **No GitHub Actions collector in v1.** Yontrack's own ledger, the contract's second producer, is
  cheaper as a step in `ci.yml` that writes facts from the values it already posts, and it
  exercises the contract, not the harvester. The harvester is for organizations.

### Build identity across sources

- **The mapping names builds per source; the report checks agreement.** Git sees tags and commits,
  Jenkins sees job runs, a spreadsheet sees "release 3.4 shipped Monday". The mapping declares a
  naming rule per source; the commit SHA is the join key wherever both sides have one; the
  reconciliation report lists every build seen by one source and not another, and the agent
  reviews it. Making one collector the authority for builds was rejected because the git-only
  estate, with no CI history left, is the common case durability planning predicts.

### The facts file

- **Hand-authored, agent-drafted, human-reviewed.** One file in the harvest repository, in the
  ledger's own fact shape, with `basis: ESTIMATED` and the source document as evidence. The
  harvester validates it against the schema and merges it. No spreadsheet collector: a
  spreadsheet is a mapping problem per spreadsheet, never generic, and the agent does that
  translation once.
- **Ids are author-chosen slugs** (`facts:release-3.4-shipped`), rejected on duplicate. Collector
  ids are readable and collector-prefixed (`jenkins:job/app-build/42:validation:TESTS`), never
  UUIDs: a reconciliation report is debugged by a human reading ids.
- **Imprecise dates are expanded, not refused.** A facts file may say `2024-03` or `2024-03-15`;
  the harvester writes the first instant of the period in the organization's timezone from the
  mapping, and the original precision into `evidence`. The contract stays as decided:
  `ESTIMATED` already means "do not trust the minute". Asking block 2 for a `precision` field was
  refused as a contract change for a producer-side convenience.

### The mapping

- **One YAML per organization**, in the harvest repository, validated against a JSON schema the
  harvester publishes; the mapping reference in the docs is generated from that schema.
- **Explicit project list, rules only for branches.** An `inventory` command proposes the list
  from the sources, the agent drafts it, a human reviews it. Explicit is what a human can review
  and what "agents produce configuration" means.
- **Organization-wide stamp and level mappings with per-project overrides**: a Jenkins estate has
  one pipeline convention and twenty exceptions.
- **The deployment marker per source is one of two**: `promotionLevel: <name>`, the default, or
  `environment: <name>` plus the stage that means it. Only the latter emits `environment`, `slot`
  and `deployment` facts. Most harvested estates have no deployment record, and the scorecard's
  fallback to the highest promotion level is for exactly them.
- **Each source carries a `durability: durable | perishable` hint**, used by the runbook and the
  reconciliation report ("perishable source X last fetched 40 days ago"). No scheduler in the
  tool; `fetch` runs per source so cron can export CI nightly and git weekly.
- **Credentials are env-var references only** (`token: ${JENKINS_TOKEN}`). The mapping is
  committed, and many organizations will hand over export files and never give the harvester a
  credential at all.

### Output

- **One ledger per `collect` run, all sources**, header `source` = the organization name from the
  mapping, file `<source>-<createdAt>.jsonl.gz`, written to `archive/`.
- **Incremental by watermark, and the watermark is an optimisation.** The server dedupes by
  `(source, id)`, so over-collecting is harmless; a lost watermark costs a bigger ledger and
  nothing else. One opaque watermark per collector per source key, in `state/`; `--full` ignores
  it.
- **A reconciliation report beside every ledger**, from one model into two files:
  `reconcile.json` for the agent and `reconcile.md` for the reviewer. It lists builds seen by one
  source and not another, unmapped branches or jobs, runs without a build, facts rejected by the
  schema, and stale perishable sources. The exit code is non-zero when the report has errors, so a
  cron run cannot ship silently.
- **No `ship` command.** The two shippers are `yontrack ledger import <file>` on a host with
  egress, and a copied file. The runbook documents both. A pull shipper or a bucket target is a
  later addition, as the ledger session already decided.
- **No redaction layer in v1**, recorded as a known gap. The signature user is the producer, not
  the committer, so names do not appear unless the mapping puts them in meta-info; evidence is a
  URL. Revisit when the first ledger crosses a trust boundary.

### The harvest repository

- **Committed**: mapping, facts file, agent playbook, runbook, and `state/` (small, and a run that
  moved the watermark is an event worth a commit in the harvest repository's log).
- **Not committed**: `archive/` (exports and ledgers), which the organization backs up. The
  template's `.gitignore` says so, and the docs must say why, for the end user.
- **The agent playbook is agent-neutral.** `AGENTS.md` plus one Markdown procedure per task, the
  convention every agent runtime reads, with `.claude/skills` as thin wrappers pointing at it.
  Four procedures: `inventory` (list sources, propose the project list), `draft-mapping`,
  `mine-facts` (a spreadsheet or wiki page into the facts file, with evidence),
  `review-reconciliation`. The loop: the agent proposes on a branch, a human reviews the diff,
  merge, run.
- **The harvester never holds a model key.** The model and its key belong to the organization's
  agent runtime; the template is runnable by any agent that reads files and runs a CLI, and by a
  human with no agent at all. A model inside the harvester was refused: it would put judgement
  inside the pipeline that produces numbers.
- **The agent runs `collect` freely and never `fetch` against live sources.** `collect` only shapes
  files already on disk; `fetch` with credentials stays a human or cron action. This keeps the
  brief's "agents produce configuration and rules, never numbers" mechanical rather than
  aspirational.

### The contract, shared by fixture

- **No shared code with the CLI.** Each repository pins the published ledger JSON schema as a
  fixture and derives its types from it; a CI check in both diffs the fixture against the
  instance's `/rest/ref/schema/json/ledger`. A shared Go module would make the public CLI depend
  on a private repository.
- **The schema is block 2's first issue**, landed on `main` well before the importer, so the
  harvester can pin it. Until the importer exists, the harvester's definition of done is
  "validates against the schema", not "imports".

### Testing

- **Golden tests per collector**: export fixture in, expected ledger out, plus schema validation.
  The export seam makes them cheap and deterministic.
- **An end-to-end import into a Yontrack docker image** is added to the harvester's CI when 6.1
  ships. It is the only proof that the contract has two producers.

### Docs and delivery

- **A standalone mkdocs site in the harvester repository**, published as a private artifact of
  that repository together with the binary: what to commit and why, the export shape per
  collector, the mapping reference generated from its schema, the runbook. Yontrack's own "Ledger
  import" page (block 2) says in one sentence that producers exist and that the format is the
  contract, and documents nothing about a private tool.
- **How the binary and the docs reach the end user is out of scope for now**: assume the user has
  them. Release scheme, install script and Homebrew are not decided.

### Vocabulary

For the harvester repository's own `CONTEXT.md`, each with an _Avoid_ list in the style of the two
previous sessions:

- **Harvester**: the tool, and the whole producer side.
- **Collector**: reads one source kind from an export. _Avoid_: connector, plugin.
- **Export**: a collector's raw input, fetched live or handed over. _Avoid_: dump, snapshot (the
  ledger session reserved it), report (the reconciliation report).
- **Facts file**: the reviewed, hand-authored estimated facts. _Avoid_: manual ledger.
- **Mapping**: the organization's YAML from local vocabulary to Yontrack structure.
- **Harvest repository**: the per-organization checkout holding mapping, facts and state.
- **Reconciliation report**: what `collect` writes beside a ledger.
- **Shipper**: one of the two documented ways to move a ledger. A word for a procedure, with no
  command behind it, on purpose.

### Issues

In `yontrack/yontrack-harvester`, under `initiative: ledger`, the same label name as block 2 so the
initiative reads as one across the two boards. **Nothing is created until Damien says so.**

## What this hands to block 2

- The schema is its first issue, before any importer code.
- Nothing else changes: every decision here stayed producer-side. The `precision` field was
  refused rather than requested.

## Sources

- [2026-09-harvesting-brief.md](2026-09-harvesting-brief.md) and
  [2026-09-ledger.md](2026-09-ledger.md), whose "Block 1, opened and deferred" section this
  session started from.
- `yontrack/yontrack-cli` at `main` (2026-09-09): `go.mod`, `cmd/`, `config/configService.go`,
  `.github/workflows/tag.yml`, `RELEASING.md`, for the CLI's shape and release scheme.
- `.github/workflows/ci.yml` in `yontrack/yontrack` for how Yontrack's own CI talks to the
  instance today.
