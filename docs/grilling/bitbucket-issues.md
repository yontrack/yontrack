# Bitbucket Cloud support — issue breakdown

Outcome of the grilling session of 2026-09-13 on full Bitbucket Cloud support in Yontrack,
the Yontrack CLI and the Yontrack skills.

All 12 issues carry the label `initiative: bitbucket`, `status:todo` and `ready-for-agent`.
The `yontrack/yontrack` ones are in milestone `5.5`; the CLI and skills ones have no milestone.
Dependencies are recorded as native GitHub dependencies, across repositories too.

| # | Issue | Repo | Blocked by |
|---|---|---|---|
| 1 | [Bitbucket Cloud test workspace runbook](https://github.com/yontrack/yontrack/issues/1755) | `yontrack/yontrack` | — |
| 2 | [Bitbucket Cloud configuration and property rework](https://github.com/yontrack/yontrack/issues/1756) | `yontrack/yontrack` | 1 |
| 3 | [Bitbucket Cloud SCM implementation and change log](https://github.com/yontrack/yontrack/issues/1757) | `yontrack/yontrack` | 2 |
| 4 | [Auto-versioning pull requests on Bitbucket Cloud](https://github.com/yontrack/yontrack/issues/1758) | `yontrack/yontrack` | 3 |
| 5 | [CI config SCM engine `bitbucket-cloud`](https://github.com/yontrack/yontrack/issues/1759) | `yontrack/yontrack` | 2 |
| 6 | [CI config CI engine `bitbucket-pipelines`](https://github.com/yontrack/yontrack/issues/1760) | `yontrack/yontrack` | 5 |
| 7 | [Bitbucket Pipelines notification channel](https://github.com/yontrack/yontrack/issues/1761) | `yontrack/yontrack` | 2 |
| 8 | [Auto-versioning post-processing through a Bitbucket pipeline](https://github.com/yontrack/yontrack/issues/1762) | `yontrack/yontrack` | 7 |
| 9 | [`project set-property bitbucket-cloud --workspace`](https://github.com/yontrack/yontrack-cli/issues/70) | `yontrack/yontrack-cli` | 2 |
| 10 | [`run-info` defaults for Bitbucket Pipelines](https://github.com/yontrack/yontrack-cli/issues/71) | `yontrack/yontrack-cli` | 6 |
| 11 | [README: using the CLI in Bitbucket Pipelines](https://github.com/yontrack/yontrack-cli/issues/72) | `yontrack/yontrack-cli` | 6, 9, 10 |
| 12 | [`yontrack-bitbucket-pipelines` skill](https://github.com/yontrack/yontrack-skills/issues/2) | `yontrack/yontrack-skills` | 11 |

## Where we start from

- `ontrack-extension-bitbucket-cloud` exists but is shallow: a configuration (workspace + user +
  app password), a project property (`configuration`, `repository`, indexation, issue service),
  a read-only client (projects and repositories), a catalog provider, CasC, decorations, and ITs
  against a real workspace which are skipped by default.
- It has **no** `SCMExtension` — so no SCM change log, no auto-versioning, no `scm://` refs —
  no CI config SCM engine, no CI engine, no notification channel, no auto-versioning
  post-processing, no KDSL, no MkDocs page.
- `BitbucketCloudConfigurator.getPullRequest` is `TODO()`: any PR lookup throws.
- Bitbucket Cloud and Bitbucket Server both register the catalog provider id `bitbucket`.
- Atlassian removed app passwords on 2026-07-28. The existing configuration, and the CI secrets
  behind the real ITs, are therefore dead.
- Bitbucket Cloud is **not used** by anyone today.
- The closest model is Bitbucket Server (`ontrack-extension-stash`); for the notification channel
  and post-processing it is GitHub. GitLab is not a model — it has none of this either.

## Decisions

### Scope

In 5.5:

- SCM basics — property, commit links, file download
- change logs
- auto-versioning pull requests
- a Bitbucket Pipelines notification channel, usable in workflows
- auto-versioning post-processing through a Bitbucket pipeline
- CI config: an SCM engine **and** a CI engine
- `yontrack-cli` and `yontrack-skills` support

Out of scope: webhook ingestion (the GitHub ingestion counterpart), SCM catalog / indicators
work, PR information on branch pages, a Bitbucket Cloud **issue service** (Jira and the other
issue services keep working through the property), GraphQL mutations for the configurations,
a Bitbucket Pipe, and **demo coverage** — there is none for GitHub either, and the demo cannot
reach bitbucket.org.

### Configuration and authentication

- Two `authType`s:
  - `API_TOKEN` — Atlassian account email + API token, HTTP basic. Every plan.
  - `ACCESS_TOKEN` — Bearer. Accepts a workspace or project access token (Premium plan only) or a
    repository access token (every plan, one repository) without Yontrack caring which.
- No app passwords.
- The configuration holds **credentials only**. `workspace` moves to the project property,
  next to `repository`.
- An auto-merge identity on the configuration (as `autoMergeUser`/`autoMergeToken` on
  Bitbucket Server), because Bitbucket does not let a user approve their own pull request.
- **Clean break, no migration**: configuration, CasC schema, property and catalog provider id
  (`bitbucket` → `bitbucket-cloud`) change without migration code, with a release-notes line.

### SCM

- Aligned with GitHub after #1552: `forAllCommits` reads the **local synced clone**;
  `getCommits(from, to)` and `getCommit` use the REST API (compare / commit).
- Auto-versioning:
  - `autoApproval` approves with the auto-merge identity, polls until the merge checks pass,
    and merges from Yontrack;
  - `remoteAutoMerge` is rejected with an explicit error (as on Bitbucket Server) — if the API
    turns out to offer a real "merge when checks pass", that is a follow-up issue;
  - settings: merge strategy (`merge_commit` / `squash` / `fast_forward`, default `squash`),
    auto-merge timeout and interval, auto-delete branch, max commits.

### Bitbucket Pipelines

- `POST /2.0/repositories/{workspace}/{repo}/pipelines/` returns the pipeline `uuid` and
  `build_number` directly — no correlation-id workaround as for GitHub workflows.
- Notification channel `bitbucket-pipelines`:
  - config: `config`, `workspace`, `repository`, `branch`, `pipeline` (name of a `custom:`
    pipeline; empty = the branch's default pipeline), `variables` (name/value), `callMode`
    (`ASYNC`/`SYNC`), `timeoutSeconds`;
  - `workspace`, `repository`, `branch` and variable values are templated, as for GitHub;
  - branch targets only; no secured variables — secrets belong in the repository variables;
  - output: `uuid`, `buildNumber`, `url`, `state`;
  - a `mock-bitbucket-pipelines` channel under the DEV profile, like `mock-jenkins`, for the KDSL
    acceptance tests.
- Auto-versioning post-processing `bitbucket-cloud` **reuses the channel's trigger-and-wait
  service**. Settings: default config, workspace, repository, pipeline, branch, retries, delay;
  per-order overrides of config, workspace, repository, pipeline, branch. Variables passed:
  `REPOSITORY`, `UPGRADE_BRANCH`, `DOCKER_IMAGE`, `DOCKER_COMMAND`, `COMMIT_MESSAGE`, `VERSION`.
  Always waits.

### CI config

- SCM engine `bitbucket-cloud`, matching `bitbucket.org` URLs — useful on its own with Jenkins.
- CI engine `bitbucket-pipelines`, a separate issue:
  - detected by `BITBUCKET_BUILD_NUMBER`;
  - SCM URL `BITBUCKET_GIT_HTTP_ORIGIN`, revision `BITBUCKET_COMMIT`, branch `BITBUCKET_BRANCH`,
    build suffix `BITBUCKET_BUILD_NUMBER`, project `BITBUCKET_REPO_SLUG`, PR `BITBUCKET_PR_ID`;
  - a build property linking the build to its pipeline run;
  - run-info source type `bitbucket-pipeline`, with its icon in `RunInfoSourceTypeIcon`.

### Testing

- Both mocked tests (every push) and tests against a real workspace (skipped without
  credentials).
- A **Free** workspace: two users (a bot and an approver), a fixture repository with `custom:`
  pipelines, API tokens as GitHub secrets. Created by Damien through a runbook (a wizard script),
  documented in the extension README.
- Real SCM / change log / auto-versioning tests run in the existing optional CI shard. Real
  **pipeline** tests run in the release workflow only, to stay under the 50 free build minutes
  a month.
- Reliability: 1,000 API requests per hour per user or token (poll at ≥ 10 s), variable pipeline
  queue time (generous timeouts), leftover branches and PRs (unique per-run names, a cleanup of
  anything older than a day at suite start), API token expiry (at most one year).

### CLI and skills

- `yontrack-cli`: `project set-property bitbucket-cloud --workspace`; `run-info` defaults from
  `BITBUCKET_*`; a README section on Bitbucket Pipelines. `ci config --env-all BITBUCKET_` needs
  no change once the server has the CI engine. No configuration commands.
- `yontrack-skills`: a `yontrack-bitbucket-pipelines` skill mirroring `yontrack-github-actions`,
  Bitbucket Cloud in `yontrack-auto-versioning`, plugin keywords and README.

### Definition of done, decided once

- **Demo**: no demo coverage for any issue of this initiative.
- **Mobile**: no mobile impact — the mobile UI renders no properties, decorations, run info or
  notification outputs.

## Sources

- [API request limits](https://support.atlassian.com/bitbucket-cloud/docs/api-request-limits/)
- [Access tokens](https://support.atlassian.com/bitbucket-cloud/docs/access-tokens/)
- [Introducing project and workspace access tokens](https://www.atlassian.com/blog/bitbucket/introducing-project-and-workspace-access-tokens)
- [App password deprecation](https://www.atlassian.com/blog/bitbucket/bitbucket-cloud-transitions-to-api-tokens-enhancing-security-with-app-password-deprecation)
- [App password brownout schedule](https://community.atlassian.com/forums/Bitbucket-articles/Deprecation-notice-Bitbucket-Cloud-app-password-brownout/ba-p/3237429)
- [Bitbucket pricing](https://www.atlassian.com/software/bitbucket/pricing)
