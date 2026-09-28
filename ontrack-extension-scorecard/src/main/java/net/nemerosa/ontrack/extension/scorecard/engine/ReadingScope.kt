package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.model.structure.Branch

/**
 * Which case gave the branches in scope.
 */
enum class ReadingScopeKind {
    /**
     * The branches matched by the branch model of the project
     */
    BRANCH_MODEL,

    /**
     * Every branch: the project has no branch model (no SCM)
     */
    ALL_BRANCHES,
}

/**
 * Branches a project is read on: its non-disabled branches matched by its branch model,
 * or all of them when the project has no branch model.
 */
data class ReadingScope(
    val kind: ReadingScopeKind,
    val branches: List<Branch>,
) {
    val details: Map<String, Any?>
        get() = mapOf(
            "kind" to kind.name,
            "branches" to branches.map { it.name },
        )
}
