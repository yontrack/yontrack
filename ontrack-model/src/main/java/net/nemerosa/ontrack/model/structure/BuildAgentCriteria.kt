package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.exceptions.InputException
import net.nemerosa.ontrack.model.security.AgentIdentifiers

/**
 * Criterion of the build filters on the assisted change of a build (#2036), read from its
 * `assistedChange` property.
 */
enum class BuildAssistedCriterion {
    /**
     * The commits of the build were written with at least one assistant.
     */
    YES,

    /**
     * The assisted change of the build is known and has no assistant.
     */
    NO,

    /**
     * The build has no assisted change, or one which could not be computed.
     */
    UNKNOWN,
}

/**
 * Criterion of the build filters on the actor who created a build (#2036), read from the `ACTOR`
 * column of the build.
 */
sealed interface BuildActorCriterion {

    /**
     * Builds created by a person.
     */
    data object Human : BuildActorCriterion

    /**
     * Builds created by any agent.
     */
    data object Agent : BuildActorCriterion

    /**
     * Builds created by one agent.
     *
     * @property identifier Identifier of the agent, `<slug>[agent]`
     */
    data class OneAgent(val identifier: String) : BuildActorCriterion
}

/**
 * Invalid value for one of the agent criteria of a build filter.
 */
class BuildAgentCriterionException(message: String) : InputException(message)

/**
 * Agent criteria of the build filters (#2036): whether the build was assisted, and which actor created it.
 */
object BuildAgentCriteria {

    /**
     * Storage ID of the `assistedChange` property, which lives in `ontrack-extension-scm`.
     *
     * The property must never be renamed (its FQCN is its storage ID), so the core can name it.
     */
    const val ASSISTED_CHANGE_PROPERTY_TYPE =
        "net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangePropertyType"

    /**
     * Basis of an assisted change which could not be computed.
     */
    const val ASSISTED_CHANGE_BASIS_UNKNOWN = "UNKNOWN"

    /**
     * Basis of an assisted change stored without one: set by the CI, the default of the property.
     */
    const val ASSISTED_CHANGE_BASIS_DEFAULT = "SET_BY_CI"

    /**
     * Value of the actor criterion for the builds of the persons.
     */
    const val ACTOR_HUMAN = "HUMAN"

    /**
     * Value of the actor criterion for the builds of the agents.
     */
    const val ACTOR_AGENT = "AGENT"

    /**
     * Parses the assisted criterion, case-insensitive.
     *
     * @return `null` when blank
     * @throws BuildAgentCriterionException When not `YES`, `NO` or `UNKNOWN`
     */
    fun parseAssisted(value: String?): BuildAssistedCriterion? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return BuildAssistedCriterion.entries.find { it.name.equals(text, ignoreCase = true) }
            ?: throw BuildAgentCriterionException("""Assisted must be YES, NO or UNKNOWN, not "$value".""")
    }

    /**
     * Parses the actor criterion, case-insensitive: `HUMAN`, `AGENT`, or the identifier of one agent.
     *
     * @return `null` when blank
     * @throws BuildAgentCriterionException When not valid
     */
    fun parseActor(value: String?): BuildActorCriterion? {
        val text = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            text == ACTOR_HUMAN.lowercase() -> BuildActorCriterion.Human
            text == ACTOR_AGENT.lowercase() -> BuildActorCriterion.Agent
            AgentIdentifiers.slug(text)?.let { AgentIdentifiers.isValidSlug(it) } == true ->
                BuildActorCriterion.OneAgent(text)

            else -> throw BuildAgentCriterionException(
                """Actor must be HUMAN, AGENT or the identifier of an agent, <slug>[agent], not "$value"."""
            )
        }
    }
}
