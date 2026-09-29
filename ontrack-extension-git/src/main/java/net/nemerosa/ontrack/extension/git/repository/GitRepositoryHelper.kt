package net.nemerosa.ontrack.extension.git.repository

import net.nemerosa.ontrack.extension.git.model.IndexableGitCommit
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project

interface GitRepositoryHelper {

    fun findBranchWithProjectAndGitBranch(project: Project, gitBranch: String): Int?

    /**
     * Using the `ENTITY_STORE` table to get the earliest build after a commit.
     */
    fun getEarliestBuildAfterCommit(
            branch: Branch,
            indexedGitCommit: IndexableGitCommit
    ): Int?

}