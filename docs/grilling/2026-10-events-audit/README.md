# Events audit — Yontrack 6.0

Outcome of the grilling session of 2026-10-07 on a customer request: give administrators access to
the `EVENTS` table from the UI (the *Information* menu), filter it, and download the filtered view
as a file.

The issue breakdown is in [issues.md](issues.md), under the `initiative: events-audit` label,
milestone `6.0`.

## Where we start from

Facts read from the code on `main` (ba18b1c873) before the decisions were taken.

- **Table.** `EVENTS` (`V1__baseline.sql`): `ID`, `EVENT_TYPE`, the nullable entity FKs `PROJECT`,
  `BRANCH`, `PROMOTION_LEVEL`, `VALIDATION_STAMP`, `BUILD`, `PROMOTION_RUN`, `VALIDATION_RUN` and
  their "extra" twins `X_*` (`V35`), `REF`, `EVENT_VALUES` (JSONB since `V36`), `EVENT_TIME` (a
  `VARCHAR(24)` in the sortable `Time.store` format) and `EVENT_USER`. Indexes on every FK and on
  `EVENT_TYPE` (`V4`); **none on `EVENT_TIME` nor `EVENT_USER`**.
- **No retention.** Nothing deletes an event except the FK cascades when an entity is deleted.
- **Reading.** `EventQueryService` offers `getEvents(offset, count)` (global, restricted to the
  visible projects and the events without a project) and per-entity variants. There is **no
  filter** by date, user or event type alone, and **no count**. `EventJdbcRepository.toEvent`
  loads every referenced entity through the structure service, one lookup each.
- **Gap in the global filter.** It looks at `PROJECT` only: an event whose `X_*` entity sits in a
  hidden project can fail while the list loads.
- **Rendering.** `EventTemplatingService.renderEvent` renders an event type's template through an
  `EventRenderer` (`PlainEventRenderer`, `MarkdownEventRenderer`, `HtmlNotificationEventRenderer`).
- **API.** No GraphQL query returns events (only `eventTypes`). REST has
  `GET /rest/events/root` and `GET /rest/events/{entityType}/{entityId}` in `EventController`,
  without filters. **Nothing in the repository calls them** — not the web UI, the KDSL nor any
  extension.
- **UI.** No page lists events. `EventDetails` (type + entities) and `SelectMultipleEvents` (an
  event type multi-select) exist. The admin records pages (jobs, queue records, hook records,
  notification records) share `StandardTable` + `FilterForm` + "load more" pagination over a
  `pageInfo`/`pageItems` GraphQL query, and are gated by `ApplicationManagement`.
- **Menu.** Information entries are declared on the backend through `UserMenuItemExtension`
  (`CoreUserMenuGroups.INFORMATION`); the frontend maps an icon by key in `UserMenu.js`.
- **Export.** Files are downloaded through the Next.js proxy `app/api/protected/downloads/**`, whose
  routes forward no query string today. opencsv is pinned in the root build and used by the SCM
  catalog export only.
- **Mobile.** No Information menu, no admin page; new desktop routes fall to the desktop-only
  interstitial (`mobileRoutes.js`).

## Decisions

| # | Question | Decision |
|---|----------|----------|
| Q1 | Use case | **Audit**: who did what, when, across the whole instance |
| Q2 | Permission | A new global function **`EventsAudit`**, granted to the Administrator role only. Whoever holds it sees **all** events, whatever the project ACLs |
| Q3 | Delivery | An initiative: query, page and export as separate issues, the export after the page |
| Q4 | Rows | Rendered message (HTML) plus time, user, type and project; entities and raw values in an expandable row (reusing `EventDetails`) |
| Q5 | API | A new, documented, admin-only GraphQL root query `events`. **Both REST endpoints are deprecated** (removed in V7) |
| Q6 | Write access | **Read-only** — no deleting nor editing events from the page |
| Q7 / Q9 | Retention | **In scope**, as its own issue: a retention in days and a cleanup job, **off by default** |
| Q8 | Mobile | No impact: desktop-only, not added to `mobileRoutes.js` |
| Q10 | Per-entity REST endpoint | Deprecated as well, pointing to `events`: non-admins lose per-entity events in V7. Accepted — no caller exists, and events are now audit data |
| Q11 | Filters | Date range (`from`, `to`), user (case-insensitive prefix), event types (multi), project (`PROJECT` or `X_PROJECT`). A Flyway migration adds indexes on `EVENT_TIME` and `EVENT_USER`. No filter on the values |
| Q12 | Columns, sort, count | Time, user, type, message, project; newest first (`ID DESC`), not sortable; **no total count**, "load more" pagination |
| Q13 | Export format | **CSV and JSON**, through `format=csv\|json` on one endpoint; plain-text message, values as one JSON column in CSV |
| Q13b | Export versioning | The export format is versioned, starting at **1**: `formatVersion` first in the JSON, an `X-Yontrack-Export-Format-Version` header for both formats. Adding a field or a trailing CSV column keeps the version; removing, renaming, retyping or reordering bumps it |
| Q14 | Export size | Streamed, capped by `ontrack.config.events.export.max-rows` (default 100 000), with a truncation marker and a warning in the UI |
| Q15 | Demo | No `DemoContent` change: seeding the demo already posts plenty of events |
| Q16 | Retention configuration | A global settings section **Events** (UI + CasC), `retentionDays`, `0` = disabled = the default |
| Q17 | Names | Menu *Information › Events* at `/core/admin/events`; function `EventsAudit`; label `initiative: events-audit` |

## Vocabulary

`CONTEXT.md` already uses *event* for what the notification system posts (under *Entry*'s _Avoid_).
The term gets its own entry with this initiative (EA1): **Event** — a record, in the `EVENTS` table,
of something that happened to a project entity, posted by Yontrack and kept until it is cleaned up;
the input of notifications and of the events page. The page is *the events page*; "events audit" is
the initiative's name only, so that it is not confused with the audit trail (`v6-audit-trail`).

## Out of scope

- Filtering on the event values (JSONB) or on entities below the project.
- A total count of the filtered events.
- Non-admin access to events (an auditor role may get `EventsAudit` later, as #1977 plans for the
  audit trail).
- Purging events from the page, and any export of the cleanup itself (the cleaned-up events are gone).
