# Testing with and without a licensed feature

Every stack a test runs against — the integration tests, the KDSL acceptance tests, the
Playwright tests — runs the backend under the `dev` profile, where `DevLicenseService` enables
every licensed feature. Testing a feature is therefore the default; testing its *absence* needs
the feature disabled, for the duration of the test only.

## Integration tests

Declare a `@Primary` `LicenseService` wrapping the `DevLicenseService` in a `@Profile(RunProfile.DEV)`
test configuration of the module, and disable the feature around the code under test. See
`TestLicenseService` and `FindingsITConfiguration` in `ontrack-extension-findings`.

## KDSL acceptance tests

The development licence exposes, under the `dev` profile only, an endpoint to enable or disable
one of its features, restricted to `ApplicationManagement`:

```
PUT /extension/license/dev/features/{featureId}
{"enabled": false}
```

The KDSL wraps it:

```kotlin
ontrack.devLicense.withoutFeature("extension.findings.native-formats") {
    // ... the feature is disabled here, and enabled again afterwards
}
```

The state is held in memory by the running instance, shared by every test using it: keep the
block short, and never leave a feature disabled. The acceptance tests of a stack run one at a
time, which is what makes this safe. See `ACCDSLFindings`.

A production instance has no such endpoint: its licence is the signed key it is given.
