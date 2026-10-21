# Auto promotion

By default, a build is promoted explicitly, by associating a promotion with it.

By configuring an _auto promotion_ on a promotion level, a build is promoted automatically as soon as a given list of validations has passed, and a given list of other promotions has been granted, on that build.

For example, a build which has passed integration tests on platforms A, B and C can be promoted automatically, without anyone having to do it by hand.

Auto promotion is configured by setting the "Auto promotion" property on a promotion level.

!!! note

    A validation counts as passed when the status of its **last** run is `PASSED` or `FIXED` — a validation which failed and was later fixed counts towards the auto promotion.

    A [CHML](index.md#chml) validation stamp can also opt in, with `warningPassesAutoPromotion`, to having a last run in `WARNING` status count as passed. This applies to the auto promotion only — a `WARNING` run still shows as a warning everywhere else — and only to a last status of exactly `WARNING`: a warning later moved to another status, `INVESTIGATING` or `EXPLAINED` for example, does not count. With this option, a `WARNING` run both promotes a build and leaves an [auto revoked](#revoking-a-promotion) promotion standing.

## Depending on the promotions of linked builds

The promotions an auto promotion requires belong to the build's own branch. To depend on the promotions of the builds
a build is [linked to](build-links.md), use a [validation on promotion](index.md#validation-on-promotion) stamp: it is
validated as soon as the linked build reaches the promotion.

For example, project A uses projects B and C, and is [auto-versioned](../../integrations/auto-versioning/auto-versioning.md) on
their `BRONZE` builds for quick feedback. To go further, a build of A must be `SILVER` **and** the B and C builds it
uses must be `SILVER` too:

* the branch of A gets two validation on promotion stamps: `B-SILVER` (project `B`, promotion `SILVER`) and
  `C-SILVER` (project `C`, promotion `SILVER`)
* the `SILVER` promotion of A is auto promoted on its own validations, plus `B-SILVER` and `C-SILVER`

Then:

* when B and C reach `SILVER`, in any order, the build of A using them gets `B-SILVER` and `C-SILVER`, and is
  promoted to `SILVER` as soon as its own validations have passed
* a new build of A linked to builds of B and C which are already `SILVER` gets `B-SILVER` and `C-SILVER` as soon as
  its links are created
* with [revocation](#revoking-a-promotion) enabled on A's `SILVER`, deleting the `SILVER` promotion of the B build
  fails A's `B-SILVER` validation, and revokes A's `SILVER`

The builds of A which existed before the stamps were configured are not validated: there is no backfill.

## Selecting the validations

The list of validation stamps can be defined by:

* selecting a fixed list of validation stamps
* selecting them by name, using the `include` and `exclude` regular expressions

!!! warning

    A validation stamp named explicitly in the list is _always_ taken into account, whatever the `include` and `exclude` regular expressions say.

The list of promotion levels which must be granted is set independently of the list of validations.

## Seeing the conditions

A promotion level with an auto promotion carries a ⚡ next to its name. Hovering it lists the conditions: the validation stamps actually required on the branch — the ones named explicitly, plus the ones matching `include` and not `exclude` — the required promotions, and the regular expressions when they are set.

Hovering a promotion of a build, in the branch builds view or on the build page, shows the same conditions under **Auto promotion conditions**, with their state for that build:

* the status of the last run of each required validation, linking to that run, or **Not run**
* each required promotion, linking to its last run on the build, or **Not granted**
* a summary line, for example `2/3 validations passed · 1/1 promotion granted`

A `WARNING` run accepted by its CHML validation stamp counts as passed in the summary line, but its row still shows the `WARNING` status, so that the tolerance stays visible.

On the build page, hovering a promotion the build does **not** have yet shows the same conditions, with their state for that build, under the promotion's name marked **Not granted** — the place to look when wondering why a build is not promoted. A promotion level without auto promotion shows its description only.

These are the _current_ conditions and statuses. The auto promotion property is not versioned, so they explain why the build is — or is not — promoted today, not what triggered a promotion in the past. They are shown for manually granted promotions too.

The GraphQL API exposes them as the `autoPromotionConditions` field of `PromotionLevel` and of `PromotionRun`, and as the `autoPromotionConditions(promotionLevelId)` field of `Build`, for any promotion level of the build's branch, granted or not.

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
