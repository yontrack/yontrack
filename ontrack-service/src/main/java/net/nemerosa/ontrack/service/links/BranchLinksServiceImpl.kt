package net.nemerosa.ontrack.service.links

import net.nemerosa.ontrack.model.buildfilter.BuildFilterService
import net.nemerosa.ontrack.model.links.BranchLinksDirection
import net.nemerosa.ontrack.model.links.BranchLinksEdge
import net.nemerosa.ontrack.model.links.BranchLinksNode
import net.nemerosa.ontrack.model.links.BranchLinksService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class BranchLinksServiceImpl(
    private val buildFilterService: BuildFilterService,
    private val structureService: StructureService
) : BranchLinksService {

    private data class LinkInfo(
        val sourceBuild: Build,
        val targetBuild: Build,
    )

    private fun convertToLinks(
        nextBuilds: MutableMap<Pair<ID, String>, LinkInfo>,
        refBranch: (LinkInfo) -> Branch,
    ): List<BranchLink> = nextBuilds.map { (key, nextBuild) ->
        val (_, qualifier) = key
        BranchLink(
            branch = refBranch(nextBuild),
            targetBuild = nextBuild.targetBuild,
            sourceBuild = nextBuild.sourceBuild,
            qualifier = qualifier,
        )
    }.sortedWith(
        compareBy(
            { it.branch.project.name },
            { it.qualifier },
        )
    )

    private fun collectLinkInfo(
        buildLink: BuildLink,
        nextBuilds: MutableMap<Pair<ID, String>, LinkInfo>,
        linkInfo: LinkInfo,
    ) {
        val key = buildLink.build.project.id to buildLink.qualifier
        // Existing build?
        val existingBuild = nextBuilds[key]
        if (existingBuild != null) {
            // Takes the new build if more recent
            if (linkInfo.sourceBuild.id() > existingBuild.sourceBuild.id()) {
                nextBuilds[key] = linkInfo
            }
        } else {
            // New build
            nextBuilds[key] = linkInfo
        }
    }

    override fun getDependencyLinks(branch: Branch, n: Int): List<BranchLink> {
        // Gets the N first builds
        val builds = getBuilds(branch, n)
        // Grouping per project & qualifier
        val nextBuilds = mutableMapOf<Pair<ID, String>, LinkInfo>()
        // Looping over the N first builds
        builds.forEach { build ->
            // Gets the builds it uses
            val dependencyBuildLinks = structureService.getBuildDependencyLinks(build, size = n).pageItems
            // Looping over these dependencies
            dependencyBuildLinks.forEach { buildLink ->
                val linkInfo = LinkInfo(
                    sourceBuild = build,
                    targetBuild = buildLink.build,
                )
                collectLinkInfo(buildLink, nextBuilds, linkInfo)
            }
        }
        // Result
        return convertToLinks(nextBuilds) {
            it.targetBuild.branch
        }
    }

    override fun getConsumerLinks(branch: Branch, n: Int): List<BranchLink> {
        // Gets the N first builds
        val builds = getBuilds(branch, n)
        // Grouping per project & qualifier
        val nextBuilds = mutableMapOf<Pair<ID, String>, LinkInfo>()
        // Looping over the N first builds
        builds.forEach { build ->
            // Gets the builds using it
            val consumerBuildLinks = structureService.getBuildConsumerLinks(build, size = n).pageItems
            // Looping over these consumers
            consumerBuildLinks.forEach { buildLink ->
                val linkInfo = LinkInfo(
                    sourceBuild = buildLink.build,
                    targetBuild = build,
                )
                collectLinkInfo(buildLink, nextBuilds, linkInfo)
            }
        }
        // Result
        return convertToLinks(nextBuilds) {
            it.sourceBuild.branch
        }
    }

    private fun populate(node: BranchLinksNode, build: Build, direction: BranchLinksDirection): BranchLinksNode {
        // Recomputes the edges
        val edges = node.edges.map { edge ->
            populate(edge, BranchLinksNode(node.branch, build, node.edges), direction)
        }
        // OK
        return BranchLinksNode(
            node.branch,
            build,
            edges
        )
    }

    private fun populate(
        edge: BranchLinksEdge,
        source: BranchLinksNode,
        direction: BranchLinksDirection
    ): BranchLinksEdge {
        // Gets the target build if any
        val target = source.build?.let { getEdgeBuild(it, edge.linkedTo.branch, direction) }
        // If no build, we return the edge as it is
        return if (target == null) {
            edge
        } else {
            val newLinkedNode = populate(edge.linkedTo, target, direction)
            BranchLinksEdge(
                direction = direction,
                linkedTo = newLinkedNode,
            )
        }
    }

    private fun getBuilds(branch: Branch, history: Int): List<Build> =
        buildFilterService.standardFilterProviderData(history).build().filterBranchBuilds(branch)

    /**
     * Gets the build target for the same project than the one defined by the branch graph.
     */
    private fun getEdgeBuild(build: Build, target: Branch, direction: BranchLinksDirection): Build? =
        when (direction) {
            BranchLinksDirection.USING ->
                structureService.getBuildDependencyLinks(build, 0, 1) {
                    it.build.branch.id == target.id
                }.pageItems.firstOrNull()?.build

            BranchLinksDirection.USED_BY ->
                structureService.getBuildConsumerLinks(
                    build = build,
                    offset = 0,
                    size = 1,
                ) {
                    it.build.branch.id == target.id
                }.pageItems.firstOrNull()?.build
        }

    private class Node(
        val branch: Branch,
        val branches: MutableList<Node> = mutableListOf()
    ) {
    }

    private fun graphToNode(node: Node, direction: BranchLinksDirection): BranchLinksNode =
        BranchLinksNode(
            branch = node.branch,
            build = null,
            edges = node.branches.map { child ->
                graphToNode(child, direction).run {
                    BranchLinksEdge(
                        direction = direction,
                        linkedTo = this,
                    )
                }
            }
        )

}