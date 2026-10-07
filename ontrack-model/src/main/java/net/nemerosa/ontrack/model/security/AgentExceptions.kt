package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.exceptions.DuplicationException
import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.exceptions.NotFoundException

/**
 * The slug of an agent does not match [AgentIdentifiers.SLUG_REGEX].
 */
class AgentSlugInvalidException(slug: String) : InputException(
    "The agent slug \"$slug\" is not valid: it must have between 1 and 32 characters, " +
            "each a lowercase letter, a digit or a dash."
)

/**
 * The slug of an agent is already taken.
 */
class AgentSlugAlreadyTakenException(slug: String) : DuplicationException(
    "The agent slug \"$slug\" is already taken."
)

/**
 * An input of an agent is not valid (display name, tool, description).
 */
class AgentInputException(message: String) : InputException(message)

/**
 * No agent with this ID.
 */
class AgentNotFoundException(id: Int) : NotFoundException("Agent with id = $id cannot be found.")

/**
 * The owner given for an agent does not exist.
 */
class AgentOwnerNotFoundException(owner: String) : InputException(
    "The owner \"$owner\" cannot be found."
)

/**
 * The owner given for an agent is not a person.
 */
class AgentOwnerNotHumanException(owner: String) : InputException(
    "The owner \"$owner\" of an agent must be a person, not an agent."
)

/**
 * An agent identifier (`<slug>[agent]`) is used where only a person can be: logging in through the
 * identity provider, creating or editing an account.
 */
class AgentIdentifierRefusedException(email: String) : InputException(
    "\"$email\" is an agent identifier: an agent never logs in through the identity provider, and " +
            "is registered as an agent rather than created as an account."
)

/**
 * An agent account is edited as an account, instead of as an agent.
 */
class AgentAccountEditionException(email: String) : InputException(
    "\"$email\" is an agent: it is edited, transferred or deleted from the agents, and has no groups."
)
