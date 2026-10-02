package net.nemerosa.ontrack.extension.issues.support

/**
 * Utility class to deal with issues.
 */
object IssueServiceUtils {

    /**
     * Collects the group(s) an issue belongs to according to its own list of types
     * and a grouping specification.
     *
     * @param issueTypes            Issue types
     * @param groupingSpecification Group -&gt; (Group types)
     * @return List of group the issue belongs to
     */
    @JvmStatic
    fun getIssueGroups(issueTypes: Collection<String>, groupingSpecification: Map<String, Set<String>>): Set<String> {
        val groups = HashSet<String>()
        for (issueType in issueTypes) {
            for ((groupName, groupTypes) in groupingSpecification) {
                if (groupTypes.contains(issueType)) {
                    groups.add(groupName)
                }
            }
        }
        return groups
    }

    /**
     * `#N` not followed by a word character - `#12abc` is no issue.
     */
    private val hashIssueKeyRegex = "#(\\d+)(?!\\w)".toRegex()

    /**
     * Extracts the `#N` issue keys of a message, as used by GitHub and GitLab, and returns their
     * numbers.
     *
     * A `#N` is not an issue of the repository when it is:
     *
     * * part of a reference to another repository - `owner/repo#N`, `group/subgroup/project#N`
     * * part of a URL - `https://host/page#N`
     * * part of an HTML entity - `&#39;`
     * * followed by a word character - `#12abc`
     *
     * @param message Text to extract the keys from
     * @return Numbers of the issues, without the `#`
     */
    @JvmStatic
    fun extractHashIssueKeys(message: String?): Set<String> =
        if (message.isNullOrBlank()) {
            emptySet()
        } else {
            hashIssueKeyRegex.findAll(message)
                .filter { match ->
                    val start = match.range.first
                    message.getOrNull(start - 1) != '&' && !isAttachedToPath(message, start)
                }
                .map { it.groupValues[1] }
                .toSet()
        }

    /**
     * `owner/repo` or `group/subgroup/project` right before a `#`.
     */
    private val repositoryPathSuffixRegex = "[\\w.-]+/[\\w.-]+$".toRegex()

    /**
     * Checks if the `#` at the given index is attached to a URL or to a repository path, by looking
     * at the word preceding it, up to the previous whitespace. A bare slash - `#12/#13`,
     * `feature/#12` - is neither.
     */
    private fun isAttachedToPath(text: String, index: Int): Boolean {
        var start = index
        while (start > 0 && !text[start - 1].isWhitespace()) {
            start--
        }
        val word = text.substring(start, index)
        return "://" in word || repositoryPathSuffixRegex.containsMatchIn(word)
    }

}
