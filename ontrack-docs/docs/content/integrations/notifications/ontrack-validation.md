# Forwarding validations

The `ontrack-validation` notification channel creates a validation run on a build when an event occurs. Combined with the
[`new_validation_run`](../../generated/events/event-new_validation_run.md) event, it can forward a validation from one
project to another: when a build of project `A` is validated, a build of project `B` gets validated as well.

## Target of the validation

By default, the validation run is created on the build of the event, using the validation stamp named by `validation`.

The `project`, `branch` and `build` fields are [templates](../../appendix/templating.md) which can point to any other
build. For example, `build: ${build}` together with a fixed `project` and `branch` targets the build with the same name in
another project.

## Status of the validation

The `status` field sets the status of the created validation run. It defaults to `PASSED`.

It is a [template](../../appendix/templating.md), which must render to a validation run status ID. The built-in IDs are
`PASSED`, `WARNING`, `FAILED`, `INTERRUPTED`, `DEFECTIVE`, `EXPLAINED`, `FIXED` and `INVESTIGATING`. They are uppercase
and must match exactly.

To forward the status of the original validation, use the `${STATUS}` value of the `new_validation_run` event:

```yaml
branch:
  notificationsConfig:
    notifications:
      - name: Forward unit tests
        events:
          - new_validation_run
        keywords: unit-tests
        channel: ontrack-validation
        channelConfig:
          project: aggregate
          branch: main
          build: ${build}
          validation: component-unit-tests
          status: ${STATUS}
```

Here, every time a build of this branch is validated against `unit-tests`, the build with the same name in the `main`
branch of the `aggregate` project is validated against `component-unit-tests`, with the same status.

!!! warning

    Without `status`, the forwarded validation is always `PASSED`, whatever the status of the original validation.

An invalid status is reported as an error:

* a fixed value, like `status: FAILD`, is rejected when the subscription is saved
* a template, like `status: ${STATUS}`, is checked only when the notification is sent: if it does not render to a known
  status ID, the notification fails and its [record](index.md#recordings) shows the error

!!! note

    The [`new_validation_run_status`](../../generated/events/event-new_validation_run_status.md) event also provides a
    `${STATUS}` value, but forwarding it creates a **new** validation run for each status change (for example, when
    the original validation is marked as `INVESTIGATING`). It does not update the status of the forwarded run.

## See also

* [Ontrack validation configuration](../../generated/notifications/notification-backend-ontrack-validation.md)
