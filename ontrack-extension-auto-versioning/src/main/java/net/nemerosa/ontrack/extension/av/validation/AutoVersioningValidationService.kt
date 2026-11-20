package net.nemerosa.ontrack.extension.av.validation

import net.nemerosa.ontrack.model.structure.Build

interface AutoVersioningValidationService {

    /**
     * Checks the given [build] and applies validation runs when needed, depending on the branch
     * configuration.
     *
     * * we get the current version of the dependencies (only those having the `validationStamp` property) from either:
     *    * the build links (faster)
     *    * the source code (longer)
     * * we compute the latest version possible for those dependencies
     * * we create a validation stamp based on the mismatch (or not) of versions
     *
     * Needs `ValidationRunCreate` on the project of the [build], checked before anything else.
     *
     * @param build Build to check and validate
     * @return List of validations having been applied (validation stamp not included)
     */
    fun checkAndValidate(build: Build): List<AutoVersioningValidationData>

}