Git
===

## Git configurations

This module holds what the Git-based SCMs share: GitHub, GitLab, Bitbucket Cloud and Bitbucket Server. It has no
configuration of its own: the pure-Git support, which associated a project with a plain Git repository, was removed
in V6 (#1924).

Each SCM extension provides a `GitConfigurator`, which reads the `GitConfiguration` of a project out of its own
project property (e.g. the GitHub configuration: name, repository, credentials, indexation interval).

Then, a branch can be associated to additional information:

* the linked Git branch
* the nature of the link between the builds and the commits
* the synchronisation between the builds and the commits, when applicable

## Git access

From a Git access point of view, when it's time to access a repository, we need the following information:

* remote
* user/password

This `GitRepository` can be computed from any `GitConfiguration`.

Indexation of repositories is based on the list of all the `GitRepository`, by collecting all projects and their
associated `GitConfiguration`.

Grouping the indexations per remote only is not enough because the way to access this repository might be different,
or the same remote and user are used for configurations of different SCMs.

Therefore, to differentiate two such repositories, we need additionally two other attributes:

* the `source`
* the `name` in this source

Together, the `source` and the `name` are enough to identify a `GitRepository`.

To validate is given indexed repository is still valid for its definition (remote + credentials), we need to compare
all the keys (source, name, remote, credentials).
