package net.nemerosa.ontrack.extension.agents.assisted

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * An assisted build is promoted to a level whose *Assisted builds require* property lists a
 * validation stamp it has not passed.
 *
 * @param stamp Name of the first validation stamp which has not passed
 */
class AssistedBuildsRequireException(stamp: String) : InputException(message(stamp)) {
    companion object {
        /**
         * Reason of the refusal, for one stamp
         */
        fun message(stamp: String) = "Assisted build: $stamp must pass first."
    }
}
