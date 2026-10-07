package net.nemerosa.ontrack.model.security

/**
 * Kind of an [Account]: a person, or a registered agent acting with its own tokens on behalf of
 * an owner.
 *
 * @property description Description of the kind, used in the GraphQL schema
 */
enum class AccountKind(
    val description: String,
) {

    /**
     * A person, logging in through the identity provider or using their own API tokens.
     */
    HUMAN("A person, logging in through the identity provider or using their own API tokens"),

    /**
     * A registered agent: a non-human principal acting with its own API tokens, owned by a human account.
     * It never logs in through the identity provider.
     */
    AGENT("A registered agent: a non-human principal acting with its own API tokens, owned by a human account"),

}
