# Promoting dependencies

The `yontrack-dependencies-promotion` notification channel promotes the builds linked from the build of the event. An
aggregate build can link to several builds of the same project under different qualifiers, for example `B -> 1.2`,
`B[moduleX] -> 1.3` and `B[moduleY] -> 1.4`: this channel promotes all of them, whatever their qualifiers are.

```yaml
channel: yontrack-dependencies-promotion
channelConfig:
  project: "B|C"
  qualifier: "module.*"
  promotion: GOLD
  fields:
    - name: reason
      value: "Promoted with ${build}"
  waitForPromotion: true
  waitForPromotionTimeout: 10m
```

See the [configuration reference](../../generated/notifications/notification-backend-yontrack-dependencies-promotion.md)
for all the fields.

## Selecting the builds

The links are always read from the **build of the event**. An event without a build ends the notification in error.

Only the direct links of this build are read. Each dependency can cascade further with a workflow of its own.

| Filter      | Not set        | Meaning                                                                           |
|-------------|----------------|-----------------------------------------------------------------------------------|
| `project`   | All projects   | Regular expression which must match the whole name of the project of the linked build |
| `qualifier` | All the links  | Regular expression which must match the whole qualifier of the link               |

* an empty `qualifier` selects the unqualified links only. In the web UI, where an empty input removes the filter, use
  `^$` instead;
* the filters are not templates, and an invalid regular expression is refused when the configuration is saved;
* the build of the event is never promoted, even if it links to itself;
* a build linked under several qualifiers is promoted once.

When no link matches, the notification is successful, without any target.

The `promotion` is the literal name of the promotion level, the same for all the builds. The template of the notification
and the values of the `fields` are rendered once from the event, and set on every promotion run.

## What happens to each build

| Situation                                                  | Status     | Notification |
|------------------------------------------------------------|------------|--------------|
| The promotion level does not exist on the branch of the build | `FAILED`   | In error     |
| The build already has a run for this promotion level       | `SKIPPED`  | Unchanged    |
| The build is promoted                                      | `PROMOTED` | Unchanged    |
| The promotion is refused, for example by a promotion check | `FAILED`   | In error     |

The output of the notification lists every build with its status.

### Skipping

A build which is already promoted is always skipped. Running the notification again is then safe, and two builds promoting
each other do not loop.

### Partial failures

Every build is attempted, whatever happens to the others, and nothing is rolled back. If at least one build failed, the
notification ends in error and lists every failure. Running it again only retries what is missing.

## Waiting for the promotions

With `waitForPromotion`, the notification waits for the notifications of the promotion runs **it created**. The builds
which were skipped are not waited for.

The notification ends in error if one of these notifications fails or does not finish within `waitForPromotionTimeout`,
once all the others have finished. The builds which could not be promoted still end it in error once the wait is over.

!!! warning

    In a [workflow](../workflows/workflows.md), the `timeout` of the node still applies, and it is 5 minutes by default.
    Set it above `waitForPromotionTimeout`, or the node fails first.

## Permissions

The promotions are made with administrator rights, as for the `yontrack-promotion` channel.

!!! warning

    Anybody who can edit a subscription or a workflow on the source project can promote the builds it links to, **in other
    projects as well**, without any right on those projects.
