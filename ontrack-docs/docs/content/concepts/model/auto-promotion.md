# Auto promotion

By default, a build is promoted explicitly, by associating a promotion with it.

By configuring an _auto promotion_ on a promotion level, a build is promoted automatically as soon as a given list of validations has passed, and a given list of other promotions has been granted, on that build.

For example, a build which has passed integration tests on platforms A, B and C can be promoted automatically, without anyone having to do it by hand.

Auto promotion is configured by setting the "Auto promotion" property on a promotion level.

!!! note

    A validation counts as passed when the status of its **last** run is `PASSED` or `FIXED` — a validation which failed and was later fixed counts towards the auto promotion.

## Selecting the validations

The list of validation stamps can be defined by:

* selecting a fixed list of validation stamps
* selecting them by name, using the `include` and `exclude` regular expressions

!!! warning

    A validation stamp named explicitly in the list is _always_ taken into account, whatever the `include` and `exclude` regular expressions say.

The list of promotion levels which must be granted is set independently of the list of validations.

## Revoking a promotion

By default, a promotion which has been granted stays granted, even if the validations which triggered it later fail.

Enabling **"Revoke the promotion when a prerequisite is no longer valid"** on the auto promotion property revokes the promotion as soon as one of its prerequisites — a required validation stamp or a required promotion — stops being valid.

This is opt-in and disabled by default.

!!! warning

    Revoking a promotion **deletes** it, but does not undo its effects: any notification or workflow already triggered by the promotion remains fired.

    Deleting the promotion run removes the promotion, not the auto-versioning pull requests it opened, the notifications it sent, or the workflows it started.

!!! note

    Enabling the option does not re-evaluate the builds which are already promoted. Revocation is forward-looking: it reacts only to a prerequisite actually becoming invalid from that point on.

### Behaviours worth knowing

Three consequences surprise people:

**Manual deletions cascade.** A prerequisite is no longer valid whoever made it untrue. Deleting a `BRONZE` promotion by hand also deletes every downstream promotion which required it and has the option enabled, in the same click. This is also what makes "undo a mis-promotion" work down the whole chain.

**A newly added prerequisite which fails revokes an old promotion.** If a validation stamp matching the `include` regular expression is added, and a run for it later fails on a build which was already promoted, that promotion is revoked. Nothing happens when the property is edited — only when a real failing run appears.

**Every run of the promotion level is revoked**, including the ones created manually. Enabling the option declares the promotion level fully auto-managed; leaving a manual run standing would show the build as promoted while its prerequisites are red.

### Being notified

A revocation posts an [`auto_promotion_revoked`](../../generated/events/event-auto_promotion_revoked.md) event, in addition to the [`delete_promotion_run`](../../generated/events/event-delete_promotion_run.md) event for the run itself. The first says _why_ the promotion is gone, the second says _that_ it is.

Subscribe to `auto_promotion_revoked` to be told about automatic revocations specifically, as opposed to a person deleting a promotion.

## Configuration as code

Auto promotion can also be set through the [CI configuration](../../configuration/ci-config.md), using the `validations`, `promotions` and `autoRevoke` keys of a promotion.
