# Deprecation and removal across majors

Before 6.0, Yontrack had no written deprecation policy. Messages were free text — "Will be removed
in V6", "… in V5", "… in 4.6", or no version at all — so nothing said when an item was due, a
dozen items tagged V5 were still there in V6, and an upgrader learnt about a removal when it broke.
The [deprecations grilling session](../grilling/2026-09-deprecations/README.md) settled the policy
below (#1917); every deprecation, from 6.0 on, follows it.

## What a deprecation is

**External contracts are user-facing deprecations**: the GraphQL API, REST, CasC keys, `ontrack.*`
configuration properties and environment variables, Helm values, the KDSL / CLI, notification
channels and templating, event types. They are named on the *Migration to V6* page
(`ontrack-docs/docs/content/appendix/migration-to-v6.md`, then its V7 successor), and they get a
runtime warning.

**Everything else is internal code.** There is no extension SPI — Yontrack extensions cannot be
written outside the repository — so Kotlin interfaces and base classes are internal too. Internal
code is cleaned up by the same rule, but never appears on the migration page and gets no runtime
warning.

## Deprecated in N, removed in N+1

**An item deprecated in major N is removed in N+1, by default.** Every V5 deprecation is removed in
V6. An exception is explicit: the item is re-tagged for the next major (`Removed in V7`) and listed
on the migration page as carried over — never kept silently.

A deprecation added in V6 is removed in V7, so it can only be added if its users are told: the
marker, the warning and the migration page, from the release that deprecates it.

## One marker format on every surface

| Surface        | Marker                                                                  |
|----------------|-------------------------------------------------------------------------|
| Kotlin         | `@Deprecated("Removed in V7. Use X instead. See #NNNN")`                |
| Java           | `@Deprecated`, and `@deprecated Removed in V7. Use X instead. See #NNNN` in its Javadoc |
| GraphQL        | `.deprecate("Removed in V7. Use X instead. See #NNNN")`, or `@deprecated(reason: "…")` in an SDL file |
| Frontend       | `/** @deprecated Removed in V7. Use X instead. See #NNNN */`            |
| Runtime warning | `message = "Removed in V7. Use X instead. See #NNNN"`                  |

Three parts, in this order and nothing else: the literal `Removed in V<N>.`, a replacement — `Use X
instead.`, or `No replacement.` — and the issue carrying out the removal, `See #NNNN`. Any
explanation goes in the KDoc, the Javadoc or the GraphQL description, not in the marker. The
cleanup of a major is then one grep: `git grep "Removed in V7"`.

`Removed in V6` and `Removed in V7` are both accepted on `v6`: the 5.5 readiness patch (#1916)
brought `Removed in V6` markers in through the main → v6 merge, and each stays until its removal
issue lands.

## Runtime warnings, for external contracts only

An administrator cannot audit what the users of an instance call. Before an upgrade, the instance
tells:

* one Micrometer counter, `ontrack.deprecated.usage` (`ontrack_deprecated_usage_total` in
  Prometheus), tagged `surface` (`graphql`, `rest`, `config`, `casc`, `env`, `templating`,
  `settings`, `property`, `ci-config`) and `item`;
* a `WARN` log, once per item per JVM run;
* a GraphQL instrumentation, `DeprecationInstrumentation`, counting every deprecated field or
  argument actually queried — the API is the largest surface, and it needs no call of its own;
* no UI: the counter is in the generated metrics documentation, and can be graphed.

Everything else goes through `DeprecationService.deprecatedUsage(surface, item, message)`, and a
deprecated configuration property through a `DeprecatedConfigurationPropertiesProvider`. The
`item` is the name an administrator sees in the metric; when it is a literal, it is also the name
the migration page must use.

Internal code gets no warning.

## The marker test

`DeprecationMarkersRepositoryTest`, in `ontrack-model`, enforces the above on every
`./gradlew test`. It scans:

* Kotlin and Java `@Deprecated` — main code, test code and test fixtures;
* the GraphQL deprecations of the main code — `.deprecate("…")`, `deprecation = "…"`, and
  `@deprecated(reason: …)` in `*.graphqls`;
* the runtime warnings of the main code — `deprecatedUsage(…)` and `DeprecatedConfigurationProperty(…)`
  with a literal item;
* the JSDoc `@deprecated` tags of `ontrack-web-core`.

It fails on an item whose marker is missing, has no replacement or no issue, and on an **external**
item not named on the migration page. External means: a GraphQL deprecation, a runtime warning, or a
deprecation in the KDSL (`ontrack-kdsl/src/main`). A GraphQL field or a KDSL item is named as a
qualified code span — `` `PromotionLevel.promotionRuns` ``, `` `Query.search(token)` ``; a runtime
warning by its item, verbatim — `` `POST /rest/structure/promotionLevels/{id}/image` ``. A
runtime warning whose item is built at runtime has no name to look for and is not checked.

The scan is textual, and reads the sources rather than the `ontrack.graphql` dumps, which are
generated from a running instance and can lag behind the code.

It runs in a task of its own, `:ontrack-model:deprecationMarkersTest`, which `test` depends on:
it reads the sources of the whole repository, and only a task declaring them as inputs is
re-run, rather than restored from the build cache, when a deprecation changes in another module.

### The baseline

The items which did not conform when the policy was adopted are listed in
`ontrack-model/src/test/resources/deprecation/markers-baseline.txt`, one per line, as the test
reports them: `<surface> <path>#<name>`. The test tolerates them, and them only.

**The baseline may only shrink.** An issue which removes an item, fixes its marker, or names it on
the migration page deletes its line in the same commit: the test fails on an entry whose item
conforms, or is gone. **No line is ever added** — a new deprecation follows the policy from the
start; the test cannot tell an added line from an old one, so this rule is the reviewer's.

## The upgrade floor

A major supports an upgrade from any release of the previous major, and from nothing older: 6.0
upgrades any 5.x installation, and a pre-5.0 one goes through a 5.x release first. Flyway replays
every migration, so the schema is safe from any 5.x starting point.

**The upgrade path is documented, not enforced.** The migration page states it; Yontrack does not
check it at startup. It never has for a previous major, and a check reading the Flyway history
before `migrate()` raced with a second instance migrating the same fresh database — it saw a
partial history and refused it (#1919).

The real upgrade risk is the code-based data conversions, which the cleanup of a major is tempted
to delete. The rule is: **the floor is the last Flyway version of the previous major's `.0`** —
`V68__V5_av_audit.sql` for 6.0. Every install of the previous major ran that `.0` once. So the
cleanup of major N+1:

* **may delete** what is present in N.0: startup migrations (`StartupService`, recorded as done
  in storage), and readers of legacy *stored* formats — `fromStorage` fallbacks, `@JsonAlias` on
  persisted data;
* **keeps until N+2** what was added after N.0, unless a Flyway migration converts the data
  instead;
* treats aliases on *input only* — API, CasC — not as data conversions: they follow the
  deprecation policy above.

## Removal: definition of done

An issue removing or deprecating an item:

* updates the migration page in the same commit — *Removed* or *Newly deprecated*, with the
  replacement — for an external item;
* deletes its items from the marker-test baseline;
* checks the demo seed (`DemoContent`), the mobile UI and the KDSL acceptance tests for the item,
  migrates them if they use it, and says which way it decided.

## The cutover gate

`v6` does not become `main` while there is a `Removed in V6` marker left, or an entry in the
baseline (`doc/dev-guide/major-branch.md`).

## Consequences

* The next major's cleanup is a grep and a list, not an archaeology.
* A deprecation costs more to add: a marker, an issue, a warning and a line on the migration page
  for an external item. That is the point — the users of an external item are told.
* The marker test is textual: a declaration written in an unusual way can be misread. It reads the
  ones this repository writes; a new pattern extends the scanner and its fixture tests
  (`DeprecationMarkersTest`).
