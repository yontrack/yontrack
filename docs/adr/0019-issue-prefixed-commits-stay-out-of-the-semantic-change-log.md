# Issue-prefixed commit subjects stay out of the semantic change log

Every commit done for a GitHub issue starts its subject with the issue number — `#1234 Some
message`. That prefix is a deliberate trade, not an oversight, and this records what it costs.

The semantic change log parses a subject with `^(\w+)(?:\(([^)]+)\))?!?: (.+)$`, anchored at
position 0, so a leading `#1234` cannot match a conventional-commit type — and
`SemanticChangelogRenderingServiceImpl` then **drops every untyped commit silently**, via
`.filter { !it.type.isNullOrBlank() }`. No "other" section, no count. An issue-prefixed subject
is invisible in the change log the SILVER notification renders to support the GOLD decision;
the `issues=true` section carries the content instead.

Issue *linking* is unaffected either way: a change log takes the issues of a commit from its
**subject** and its **trailer lines** (`issueReferenceText` in `SCMCommitMessages.kt`), so the
leading `#1234` is picked up. The body prose is ignored — a `#1236` mentioned there is not linked;
to name another issue, put it in a trailer line (`Refs: #1236`). The trailer keywords are
`close(s|d)`, `fix(es|ed)`, `resolve(s|d)`, `ref(s)`, `references`, `related`, `issue(s)` and
`jira-ticket`, at the start of a line, with or without a colon.

## Commits with no issue

A commit subject that starts with a conventional-commit type is the only kind that appears in a
semantic change log, so a commit with no issue behind it uses one. The known types are the only
ones with a title and an emoji: `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `style`,
`refactor`, `perf`, `test`. Anything else is accepted and rendered as a raw section title, so a
typo makes its own section rather than failing.

`doc:` is the typo that already happened. Both `doc:` and `docs:` are in the history, which
produces a bare `doc` section beside `📝 Documentation`, and the two cannot be merged from the
template side: mapping with `?sections=doc=Documentation` gives the mapped title no emoji, and the
sort that follows compares titles while ignoring emojis, so the keys collide and one group's
commits are dropped outright.

## Consequences

Until typed subjects are adopted across the repository (#1690), most commits are absent from every
semantic change log, and the `issues=true` section carries the content instead.
