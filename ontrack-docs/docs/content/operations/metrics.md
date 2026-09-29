# Metrics

## List of metrics

The list of all Yontrack specific metrics is available in the [reference](../generated/metrics/index.md).

## Usage of deprecated features

Yontrack counts every use of a deprecated external feature — a GraphQL field or argument, a REST
endpoint, a configuration property, a CasC key, a templating field, a setting, a property or a CI
configuration attribute — which the next major version removes.

* the counter `ontrack_deprecated_usage_total` is tagged with the `surface` through which the feature
  was used (`graphql`, `rest`, `config`, `casc`, `env`, `templating`, `settings`, `property` or
  `ci-config`) and the deprecated `item`;
* the first use of each item since the start of Yontrack is also logged as a `WARN`, prefixed by
  `[deprecation]`, with what to use instead and the issue which removes it.

Before upgrading to the next major version, check this counter, for example with:

```
sum by (surface, item) (ontrack_deprecated_usage_total)
```

Every item it lists must be migrated before the upgrade.
