Extension: GitLab
=================

This extension supports GitLab, both **gitlab.com** and **self-managed** instances, at whatever URL
the configuration names.

**GitLab Free is the baseline**: nothing in this module may depend on Premium or Ultimate. Two
consequences shape everything below:

* merge request **approve/unapprove is a Free endpoint** — only approval *rules* are Premium — so
  Ontrack can approve a merge request itself, and no second identity is needed;
* **project and group access tokens are Premium on gitlab.com**, so a gitlab.com setup authenticates
  with a **personal access token**.

The requirements this module is being rebuilt against are in
[`docs/grilling/2026-09-gitlab/README.md`](../docs/grilling/2026-09-gitlab/README.md).

Testing
-------

The module has two kinds of tests:

* **mocked** tests, run on every push, which need nothing. They are the default: the client is
  covered with `MockRestServiceServer`, and the pipeline channel and the post-processing with their
  DEV-profile mocks.
* **real** tests, which run against the gitlab.com group dedicated to the tests. They are
  **skipped** when no credential is set. `@TestOnGitLab` is the annotation that gates them, and
  `GitLabTestProperties` / `gitLabTestEnv` the environment they read (`GitLabTestUtils.kt`).

Real tests are split by what they cost, and the split is an annotation:

| Kind                                                           | Annotation                 | Where they run                                                              |
|----------------------------------------------------------------|----------------------------|-----------------------------------------------------------------------------|
| **API-only** — SCM, change log, auto-versioning merge requests | `@TestOnGitLab`            | integration shard 5 of `.github/workflows/ci.yml`, skipped by default       |
| **Pipeline** — anything that starts a job                      | `@TestOnGitLabPipelines`   | `.github/workflows/gitlab-real.yml`, on `main` only — they cost compute minutes |

`@TestOnGitLabPipelines` needs the credentials **and** `ontrack.test.extension.gitlab.pipelines`,
which only `gitlab-real.yml` sets — `ci.yml` pins it to `false`. So a checkout that is fully
provisioned still starts no pipeline on a push, and `./gradlew :ontrack-extension-gitlab:integrationTest`
on a developer's machine starts none either unless it is asked to.

### Real pipeline tests, on BRONZE

`.github/workflows/gitlab-real.yml` runs the two pipeline suites —
`GitLabPipelineNotificationChannelRealIT` and `GitLabPostProcessingRealIT`, four pipelines in all —
and reports the **`GITLAB.REAL`** validation on the build that was promoted. It is dispatched by the
`On BRONZE - GitLab pipelines` subscription in `.yontrack/ci.yaml`, through the `github-workflow`
notification channel, in the same way the GOLD promotion dispatches `release.yml`.

Three things keep it inside the budget, and all three are deliberate:

* the subscription lives in the **`^main$`** block of `custom.configs`, not in the defaults —
  BRONZE is granted on every green build of every branch, and this is what restricts it to `main`;
* the workflow's `concurrency` group **cancels in progress**, so a burst of commits collapses into
  one run rather than one run per commit;
* the switch above, so nothing else in CI can start a pipeline by accident.

`GITLAB.REAL` is **recording only**: declared in `.yontrack/ci.yaml`, part of no promotion, and
nothing waits for it — like the `SECURITY.*` and `COVERAGE.*` stamps.

`.github/workflows/bitbucket-real.yml` and `BITBUCKET.REAL` are the same arrangement for Bitbucket
Cloud. The only difference is the budget: a Free Bitbucket workspace gets 50 build minutes a month
against this namespace's 400, so that workflow adds a **cooldown** to the two guards below and skips
most of the BRONZEs it is dispatched for.

**Until the fixture is provisioned**, no `ONTRACK_TEST_EXTENSION_GITLAB_*` secret exists, and the
workflow stops at its first step with a warning annotation, reporting no validation at all. It is
not red: the tests never ran, so "the real GitLab tests failed" would be untrue, and a red run on
every BRONZE of `main` is how a genuine failure later gets ignored. A **partial** set of credentials
is a different matter and does fail, in `gitLabTestEnabled`, as it does everywhere else.

### The test fixture

A **Free** gitlab.com setup, containing:

| What                                               | Why                                                                                                     |
|----------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| a **bot** gitlab.com account                       | does everything: reading, branches, merge requests, approvals, pipelines. It owns the group.             |
| a **group**, e.g. `yontrack-test`                  | holds the fixture project                                                                                |
| a **fixture project**, e.g. `yontrack-fixture`     | carries the pipeline; shared runners enabled                                                             |
| `merge_requests_author_approval = true` on it      | lets the bot approve its own merge request — GitLab's answer to what needed a second account on Bitbucket |
| `ci_pipeline_variables_minimum_override_role`      | lets the bot pass pipeline variables; a new project refuses them outright                                |
| one **personal access token**, scope `api`         | the only credential; project and group access tokens are Premium on gitlab.com                           |

**Pipeline variables have to be switched on.** Every pipeline test passes variables — they are how
the fixture picks which of its two jobs runs — and recent GitLab versions default a *new* project's
`ci_pipeline_variables_minimum_override_role` to `no_one_allowed`. Until it is set to `owner`, every
trigger comes back `400 Bad Request` with `Insufficient permissions to set pipeline variables`, and
the bot owning the project makes no difference: it is the project setting that refuses, not the role.
Settings → CI/CD → Variables → *Minimum role to use pipeline variables*, or:

```bash
curl -sS --request PUT --header "PRIVATE-TOKEN: $TOKEN" \
  "https://gitlab.com/api/v4/projects/<group>%2F<project>" \
  --data "ci_pipeline_variables_minimum_override_role=owner"
```

**One account, not two.** GitLab does not forbid self-approval the way Bitbucket Cloud does — it is
the project setting above — so a second gitlab.com account, with its own 2FA, rotation and recovery,
is avoided.

The fixture project's content lives in
[`src/test/resources/gitlab-fixture/`](src/test/resources/gitlab-fixture), from where the wizard
commits it, and `GitLabTestFixture` names what the tests rely on:

* `.gitlab-ci.yml`, with **two jobs and one trigger variable each**, so that a test never starts
  the other one:

  | Job    | Trigger variable | What it does                                                                                                        |
  |--------|------------------|---------------------------------------------------------------------------------------------------------------------|
  | `mock` | `MOCK_RESULT`    | ends as `MOCK_RESULT` asks (`success` or `failure`), after `MOCK_DURATION` seconds, echoing `MOCK_MESSAGE`          |
  | `av`   | `UPGRADE_BRANCH` | checks it received every variable the auto-versioning post-processing sends, then runs `DOCKER_COMMAND` — `true` succeeds, `false` fails |

  The `workflow` rules create a pipeline **only when one of the two is passed**, so a branch the
  tests push, a merge request they open and a tag they create run nothing at all and cost nothing.
  Both jobs are capped at five minutes.

  `av` deliberately **pushes nothing back** on the upgrade branch, where the Bitbucket Cloud
  fixture's equivalent commits the version. What Yontrack answers for is triggering the pipeline
  with the right variables on the right ref, waiting for it and reporting it; committing is the
  pipeline's own business, and doing it here would mean a write token as a CI/CD variable of the
  fixture project, with a rotation of its own.
* `gradle.properties` with a `version` entry, edited by the auto-versioning tests.

`GitLabTestFixtureTest` keeps those files honest.

### Provisioning it

Only a human can create a gitlab.com account, pass its identity verification and mint a token, so
this is a wizard which walks through every step, checks each result against the API, optionally runs
one pipeline to prove the fixture works, and writes the GitHub secrets:

```bash
ontrack-extension-gitlab/scripts/gitlab-test-project.sh
```

It needs `curl`, `jq`, and `gh` signed in with admin rights on `yontrack/yontrack`. It remembers the
values in `.gitlab-test.env` at the root of the checkout — gitignored, and holding the token in
clear. Re-run it to renew the token, or to commit a changed fixture: Enter keeps every value already
captured.

### Secrets and properties

Each property is read as a system property or, failing that, as the environment variable of the same
name upper-cased with `_` for `.`. CI passes them as environment variables from secrets of that name.

| Property (`ontrack.test.extension.gitlab.` …) | CI source (`ONTRACK_TEST_EXTENSION_GITLAB_` …) | Content                                          |
|-----------------------------------------------|------------------------------------------------|--------------------------------------------------|
| `group`                                       | secret `GROUP`                                 | path of the test group, e.g. `yontrack-test`     |
| `project`                                     | secret `PROJECT`                               | path of the fixture project **within** the group |
| `token`                                       | secret `TOKEN`                                 | the bot's personal access token, scope `api`     |
| `token.expiry`                                | **variable** `TOKEN_EXPIRY`                    | the token's expiry date, `YYYY-MM-DD`            |
| `ignore`                                      | a workflow input                               | `true` skips the real tests                      |
| `pipelines`                                   | set by `gitlab-real.yml` only                  | `true` also runs the tests that start a pipeline |

The full path GitLab takes wherever an `:id` appears is `<group>/<project>`, URL-encoded — the two
are kept apart because the group is what a test sweeps and the project is what it acts on.

The instance is always `https://gitlab.com`: there is no URL secret. The real tests are skipped when
no credential is set, and **fail** when only some are, so that a half-configured CI does not pass
silently. The secrets are passed to integration shard 5 by `.github/workflows/ci.yml`, whose
`SKIP_GITLAB_IT` input (default `true`) is what turns the real tests on for a run started by hand,
and to `.github/workflows/gitlab-real.yml`, which is the only one to set `pipelines`.

### Writing a real test

* Read the group and the project from the test environment — never hardcode `yontrack-test`.
* Name every branch and every merge request with `gitLabTestBranch()`. The name carries the
  `yontrack-it/` prefix, the creation time and the CI run, so that parallel shards, worktrees and
  developers never collide and a leftover can be dated and traced back.
* Delete what a test creates in a `finally`, as `GitLabMergeRequestIT` does — deleting a branch which
  is already gone is not an error, so the cleanup is unconditional and a merged merge request needs no
  special case. A leftover therefore only ever comes from a killed run; the automatic sweep of
  anything older than a day is not implemented yet, so clean those up in the GitLab UI. Nothing
  outside the `yontrack-it/` prefix is ever deleted.
* Trigger a pipeline only in a test that is meant to cost minutes, annotate it with
  `@TestOnGitLabPipelines` rather than `@TestOnGitLab`, and pass `MOCK_DURATION` as low as the
  assertion allows. A pipeline test which can be replaced by a mocked one, or whose point another
  pipeline test already makes, is a compute minute spent on every BRONZE of `main` for nothing.

### Cost

The Free tier costs nothing and gives the namespace **400 compute minutes a month** — eight times
what the Bitbucket Cloud fixture gets, and still the constraint that shapes the tests:

* nothing runs on a push, by construction of the fixture's `workflow` rules;
* API-only tests — SCM, change log, merge requests, approvals — consume **no** compute minute at all;
* pipeline tests run on `main` only, collapsing a burst of commits into one run, which lands around
  20–40 runs a month. Four pipelines a run, each a few seconds of job time plus the runner's own
  start-up.

A new free namespace gets **no shared runner until gitlab.com's identity verification is done**
(phone, and a payment card on a new account). Until then every pipeline stays pending.

### Reliability

* **Pipeline creation** is capped at **25 per minute per project**. A test that triggers pipelines in
  a loop is wrong.
* **Rate limits**: poll no faster than **every 10 seconds**. The announced tier-aware limits drop
  Free to a 100 requests/minute burst.
* **Leftovers**: a test deletes its own branch in a `finally`, so only a killed run leaves branches
  and merge requests behind. Delete the `yontrack-it/` branches in the GitLab UI when that happens.
* **Token expiry**: a GitLab personal access token lasts **365 days at most**. There is no such thing
  as a non-expiring one, so this is an annual chore.

### Renewal

The token expires on: **<!-- token-expiry -->2027-09-20<!-- /token-expiry -->**.

Before that date, re-run the wizard: mint a new token in stage 4 (Enter keeps everything else), let
it replace the secrets, and commit the date it writes above.
