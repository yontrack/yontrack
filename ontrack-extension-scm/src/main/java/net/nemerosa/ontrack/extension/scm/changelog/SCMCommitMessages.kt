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
 * Subject of a commit message, as rendered in a change log: the first line of the message,
 * truncated when too long.
 *
 * Note this never touches the message stored in [SCMCommit.message]: issue keys are extracted from
 * the whole message, body included, and the search indexes it in full.
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
