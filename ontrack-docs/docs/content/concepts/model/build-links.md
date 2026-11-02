# Build links

A **build link** records that one build uses another one: a build of an application which embeds a
given build of a library is linked to it. Links go across branches and across projects.

A link has two sides, and each build reads it from its own side:

| Name         | What it is                                  | Label in the UI |
|--------------|---------------------------------------------|-----------------|
| **Dependency** | A build this build uses                   | **Depends on**  |
| **Consumer**   | A build which uses this build             | **Used by**     |

If the build `app 1.0` is linked to the build `lib 2.3`, then `lib 2.3` is a dependency of `app 1.0`,
and `app 1.0` is a consumer of `lib 2.3`.

!!! note

    A dependency is upstream of its consumers: changes come from the dependency, and flow to the
    builds which use it.

## Qualifiers

A link has an optional _qualifier_, empty by default, which tells apart several links between the same
projects: for example, an application embedding two distinct versions of the same library, one
for each of its components.

## Where to see them

* the page of a build lists its links in its _Depends on_ and _Used by_ sections
* the _Links_ command of a build or of a branch opens a page displaying them, its graph showing both
  sides
* the link decoration of a build shows how many it has on each side, and lists them when hovered
* the [Build dependencies tree](../../dashboards/widgets/build-dependencies-tree.md) widget displays
  the dependencies of a build, level by level

## What uses them

* a [validation on promotion](index.md#validation-on-promotion) stamp validates the consumers of a
  build when it reaches a promotion
* [auto-versioning](../../integrations/auto-versioning/auto-versioning.md) reads them to find the
  version of a dependency used by a build

## In the API

In GraphQL:

* `Build.usingQualified` lists the dependencies of a build, and `Build.usedByQualified` its consumers
* `Branch.dependencyLinks` and `Branch.consumerLinks` do the same for a branch, based on its latest
  builds
* the `linkBuild` and `linksBuild` mutations create links
