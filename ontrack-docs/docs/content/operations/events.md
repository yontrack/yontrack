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
| User    | Name of the user who posted the event                         |
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
- a page holds at most 100 events
- there is no total count: `pageInfo.nextPage` is set as long as there are more events, and gives
  the `offset` and `size` of the next page
- the `message` is HTML

The `GET /rest/events/...` REST endpoints are deprecated in favour of this query, and are removed
in Yontrack 7 — see the [migration to V6](../appendix/migration-to-v6.md).
