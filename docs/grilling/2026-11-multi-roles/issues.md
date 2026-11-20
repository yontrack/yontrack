# Several roles per account and group, and auto-versioning permissions — issue breakdown

Breakdown of [README.md](README.md) into agent-sized issues, created on 2026-11-20.

Every issue carries `initiative: multi-roles`, `status:todo`, `ready-for-agent` and
`type: enhancement`, milestone `6.0`, base `main`.

Dependencies are recorded as native GitHub dependencies and as a *Depends on* line at the top of
each body.

| # | GitHub | Issue | Decisions | Depends on |
|---|--------|-------|-----------|------------|
| R1 | [#2160](https://github.com/yontrack/yontrack/issues/2160) | ADR 0023 and CONTEXT.md: roles add up | 1, 2 | — |
| R2 | [#2161](https://github.com/yontrack/yontrack/issues/2161) | Several project roles per account and group: storage, service, GraphQL grant/revoke | 2, 3, 6, 18 | R1 |
| R3 | [#2162](https://github.com/yontrack/yontrack/issues/2162) | Several global roles per account and group: evaluation, GraphQL, global permissions page | 2, 3, 4, 5, 10, 18 | R1 |
| R4 | [#2163](https://github.com/yontrack/yontrack/issues/2163) | CasC: several roles per group in group-permissions and project-permissions | 8 | R2, R3 |
| R5 | [#2164](https://github.com/yontrack/yontrack/issues/2164) | Project permissions page | 9, 20 | R2 |
| R6 | [#2165](https://github.com/yontrack/yontrack/issues/2165) | Deprecate the REST permission endpoints | 7, 18 | R2, R3 |
| R7 | [#2166](https://github.com/yontrack/yontrack/issues/2166) | Auto-versioning controller roles and the reschedule permission | 11–14, 18 | — |
| R8 | [#2167](https://github.com/yontrack/yontrack/issues/2167) | Auto-versioning: protect schedule, cleanup, purge and check | 15, 17, 18 | — |
| R9 | [#2168](https://github.com/yontrack/yontrack/issues/2168) | Auto-versioning: audit reads limited to visible projects | 16 | — |
| R10 | [#2169](https://github.com/yontrack/yontrack/issues/2169) | Demo seed: a group with Participant and Auto-versioning controller | 19 | R2, R7 |

R1, R7, R8 and R9 can start at once. R1 is docs-only (`[skip ci]`).
