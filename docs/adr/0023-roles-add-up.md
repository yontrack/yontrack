# Roles add up

Until 6.0, an account or a group held **at most one global role, and at most one role per
project**. Saving a role replaced the previous one: `RoleJdbcRepository` deleted every row of the
target - globally, or on the project - before inserting the new one, and reading a role returned
the first row only. Nothing in the tables asked for it: the primary keys of
`GLOBAL_AUTHORIZATIONS`, `GROUP_GLOBAL_AUTHORIZATIONS`, `PROJECT_AUTHORIZATIONS` and
`GROUP_PROJECT_AUTHORIZATIONS` all include `ROLE`. And roles already added up across groups:
`AccountAuthenticatedUser` unions the account's own role assignments with those of every group it
belongs to.

The limit showed when a group had to both add statuses to validation runs (Participant) and
reschedule auto-versioning orders (a new role): one role per group and project could not say it.
The decisions are in `docs/grilling/2026-11-multi-roles/`.

## Decisions

**Roles add up.** An account or a group may hold **several global roles** and **several roles on
each project**. What it may do is the union of the functions of all the roles it holds - globally,
on the project, directly or through any of its groups - as it already was across groups. **No role
excludes another**: there is no exclusive role, and holding a role never takes a function away.

**Accounts and groups alike**, globally and per project: the same rules for the four tables.

**Grant and revoke, never set.** Granting a role inserts it, and does nothing when the target holds
it already; revoking a role deletes that role only. Nothing deletes a target's other roles to make
room. No migration: the tables already allow several rows.

**Evaluation holds a set of global roles.** `Authorisations` keeps a set of global roles, as it
already keeps a set of project role associations.

*Rejected:* one group per combination of roles. Groups already add up, so an account in a
*Participants* group and in an *Auto-versioning controllers* group gets both - but it takes a group
for every combination someone needs, it cannot give two roles to an account directly, and it leaves
the one-role-per-target rule in place for no reason the storage has.

*Rejected:* a role made for each combination (Participant plus rescheduling, and so on).
Combinations multiply with every role that Yontrack or an extension adds.

## Consequences

- Recorded before it is built: until the issues of `initiative: multi-roles` land (#2161 to
  #2165), saving a role still replaces the previous one. The rule applies to new code now.
- **GraphQL grants add.** `grantGlobalRoleToAccount` and `grantGlobalRoleToAccountGroup` add a role
  instead of replacing the current one - a behaviour change, on the migration page. New
  `revoke…` mutations remove one role; `deleteGlobalRoleFromAccount` and
  `deleteGlobalRoleFromAccountGroup`, which remove them all, are deprecated. Project roles get
  `grantProjectRoleTo…` and `revokeProjectRoleFrom…` mutations, checked against
  `ProjectAuthorisationMgt`.
- **`globalRole` becomes `globalRoles`**: `Account.globalRole` and `AccountGroup.globalRole` are
  deprecated for new `globalRoles` lists. `Project.projectRoles` already lists several roles and
  does not change.
- **CasC sets exactly the listed roles of what it mentions.** The format does not change: two roles
  are two entries. For each group listed in `admin.group-permissions`, and each (group, project)
  pair listed in `admin.project-permissions`, CasC grants the listed roles and revokes the others. A
  group, or a pair, that CasC does not mention is left untouched, so grants made in the UI survive.
  Before, the last entry for a group and project silently won.
- **The REST permission endpoints are deprecated.** All the endpoints of `PermissionController`
  (`/rest/accounts/permissions`), reads included, warn at runtime and go in V7. Only GraphQL is
  adapted to several roles.
- The global permissions page shows several roles per account or group and revokes them one by one;
  a project permissions page appears at `/project/{id}/permissions`.
- A role no longer has to cover everything a target needs. A new role can carry only the functions
  of its purpose, plus `ProjectView` so that it can also be held alone, as the auto-versioning
  controller roles do (#2166).
