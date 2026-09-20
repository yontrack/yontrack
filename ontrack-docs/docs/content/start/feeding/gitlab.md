# Feeding Yontrack from GitLab CI/CD

!!! note

    Make sure [Yontrack](../configuration/gitlab.md) is configured for GitLab. The
    [`gitlab` SCM engine](../../reference/ci-config/scm-engines/gitlab.md) matches your pipeline against the
    GitLab configurations registered in Yontrack, so at least one of them must name your instance.

## Yontrack token

First, your GitLab pipelines need to be able to connect to Yontrack.

In Yontrack, [create a token](../../security/tokens.md).

In GitLab, at the project or group level, under _Settings_ > _CI/CD_ > _Variables_, create:

* a variable named `YONTRACK_URL` containing the URL of your Yontrack instance
* a **masked** variable named `YONTRACK_TOKEN` containing the token you created in the previous step

## The CI configuration file

Create a `.yontrack/ci.yaml` file at the root of your repository:

```yaml
version: v1
configuration: {}
```

!!! note

    This uses a default configuration for the items created in Yontrack.

    See [CI Configuration](../../configuration/ci-config.md) for more information.

## Calling Yontrack from a job

The [Yontrack CLI](https://github.com/nemerosa/ontrack-cli) is the way in. Install and authenticate it in your
job — see the CLI's own documentation for the installation and for `yontrack config` — then call
`yontrack ci config` once, early in the pipeline: it creates the project, the branch and the build, and every
later call attaches itself to that build.

```yaml
stages:
  - setup
  - build

yontrack-config:
  stage: setup
  script:
    # The Yontrack CLI is installed and pointed at $YONTRACK_URL with $YONTRACK_TOKEN beforehand
    - yontrack ci config
      --file .yontrack/ci.yaml
      --env GITLAB_CI
      --env CI_PROJECT_URL
      --env CI_PROJECT_PATH
      --env CI_PROJECT_NAME
      --env CI_COMMIT_SHA
      --env CI_COMMIT_REF_NAME
      --env CI_MERGE_REQUEST_IID
      --env CI_PIPELINE_ID
      --env CI_PIPELINE_IID
      --env CI_PIPELINE_URL
```

The [`gitlab-ci` CI engine](../../reference/ci-config/ci-engines/gitlab-ci.md) and the
[`gitlab` SCM engine](../../reference/ci-config/scm-engines/gitlab.md) are detected automatically from these
variables — there is nothing to name explicitly.

!!! warning "Do not pass the whole `CI_` namespace"

    It is tempting to write `--env-all CI_`, as the Bitbucket Pipelines page does for `BITBUCKET_`. Don't:
    `CI_REPOSITORY_URL` and `CI_JOB_TOKEN` both carry the job token, and neither is used by Yontrack.
    `CI_PROJECT_URL` names the same repository without any credential, and it is the variable the engine reads.

## Validating and promoting

Once the build exists, the rest of the CLI works as it does anywhere else:

```yaml
unit-tests:
  stage: build
  script:
    - ./run-tests.sh
    - yontrack validate --validation UNIT --status PASSED
```

A validation that reflects whether the step succeeded, and fails the job afterwards:

```yaml
unit-tests:
  stage: build
  script:
    - STATUS=PASSED
    - ./run-tests.sh || STATUS=FAILED
    - yontrack validate --validation UNIT --status "$STATUS"
    - test "$STATUS" = PASSED
```

## Merge request pipelines

Merge request pipelines are **rejected**, as they are on every other CI engine: Yontrack tracks branches, not
merge requests. A pipeline where `CI_MERGE_REQUEST_IID` is set gets a branch name of `PR-<iid>`, which the CI
configuration refuses.

Guard the Yontrack jobs so they do not run at all for a merge request:

```yaml
yontrack-config:
  stage: setup
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
      when: never
    - when: on_success
  script:
    - # ... as above
```

## Tag pipelines

In a tag pipeline, `CI_COMMIT_REF_NAME` is the **tag** name, not a branch, and `CI_COMMIT_BRANCH` is not set at
all. If you feed Yontrack from tag pipelines, give the branch explicitly:

```yaml
  script:
    - export BRANCH_NAME=main
    - yontrack ci config --file .yontrack/ci.yaml --env BRANCH_NAME --env GITLAB_CI # ... and the rest
```

## See also

* [`gitlab-ci` CI engine](../../reference/ci-config/ci-engines/gitlab-ci.md) — the full variable mapping
* [`gitlab` SCM engine](../../reference/ci-config/scm-engines/gitlab.md)
* [CI Configuration](../../configuration/ci-config.md)
* [Setting up GitLab](../configuration/gitlab.md)
