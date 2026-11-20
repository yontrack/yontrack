# Several roles per account and group, and auto-versioning permissions — grilling session

Grilling session of 2026-11-20 for Yontrack 6.0. Issues: [issues.md](issues.md), initiative
`initiative: multi-roles`.

## The request

A group of users must be able to both:

- add new statuses to a validation run (the **Participant** role);
- reschedule an auto-versioning order,

by being granted **two roles**: Participant, and a new **auto-versioning controller** role which
allows only the rescheduling.

## Research

### Role assignments (6.0, `main`)

- **One role per target and project.** `RoleJdbcRepository.saveProjectRoleForGroup` (and
  `…ForAccount`) deletes every row of the group on the project before inserting the new one;
  `saveGlobalRoleForGroup` (and `…ForAccount`) does the same for the global role. Reading a group's
  role on a project (`findProjectRoleAssociationsByGroup(group, project)`) and its global role
  (`findGlobalRoleByGroup`) returns the first row only.
- **The tables already allow several**: the primary keys of `GROUP_PROJECT_AUTHORIZATIONS`,
  `PROJECT_AUTHORIZATIONS`, `GROUP_GLOBAL_AUTHORIZATIONS` and `GLOBAL_AUTHORIZATIONS` include `ROLE`.
  No Flyway migration is needed.
- **Evaluation**: `Authorisations.projectRoleAssociations` is already a set, but
  `Authorisations.globalRole` is a single role. `AccountAuthenticatedUser` already unions the account's
  own authorisations with those of all its groups.
- **Extensions add roles and functions** through a `RoleContributor` (example:
  `EnvironmentsRoleContributor`). Built-in roles cannot be overridden but receive contributed
  functions. The Administrator role receives every contributed function automatically; Owner does not.
- **API surface**:
  - REST `PermissionController` (`/rest/accounts/permissions`, 9 endpoints) is the only way to manage
    **project** permissions; `ontrack-web-tests/ontrack/admin.js` uses it.
  - GraphQL has `grantGlobalRoleToAccount` / `…ToAccountGroup` (replace) and
    `deleteGlobalRoleFromAccount` / `…FromAccountGroup` (remove all), plus `Account.globalRole`,
    `AccountGroup.globalRole`, `…authorizedProjects`, `Project.projectRoles` (role → groups and
    accounts), `permissionTargets`, the global roles list. No GraphQL mutation for project roles.
  - The web UI has a global permissions page (`GlobalPermissionsView`) and **no** project permissions
    page — `security/roles.md` says so.
  - CasC `admin.group-permissions` (`{group, role}`, synchronised on the group, never deleting) and
    `admin.project-permissions` (`{group, role, projects}`, each entry saved in turn: a second entry for
    the same group and project silently replaces the first).

### Auto-versioning permissions (6.0, `main`)

The only checks in `ontrack-extension-auto-versioning` are `ProjectConfig` on the branch
configuration and on the project property. Everything else checks nothing:

| Operation | Today |
|---|---|
| `rescheduleAutoVersioning` | No check. The entry's branch is loaded through `branchJdbcRepositoryAccessor`, bypassing `ProjectView`. The UI button is always shown. |
| `scheduleAutoVersioning` | No check. Runs `AutoVersioningScheduler.schedule(time)`, as the `AutoVersioningSchedulerJob` cron does; with a `time`, it starts orders scheduled before that time. Called only by the KDSL (`AutoVersioningMgt.schedule`) in `ACCAutoVersioningScheduled`, as admin. No UI, no CLI command. |
| `cleanupAutoVersioningAuditEntries` | No check. Applies the retention policy. |
| `purgeAutoVersioningAuditEntries` | No check. Deletes every audit entry. |
| `checkAutoVersioning`, `POST /extension/auto-versioning/build/{id}/check` | Only indirect: `newValidationRun` checks `ValidationRunCreate` when a validation is created. |
| `autoVersioningAuditEntries`, `autoVersioningAuditEntry`, the count | No project visibility filter. |
| `GET /extension/auto-versioning/stats` | No check; counts pending orders across all projects. |

The only gate is `ReadOnlyTokenInstrumentation`, which refuses every mutation to a read-only token.

## Decisions

### Several roles

1. **Roles add up.** An account or a group may hold **several global roles** and **several roles per
   project**. Its rights are the union of all of them, as they already are across groups. No role is
   exclusive of another. Recorded as **ADR 0023**, and in `CONTEXT.md`.
2. **Accounts and groups alike**: the same change applies to both, globally and per project.
3. **Storage**: grant (insert, no-op when present) and revoke (delete one role) replace the
   delete-then-insert. No migration.
4. **Evaluation**: `Authorisations` holds a set of global roles.

### GraphQL

5. **Global roles**:
   - `grantGlobalRoleToAccount` / `grantGlobalRoleToAccountGroup` **add** a role instead of replacing
     the current one — a behaviour change, on the migration page.
   - New `revokeGlobalRoleFromAccount` / `revokeGlobalRoleFromAccountGroup`, taking the role.
   - `deleteGlobalRoleFromAccount` / `deleteGlobalRoleFromAccountGroup` (remove every global role) are
     **deprecated**.
   - `Account.globalRole` / `AccountGroup.globalRole` are **deprecated** for new `globalRoles` lists.
6. **Project roles**: new `grantProjectRoleToAccount`, `grantProjectRoleToAccountGroup`,
   `revokeProjectRoleFromAccount`, `revokeProjectRoleFromAccountGroup`, checked against
   `ProjectAuthorisationMgt`. Reads go through the existing `Project.projectRoles`, which already
   supports several roles.

### REST

7. **The whole `PermissionController` is deprecated** — all 9 endpoints, reads included — with a
   runtime warning each, "Removed in V7. Use the GraphQL … instead". GraphQL covers what they return.
   The web tests move to GraphQL. Only GraphQL is adapted to several roles.

### CasC

8. **The format does not change**: two roles are two entries. For every **group** listed in
   `group-permissions`, and every **(group, project)** pair listed in `project-permissions`, CasC sets
   **exactly** the listed roles — granting the missing ones, revoking the others. A group, or a group
   and project pair, CasC does not mention is left untouched, so grants made in the UI survive.

   ```yaml
   ontrack:
     admin:
       group-permissions:
         - group: Developers
           role: PARTICIPANT
         - group: Developers
           role: GLOBAL_AUTO_VERSIONING_CONTROLLER
       project-permissions:
         - group: Developers
           role: PARTICIPANT
           projects: [yontrack, yontrack-cli]
         - group: Developers
           role: AUTO_VERSIONING_CONTROLLER
           projects: [yontrack]
   ```

   | | Before | After |
   |---|---|---|
   | Developers, global | one role, whichever entry is saved last | `PARTICIPANT` + `GLOBAL_AUTO_VERSIONING_CONTROLLER` |
   | Developers × `yontrack` | `AUTO_VERSIONING_CONTROLLER` only (last entry wins) | `PARTICIPANT` + `AUTO_VERSIONING_CONTROLLER` |
   | Developers × `yontrack-cli` | `PARTICIPANT` | `PARTICIPANT` |
   | Rerun without the `AUTO_VERSIONING_CONTROLLER` entry | — | `yontrack` back to `PARTICIPANT` only |
   | Group or pair absent from CasC | untouched | untouched |

   Rendering already emits one entry per (group, role).

### UI

9. **Project permissions page** at `/project/{id}/permissions`, reached from a *Permissions* command
   on the project page, shown only with `ProjectAuthorisationMgt`:
   - one row per account or group, its roles as tags;
   - adding: choose an account or group, then one or more roles;
   - removing: per role.

   It is **desktop-only**: an entry in `mobileRoutes.js` sends phones to the desktop page.
10. **Global permissions page** (`GlobalPermissionsView`) shows several roles per account or group and
    removes them one by one.

### Auto-versioning

11. **New project function `AutoVersioningReschedule`**, checked in
    `AutoVersioningDispatcherImpl.reschedule` on the order's **target** project, after a `ProjectView`
    check on it. The *Reschedule* button shows only when it is granted.
12. **New project role `AUTO_VERSIONING_CONTROLLER`** ("Auto-versioning controller"): `ProjectView` +
    `AutoVersioningReschedule`, usable alone or with Participant.
13. **New global role `GLOBAL_AUTO_VERSIONING_CONTROLLER`** ("Global auto-versioning controller"):
    the same two functions on every project.
14. **Also granted to** the project roles Owner and Project manager, and to the global role Automation;
    Administrator gets it automatically. **Not** to Participant. **Denied** to agents (`AgentPolicy`) and
    to read-only tokens (`ReadOnlyTokenPolicy.deniedFunctions`).
15. **Other write operations**:
    - `scheduleAutoVersioning`, `cleanupAutoVersioningAuditEntries`, `purgeAutoVersioningAuditEntries`
      require the global `ApplicationManagement` (the function of the jobs admin page).
      `scheduleAutoVersioning` is **kept**: its `time` argument is what the acceptance tests need.
    - `checkAutoVersioning` and `POST …/build/{id}/check` require `ValidationRunCreate` on the build's
      project, before anything else.
    - The branch configuration operations and the project property keep `ProjectConfig`.
16. **Reads**: an auto-versioning order is visible with `ProjectView` on its **target** project; the
    source project is not checked. The audit entries query, the single entry and the count filter in
    SQL, so counts and pagination stay right. `GET …/stats` counts only the visible orders. The fields
    on branches, promotions and builds are already gated by their entity.
17. **Internal paths** (queue processing, the workflow node, the dispatcher on promotion) keep running
    as admin.

### Migration, demo, mobile

18. **Migration page**, *Breaking changes*: one section for "roles add up" (the grant mutations and
    CasC change behaviour, the REST endpoints are deprecated) and one for "auto-versioning operations
    require permissions". The GraphQL and REST deprecations are named in the deprecations list as
    usual.
19. **Demo seed**: a *Release engineers* group holding Participant and Auto-versioning controller on
    one demo project, so the project permissions page shows a group with two roles. The demo has no
    auto-versioning orders, so the Reschedule button is not part of it.
20. **Mobile**: only the new project permissions route matters, and it is desktop-only (decision 9).
    Auto-versioning pages are already desktop-only.

### Delivery

21. Initiative `initiative: multi-roles`, milestone **6.0**, base `main`, every issue at
    `status:todo` + `ready-for-agent`. See [issues.md](issues.md).
