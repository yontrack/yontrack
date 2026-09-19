# GitLab support — requirements

Outcome of the grilling session of 2026-09-19 on full GitLab support in Yontrack, the Yontrack CLI
and the Yontrack skills. The session settled 28 questions. This document records the decisions and
their reasons, not the questions.

All 14 issues carry the label `initiative: gitlab`, `status:todo` and `ready-for-agent`. The
`yontrack/yontrack` ones are in milestone `5.5`; the CLI and skills ones have no milestone.
Dependencies are recorded as native GitHub dependencies, across repositories too.

`#588` has been moved into milestone `5.5` and the initiative; it is closed by issue 2.

| # | Issue | Repo | Blocked by |
|---|---|---|---|
| 1 | [GitLab test project runbook](https://github.com/yontrack/yontrack/issues/1826) | `yontrack/yontrack` | — |
| 2 | [GitLab configuration and client rework](https://github.com/yontrack/yontrack/issues/1827) | `yontrack/yontrack` | 1 |
| 3 | [GitLab issue service rework](https://github.com/yontrack/yontrack/issues/1828) | `yontrack/yontrack` | 2 |
| 4 | [GitLab SCM implementation and change log](https://github.com/yontrack/yontrack/issues/1829) | `yontrack/yontrack` | 2 |
| 5 | [Auto-versioning merge requests on GitLab](https://github.com/yontrack/yontrack/issues/1830) | `yontrack/yontrack` | 4 |
| 6 | [CI config SCM engine `gitlab`](https://github.com/yontrack/yontrack/issues/1831) | `yontrack/yontrack` | 2 |
| 7 | [CI config CI engine `gitlab-ci`](https://github.com/yontrack/yontrack/issues/1832) | `yontrack/yontrack` | 6 |
| 8 | [GitLab pipeline notification channel](https://github.com/yontrack/yontrack/issues/1833) | `yontrack/yontrack` | 2 |
| 9 | [Auto-versioning post-processing through a GitLab pipeline](https://github.com/yontrack/yontrack/issues/1834) | `yontrack/yontrack` | 8 |
| 10 | [Real GitLab pipeline tests on BRONZE](https://github.com/yontrack/yontrack/issues/1835) | `yontrack/yontrack` | 9 |
| 11 | [`project set-property gitlab`](https://github.com/yontrack/yontrack-cli/issues/74) | `yontrack/yontrack-cli` | — |
| 12 | [`run-info` defaults for GitLab CI](https://github.com/yontrack/yontrack-cli/issues/75) | `yontrack/yontrack-cli` | 7, [`yontrack-cli#71`](https://github.com/yontrack/yontrack-cli/issues/71) |
| 13 | [README: using the CLI in GitLab CI](https://github.com/yontrack/yontrack-cli/issues/76) | `yontrack/yontrack-cli` | 7, 11, 12 |
| 14 | [`yontrack-gitlab-ci` skill](https://github.com/yontrack/yontrack-skills/issues/3) | `yontrack/yontrack-skills` | 13 |

Issue 11 has no blocker at all and can be picked up on day one — see *CLI and skills*. Issue 12
carries a **cross-initiative** dependency on the Bitbucket initiative's `yontrack-cli#71`, which is
where the shared CI-environment detection helper is created.

## Where we start from

`ontrack-extension-gitlab` is the shallowest of the four SCM extensions: **21 files** in
`src/main`, against 37 for Bitbucket Server, 58 for Bitbucket Cloud and 307 for GitHub. It has no
`src/main/resources`, no README, no migration.

| Layer | State |
|---|---|
| Configuration | `GitLabConfiguration` on the legacy `UserPasswordConfiguration` base: `name`, `url`, `user`, `password`, `isIgnoreSslCertificate`. No auth-type discriminator, no auto-merge credentials. It is the only one of the four still on that base. |
| Client | `DefaultOntrackGitLabClient`, ~75 lines over `org.gitlab4j:gitlab4j-api` (pinned to 6.1.0 in the root `build.gradle.kts`). Exactly three calls: owned projects, get issue, get merge request. No branches, commits, files, tags, pipelines or writes. |
| Property | `GitLabProjectConfigurationProperty` — configuration, repository, indexation interval, issue service — with its type, mutation provider and decorator. |
| Git | `GitLabConfigurator` (`GitConfigurator`) and `GitLabGitConfiguration`, remote `<url>/<repo>.git`, `UsernamePasswordGitRepositoryAuthenticator(user, password)`. |
| Issues | `GitLabIssueServiceExtension`, service id `gitlab`, pattern `(#(\d+))`. **`getConfigurationList()` returns an empty list**, so a GitLab issue service can never be selected in the property UI. **`getLastCommit()` is `TODO()`**. |
| Frontend | A configurations page, a decoration, and the property's `Icon`/`Display`/`Form`/`FormPrepare`. Nothing else. |
| Missing | `SCMExtension`/`SCM`, `SCMChangeLogEnabled`, `SCMCatalogProvider`, `SCMEngine`, `CIEngine`, `PostProcessing`, any notification channel, any settings, any CasC context, any ingestion, any KDSL, any acceptance test, any demo reference, and **any documentation** — the only GitLab page is the dead asciidoc `usage-gitlab.adoc`, whose one link in `feature-scm.adoc` is commented out. |

Two things make this unlike the Bitbucket Cloud rebuild that this initiative otherwise mirrors:

- **GitLab has users.** Support dates from #352 (2019) and the tracker carries their traces — #588
  (proxy, open since milestone 2.34), #803 (API v3), #1217 (configuration not possible through the
  UI). Bitbucket Cloud was used by nobody, which is what licensed its clean break. Here a silent
  break on upgrade is not acceptable.
- **An issue service already exists**, so issues are a *rework*, not a new capability.

The closest model is Bitbucket Cloud, whose own rebuild is recorded in
[`bitbucket-issues.md`](../bitbucket-issues.md) — except for the SCM engine, where GitHub is the
model, and for auto-merge, where GitLab is better served than either.

## Decisions

### Scope

In 5.5:

- SCM basics — commit links, file download — and change logs
- issues, as a rework of the existing issue service
- auto-versioning merge requests, in both approval modes
- a GitLab pipeline notification channel, usable in workflows
- auto-versioning post-processing through a GitLab pipeline
- CI config: an SCM engine **and** a CI engine
- `yontrack-cli` and `yontrack-skills` support

Out of scope: **webhook ingestion** — GitHub's counterpart is 175 files, 57% of that module, and is
an initiative of its own; the **SCM catalog provider**, which nothing here needs and which would
mean walking groups and subgroups recursively; GraphQL mutations for the configurations; PR
information on branch pages; GitLab pipeline **`inputs`** (GA in 18.1), which need a `spec:inputs`
header in the target pipeline; and **demo coverage**, as for Bitbucket Cloud and GitHub.

### Deployments and tier

Both **gitlab.com and self-managed** are supported. Self-managed is where most serious GitLab use
lives, and the existing configuration is already URL-aware.

**GitLab Free is the baseline.** Nothing may depend on Premium or Ultimate. Two consequences worth
stating, because both differ from the Bitbucket assumptions:

- Merge request **approve/unapprove is a Free endpoint**; only approval *rules* are Premium. So
  Ontrack can approve, and there is no need for the separate approver identity Bitbucket required.
- **Project and group access tokens are Premium-only on gitlab.com** (they are available on any
  self-managed licence, including Free). A gitlab.com Free setup therefore uses personal access
  tokens.

### The client: gitlab4j goes

`gitlab4j-api` is dropped and replaced by a hand-rolled `GitLabClient` / `DefaultGitLabClient` over
Spring's `RestTemplate`, as GitHub, Bitbucket Cloud and Bitbucket Server all do.

The library is not at fault — 6.3.0 is current, actively maintained, Jakarta-based and covers
pipelines, merge requests, compare and files. The reasons are Yontrack's, not GitLab's:

- The SCM, pull-request and change-log code then reads like the other three SCMs.
- `MockRestServiceServer` becomes available. Since this initiative mocks everything by default,
  that is the difference between a testable client and an untestable one.
- It removes the whole Jersey stack — `jersey-client`, `jersey-hk2`, `jersey-apache-connector`,
  `jersey-media-multipart`, `jersey-media-json-jackson` — from the distribution.
- It gives explicit control over 429 handling (`Retry-After`, `RateLimit-*`), over pagination on
  endpoints that return no totals, and over URL-encoding of project paths.

**This fixes #588.** The reporter's own diagnosis was that the GitHub extension honoured the JVM
proxy settings and the GitLab one ignored them — which is precisely Jersey ignoring what
`RestTemplate` honours. #588 moves to milestone 5.5 and this initiative, and issue 2 closes it.

The cost is real and should be expected: `GitLabIssueWrapper` currently wraps
`org.gitlab4j.api.models.Issue` and has to be rewritten, and every endpoint is hand-written.

### Configuration

The configuration becomes `name`, `url`, `token`, `ignoreSslCertificate`, on `CredentialsConfiguration`
like GitHub and Bitbucket Cloud.

- **`user` is dropped.** GitLab's API has no password authentication, and HTTPS Git authentication
  with a PAT accepts any username — a constant (`oauth2`) is used.
- **No `authType` discriminator.** Unlike Bitbucket Cloud, GitLab offers exactly one usable API
  auth mode: deploy tokens are explicitly barred from the REST API, and job tokens live only for
  the duration of a job.
- **No auto-merge identity.** GitLab does not forbid self-approval; whether it is allowed is the
  project setting `merge_requests_author_approval`. That is a deployment concern, documented, not a
  configuration field.
- **`ignoreSslCertificate` stays** — it earns its place on self-managed behind an internal CA.

**Migration, not a clean break.** Configurations are stored as encrypted JSON, so this is *not* a
Flyway migration: it is a `StartupService` calling `ConfigurationRepository.migrate(...)`, exactly
as `GitHubConfigurationTokenMigration` does. It maps `password` → `token` and drops `user`. The
field already holds a personal access token in practice — the client passes it straight through as
the PAT and the configuration page already labels it "Token" — so this is closer to a rename than a
migration. A release-notes line covers the CasC schema change.

### The project property does not change

Bitbucket Cloud had to move `workspace` out of the configuration and onto the property. GitLab's
equivalent is a *path* that can be arbitrarily deep — `group/subgroup/subsubgroup/project` — so a
workspace/repository split would be actively wrong.

The property keeps its single `repository` field holding the full path, alongside `configuration`,
`indexationInterval` and `issueServiceConfigurationIdentifier`. The client URL-encodes the path
(`/` → `%2F`) at call time. **Existing GitLab project properties keep working untouched**, and the
CLI's already-published `setProjectGitLabConfigurationProperty` mutation needs no change.

### Issues

Two live defects are fixed:

- `getConfigurationList()` returns an empty list, so the GitLab issue service cannot be selected in
  the property UI at all;
- `getLastCommit()` is `TODO()`.

A frontend is added — `components/framework/issues/gitlab-issues.js` and `issues/gitlab/Summary.js`
— mirroring GitHub's, so GitLab issues stop rendering bare in change logs. The wrapper already
exposes labels and the milestone URL.

Only the `#123` reference form is supported. Cross-project `group/project#123` references need
cross-project API access and introduce ambiguity for little gain; they are a follow-up. GitLab's
*closing* keywords (`Closes #123`, `Fixes #123`) are an admin-customisable, project-level concern
that Yontrack does not parse.

### SCM and auto-versioning

`GitLabSCMExtension` implements `SCMExtension` with an inner `SCMChangeLogEnabled`, aligned with
GitHub and Bitbucket Cloud: `forAllCommits` reads the **local synced clone**, while
`getCommits(from, to)` and `getCommit` use the REST API (`repository/compare`, `repository/commits/:sha`).
File refs are `scm://gitlab/<configuration>/<project path>/<path>`.

Auto-versioning supports **both** values of the existing `AutoApprovalMode` enum — GitLab is the
second SCM after GitHub to honour `SCM` rather than reject it:

- **`CLIENT`** — Ontrack approves (`POST …/approve`, a Free-tier endpoint), polls, and merges.
- **`SCM`** — `PUT …/merge` with **`auto_merge: true`**. Not `merge_when_pipeline_succeeds`, which
  was deprecated in 17.11; note the cancel endpoint kept the old name
  (`cancel_merge_when_pipeline_succeeds`).

Three GitLab specifics the implementation must respect:

- Poll **`detailed_merge_status`**, never `merge_status`, deprecated since 15.6.
- **Always send `sha`** on merge: 19.2 added a project setting that makes it mandatory, and a
  mismatch is a 409.
- Read back **`squash_on_merge`**, not `squash` — project settings can override what was requested.

Two behaviours are **documented rather than enforced**, which is the "supported, at least
documented if not tested" position:

- on projects with **merge trains**, 19.1+ routes an `auto_merge` request into the train instead of
  merging directly;
- self-approval depends on the project's `merge_requests_author_approval` setting, and a production
  deployment often wants a separate approver identity even though the test fixture does not.

### CI configuration

Two engines, two names, as Bitbucket Cloud did with `bitbucket-cloud` and `bitbucket-pipelines`.

**SCM engine `gitlab`.** `matchesUrl` cannot be a hardcoded host — a self-managed instance is on an
arbitrary one. This is the one place where **GitHub, not Bitbucket, is the model**: the SCM URL is
matched against the *configured* instance URLs, as `GitHubSCMEngine.findConfigurationByURL` does,
and everything after the host is the project path, stripping `.git`.

**CI engine `gitlab-ci`**, detected by `GITLAB_CI == "true"`:

| What | From |
|---|---|
| SCM URL | `CI_PROJECT_URL` + `.git` |
| Revision | `CI_COMMIT_SHA` |
| Branch | `CI_COMMIT_REF_NAME`, or `PR-<CI_MERGE_REQUEST_IID>` when that variable is set |
| Build suffix | `CI_PIPELINE_IID` |
| Project name | `CI_PROJECT_NAME` |

**`CI_REPOSITORY_URL` must not be used**: it embeds `gitlab-ci-token:<job token>`, and using it
would write a credential into the project property. The `PR-<iid>` form exists so that merge-request
pipelines are rejected as on the other engines.

The engine writes a `BuildGitLabPipelineRunProperty` (project path, pipeline id and iid,
`CI_PIPELINE_URL`) with its type, mutation provider and frontend components, and adds a
`gitlab-pipeline` run-info source type with its icon in `RunInfoSourceTypeIcon`.

### GitLab pipelines

`POST /projects/:id/pipeline`, authenticated with the configuration's token, returns the pipeline
object — including its `id` — immediately. No correlation-id workaround is needed, as it was for
GitHub workflows. Trigger tokens are not used: they would mean a second secret per project for no
gain.

A `GitLabPipelinesService` provides trigger-and-wait and is deliberately the shared seam between the
notification channel and the post-processing, as `BitbucketPipelinesService` is.

Notification channel **`gitlab-pipeline`**:

- config: `config`, `project`, `ref`, `variables` (name/value), `callMode` (`ASYNC`/`SYNC`),
  `timeoutSeconds`;
- `project`, `ref` and variable values are templated;
- output: `id`, `iid`, `url`, `status`;
- minimum poll interval **10 s** — not for today's limits but for the **announced** tier-aware ones,
  which drop Free to a 100 req/min burst;
- a `mock-gitlab-pipeline` channel under the DEV profile, with its recorder and controller under
  `/extension/gitlab/mock/pipelines`, exactly as Bitbucket Cloud does.

### Auto-versioning post-processing

Post-processing **`gitlab`**, reusing the channel's trigger-and-wait service. Settings: default
config, project, ref, retries, delay; per-order overrides of config, project and ref. Variables
passed, **uppercase** to match both Bitbucket Cloud and GitLab CI convention (GitHub's lowercase is
the outlier): `REPOSITORY`, `UPGRADE_BRANCH`, `DOCKER_IMAGE`, `DOCKER_COMMAND`, `COMMIT_MESSAGE`,
`VERSION`. Always waits. A `mock-gitlab` post-processing under the DEV profile.

### Settings and CasC

Both are absent today and both are in scope.

- `GitLabSettings`: `maxCommits`, `squash`, `removeSourceBranch`, `autoMergeTimeout`,
  `autoMergeInterval`. There is deliberately **no three-way merge strategy** as on Bitbucket Cloud:
  GitLab's merge API offers `squash` and `should_remove_source_branch` and nothing else.
- `GitLabPostProcessingSettings`, with its manager, provider and CasC.
- `GitLabConfigurationCascContext` — an `AbstractCascContext` + `SubConfigContext` on field
  `gitlab`, rendering the token obfuscated.
- Settings forms `gitlab-form.js` and `gitlab-av-post-processing-form.js`.

### Testing

**Mocks are the default and cover everything.** `MockRestServiceServer` for the client, the
DEV-profile mock channel and mock post-processing for the rest. The KDSL acceptance tests —
`ACCGitLabExtension`, `ACCGitLabPipelineNotifications`, `ACCGitLabAutoVersioningPostProcessing` —
run entirely against the mock channel and never touch gitlab.com, alongside the KDSL types
(configuration, configurations extension, project property extension, notification channel config
with `CHANNEL`/`MOCK_CHANNEL`, mock management client).

**Real tests are split by what they cost**, which is the decisive constraint:

- Real tests that are **API-only** — SCM, change log, auto-versioning merge requests — consume no
  compute minutes. They live in **integration shard 5** (where `ontrack-extension-gitlab` already
  sits), env-gated and **skipped by default**, via `ONTRACK_TEST_EXTENSION_GITLAB_IGNORE`, exactly
  as the Bitbucket Cloud and GitHub ITs are. Introduced by issue 2.
- Real tests that **run a pipeline** live in a dedicated `.github/workflows/gitlab-real.yml`,
  dispatched from the `On BRONZE` notification through the `github-workflow` channel — the same
  mechanism by which GOLD dispatches `release.yml` — but **restricted to `main`**, with
  `concurrency: cancel-in-progress: true` so a burst of commits collapses into one run. It reports
  a recording-only validation **`GITLAB.REAL`**, declared in `.yontrack/ci.yaml` and part of no
  promotion, like the `SECURITY.*` and `COVERAGE.*` stamps.

The budget is why. A Free namespace gets **400 compute minutes a month**, and BRONZE is granted on
every green build of every branch; unrestricted, two tests against a one-minute pipeline would
exceed the budget before anything else ran. Restricted to `main` with collapsing, it lands around
20–40 runs a month.

A **dockerised GitLab was considered and rejected**. The blocking fact is that every one of the five
integration shards brings its own copy of `compose/docker-compose-it.yml` up on its own runner, so
a GitLab service there would boot five GitLab containers per commit; it would need its own compose
file, its own workflow, and a second container for a registered GitLab Runner before it could test
a pipeline at all. The gitlab.com Free tier is the simpler instrument, and 400 minutes a month is
eight times Bitbucket's 50.

**The fixture** is one free group, one bot user, and one fixture project carrying a `.gitlab-ci.yml`,
with `merge_requests_author_approval = true` so the bot can approve its own merge request. One
account, not Bitbucket's two: GitLab does not forbid self-approval, and a second gitlab.com account
is real recurring cost in 2FA, rotation and recovery. Authentication is a **personal access token**,
because project and group access tokens are Premium on gitlab.com.

**Operational rules**, learned from the Bitbucket runbook: a wizard script
`scripts/gitlab-test-project.sh` and a module README (issue 1, which blocks everything else);
secrets as `ONTRACK_TEST_EXTENSION_GITLAB_*` with the expiry recorded in a repository variable;
**PATs cap at 365 days**, making rotation an annual chore; unique per-run branch and merge-request
names, with a sweep at suite start of anything older than a day; pipeline creation is capped at
**25 per minute per project**.

### Documentation

Six mkdocs pages, each with its `mkdocs.yml` nav entry, each folded into the issue that owns the
feature rather than gathered into a documentation issue:

| Page | Issue |
|---|---|
| `start/configuration/gitlab.md` | 2 |
| `start/feeding/gitlab.md` | 7 |
| `integrations/notifications/gitlab-pipeline.md` | 8 |
| `integrations/auto-versioning/gitlab.md` | 9 |
| `reference/ci-config/scm-engines/gitlab.md` | 6 |
| `reference/ci-config/ci-engines/gitlab-ci.md` | 7 |

`start/feeding/gitlab.md` is the one Bitbucket Cloud does not have and GitHub does: with a CI engine
and CLI support, "feeding Yontrack from GitLab CI" is the page a new user actually needs.

The dead `ontrack-docs/src/docs/asciidoc/usage-gitlab.adoc` is deleted on the way, with its
`include::` in `usage-git.adoc` and the commented-out link in `feature-scm.adoc`. A release-notes
line covers the configuration change.

### CLI and skills

`yontrack-cli`:

- **`project set-property gitlab`** — a direct copy of `cmd/projectSetPropertyGitHub.go`. The
  bundled `ontrack.graphql` **already carries** `setProjectGitLabConfigurationProperty` with the
  same field set, and the property does not change, so this issue has **no blocker** and differs
  from Bitbucket's `yontrack-cli#70`, which was gated on the workspace rework.
- **`run-info` defaults for GitLab CI** — the CLI infers nothing today; `GetRunInfo` reads five
  flags and returns nothing when all five are unset. Bitbucket's `yontrack-cli#71` creates the
  shared `utils` detection helper and says in as many words that "a later GitHub Actions or GitLab
  default can sit beside it", so this issue depends on it and adds the `CI_*` case:
  `--source-type gitlab-pipeline`, `--source-uri` from `CI_PIPELINE_URL`, `--trigger-type commit`,
  `--trigger-data` from `CI_COMMIT_SHA`. Explicit flags always win.
- **A README section on GitLab CI**, and the `# Integrations` list extended. `ci config --env-all CI_`
  needs no change once the server has the CI engine.

`yontrack-skills`: a **`yontrack-gitlab-ci`** skill mirroring `yontrack-github-actions`, GitLab
mentioned in `yontrack-auto-versioning` where it discusses `pushMode: PR` and post-processing, and
the plugin metadata — the `skills` array and keywords in `.claude-plugin/plugin.json`, the keywords
in `.claude-plugin/marketplace.json`, and the README skill list. Per that repo's own rule, the skill
is **self-contained** rather than cross-referencing the GitHub one, because a skill is loaded alone.

## Definition of done, decided once

- **Demo**: no demo coverage for any issue of this initiative. The demo instance cannot reach
  gitlab.com any more than it could bitbucket.org, and there is no GitHub demo coverage either.
- **Mobile**: no mobile impact. The mobile UI has eight screens — home, projects, project, branch,
  build, deployment, workflow-instance, account — and `components/mobile/mobileRoutes.js` routes
  `/extension/scm/…/changelog` and `/extension/scm/…/issue-info/` to the **desktop-only** screen.
  Change logs and issue pages are deliberately absent from mobile, so the GitLab issue summary
  component added by issue 3 has no mobile counterpart, and no other change here touches a
  GraphQL field the mobile UI reads.

## Sources

- [Access token scopes](https://docs.gitlab.com/security/tokens/access_token_scopes/)
- [Project access tokens](https://docs.gitlab.com/user/project/settings/project_access_tokens/)
- [Pipelines API](https://docs.gitlab.com/api/pipelines/) and [pipeline triggers](https://docs.gitlab.com/api/pipeline_triggers/)
- [Merge requests API](https://docs.gitlab.com/api/merge_requests/) and [merge request approvals](https://docs.gitlab.com/api/merge_request_approvals/)
- [Predefined CI/CD variables](https://docs.gitlab.com/ci/variables/predefined_variables/)
- [Repositories](https://docs.gitlab.com/api/repositories/), [commits](https://docs.gitlab.com/api/commits/), [branches](https://docs.gitlab.com/api/branches/), [repository files](https://docs.gitlab.com/api/repository_files/)
- [Issues API](https://docs.gitlab.com/api/issues/) and the [issue closing pattern](https://docs.gitlab.com/administration/issue_closing_pattern/)
- [GitLab.com rate limits](https://docs.gitlab.com/user/gitlab_com/rate_limits/) and [compute minutes](https://docs.gitlab.com/ci/pipelines/compute_minutes/)
- [gitlab4j-api](https://github.com/gitlab4j/gitlab4j-api)
- [Bitbucket Cloud support — issue breakdown](../bitbucket-issues.md), the initiative this one mirrors
