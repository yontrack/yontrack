package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.model.structure.Branch

/**
 * Whether a branch shows the medals of the auto-promoted levels a build does not have yet.
 *
 * Configured by [NotGrantedMedalsPropertyType] on the branch or its project, and falling back to the
 * global [NotGrantedMedalsSettings]. The cascade is *first found wins*, whichever way it answers: a
 * `false` on a branch turns the medals off under a project which has them on.
 */
interface NotGrantedMedalsService {

    fun isEnabled(branch: Branch): Boolean

}
