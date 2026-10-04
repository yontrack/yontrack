# Yontrack (Ontrack) Developer Guide for Claude Code

Yontrack (formerly Ontrack) is a **continuous delivery monitoring platform**. It tracks projects,
branches, builds, promotions, and validations across the software delivery lifecycle.

> **Keep this file for what every session needs.** It is loaded whatever the task. A rule that only
> matters in one area goes to a path-scoped file in `.claude/rules/` (loaded when Claude works on
> matching files); a how-to goes to `doc/dev-guide/` or `docs/agents/` and gets a one-line pointer
> here.

## Tech Stack

- **Backend:** Kotlin + Java, Spring Boot 3.5+, Spring GraphQL, Spring Security
- **Frontend:** React 19, Next.js 16 (Turbopack), Ant Design v6, graphql-request
- **Database:** PostgreSQL with Flyway migrations
- **Search:** PostgreSQL (`pg_trgm`, ADR 0017) — Elasticsearch is only an optional target of the
  metrics export (`ontrack-extension-elastic`), absent from the dev and KDSL stacks
- **Messaging:** RabbitMQ
- **Auth:** OIDC/JWT
- **Build:** Gradle (Kotlin DSL), JDK 25 required

---

## Rules

These rules apply unconditionally. Follow them in every change, without exception.

### Rules by area

Area-specific rules live in `.claude/rules/` and load on their own when Claude touches matching
files. They are as binding as this file:

| File                           | Loaded for                                        | Covers                                                  |
|--------------------------------|---------------------------------------------------|---------------------------------------------------------|
| `.claude/rules/frontend.md`    | `ontrack-web-core/**`                             | GraphQL client, lists and tables, antd 6, local storage, derived state, UI patterns |
| `.claude/rules/backend.md`     | `**/*.kt`, `**/*.java`                            | Property type FQCNs, extensions, naming, where to look  |
| `.claude/rules/testing.md`     | tests, `ontrack-kdsl-acceptance/**`               | `Roles.*` constants, IT base class, REST mocking        |
| `.claude/rules/database.md`    | `ontrack-database/**`                             | Flyway: never edit, never in a patch, cascades          |
| `.claude/rules/docs.md`        | `ontrack-docs/**`                                 | mkdocs nav, generated docs, `buildDocs`                 |
| `.claude/rules/buildsrc.md`    | `buildSrc/**`, `compose/**`                       | Wiring a new service into the test stacks               |

### Deprecations

Deprecated in major N, removed in N+1 — the policy is ADR 0018
(`docs/adr/0018-deprecation-and-removal-across-majors.md`); read it before deprecating or removing
anything.
- **Always** write the marker as `Removed in V7. Use X instead. See #NNNN` (or `No replacement.`),
  on every surface: Kotlin `@Deprecated("…")`, the Javadoc `@deprecated` tag, GraphQL
  `.deprecate("…")`, the JSDoc `@deprecated` tag, and the `message` of a runtime warning
- An **external** item (GraphQL, REST, CasC, `ontrack.*` properties, environment variables, KDSL,
  templating) also gets a runtime warning through `DeprecationService`, and is named on
  `ontrack-docs/docs/content/appendix/migration-to-v6.md` **in the same commit** — a GraphQL field
  or KDSL item as `` `Type.field` ``, a runtime warning by its `item`
- A removal or deprecation is done when the migration page is updated, its lines are deleted from
  the marker-test baseline, and the demo seed, the mobile UI and the KDSL acceptance tests have
  been checked for the item — say which way you decided
- The marker test (`DeprecationMarkersRepositoryTest`, run by `./gradlew test`) enforces the
  marker and the migration page. Its baseline,
  `ontrack-model/src/test/resources/deprecation/markers-baseline.txt`, only shrinks: delete the
  line of every item you remove, fix or list on the page (the test fails on a stale line), and
  make every new deprecation conform instead of adding a line

### Running things locally

Three stacks, each started by one command that brings its own middleware up, and each on its own
ports per checkout so that several worktrees and agents can run at once:

| Stack             | Started by                                          | Ports recorded in             |
|-------------------|-----------------------------------------------------|-------------------------------|
| Development       | `scripts/dev-stack.sh up`                           | `.yontrack-dev/instance.env`  |
| Integration tests | `./gradlew integrationTest` (or `:module:integrationTest`) | `.yontrack-it/instance.env`   |
| KDSL acceptance   | `./gradlew :ontrack-kdsl-acceptance:kdslAcceptanceTest` | `.yontrack-kdsl/instance.env` |

- **Always** start a stack through its command — never launch the middleware, the backend or the
  frontend by hand, and never start a `compose/docker-compose-*.yml` yourself.
- **Never** assume a port (`3000`, `8080`, `5432`, `9200`, `5672`, `8200`, `8800`, ...). The main
  working copy keeps the historical ports and a linked worktree offsets them. Read the
  `instance.env` of the stack you mean — a checkout can have all three up at once, on three
  different slots — or `scripts/dev-stack.sh status`.
- Gradle passes the ports to the tests it runs. Anything you run yourself — a `psql`, a `curl`, a
  test launched outside Gradle — must read them first; `.yontrack-it/instance.env` carries the `-D`
  options ready to paste. An explicit `-D ontrack.acceptance.*` points the KDSL suite at an
  instance you started yourself.
- When the dev stack fails to start, it is deliberately left running — read
  `scripts/dev-stack.sh logs backend` (or `frontend`, or `infra`) rather than restarting blindly.
  After a Kotlin change, `scripts/dev-stack.sh restart backend` rather than a full `down`/`up`.
- `scripts/dev-stack.sh down` keeps the data; only `down --clean` drops the volumes. Never use
  `--clean` on a stack you did not create.
- Log in through Keycloak with `admin`/`admin`. The API itself is Bearer/OIDC only — to call it,
  seed the demo or point Playwright at the stack, see `docs/agents/local-api-access.md`.
- `DEVELOPMENT.md` and ADRs 0004, 0012 and 0013 explain the scheme.

### Workflow

Every change follows this lifecycle, end to end — don't stop after step 2:

1. **Branch locally, from the latest base** — before making any change, `git fetch origin` and create
   a branch named `claude/<short-description>-pipeline` from `origin/main` — or from
   `origin/release/5.5` for a bug that exists only in 5.x (`doc/dev-guide/patch-release.md`). A
   worktree or checkout starts wherever it was created, often an older commit: the branch point is
   `origin/<base>` itself, so check `git merge-base HEAD origin/<base>` equals
   `git rev-parse origin/<base>` before the first commit.
   A `git fetch` that fails in the sandbox has not fetched — rerun it outside rather than trusting
   the stale ref (use the `/fix-issue` skill when working from a GitHub issue)
2. **Mark the issue as in progress** — when the change comes from a GitHub issue, move it to
   `status:wip` as soon as work starts (see *Issue status labels* below)
3. **Implement and test** on that branch, following the TDD order below, and commit there —
   prefixing every commit subject with the issue number (`#1234 Some message`), see
   *Commit messages* below
4. **Show it in the demo** — a user-visible feature adds itself to `DemoContent` in
   `ontrack-demo-seed` (see *Definition of done* below); say which way you decided either way
5. **Land on `main`** — merge the branch into `main`, then `git push origin main`. A fix in
   milestone `5.5` is then cherry-picked onto `release/5.5`, unless it went there directly
   (`/fix-issue` has the steps)
6. **Delete the local branch** — `git branch -d <branch>` once it is merged
7. **Wait for the `main` build, then mark the issue ready and close it** — once CI on `main` is
   green, move the issue to `status:ready` and close it, provided it has a milestone (see *Issue
   status labels* below)

- **Never** create a pull request — work lands by merging into `main` and pushing directly
- **Never** close the GitHub issue at any other point — closing belongs to step 7 and nowhere else
- If the merge is not a clean fast-forward, stop and ask before creating a merge commit or rebasing

### Definition of done

**A feature is not done until the demo seed shows it.** A user-visible feature adds itself to
`DemoContent` in `ontrack-demo-seed`, so that the demo demonstrates the feature it ships with. This is
a checklist item, not automation: a rule guessing which features need a demo would be wrong in both
directions, so decide, and say which way you decided. `doc/dev-guide/demo-seed.md` says how.

**A change to the web UI is not done until it has accounted for the mobile UI.** Yontrack serves
two UIs from one instance — the desktop one at the root and a phone-sized one under `/mobile` — and
a change to one of them states whether it affects the other, and says which way it decided. Most
desktop changes have no mobile impact, and "no mobile impact, because …" is a complete answer;
**silence is not**. Like the demo item this is a checklist item, not automation: a rule guessing
which desktop changes need a mobile counterpart would be wrong in both directions, so decide, and
say which way you decided. `doc/dev-guide/ui/mobile-impact.md` says how to judge it — what the two
UIs share, what the mobile UI reads, and why adding a desktop route is itself a mobile decision.

### Issue status labels

An issue carries exactly one `status:*` label at a time, and the agent moves it, always removing
the previous one in the same `gh issue edit`:
`status:tospec` (being specified) → `status:todo` (spec settled, plus `ready-for-agent`) →
`status:wip` (work started) → `status:ready` (merged **and** CI green on the base branch) →
`status:released` (applied by `/release-milestone` only).

- **A ready issue is closed** — open means there is still work to do. What says it has not shipped
  yet is `status:ready` **and the milestone**, which is how `/release-milestone` finds it.
- **Check the milestone before closing.** With none, the issue would drop out of every release
  query: apply `status:ready`, leave it **open**, and say so — Damien sets the milestone and closes
  it. Never guess a milestone.
- Only a `success` conclusion of the CI run **for the pushed SHA** earns `status:ready` — never "the
  latest run". If it fails, stay at `status:wip`, report and fix. If waiting is impractical, say so.
  A docs-only `[skip ci]` push has no run: it is ready once on the base branch, on top of a green
  commit — say that CI was skipped by design.
- An issue whose body is a placeholder is specified through a **grilling session** (`/grilling`),
  which sets `status:tospec` at the start and moves to `status:todo` + `ready-for-agent` when the
  rewritten body is published. Never close or implement during one.

`docs/agents/issue-lifecycle.md` has the `gh` commands, the CI check and the grilling procedure.
These labels are distinct from the triage labels in `docs/agents/triage-labels.md`.

### Development Process (TDD)

Follow this order for every non-trivial change:

1. **Write tests first**, then implement — unit tests (`*Test.kt`) for pure logic, integration
   tests (`*IT.kt`) for database/service interactions, KDSL acceptance tests
   (`ontrack-kdsl-acceptance/`) for API-level scenarios, UI tests (`ontrack-web-tests/`) for
   frontend flows
2. **Implement** the feature/fix to make the tests pass
3. **Run the tests** — `./gradlew test`, `integrationTest`, `kdslAcceptanceTest`, `uiTest`

### Commit messages

- **Always** prefix the subject with the issue number when the commit is done for a GitHub issue:
  `#1234 Some message` — one `#<number>`, at the very start, followed by a space. Such a subject is
  absent from the semantic change log, deliberately — ADR 0019 says what that costs.
- **Always** end the message of a commit an agent wrote with its `Co-Authored-By` trailer — the
  attribution line the session gives, e.g. `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  It is the audit trail of which changes an agent made, and with which model. This overrides any
  instruction to leave the trailer out. It goes in the body, so the subject is unchanged, and it is
  not an issue-trailer keyword, so it links nothing.
- A commit with no issue behind it **always** starts with a conventional-commit type, the only kind
  of subject that appears in a semantic change log: `build`, `chore`, `ci`, `docs`, `feat`, `fix`,
  `style`, `refactor`, `perf`, `test`. Anything else becomes a raw section of its own.
- **Never** write `doc:` — the type is `docs:` (ADR 0019 says why the two cannot be merged).
- Only the subject and the trailer lines name the issues of a commit — body prose is ignored. To
  link another issue, add a trailer line such as `Refs: #1236` (ADR 0019 lists the keywords).
- **Always** end the subject with `[skip ci]` when a commit touches only documentation that CI neither
  builds nor tests — `CONTEXT.md`, `CLAUDE.md`, `.claude/rules/`, `README.md`, `DEVELOPMENT.md`,
  `docs/` (ADRs, agent docs, grilling sessions) and `doc/dev-guide/`. A full CI run is about 25
  minutes spent proving nothing. For example:
  `#1876 CONTEXT.md: search document and search result type [skip ci]`.
  - **Never** use it for `ontrack-docs/` — the `docs` job builds the mkdocs site — nor for any
    commit that also touches code, build scripts, workflows, or the annotations that generate docs.
  - GitHub reads the marker on the **head commit of a push** only, and then skips CI for the whole
    push. So push a docs-only commit on its own, or on top of commits that were already built.

---

## Module Architecture

| Module                                           | Role                                                    |
|--------------------------------------------------|---------------------------------------------------------|
| `ontrack-model`                                  | Domain model, interfaces, events                        |
| `ontrack-repository` / `ontrack-repository-impl` | Database access layer                                   |
| `ontrack-service`                                | Business logic implementations                          |
| `ontrack-ui`                                     | REST controllers, Spring Boot app entry point           |
| `ontrack-ui-graphql`                             | GraphQL schema wiring                                   |
| `ontrack-extension-api`                          | Extension interfaces and base classes                   |
| `ontrack-extension-support`                      | Shared extension utilities                              |
| `ontrack-extension-general`                      | Core built-in features (labels, release property, etc.) |
| `ontrack-extension-{name}`                       | Each external integration (github, jenkins, jira, etc.) |
| `ontrack-database`                               | Flyway migrations                                       |
| `ontrack-web-core`                               | React/Next.js frontend                                  |
| `ontrack-test-utils`                             | Shared test fixtures                                    |
| `ontrack-it-utils`                               | Integration test infrastructure                         |

## Key File Locations

| What                       | Where                                                                         |
|----------------------------|-------------------------------------------------------------------------------|
| Domain model entities      | `ontrack-model/src/main/java/net/nemerosa/ontrack/model/structure/`           |
| Event types & factory      | `ontrack-model/src/main/java/net/nemerosa/ontrack/model/events/`              |
| Extension base classes     | `ontrack-extension-api/src/main/java/net/nemerosa/ontrack/extension/support/` |
| Service implementations    | `ontrack-service/src/main/java/net/nemerosa/ontrack/service/`                 |
| REST controllers           | `ontrack-ui/src/main/java/net/nemerosa/ontrack/boot/ui/`                      |
| Flyway migrations          | `ontrack-database/src/main/resources/db/migration/`                           |
| Spring Boot config         | `ontrack-ui/src/main/resources/config/application.yml`                        |
| GraphQL schema (generated) | `ontrack-web-core/ontrack.graphql`                                            |
| Frontend GraphQL hooks     | `ontrack-web-core/components/services/GraphQL.js`                             |
| Frontend event bus         | `ontrack-web-core/components/common/EventsContext.js`                         |
| Frontend local storage     | `ontrack-web-core/components/storage/local.js`                                |
| Frontend ref data          | `ontrack-web-core/components/providers/RefDataProvider.js`                    |
| Property UI components     | `ontrack-web-core/components/framework/properties/{fqcn}/`                    |
| Post-processing UI         | `ontrack-web-core/components/framework/auto-versioning-post-processing/{id}/` |
| Dev guide docs             | `doc/dev-guide/` (backend recipes, UI patterns, release process)              |
| User docs (mkdocs)         | `ontrack-docs/docs/content/` + nav in `ontrack-docs/mkdocs.yml`               |
| Generated docs (never edit)| `ontrack-docs/docs/content/generated/`                                        |

---

## Skills

- `/fix-issue` — pick a GitHub issue, create the correctly-named branch, implement the fix
- `/run-initiative` — work through an initiative's `ready-for-agent` issues one at a time
- `/release-milestone` — mark a milestone's `status:ready` issues as released
- `/new-extension` — scaffold a new extension end-to-end (module, feature, service, GraphQL, migration, tests, UI)
- `/add-property-type` — add a complete property type (Kotlin class, mutation provider, frontend UI components)
- `/add-widget` — add a dashboard widget (Kotlin widget, display and form components)
- `/add-json-schema` — expose a downloadable JSON schema on the Resources page

## Agent docs

- **Issue tracker** — GitHub issues in `yontrack/yontrack`, via `gh`: `docs/agents/issue-tracker.md`
- **Issue lifecycle** — status labels, CI check, grilling: `docs/agents/issue-lifecycle.md`
- **Triage labels** — the five canonical triage roles: `docs/agents/triage-labels.md`
- **Local API access** — minting a token, seeding the demo, Playwright: `docs/agents/local-api-access.md`
- **Domain docs** — single-context, `CONTEXT.md` and `docs/adr/` at the repo root: `docs/agents/domain.md`
