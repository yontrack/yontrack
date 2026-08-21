# Build filtering

The list of builds shown in a branch page is produced by a *build filter*. By default, Yontrack
displays the last builds of the branch, but a filter can narrow that list down to the builds you
actually care about - the ones promoted to a given level, validated by a given stamp, created in a
given period, linked to another project, or named in a given way.

Filters are edited from the filter dropdown at the top of the build list. A filter can be used
once, or saved under a name so it can be reused later.

## The standard filter

The standard filter is the general-purpose filter. Its criteria are grouped into tabs, and all the
criteria you fill in are combined: a build is kept only when it satisfies every one of them.

### Build

`With display name`
:   The build [display name](#filtering-on-the-build-display-name) must match a regular expression.

### Promotion

`With promotion level`
:   The build must be promoted to this promotion level.

`Since promotion level`
:   Only the builds created since the last build promoted to this level, that build included.

### Validation

`With validation stamp` / `... with status`
:   The build must have a run for this validation stamp, optionally in this status.

`Since validation stamp` / `... with status`
:   Only the builds created since the last build validated by this stamp, that build included.

### Property

`With property` / `... with value`
:   The build must have this property, optionally with this value. The way the value is matched
    depends on the property.

`Since property` / `... with value`
:   Only the builds created since the last build having this property, that build included.

### Links

`Linked from` / `... with promotion`
:   The build must be linked *from* the builds selected by a `PRJ:BLD` pattern, where `PRJ` is a
    project name and `BLD` a build name which accepts `*` as a placeholder. Optionally, the linking
    build must have a given promotion.

`Linked to` / `... with promotion`
:   The same, for the builds the build is linked *to*.

### Time

`Build after` / `Build before`
:   The build must have been created on or after, respectively on or before, this date.

## Filtering on the build display name

A build has a name, given when the build is created. It can also be given a
[release label](../../generated/properties/property-net.nemerosa.ontrack.extension.general.ReleasePropertyType.md),
typically a version number like `1.2.0`. The *display name* of a build is its release label when it
has one, and its name otherwise - it is what Yontrack shows for the build across the UI.

The `With display name` criterion keeps only the builds whose display name matches a regular
expression. Because it works on the display name and not on the name alone, a single filter
covers both the labelled and the unlabelled builds of a branch:

* a build labelled `1.2.0` is matched by `^1\.2\.` whatever its name is;
* a build with no label is matched on its own name.

!!! note

    The match is always performed against the release label when the build has one, even for
    projects where the
    [build link display options](../../generated/properties/property-net.nemerosa.ontrack.extension.general.BuildLinkDisplayPropertyType.md)
    are configured with `useLabel` set to `false`. In such a project, the build list shows build
    names while the filter still matches labels, so a build whose displayed name does not look
    like the pattern can be returned.

### Pattern syntax

The pattern is a PostgreSQL
[POSIX regular expression](https://www.postgresql.org/docs/current/functions-matching.html#FUNCTIONS-POSIX-REGEXP),
which differs in places from the regular expressions of Java, JavaScript or Perl.

Two behaviours are worth remembering:

* the match is **case insensitive** - `RELEASE` and `release` match the same builds. Start the
  pattern with `(?c)` to make it case sensitive;
* the match is **partial** - the pattern has to be found somewhere inside the display name, not to
  cover the whole of it. Use `^` and `$` to anchor it.

| Pattern            | Matches                                                      |
|--------------------|--------------------------------------------------------------|
| `1\.2\.`           | any display name containing `1.2.`, like `1.2.4` or `v1.2.0` |
| `^1\.2\.`          | any display name starting with `1.2.`                        |
| `^1\.2\.3$`        | exactly `1.2.3`                                              |
| `(1\.2\|1\.3)\.`   | display names containing either `1.2.` or `1.3.`             |
| `^release-[0-9]+$` | `release-` followed by digits only                           |

If the pattern is not a valid regular expression, the filter returns no build at all rather than an
error. A filter which unexpectedly comes back empty is worth checking for a typo in its pattern.
