# Events audit — issue breakdown

Breakdown of [README.md](README.md) into agent-sized issues, created on 2026-10-07.

All the issues are in `yontrack`, base branch `main`, milestone `6.0`. Each one carries `initiative: events-audit`, `type: enhancement`, `status:todo` and `ready-for-agent`. Dependencies are recorded as native GitHub dependencies, and as a *Blocked by* line at the top of each body.

| # | GitHub | Issue | Blocked by |
|---|--------|-------|------------|
| EA1 | [#2013](https://github.com/yontrack/yontrack/issues/2013) | Admin-only `events` GraphQL query with filters (`EventsAudit`), indexes, `CONTEXT.md`, KDSL | — |
| EA2 | [#2014](https://github.com/yontrack/yontrack/issues/2014) | Deprecate the `/rest/events` endpoints (markers, runtime warnings, migration page) | EA1 |
| EA3 | [#2015](https://github.com/yontrack/yontrack/issues/2015) | *Information › Events* page, user documentation | EA1 |
| EA4 | [#2016](https://github.com/yontrack/yontrack/issues/2016) | CSV and JSON export of the filtered events, capped and streamed | EA1, EA3 |
| EA5 | [#2017](https://github.com/yontrack/yontrack/issues/2017) | Retention setting and cleanup job, off by default | — |

## Order

EA1 and EA5 can start at once. EA2 and EA3 follow EA1, and EA4 comes last.

## Settled during the breakdown

These details were not put to the session; they are the breakdown's choices:

- **The export endpoint** is `GET /rest/admin/events/export`. It is not under `/rest/events`, which EA2 deprecates.
- **Truncation is known before streaming**, through an existence check at offset `max-rows`. It is reported three ways:
  - an `X-Yontrack-Export-Truncated` header;
  - in the file itself: a last `TRUNCATED` row in CSV, and `truncated`/`maxRows` flags at the head of the JSON;
  - an `eventsExport(filter)` GraphQL field, which the page uses to show its warning.
- **The user filter** uses an index on `LOWER(EVENT_USER) text_pattern_ops`, so that the case-insensitive prefix can use it.
- **The project filter** takes a project name. A page of the `events` query holds at most 100 events.
