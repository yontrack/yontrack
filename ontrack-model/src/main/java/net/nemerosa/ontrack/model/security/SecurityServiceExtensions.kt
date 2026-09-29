package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.structure.ProjectEntity

inline fun <reified F : ProjectFunction> SecurityService.isProjectFunctionGranted(e: ProjectEntity) =
        isProjectFunctionGranted(e, F::class.java)

inline fun <reified F : GlobalFunction> SecurityService.isGlobalFunctionGranted() =
        isGlobalFunctionGranted(F::class.java)
