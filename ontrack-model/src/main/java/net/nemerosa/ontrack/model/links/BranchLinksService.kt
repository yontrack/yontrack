package net.nemerosa.ontrack.model.links

import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.BranchLink

/**
 * Service to compute links between branches.
 */
interface BranchLinksService {

    /**
     * Given a [branch], returns the links to its dependencies, based on the first [n] builds.
     *
     * @param branch Source branch
     * @param n Number of builds to take into account to get the branch dependencies
     * @return The branches [branch] depends on
     */
    fun getDependencyLinks(
        branch: Branch,
        n: Int,
    ): List<BranchLink>

    /**
     * Given a [branch], returns the links from its consumers, based on the first [n] builds.
     *
     * @param branch Source branch
     * @param n Number of builds to take into account to get the branch consumers
     * @return The branches [branch] is a dependency of
     */
    fun getConsumerLinks(
        branch: Branch,
        n: Int,
    ): List<BranchLink>

}