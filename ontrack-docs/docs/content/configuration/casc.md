# Configuration as Code

While Yontrack can be configured using its UI, it's recommended to use the CasC (Configuration as Code).

Yontrack supports to be configured as code by default.

## Using config map or secret

Using the [Yontrack Helm chart](https://github.com/yontrack/yontrack-chart), you can put your CasC files in secrets and/or config maps.

For example:

```yaml
ontrack:
  casc:
    map: some-config-map-name
    secret: some-secret-name
```

Entries of the config map or secret can have an arbitrary number of entries, and each entry name must be like `<any-name>.yaml` and contain some Casc code.

## CasC directly in values

You can use the `casc` top value to declare the Casc configuration directly in the values:

```yaml
casc:
  ontrack:
    config:
      settings:
        system-message:
            content: "Yontrack is up and running!"
            type: "INFO"
```

## Using secrets

{% raw %}
Casc files can refer to secrets through `{{ secret.name.property }}` expressions which are extrapolated using environment variables or secret files.
{% endraw %}

### Using environment variables

The default behavior is to use environment variables. The name of the environment variable to consider is:
`SECRET_<NAME>_<PROPERTY>`.

For example, if your CasC fragment contains:

{% raw %}
```yaml
ontrack:
  config:
    github:
      - name: github.com
        token: {{ secret.github.token }}
```
{% endraw %}

Given a `ontrack-github` K8S secret containing the secret token in its `token` property, you can set the following
values for the chart:

```yaml
ontrack:
  env:
    - name: SECRET_GITHUB_TOKEN
      valueFrom:
        secretKeyRef:
          name: "ontrack-github"
          key: "token"
```

### Using secret files

Instead of using environment variables, you can also map secrets to files and tell Yontrack to refer to the secrets in the files.

Given the example below:

{% raw %}
```yaml
ontrack:
  config:
    github:
      - name: github.com
        token: {{ secret.yontrack-github.token }}
```
{% endraw %}

You can map the `yontrack-github` K8S secret onto a volume and tell Ontrack to use this volume:

```yaml
ontrack:
  casc:
    secrets:
      mapping: file
      names:
        - yontrack-github
```

> The way your define these secrets in the first place depends on your configuration and cloud environment. A typical approach is to use external secret definitions.

## Casc schema

All the CasC fragments must comply with the Yontrack CasC format.

This schema is available in the UI in the user menu at _System > Configuration as code_.

You can download the JSON schema using the _JSON Schema_ button or use the UI to configure what you need and look at the generated CasC YAML code.

### Showing the CasC YAML in the UI

* edit your configuration manually using the Yontrack UI
* navigate to the _System > Configuration as code_ page in your user menu
* click on the _Load_ button
* your CasC is displayed

Your can use the displayed YAML or parts of it to configure your Yontrack instance.

### Using the JSON schema

You can download the Yontrack CasC [JSON Schema](https://json-schema.org/) by navigating to _System_ > _Configuration as Code_ and click on _JSON Schema_.

This downloads a `ontrack-casc-schema.json` file.

> Note that this schema is versioned.

You can use it to validate your CasC YAML files. See the [appendixes](../appendix/json-schemas.md) to learn more about using JSON schemas for edition and validation.

[//]: # (TODO Using the API)