Extension: Bitbucket Cloud
==========================

This extension allows the support for Bitbucket Cloud, namely at https://bitbucket.org

Since the REST API is not the same than Bitbucket Server (2.0 versus 1.0), support for Bitbucket Cloud is done is a separate module than for Bitbucket Server (which stays in the `ontrack-extension-stash` module).

Connections to Bitbucket Cloud are done at workspace level. Ontrack projects are then linked to a repository. The Bitbucket Cloud projet is not part of the configuration but will be displayed as an extra information.

Testing
-------

The module has two kinds of tests:

* **mocked** tests, run on every push, which need nothing;
* **real** tests, annotated `@TestOnBitbucketCloud`, which run against a Bitbucket Cloud workspace dedicated to
  the tests. They are **skipped** when no credential is set.

### The test workspace

A **Free** Bitbucket Cloud workspace, e.g. `yontrack-test`, containing:

| What                          | Why                                                                                                          |
|-------------------------------|--------------------------------------------------------------------------------------------------------------|
| a **bot** Atlassian account   | does everything: reading, branches, pull requests, pipelines. It created the workspace and is its admin.    |
| an **approver** account       | approves the bot's pull requests — Bitbucket does not let a user approve their own (auto-versioning).       |
| a **project**, e.g. `YONTRACK` | holds the fixture repository                                                                               |
| a **fixture repository**, e.g. `yontrack-fixture` | the approver has write access to it; Pipelines are enabled                              |
| three **tokens**              | an API token for the bot, one for the approver, and a repository access token on the fixture repository     |

The fixture repository content lives in
[`src/test/resources/bitbucket-cloud-fixture/`](src/test/resources/bitbucket-cloud-fixture), and
`BitbucketCloudTestFixture` names what the tests rely on:

* `gradle.properties` with a `version` entry, edited by the auto-versioning tests;
* `bitbucket-pipelines.yml`, with **custom pipelines only** — a default pipeline would run on every branch the tests
  push:
  * `yontrack-echo` echoes its `MESSAGE` variable,
  * `yontrack-fail` fails when its `FAIL` variable is `true`,
  * `yontrack-auto-versioning` is shaped for auto-versioning post-processing: it reads `REPOSITORY`,
    `UPGRADE_BRANCH`, `DOCKER_IMAGE`, `DOCKER_COMMAND`, `COMMIT_MESSAGE` and `VERSION`, and commits `VERSION` to
    `post-processing.txt` on `UPGRADE_BRANCH`.

`BitbucketCloudTestFixtureTest` keeps those files honest, and `BitbucketCloudTestWorkspaceIT` checks that the
workspace matches them.

### Provisioning it

Only a human can create Atlassian accounts and tokens, so this is a wizard which walks through every step, checks
each result against the API, and writes the GitHub secrets:

```bash
ontrack-extension-bitbucket-cloud/scripts/bitbucket-cloud-test-workspace.sh
```

It needs `curl`, and `gh` signed in with admin rights on `yontrack/yontrack`. It remembers the values in
`.bitbucket-cloud-test.env` at the root of the checkout — gitignored, and holding the tokens in clear. Re-run it to
renew the tokens: Enter keeps every value already captured.

### Secrets and properties

Each property is read as a system property or, failing that, as the environment variable of the same name
upper-cased with `_` for `.`. CI passes them as environment variables from secrets of that name.

| Property (`ontrack.test.extension.bitbucket.cloud.` …) | CI source (`ONTRACK_TEST_EXTENSION_BITBUCKET_CLOUD_` …) | Content                              |
|--------------------------------------------------------|---------------------------------------------------------|--------------------------------------|
| `workspace`                                            | secret `WORKSPACE`                                      | workspace slug                       |
| `project`                                              | secret `PROJECT`                                        | key of the fixture project           |
| `repository`                                           | secret `REPOSITORY`                                     | slug of the fixture repository       |
| `bot.email`                                            | secret `BOT_EMAIL`                                      | the bot's Atlassian account email    |
| `bot.token`                                            | secret `BOT_TOKEN`                                      | the bot's API token                  |
| `approver.email`                                       | secret `APPROVER_EMAIL`                                 | the approver's Atlassian account email |
| `approver.token`                                       | secret `APPROVER_TOKEN`                                 | the approver's API token             |
| `access.token`                                         | secret `ACCESS_TOKEN`                                   | repository access token (Bearer)     |
| `tokens.expiry`                                        | **variable** `TOKENS_EXPIRY`                            | earliest expiry of the three tokens, `YYYY-MM-DD` |
| `ignore`                                               | `SKIP_BITBUCKET_CLOUD_IT` workflow input                | `true` skips the real tests          |
| `pipelines`                                            | `"false"` in `ci.yml`                                   | `true` runs the real **pipeline** tests |

The real tests are skipped when none of the credentials is set, and **fail** when only some are, so that a
half-configured CI does not pass silently.

Scopes of the tokens:

* bot API token — `read:user`, `read:workspace`, `read:project`, `read:repository`, `write:repository`,
  `read:pullrequest`, `write:pullrequest`, `read:pipeline`, `write:pipeline` (all `:bitbucket`);
* approver API token — `read:user`, `read:repository`, `read:pullrequest`, `write:pullrequest`;
* repository access token — Repositories read/write, Pull requests read/write, Pipelines read/write.

To run the real tests locally:

```bash
set -a; source .bitbucket-cloud-test.env; set +a
./gradlew :ontrack-extension-bitbucket-cloud:integrationTest
```

In CI, they run in the integration shard of `.github/workflows/ci.yml` when the workflow is dispatched with
`SKIP_BITBUCKET_CLOUD_IT` unticked.

### Writing a real test

* Annotate it with `@TestOnBitbucketCloud` — or `@TestOnBitbucketCloudPipelines` when it runs a pipeline.
* Read the workspace from `bitbucketCloudTestEnv`; `bitbucketCloudTestConfigReal()` gives a configuration for the bot.
* Name every branch with `BitbucketCloudTestNames().branch("what")`: the name carries the creation time and the CI
  run, so that parallel shards, worktrees and developers never collide.
* Don't bother deleting what a test creates when it fails: before the first real test of a JVM,
  `BitbucketCloudTestCleanupExtension` declines the open pull requests of test branches older than a day, and
  deletes those branches. Branches not named by `BitbucketCloudTestNames` are never touched.

### Cost

The Free plan costs nothing and has **50 build minutes a month** and five users. Every fixture step is capped at
5 minutes. Pipeline tests are therefore off in `ci.yml` and run in the **release workflow only**, through the
`pipelines` switch.

### Reliability

* **Rate limit**: 1,000 API requests per hour per user or token. Poll no faster than every 10 seconds.
* **Pipeline queue**: a pipeline can wait in the queue for a while before it starts. Give pipeline tests generous
  timeouts.
* **Leftovers**: a killed run leaves branches and pull requests behind. They are cleaned up by the next run a day
  later — to clean up sooner, delete the `yontrack-test-*` branches in the Bitbucket UI.
* **Token expiry**: API tokens last one year at most. `BitbucketCloudTestWorkspaceIT` fails from two weeks before
  the expiry date.

### Renewal

The tokens expire on: **<!-- tokens-expiry -->2027-09-14<!-- /tokens-expiry -->**.

Before that date, re-run the wizard: create new tokens in stages 4, 6 and 11 (Enter keeps everything else), let it
replace the secrets, and commit the date it writes above.
