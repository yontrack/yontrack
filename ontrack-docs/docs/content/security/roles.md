# Roles

Yontrack uses a role-based security model.

Accounts are created when a user connects for the first time. Accounts can be associated to [groups](groups.md),
either explicitly or by [mapping groups](group-mappings.md) from the identity provider to Yontrack groups.

Roles can be assigned to accounts or to groups.

Roles are either scoped:

* globally - the role is granted throughout the whole of Yontrack
* to a project - the role is granted for a given project

## Global roles

An **ADMINISTRATOR** has access to all the functions of Ontrack, in all
projects. At least such a role should be defined.

A **CREATOR** can create any project and can, on all projects, configure them,
create branches, create promotion levels and
validation stamps. This role should be attributed to service users in charge
of automating the definition of projects and branches.

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

Creation:

1. type the first letter of the account or the group you want to add a permission for
2. select the account or the group
3. select the role you want to give
4. click on _Add permission_

Global permissions are created or deleted, not updated.

By using [Configuration as Code](../configuration/casc.md), you have a better control over the global permissions:

```yaml
ontrack:
  admin:
    group-permissions:
      - group: Administrators
        role: ADMINISTRATOR 
```

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
build filters. He can also assign labels to the project.

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
```

!!! note

    Casc cannot be used to grant projlect roles to accounts.
