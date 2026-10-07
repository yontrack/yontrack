package net.nemerosa.ontrack.model.security

/**
 * Identifiers of the agent accounts.
 *
 * An agent is identified by `<slug>[agent]`, stored in place of an email. `[` is not valid in an
 * unquoted email address, so no identity provider login can ever claim an agent identifier.
 */
object AgentIdentifiers {

    /**
     * Suffix of every agent identifier.
     */
    const val SUFFIX = "[agent]"

    /**
     * Pattern of a slug.
     */
    val SLUG_REGEX = Regex("[a-z0-9-]{1,32}")

    /**
     * Tools offered by the UI. The tool is stored as free text, so that others can be used.
     */
    val TOOLS = listOf("Claude Code", "Codex", "Copilot", "Devin", "Other")

    /**
     * Is this slug valid?
     */
    fun isValidSlug(slug: String): Boolean = SLUG_REGEX.matches(slug)

    /**
     * Identifier of an agent from its slug.
     */
    fun identifier(slug: String): String = "$slug$SUFFIX"

    /**
     * Slug of an agent from its identifier, or `null` when the identifier is not an agent's.
     */
    fun slug(identifier: String): String? = identifier.takeIf { isAgentIdentifier(it) }?.removeSuffix(SUFFIX)

    /**
     * Does this email (or user name) designate an agent?
     */
    fun isAgentIdentifier(email: String): Boolean = email.trim().endsWith(SUFFIX)

}
