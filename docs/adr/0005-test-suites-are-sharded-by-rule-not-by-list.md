# Test suites are sharded by a rule over the discovered set, never by a list

`UI tests (main)` and `KDSL acceptance tests` each run in two shards, on separate
runners. Which test goes to which shard is decided by a rule applied to the set
of tests that were actually discovered — never by an enumeration of test names
held in the workflow or the build.

For Playwright this is its own `--shard=i/n`, which walks the spec files in path
order and deals them out balancing test count. For the KDSL acceptance suite
there is no equivalent, so `ShardingFilter` — a JUnit Platform
`PostDiscoveryFilter` registered through `META-INF/services` — reads the
discovered test classes back out of the descriptor tree, sorts them and deals
them out round-robin. Both are inert unless a shard is asked for, so a local run
always sees the whole suite.

The rule is what makes a new test safe. An `ACC*` class added next month is
discovered, takes its place in the sorted order and lands on a shard, with
nothing to remember and nothing to update. A list has the opposite property: the
suite still passes when a name is missing from it, because the tests that do run
are green, and the ones that were never run report nothing at all. That failure
mode is not hypothetical here — #1656 was BRONZE being granted while the
integration results were reported nowhere — and the reporting jobs are built
around the assumption that silence must be read as failure. A partition that can
silently lose a class puts the hole back one level down, where no report job can
see it.

## Considered options

**Listing classes per shard in the workflow matrix**, as the `integration` job
lists modules, was rejected for exactly that reason. It is the only option that
allows a shard split balanced on measured durations, which is a real benefit and
the reason someone will eventually want it — hence this record. The difference
in kind is that a module is a directory that already exists and is hard to add
without noticing, whereas a test class is a file among sixty.

**Hashing the class name** inside Gradle's `include` predicate would have been
five lines instead of a filter class, and keeps the safety property: every class
is assigned somewhere. It was rejected on balance rather than safety. Dealing
*sorted* names out round-robin is also a crude cost heuristic — names cluster by
feature and so does the time, so the twenty-three `ACCAutoVersioning*` classes,
which all wait on auto-versioning to complete, split evenly by construction. A
hash splits them binomially and 8/15 is an ordinary draw.

**Scanning the source tree** at configuration time to build the sorted list was
rejected because filename and class name already disagree: `ACCProperties.kt`
declares an `object`, not a test, and matches the task's `**/ACC*.class` filter
along with its nested objects. A second source of truth about what the suite
contains is the same hole in a different place.

**Raising the parallelism inside the existing job** — more Playwright workers,
more Gradle forks — needs no extra runner at all and was rejected as unavailable
rather than undesirable. Every UI test signs in as the same `admin@ontrack.local`
and several mutate that user's or the instance's global state, so the suite
cannot currently run two tests at once against one Yontrack. Separate runners
mean separate Compose stacks, which makes shards safe by construction. Should
per-test isolation ever arrive, in-job parallelism becomes the cheaper lever and
this decision is worth revisiting.

## Consequences

`KDSL.ACCEPTANCE` is no longer validated from the job that runs the tests. Two
shards on two runners cannot both stamp one validation without the promotion
reading whichever landed last, so `kdsl-report` collects the shards' JUnit
artefacts and validates once, and the shards upload results they previously never
uploaded at all. It follows `ui-tests-report` rather than `integration-report` in
deciding the verdict: the matrix result is what says a leg died, because a shard
killed by `timeout-minutes` counts as cancelled, uploads nothing, and leaves a
partial set of artefacts that are all green.

Two shards, not more, and the number is not a target to keep raising. The figures
below are measured from the first sharded run rather than projected, because the
projection that justified two shards was wrong in an instructive way.

What a shard duplicates is not only the image pull and `compose up`. It is also
Gradle's configuration and the compilation of the test source set, and together
those come to about 260 seconds on the KDSL job — well over half of it. The
suite's own execution is 366 seconds, so two shards run about 6 minutes each in
tests plus overhead and land at 7.1 and 7.6 minutes; the UI shards, which have far
less to compile, come in at 6.1 and 6.6. The parallel band costs the slowest job in
it, and the slowest `integration` leg was 6.7 minutes in the same run.

So a third KDSL shard would take its execution to roughly 122 seconds, and the job
to about 6.3 minutes — but the band would then be set by `integration` at 6.7, and
the critical path would improve by about 0.9 minutes for one more runner and one
more Compose stack. That is the reason to stop at two, and it is a weaker reason
than the original estimate of "under a minute against a 6.0 minute floor" made it
look: the gain is real, it is simply bought at a poor rate, and it evaporates
entirely unless `integration` is resharded in the same breath.

The `ui-tests` matrix now carries two independent fields. `variant` is the
authentication mode the stack runs in; `shard` is a partition of one suite across
runners. Only `main` is sharded, so ldap and oidc sit at shard 1 of 1, and every
artefact is named for both — two legs of one variant would otherwise upload under
the same name.

The KDSL balance was unverifiable when the split was chosen — the suite's JUnit
results were consumed inline and never published, so no per-class durations existed
anywhere — and the artefacts the reporting job now needs are also what made it
measurable. The first sharded run puts the two shards at 171 and 195 seconds
against a best-possible 183 and 183: a 7% imbalance, and within 12 seconds of
optimal on the slow side. Dealing sorted names out round-robin did what it was
chosen to do, visibly so on the three ~60 second GitHub ingestion classes, which
are adjacent once sorted and alternated rather than clumping.
