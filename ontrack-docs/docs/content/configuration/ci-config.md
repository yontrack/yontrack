# CI configuration

Jenkins pipelines, GitHub workflows, direct integrations with the CLI can use a `.yontrack/ci.yaml` file for an easy
setup of the Yontrack items based on the available information.

!!! note

    Prior to Yontrack V5, the usual way of feeding data into Yontrack was to use the GraphQL CLI, either directly or via integrations like Jenkins pipeline steps, GitHub actions, CLI, etc.

    This is still possible, but the new "CI configuration" is the recommended way to feed data into Yontrack.

This `.yontrack/ci.yaml` file acts a unique way to configure the elements of your pipeliens into Yontrack, and is
compatible with all the support CI tools.

For example, you can just use one unique endpoint to send a configuration file to Yontrack:

```yaml
version: v1
configuration:
  defaults:
    branch:
      validations:
        BUILD:
          tests: { }
        UI_UNIT:
          tests: { }
        KDSL.ACCEPTANCE:
          tests: { }
        PLAYWRIGHT:
          tests: { }
      promotions:
        BRONZE:
          validations:
            - BUILD
            - UI_UNIT
            - KDSL.ACCEPTANCE
            - PLAYWRIGHT
        RELEASE:
          promotions:
            - BRONZE
          validations:
            - GITHUB.RELEASE
      notificationsConfig:
        notifications:
          - name: On validation error
            events:
              - new_validation_run
            keywords: failed
            channel: slack
            channelConfig:
              channel: '#notifications'
              type: 'ERROR'
            contentTemplate: |
              Build ${build} has failed on ${validationStamp}.
          - name: On BRONZE
            promotion: BRONZE
            events:
              - new_promotion_run
            channel: slack
            channelConfig:
              channel: '#notifications'
              type: 'SUCCESS'
            contentTemplate: |
              Build ${build} has been promoted to ${promotionLevel}.
          - name: On internal release
            promotion: RELEASE
            events:
              - new_promotion_run
            channel: slack
            channelConfig:
              channel: '#internal-releases'
              type: 'SUCCESS'
            contentTemplate: |
              Yontrack ${build} has been released.

              ${promotionRun.changelog?title=true&commitsOption=ALWAYS}
  custom:
    configs:
      - conditions:
          name: branch
          condition: '^release/\d+\.\d+$'
        branch:
          notificationsConfig:
            notifications:
              - name: On RELEASE
                promotion: RELEASE
                events:
                  - new_promotion_run
                channel: slack
                channelConfig:
                  channel: '#releases'
                  type: 'SUCCESS'
                contentTemplate: |
                  Yontrack ${build} has been released.

                  ${promotionRun.changelog?title=true&commitsOption=OPTIONAL}
```

## Integrations

There are several ways to integrate this Yontrack CI Configuration from your tools.

* [GitHub actions](#github-actions)
* [Jenkins pipeline](#jenkins-pipeline)
* [Yontrack CLI](#yontrack-cli)
* [Direct GraphQL call](#direct-graphql-call)

### GitHub actions

You can use the [
`nemerosa/ontrack-github-actions-cli-config`](https://github.com/nemerosa/ontrack-github-actions-cli-config) GitHub
action to configure Yontrack from your GitHub workflows:

```yaml
  - name: "Yontrack configuration"
    id: yontrack-config
    uses: nemerosa/ontrack-github-actions-cli-config@v{{ ontrack_github_actions_cli_config_version }}
    env:
      YONTRACK_URL: ${{ '{{' }} vars.YONTRACK_URL {{ '}}' }}
      YONTRACK_TOKEN: ${{ '{{' }} secrets.YONTRACK_TOKEN {{ '}}' }}
    with:
      github-token: ${{ '{{' }} secrets.GITHUB_TOKEN {{ '}}' }}
```

It expects the `.yontrack/ci.yaml` file to be present in the root of the repository.

### Jenkins pipeline

You can use the https://github.com/nemerosa/ontrack-jenkins-cli-pipeline Jenkins pipeline library to configure Yontrack
from your Jenkins pipelines:

```groovy
ontrackCliCIConfig()
```

It expects the `.yontrack/ci.yaml` file to be present in the root of the repository.

### Yontrack CLI

You can use the [Yontrack CLI](https://github.com/nemerosa/ontrack-cli) to configure Yontrack from your CI pipelines:

```shell
ontrack-cli ci config \
  --file .yontrack/ci.yaml \
  --env ... \
  --env ...
```

!!! note

    See [environment variables](#environment-variables) for the list of inputs.

### Direct GraphQL call

Finally, you can always use the GraphQL API to configure Yontrack from your CI pipelines:

```graphql
mutation {
    configureBuild(input: {
        config: "...",
        ci: "",
        scm: "",
        env: [...]
    }) {
        build {
            id
        }
        errors {
            message
        }
    }
}
```

In this mutation's input:

* `config` is the YAML for the CI Configuration
* `ci` is the name of the [CI engine](#ci-engines) to use
* `scm` is the name of the [SCM engine](#scm-engines) to use
* `env` is the list of [environment variables](#environment-variables) to use

## Reference

The minimal CI Configuration file is:

```yaml
version: v1
configuration: { }
```

The following elements will be configured automatically in Yontrack:

* a [project](#project-configuration) will be created based on the detected SCM URL (for example, if the SCM URL is
  `https://github.com/yontrack/yontrack`, a project named `ontrack` will be created and associated with the
  `https://github.com/yontrack/yontrack` GitHub repository)
* a [branch](#branch-configuration) will be created based on the detected SCM branch (for example, if the SCM branch is
  `release/1.0`, a branch named `release-1.0` will be created and associated with the `release/1.0` SCM branch)
* a [build](#build-configuration) will be created and associated with the detected SCM commit. The name of the build is
  generated using a timestamp. If a `VERSION` [environment variable](#environment-variables) is defined, its value will
  be used as the build release label.

The `configuration` section can have two sub-sections:

* `defaults` contains the [configurations](#configuration) which are used by default.
* `custom` contains a list of [configurations](#configuration), each of them associated with number
  of [conditions](#conditions). These configurations act as overrides to the `defaults` ones if the conditions are met.

### Configuration

Each configuration contains the following optional elements:

* [`project`](#project-configuration)
* [`branch`](#branch-configuration)
* [`build`](#build-configuration)

#### Project configuration

The `project` configuration is optional.

It may contain the following elements:

* `properties` - list of [properties](#properties) to set for the project
* `name` - if set, overrides the name of the project instead of computing it from the environment
* `scmConfig` - name of the SCM configuration to use for this project instead of computing it from the environment
* `issueServiceIdentifier` - issue service configuration for this project
* `scmIndexationInterval` - SCM indexation interval (in minutes)
* `extensions` - list of [extensions](#extensions) to apply to the project

#### Branch configuration

The `branch` configuration is optional.

It may contain the following elements:

* `properties` - list of [properties](#properties) to set for the branch
* `validations` - list of [validation stamps](#validations) to define for the branch
* `promotions` - list of [promotion levels](#promotions) to define for the branch
* `extensions` - list of [extensions](#extensions) to apply to the branch

##### Validations

The `validations` property is a map of all the [validation stamps](../concepts/model/index.md#validation-stamps) to
define for the branch.

The simplest configuration is to define only a list of names for these validation stamps:

```yaml
branch:
  validations:
    - build: { }
    - ui-unit: { }
    - acceptance: { }
    - ui-acceptance: { }
```

This code would define four validation stamps named `build`, `ui-unit`, `acceptance` and `ui-acceptance`, not typed.

The validation stamps can be typed using their configurations. For example, to make the `build` validation stamp typed
for a test summary:

```yaml
branch:
  validations:
    - build:
        tests:
          warningIfSkipped: true
```

!!! note

    Use the [JSON schema](#json-schema) to see which other types are available.

##### Promotions

The `promotions` property is a map of all the [promotion levels](../concepts/model/index.md#promotion-levels) to define
for the branch.

The simplest configuration is to define only a list of names for these promotion levels:

```yaml
branch:
  promotions:
    - BRONZE: { }
    - SILVER: { }
```

Each promotion level can be associated with a list of validation stamps and/or other promotion levels:

```yaml
branch:
  promotions:
    - BRONZE:
        validations:
          - build
          - ui-unit
    - SILVER:
        validations:
          - acceptance
          - ui-acceptance
        promotions:
          - BRONZE
```

A promotion can also have dependencies. For example:

```yaml
branch:
  promotions:
    - BRONZE:
        validations:
          - build
          - ui-unit
    - SILVER:
        validations:
          - acceptance
          - ui-acceptance
        dependsOn:
          - BRONZE
```

The semantic of `dependsOn` is different from `promotions`:

* `promotions` define the list of other promotions that participate in the auto-promotion of this promotion ("if BRONZE is granted and the listed validations are OK, then SILVER is granted automatically")
* `dependsOn` defines the list of promotions that must be completed before the promotion can be completed ("trying to promote to SILVER without having completed BRONZE" will fail)

###### Fields

A promotion level can define a list of [fields](../concepts/model/promotion-level-fields.md) that must be filled in when a build is promoted. Fields are defined under the `fields` key of a promotion:

```yaml
branch:
  promotions:
    BRONZE:
      validations:
        - build
      fields:
        - name: ticket
          displayName: Ticket
          description: "JIRA ticket reference"
          type: TEXT
          required: true
        - name: env
          displayName: Environment
          type: CHOICE
          options:
            - staging
            - prod
        - name: hotfix
          displayName: Hotfix?
          type: BOOLEAN
```

Each field supports the following properties:

| Property      | Required | Description                                                                 |
|---------------|----------|-----------------------------------------------------------------------------|
| `name`        | Yes      | Technical identifier for the field (unique per promotion level)             |
| `displayName` | Yes      | Human-readable label shown in the UI                                        |
| `description` | No       | Optional description shown as a tooltip                                     |
| `type`        | Yes      | One of `TEXT`, `NUMBER`, `BOOLEAN`, `CHOICE`, `LINK`                        |
| `required`    | No       | Whether the field must be filled in before promoting (default: `false`)     |
| `options`     | No       | List of allowed values — only valid when `type` is `CHOICE`                 |

When the CI config is applied multiple times (e.g. on different branches), fields are merged by their `name`: existing fields are updated, new fields are added, and no fields are removed.

#### Build configuration

The `build` configuration is optional.

It may contain the following elements:

* `properties` - list of [properties](#properties) to set for the build
* `buildName` - overrides the computed build name using a [template](../appendix/templating.md) (
  see [below](#build-name))
* `extensions` - list of [extensions](#extensions) to apply to the build

When using the Bitbucket or GitHub [SCM](#scm-engines), the `GIT_COMMIT` [environment variable](#environment-variables)
is used to set
a [Git Commit property](../generated/properties/property-net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType.md)
on the build.

For the GitHub [CI engine](#ci-engines), the build is linked to the GitHub workflow run.

For the Jenkins [CI engine](#ci-engines), the build is linked to the Jenkins build.

If the `VERSION` [environment variable](#environment-variables) is defined, its value will be used as the build release
label.

##### Build name

By default, the build name is computed using:

```text
{timestamp}-{suffix}
```

where:

* the timestamp is the current UTC timestamp using the `yyyyMMddHHmmss` format
* the suffix depends on the [CI engine](#ci-engines) being used, but is usually the value of the
  `BUILD_NUMBER` [environment variable](#environment-variables) or the `GITHUB_RUN_NUMBER` environment variable in a
  GitHub workflow.

This build name can be overridden using the `buildName` property, which is a [template](../appendix/templating.md) with
only one context, `env`, allowing to access the [environment variables](#environment-variables) defined in the CI
configuration.

For example, if you want to use your custom timestamp format and a build number, you can use:

{% raw %}

```text
buildName: '${#.datetime?format=yyyy-MM-dd-HH-mm-ss}-${env.BUILD_NUMBER}'
```

{% endraw %}

### Conditions

Each custom configuration is associated with a list of conditions. For example:

```yaml
custom:
  configs:
    - conditions:
        - name: branch
          config: 'main'
        - name: environment-regex
          config:
            name: VERSION
            regex: '\d+\.\d+\.\d+'
      branch:
      # Branch configuration
```

In this case, the `branch` configuration will be used to patch the default configuration for any SCM branch matching the
regular expression `main` _and_ having a `VERSION` environment variable with a value matching the regular expression
`\d+\.\d+\.\d+`.

The custom configuration is applied only if **all the conditions**   in the list are met.

If you need some _or_ condition, you can use the [`or`](../generated/ci-config/conditions/or.md) condition.

See the [conditions reference](../generated/ci-config/conditions/index.md) for the list of supported conditions and
their parameters.

#### Merging the configurations

When a custom configuration conditions are met, it is merged with the default configuration.

The rules to merge a custom configuration into a default one are:

* properties, validations and promotions are merged at key level
* in lists:
    * custom elements not present in the default list are added
    * default elements not present in the custom list are left
    * elements present in both lists are merged. The rules depend on the type of [extension](#extensions)

### CI engines

In the context of the CI Configuration, a CI engine is the client which will call Yontrack to configure the elements
like the project, branch and build.

It's responsible to collect some environment and allow Yontrack to perform its actions.

If a CI engine is not specified, Yontrack will do its best to guess it from the provided environment. Each CI engine has
its own way of checking the environment to see if it matches.

See the [CI engines reference](../reference/ci-config/ci-engines/index.md) for the list of supported CI engines and the
way they work.

!!! note

    If the way you [inject the CI Configuration](#integrations) does not support the detection of the CI engine, you can pass it explicitly using the `ci` parameter. For example with the Yontrack CLI:

    ```bash
    yontrack \
      ... \
      --ci github
    ```

### SCM engines

In the context of the CI Configuration, a SCM engine is responsible for linking the project, branch and build to a SCM
repository.

If a SCM engine is not specified, Yontrack will do its best to guess it from the provided environment. Each SCM engine
has its own way of checking the environment to see if it matches. It usually uses the SCM URL provided by
the [CI engine](#ci-engines).

See the [SCM engines reference](../reference/ci-config/scm-engines/index.md) for the list of supported SCM engines and
the way they work.

!!! note

    If the way you [inject the CI Configuration](#integrations) does not support the detection of the SCM engine, you can pass it explicitly using the `scm` parameter. For example with the Yontrack CLI:

    ```bash
    yontrack \
      ... \
      --scm bitbucket-server
    ```

### Including files

It can be very cumbersome to define the whole CI Configuration in a single file.

Most of the [integrations](#integrations) allow to include other files using the `@<file path>` syntax.

For example, given a `.yontrack/branch.yaml` file:

```yaml
validations:
  BUILD:
    tests: { }
  UI_UNIT:
    tests: { }
  KDSL.ACCEPTANCE:
    tests: { }
  PLAYWRIGHT:
    tests: { }
promotions:
  BRONZE:
    validations:
      - BUILD
      - UI_UNIT
      - KDSL.ACCEPTANCE
      - PLAYWRIGHT
  RELEASE:
    promotions:
      - BRONZE
    validations:
      - GITHUB.RELEASE
```

you can include it at the default `branch` configuration level:

```yaml
version: v1
configuration:
  defaults:
    branch: '@branch.yaml'
```

This inclusion trick can be used anywhere in the CI Configuration file.

### Environment variables

Each [CI engine](#ci-engines) is responsible for collecting the environment variables it needs to configure Yontrack.

See the list of [CI engines](../reference/ci-config/ci-engines/index.md) to check what environment variables they
expect.

### Extensions

#### Auto-versioning

This extension is set at the branch level to set
its [auto-versioning](../integrations/auto-versioning/auto-versioning.md) configuration.

!!! note

    See the [auto-versioning reference](../integrations/auto-versioning/auto-versioning.md#configuration) for the list of available configurations.

Syntax:

```yaml
branch:
  autoVersioning:
    configurations: [ list of configurations ]
```

For example:

```yaml
branch:
  autoVersioning:
    configurations:
      - sourceProject: yontrack
        sourceBranch: '&latest'
        sourcePromotion: RELEASE
        targetPath: versions.properties
        targetProperty: yontrack
```

If the auto-versioning configuration must not be applied to all branches, you can use the `branchFilter` property to
restrict it to a list of branch names using regular expressions. For example:

```yaml
version: v1
configuration:
  default:
    branch:
      autoVersioning:
        branchFilter:
          includes:
            - main
            - 'release\\.*'
          excludes:
            - 'release\\1\..*'
```

This restricts the auto-versioning configuration to the `main` and `release/*` branches, but not the `release/1.*` ones.

#### Auto-versioning check

This extension is set at the build level to check the compliance with the latest versions using
the [auto-versioning check](../integrations/auto-versioning/auto-versioning.md#auto-versioning-checks).

Syntax:

```yaml
build:
  autoVersioningCheck: [ true | false ]
```

For example:

```yaml
build:
  autoVersioningCheck: true
```

When this is set (to `true),
an [auto-versioning check](../integrations/auto-versioning/auto-versioning.md#auto-versioning-checks) will be performed
automatically after the build is created.

#### Environments

This extension is set at the project level to configure [environments](../integrations/environments/environments.md) and slots for this project.

See the [environments reference](../integrations/environments/environments.md#configuration) for some examples.

#### Notifications

This extension is set at the branch level to configure the notifications for this branch.

Syntax:

```yaml
branch:
  notificationsConfig:
    notifications: <list of notifications>
```

Each notification configuration has the following properties:

* `name` - Unique name of the subscription in its scope.
* `promotion` - Targeting a promotion instead of the branch. The promotion must have been configured first.
* `events` - List of [events types](../generated/events/index.md) to subscribe to
* `keywords` - Optional space-separated list of tokens to look for in the events
* `channel` - Name of the channel to send the notification to
* `channelConfig` - Configuration of the [channel](../integrations/notifications/index.md)
* `contentTemplate` - Optional template to use for the message

Example:

```yaml
branch:
  notificationsConfig:
    notifications:
      - name: On validation error
        events:
          - new_validation_run
        keywords: failed
        channel: slack
        channelConfig:
          channel: '#notifications'
          type: 'ERROR'
        contentTemplate: |
          Build ${build} has failed on ${validationStamp}.
```

#### Workflows

Whereas workflows can be specified using the [`notificationsConfig`](#notifications) extension, you can also use the
`workflows` extension to define them directly in the CI Configuration, in a more straightforward way.

Syntax:

```yaml
branch:
  workflows:
    <PROMOTION>:
      <workflows>
```

This configuration is used to associate a list of named workflows to some promotions.

Each workflow is defined as [usual](../integrations/workflows/workflows.md).

Example:

```yaml
workflows:
  RELEASE:
    - name: On internal release
      nodes:
        - id: notification
          executorId: notification
          data:
            channel: slack
            channelConfig:
              channel: '#internal-releases'
              type: 'SUCCESS'
            template: |
              Yontrack ${build} has been released.

              ${promotionRun.changelog?title=true&commitsOption=ALWAYS}
        - id: docs.yontrack.com
          executorId: notification
          data:
            channel: github-workflow
            channelConfig:
              config: github.com
              owner: yontrack
              repository: doc.yontrack.com
              workflowId: doc.yml
              reference: main
              callMode: ASYNC
```

### Properties

Arbitrary properties can be set on projects, branches and builds.

The general syntax is (for example for a build):

```yaml
build:
  properties:
    <fqcn>: <config>
```

where:

* `<fqcn>` is the fully qualified class name of the property type
* `<config>` is the configuration of the property

!!! note

    See the [properties reference](../generated/properties/index.md) for the list of available properties.

Some properties provide aliases to simplify the configuration.

For example:

* `release` for
  the [release property](../generated/properties/property-net.nemerosa.ontrack.extension.general.ReleasePropertyType.md) -
  note that most of the time, you won't need this since it is automatically set when the `VERSION` environment variable
  is defined.
* `useLabel` for
  the [use label property](../generated/properties/property-net.nemerosa.ontrack.extension.general.BuildLinkDisplayPropertyType.md)

For example, for a project:

```yaml
project:
  properties:
    useLabel: false
```

!!! note

    You can use the [JSON schema](#json-schema) to see which other properties are available and what is their configuration.

## JSON schema

The JSON Schema for the CI Configuration is available in the UI in the user menu, under _User information_ > _Resources_
in the _JSON schemas_ section:

![CI Config JSON Schema](ci-config-json-schema.png)

Once downloaded, you can use it to validate your CI Configuration files. See
the [appendixes](../appendix/json-schemas.md) to learn more about using JSON schemas for edition and validation.

## Metrics

Yontrack exports some [operational metrics](../operations/metrics.md) about the CI config injection.

See the [reference](../generated/metrics/net.nemerosa.ontrack.extension.config.ci.CIConfigMetrics.md) for the list of
these metrics.
