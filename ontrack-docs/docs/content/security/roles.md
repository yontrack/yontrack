# Roles

Yontrack uses a role-based security model.

Accounts are created when a user connects for the first time. Accounts can be associated to [groups](groups.md),
either explicitly or by [mapping groups](group-mappings.md) from the identity provider to Yontrack groups.

Roles can be assigned to accounts or to groups.

Roles are either scoped:

* globally - the role is granted throughout the whole of Yontrack
* to a project - the role is granted for a given project

## Roles add up

An account or a group may hold **several global roles**, and **several project roles** on the same
project. What it may do is the **union** of the functions of all the roles it holds - globally, on
this project, directly or through any of its groups. No role excludes another: holding a role never
takes a function away.

So a group of release engineers can be both *PARTICIPANT* and *VALIDATION_MANAGER* on a project,
and gets the functions of both there - and none of them on the projects where it holds no role. In
the same way, a group can be both a global *CREATOR* and a global *PARTICIPANT*: it creates projects
and comments on the validation runs of all of them.

## Global roles

An **ADMINISTRATOR** has access to all the functions of Ontrack, in all
projects. At least such a role should be defined.

A **CREATOR** can create any project and can, on all projects, configure them,
create branches, create promotion levels and
validation stamps. This role should be attributed to service users in charge
of automating the definition of projects and branches. A creator also manages the
[estates](../scorecard/estates.md) of the delivery scorecard.

An **AUTOMATION** user can do the same things than a _CREATOR_ but can, on all
projects, additionally edit promotion levels and validation stamps, create
builds, promote and validate them,
manage account groups and project permissions.
This role is suited for build and integration automation (CI).

A **CONTROLLER** can, on all projects, create builds, promote and validate
them. It is suited for a basic CI
need when the Ontrack structure already exists and does not need to be created.

A **GLOBAL VALIDATION MANAGER** can manage validation stamps across all projects.

A **PARTICIPANT** can view all projects, and can add comments to all validation runs.

A **READ_ONLY** can view all projects, but cannot perform any action on them.

The global roles can only be assigned by an _administrator_, in the _System_ > _Account
management_ page, by going to the _Global permissions_ command.

A _global permission_ is created by associating:

* a _permission target_ (an account or a group)
* a _global role_

The page lists each account and group once, with all its global roles.

Creation:

1. type the first letter of the account or the group you want to add a permission for
2. select the account or the group
3. select the role you want to give
4. click on _Add permission_

Adding a role to an account or a group keeps the global roles it already holds. Each role is
removed on its own, with the remove button next to it. Global permissions are created or deleted,
not updated.

The GraphQL API grants and revokes global roles one at a time, with the `grantGlobalRoleToAccount`,
`grantGlobalRoleToAccountGroup`, `revokeGlobalRoleFromAccount` and
`revokeGlobalRoleFromAccountGroup` mutations: granting a role keeps the other global roles of the
account or group, and revoking a role removes this role only. `Account.globalRoles` and
`AccountGroup.globalRoles` list the global roles.

By using [Configuration as Code](../configuration/casc.md), you have a better control over the global permissions:

```yaml
ontrack:
  admin:
    group-permissions:
      - group: Administrators
        role: ADMINISTRATOR
      - group: Developers
        role: PARTICIPANT
      - group: Developers
        role: CREATOR
```

Two roles for a group are two entries. Every group listed in `group-permissions` gets **exactly**
the listed global roles: the missing ones are granted and the others are revoked, including the
roles granted outside CasC. A group which is not listed keeps all its global roles.

!!! note

    Casc cannot be used to grant global roles to accounts.

## Project roles

A project **OWNER** can perform all operations on a project but to delete it.

A project **PARTICIPANT** has the right to see a project and to add comments
in the validation runs (comment + status change).

A project **VALIDATION_MANAGER** can manage the validation stamps and
create/edit the validation runs.

A project **PROMOTER** can create and delete promotion runs, can change the
validation runs statuses.

A project **PROJECT_MANAGER** cumulates the functions of a PROMOTER and of a
VALIDATION_MANAGER. He can additionally manage branches (creation / edition / deletion) and the common
build filters. He can also assign [labels](../concepts/model/project-labels.md) to the project.

A project **READ_ONLY** user can view this project, but cannot perform any action on it.

Only project owners, automation users and administrators can grant rights
in a project.

In the project page, select the _Permissions_ command.

A _project permission_ is created by associating:

* a _permission target_ (an account or a group)
* a _project role_

Creation:

1. type the first letter of the account or the group you want to add a
   permission for
2. select the account or the group
3. select the role you want to give
4. click on _Add permission_

Project permissions are created or deleted, not updated.

The GraphQL API grants and revokes project roles one at a time, with the `grantProjectRoleToAccount`,
`grantProjectRoleToAccountGroup`, `revokeProjectRoleFromAccount` and
`revokeProjectRoleFromAccountGroup` mutations: granting a role keeps the other roles of the account
or group on the project, and revoking a role removes this role only.

!!! warning

    As of now, the Yontrack UI does not allow to set the project permissions.

By using [Configuration as Code](../configuration/casc.md), you have a better control over the project permissions:

```yaml
ontrack:
  admin:
    project-permissions:
      - group: "Yontrack Administrators"
        role: PROJECT_MANAGER
        projects:
          - yontrack
          - yontrack-cli
      - group: Developers
        role: PARTICIPANT
        projects:
          - yontrack
          - yontrack-cli
      - group: Developers
        role: PROMOTER
        projects:
          - yontrack
```

The entries are grouped by group and project: for every group and project listed, the group gets
**exactly** the listed roles on the project - the missing ones are granted and the others are
revoked, including the roles granted outside CasC. Above, _Developers_ holds `PARTICIPANT` and
`PROMOTER` on `yontrack`, and `PARTICIPANT` only on `yontrack-cli`. A group and project pair which
is not listed keeps all its roles.

!!! note

    Casc cannot be used to grant project roles to accounts.
