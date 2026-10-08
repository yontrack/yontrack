package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.model.structure.Build

/**
 * Assisted change of the builds: whether the commits since the previous build on their branch were
 * written with assistants.
 */
interface AssistedChangeService {

    /**
     * Assisted change of a build, `null` when it has not been computed nor set.
     */
    fun getAssistedChange(build: Build): AssistedChangeProperty?

    /**
     * Computes the assisted change of a build and stores it, synchronously.
     *
     * 1. A value set by the CI wins: it is kept.
     * 2. Nothing is done while the build has no commit.
     * 3. `UNKNOWN` is stored when the project has no SCM able to compute change logs, when no previous
     *    build on the branch has a commit, or when the SCM fails.
     * 4. Otherwise, the change log from the previous build with a commit gives the `COMPUTED` value.
     *
     * The value is written only when it changes.
     *
     * @param build Build to compute the assisted change for
     * @return Assisted change of the build after the computation, `null` if there is none
     */
    fun computeAssistedChange(build: Build): AssistedChangeProperty?

}
