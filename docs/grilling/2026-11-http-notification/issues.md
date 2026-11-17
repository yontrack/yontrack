# HTTP call notification channel — issue breakdown

Breakdown of [README.md](README.md) into agent-sized issues, created on 2026-11-17.

Every issue carries `initiative: http-notification`.
- **H1–H7**: `status:todo` + `ready-for-agent` + `type: enhancement`, milestone `6.0`, base `main`.
- **H8** lives in `yontrack-infra-gitops`, with `enhancement` and no milestone, and lands through a PR.
- **H9**: a 6.1 placeholder at `status:tospec` only, to be specified in its own grilling session.

Dependencies are recorded as native GitHub dependencies (across repositories too) and as a
*Depends on* line at the top of each body.

| # | GitHub | Issue | Repo / base | Milestone | Depends on |
|---|--------|-------|-------------|-----------|------------|
| H1 | [#2150](https://github.com/yontrack/yontrack/issues/2150) | CONTEXT.md: HTTP configuration, HTTP call channel, conditions, status URL | `yontrack` / `main` | 6.0 | — |
| H2 | [#2151](https://github.com/yontrack/yontrack/issues/2151) | HTTP configuration: base URL and shared authentication, CasC, GraphQL, UI | `yontrack` / `main` | 6.0 | H1 |
| H3 | [#2152](https://github.com/yontrack/yontrack/issues/2152) | HTTP notifications settings: max timeout, max response size, request timeout | `yontrack` / `main` | 6.0 | — |
| H4 | [#2153](https://github.com/yontrack/yontrack/issues/2153) | HTTP call notification channel: single GET/POST/PUT call under an HTTP configuration | `yontrack` / `main` | 6.0 | H2, H3 |
| H5 | [#2154](https://github.com/yontrack/yontrack/issues/2154) | HTTP call channel: CEL success/failure conditions and polling | `yontrack` / `main` | 6.0 | H4 |
| H6 | [#2155](https://github.com/yontrack/yontrack/issues/2155) | HTTP call channel: mock deployment API and KDSL acceptance tests | `yontrack` / `main` | 6.0 | H5 |
| H7 | [#2156](https://github.com/yontrack/yontrack/issues/2156) | Demo seed: HTTP call channel on a petclinic promotion | `yontrack` / `main` | 6.0 | H6 |
| H8 | [yontrack-infra-gitops#308](https://github.com/yontrack/yontrack-infra-gitops/issues/308) | Demo: enable the HTTP call mock deployment API | `yontrack-infra-gitops` / `main` | — | H6 |
| H9 | [#2157](https://github.com/yontrack/yontrack/issues/2157) | Notification channels finishing in the background through a queue | `yontrack` / `main` | 6.1 | — |

H1 and H3 can start at once. H8 must be merged before H7's seed runs against the demo, since the seed
stops before deleting anything when the mock is off.
