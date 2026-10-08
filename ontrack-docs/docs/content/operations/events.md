# Events

Yontrack records an **event** each time something happens to a project entity: a project or a
branch created, a build promoted, a validation run, a property changed, an entity deleted, ...
Each event has a type, a time, the name of the user who posted it, the entities it is about and,
for some types, a few named values (like the name of a deleted branch).

Events are what [notifications](../integrations/notifications/index.md) react to. They are kept
in the database, and the *Events* page lists them, for an audit of who did what, and when,
across the whole instance.

The event types and the messages they render are listed in the
[event types reference](../generated/events/index.md).

## Who can see the events

The events page and the `events` GraphQL query require the **events audit** global function
(`EventsAudit`), which only the *Administrator* role has. Whoever holds it sees **all** the
events of the instance, whatever the project permissions.

For the other users, the menu entry is absent and the `events` query is refused.

## The events page

As an administrator, open the user menu and choose *Information › Events*.

The page lists the events of the instance, newest first, 20 at a time: *Load more...* at the
bottom of the table loads the next ones. There is no total count of the events.

Each row shows:

| Column  | Content                                                       |
|---------|---------------------------------------------------------------|
| Time    | When the event was posted, in your own time zone              |
| User    | Name of the user who posted the event - for an event done by an [agent](../agents/index.md), the badge of the agent: its name and its owner, linking to its session when it gave one |
| Type    | ID and description of the event type                          |
| Message | Message of the event, with links to the entities it is about  |
| Project | Project of the event, if any                                  |

Expanding a row shows the details of the event: its type, the entities it is about (with a
link to each of them), its additional entities (like the target of a build link), the type of
the entity it refers to first, and its values, as a name/value table.

The page is read-only: no event can be edited or deleted from it.

!!! note "Deleted entities"

    When an entity is deleted, the events about it are deleted along with it. Its deletion is an
    event of its own, recorded on the entity above it, whose values give the name and the ID of
    the deleted entity: the deletion of a branch is recorded on its project, for example.

### Filtering the events

The form above the table filters the events. Fill in any of the fields and click *Filter*;
*Clear filter* lists all the events again.

| Filter      | Keeps the events                                                                  |
|-------------|-----------------------------------------------------------------------------------|
| Time        | posted between the two dates and times, both included. Either of them can be left empty |
| User        | posted by a user whose name starts with the given text, ignoring the case         |
| Event types | of any of the selected types                                                      |
| Project     | of the selected project, or whose additional entities belong to it               |
| Actor       | of any actor (*All*), done by a person (*Humans*), done by an [agent](../agents/index.md) (*Agents*), or done by one of the registered agents |

The events of an agent which has since been deleted keep its name, and are found with *Agents*.

The filters are combined: an event must match all of them to be listed.

## Scripting with the `events` query

The same list is available through the `events` GraphQL query, to administrators only. It takes
the same filters as the page, and returns the events newest first, a page at a time:

```graphql
query {
  events(
    filter: {
      from: "2026-10-01T00:00:00Z"
      to: "2026-10-07T23:59:59Z"
      user: "admin"
      eventTypes: ["new_promotion_run"]
      project: "my-project"
      actor: "agent"
    }
    offset: 0
    size: 50
  ) {
    pageInfo {
      nextPage {
        offset
        size
      }
    }
    pageItems {
      id
      time
      user
      actor {
        agent
        displayName
        owner
        sessionLink
      }
      eventType {
        id
      }
      message
      project {
        name
      }
      entities {
        type
        id
        displayName
      }
      values {
        name
        value
      }
    }
  }
}
```

- `from` and `to` are UTC times, both included
- `actor` is `agent` for the events done by agents, `human` for those done by persons, or the
  identifier of one agent, like `claude[agent]`, ignoring the case
- `actor` is null for an event done by a person
- a page holds at most 100 events
- there is no total count: `pageInfo.nextPage` is set as long as there are more events, and gives
  the `offset` and `size` of the next page
- the `message` is HTML

The `GET /rest/events/...` REST endpoints are deprecated in favour of this query, and are removed
in Yontrack 7 — see the [migration to V6](../appendix/migration-to-v6.md).

## Export

The events matching the filter of the page can be downloaded as a file: *Download CSV* and
*Download JSON*, next to the filter, export the events of the filter **applied** to the table
(click *Filter* first), newest first. The file is named `yontrack-events-<yyyyMMdd-HHmmss>.csv`
(or `.json`), from the time of the export, in UTC.

### Content

Each event gives:

| Field                                                         | Content                                                                 |
|---------------------------------------------------------------|-------------------------------------------------------------------------|
| `id`                                                          | ID of the event                                                         |
| `time`                                                        | When the event was posted, as ISO-8601 in UTC, like `2026-10-07T09:15:00Z` |
| `user`                                                        | Name of the user who posted the event                                   |
| `eventType`                                                   | ID of the event type, like `new_build`                                  |
| `message`                                                     | Message of the event, as plain text                                     |
| `project`, `branch`, `build`, `promotionLevel`, `validationStamp` | Names of the entities the event is about                            |
| `promotionRun`, `validationRun`                               | IDs of the runs the event is about                                      |
| `xProject`, `xBranch`, `xBuild`, `xPromotionLevel`, `xValidationStamp`, `xPromotionRun`, `xValidationRun` | The same for the additional entities of the event |
| `ref`                                                         | Type of the entity the event refers to first, like `BUILD`              |
| `values`                                                      | Values of the event, by name                                            |
| `actorKind`                                                   | `agent` for an event done by an [agent](../agents/index.md), `human` otherwise |
| `agent`                                                       | Identifier of the agent, like `claude[agent]` - empty for a person      |
| `owner`                                                       | Email of the owner of the agent - empty for a person                    |
| `sessionLink`                                                 | Link to the agent session, when the agent gave one                      |

**CSV.** The first row holds the names of the columns, in the order of the table above. An
absent entity is an empty cell, and `values` is one column holding the values as a JSON
object, like `{"BRANCH":"main","BRANCH_ID":"12"}`. The actor columns come after `values`, at the
end of the row: they were added to the version 1 of the format, and the columns before them kept
their positions. The file is in UTF-8, its values quoted when
they need to be.

**JSON.** One object, which describes the export before its events:

```json
{
  "formatVersion": 1,
  "exportedAt": "2026-10-07T09:20:31.123Z",
  "filter": {
    "from": "2026-10-01T00:00:00Z",
    "to": null,
    "user": null,
    "eventTypes": ["new_build"],
    "project": "my-project",
    "actor": null
  },
  "maxRows": 100000,
  "truncated": false,
  "events": [
    {
      "id": 1234,
      "time": "2026-10-07T09:15:00Z",
      "user": "claude[agent]",
      "eventType": "new_build",
      "message": "New build 12 for branch main in my-project.",
      "project": "my-project",
      "branch": "main",
      "build": "12",
      "promotionLevel": null,
      "validationStamp": null,
      "promotionRun": null,
      "validationRun": null,
      "xProject": null,
      "xBranch": null,
      "xBuild": null,
      "xPromotionLevel": null,
      "xValidationStamp": null,
      "xPromotionRun": null,
      "xValidationRun": null,
      "ref": "BUILD",
      "values": {},
      "actorKind": "agent",
      "agent": "claude[agent]",
      "owner": "alice@example.com",
      "sessionLink": "https://claude.ai/code/session-1"
    }
  ]
}
```

An absent entity is `null`, and `values` is an object. For an event done by a person, `actorKind`
is `human`, and `agent`, `owner` and `sessionLink` are `null`.

### Format version

The format of the export is versioned, so that a script reading it can check what it gets. The
version is the same for both formats, and is currently **`1`**:

- in the JSON, it is the first field, `formatVersion`;
- for both formats, the answer has the `X-Yontrack-Export-Format-Version` header.

**Adding** a field, or a column at the end of the CSV, keeps the version. **Removing, renaming or
retyping** a field, or **reordering** the CSV columns, bumps it.

### Maximum number of events

An export holds at most **100 000** events, the most recent matching ones. The administrators can
change this maximum with the `ontrack.config.events.export.max-rows` setting (see the
[general configuration properties](../generated/configurations/net.nemerosa.ontrack.model.support.OntrackConfigProperties.md)).

When more events match the filter, the export is **truncated**:

- the page warns about it next to the buttons, before anything is downloaded;
- the answer has the `X-Yontrack-Export-Truncated: true` header (`false` otherwise);
- in the CSV, the last row has `TRUNCATED` as its `id`, and the message
  `Export limited to <max-rows> events: narrow the filter`;
- in the JSON, `truncated` is `true`, and `maxRows` gives the maximum.

Narrow the filter - a shorter time range, for example - to get all the events.

### REST endpoint

The export is the `GET /rest/admin/events/export` endpoint, for the holders of the events audit
function only. Its parameters are:

| Parameter    | Content                                                                       |
|--------------|-------------------------------------------------------------------------------|
| `format`     | `csv` or `json` - required                                                    |
| `from`, `to` | ISO-8601 times, both included - in UTC, unless they give an offset            |
| `user`       | Prefix of the name of the user who posted the event, ignoring the case        |
| `eventTypes` | IDs of event types, repeated (`eventTypes=a&eventTypes=b`) or separated by commas |
| `project`    | Name of a project                                                             |
| `actor`      | `agent`, `human`, or the identifier of one agent, like `claude[agent]`        |

For example, with an [API token](../security/tokens.md):

```bash
curl --fail-with-body \
  -H "X-Ontrack-Token: $YONTRACK_TOKEN" \
  --output events.csv \
  "$YONTRACK_URL/rest/admin/events/export?format=csv&project=my-project&eventTypes=new_build&from=2026-10-01T00:00:00Z"
```

The file is written as it is read from the database, and its headers come first: a script can
check `X-Yontrack-Export-Truncated` before reading the content. An unknown `format`, or a time
which cannot be read, is refused with a `400`, and a user without the events audit function gets
a `403`.

The `eventsExport` GraphQL query takes the same filter as the `events` query, and tells, without
exporting anything, the maximum number of events of an export and whether the export of the
filter would be truncated:

```graphql
query {
  eventsExport(filter: {project: "my-project"}) {
    maxRows
    truncated
  }
}
```

## Retention

By default, Yontrack keeps the events **forever**: only the deletion of an entity deletes its
events. Deleting audit data is the decision of the administrators, who can set a **retention**,
in days, after which the events are deleted.

!!! warning

    The deletion of the events cannot be undone. [Export](#export) the events you need to keep
    before setting a retention.

As an administrator, open the user menu, choose _System_ > _Settings_, then select _Events_:

| Setting          | Default | Meaning                                                              |
|------------------|---------|----------------------------------------------------------------------|
| Retention (days) | `0`     | Number of days the events are kept. `0` keeps the events forever.    |

A negative retention is refused.

As [code](../configuration/casc.md):

```yaml
ontrack:
  config:
    settings:
      events:
        retentionDays: 365
```

### Cleanup job

The *Events cleanup* job, `core / events-cleanup / events-cleanup` on the *Jobs* page, runs every
day, and can be launched from there at any time. When the retention is `N` days, it deletes the
events posted more than `N` days ago; when it is `0`, it does nothing.

It deletes the events in batches of at most **10 000**, each in its own statement, to keep the
locks on the events short. The administrators can change this size with the
`ontrack.config.events.cleanup.batch-size` setting (see the
[general configuration properties](../generated/configurations/net.nemerosa.ontrack.model.support.OntrackConfigProperties.md)).
Each run logs the number of deleted events, at the `INFO` level.
