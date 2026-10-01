package net.nemerosa.ontrack.extension.scm.changelog

/**
 * Default maximum length for a commit message rendered inside a change log.
 *
 * Commit messages - those written by coding agents in particular - routinely run to dozens of
 * lines, and a change log only ever needs the subject of the commit. The full message stays one
 * click away, on the commit page.
 */
const val COMMIT_MESSAGE_DEFAULT_MAX_LENGTH = 100

/**
 * Description of the `commitsMaxLength` option, shared by every change log configuration.
 */
const val COMMIT_MESSAGE_MAX_LENGTH_DESCRIPTION =
    "Maximum length of a commit message in a change log, ellipsis included. Only the first line of a commit message is ever rendered; set this to 0 to render that line in full."

/**
 * Subject of a commit message, as rendered in a change log: the first line of the message,
 * truncated when too long.
 *
 * Note this never touches the message stored in [SCMCommit.message]: the search indexes it in full,
 * and issue keys are extracted from its [issueReferenceText].
 *
 * @param message Full commit message
 * @param maxLength Maximum length of the result, the ellipsis included. `0` or less disables the
 * truncation - only the first line is returned in any case.
 */
fun shortCommitMessage(message: String, maxLength: Int = COMMIT_MESSAGE_DEFAULT_MAX_LENGTH): String {
    val subject = message.lineSequence().firstOrNull() ?: ""
    return if (maxLength <= 0 || subject.length <= maxLength) {
        subject
    } else {
        subject.take(maxLength - 1).trimEnd() + "…"
    }
}

/**
 * Keywords which, at the start of a line of a commit body, make this line a trailer naming issues.
 */
private val ISSUE_TRAILER_KEYWORDS = listOf(
    "close", "closes", "closed",
    "fix", "fixes", "fixed",
    "resolve", "resolves", "resolved",
    "ref", "refs", "references", "related",
    "issue", "issues",
    "jira-ticket",
)

/**
 * A trailer line: one of the [ISSUE_TRAILER_KEYWORDS], as a whole word, with or without a colon,
 * followed by its value.
 */
private val issueTrailerRegex = Regex(
    "^(?:${ISSUE_TRAILER_KEYWORDS.joinToString("|") { Regex.escape(it) }})(?:\\s*:|\\s|$)(.*)$",
    RegexOption.IGNORE_CASE,
)

/**
 * Part of a commit message which names its issues: the subject (first line) and the value of each
 * trailer line of the body (see [ISSUE_TRAILER_KEYWORDS]), one per line. The rest of the body is
 * prose, and an issue it mentions - historically, or in another repository - is not one the commit
 * works on.
 *
 * @param message Full commit message
 * @return Text to extract the issue keys from
 */
fun issueReferenceText(message: String): String {
    val lines = message.lines()
    val subject = lines.firstOrNull() ?: ""
    val trailerValues = lines.drop(1).mapNotNull { line ->
        issueTrailerRegex.matchEntire(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
    return (listOf(subject) + trailerValues).joinToString("\n")
}
