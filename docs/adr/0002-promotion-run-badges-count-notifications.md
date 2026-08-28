# The badges on a promotion run count notifications, not workflows

The numeric badges rendered on promotion medals come from `EntityNotificationsBadge`,
which counts an entity's **notification records** and buckets them into success,
in-flight, and error. A workflow is only one notification channel among many —
mail, Slack, and webhook are others — so these counts are notification counts,
and naming them "workflows" narrows a general concept to one of its special cases.

We record this because the design handoff for the branch pipeline view asserts the
opposite, specifying a "workflow badge cluster" and "workflow summary pills" as
though `promotionRun.workflows` existed. It does not exist in the GraphQL API, and
a future reader comparing the handoff to the code will otherwise conclude the
component was simply never built. The components are named for notifications and
fed from the existing `notificationRecords` query; no backend work is required.

## Consequences

A user whose subscriptions deliver only to Slack still sees badge counts, which a
workflow-scoped reading would have shown as empty. Breaking the counts down by
channel in the build inspector — "3 mail · 1 workflow failed" — remains open as a
follow-up, and would answer the handoff's own question about where clicking a badge
should lead.
