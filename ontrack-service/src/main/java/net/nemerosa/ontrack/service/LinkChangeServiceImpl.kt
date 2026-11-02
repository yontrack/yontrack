package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class LinkChangeServiceImpl(
    private val structureService: StructureService,
) : LinkChangeService {

    override fun linkChanges(from: Build, to: Build): List<LinkChange> {
        val (actualFrom, actualTo) = sortById(from, to)

        val fromLinks = structureService.getBuildDependencyLinks(actualFrom).pageItems
        val toLinks = structureService.getBuildDependencyLinks(actualTo).pageItems

        val changes = mutableListOf<LinkChange>()

        syncForward(
            from = fromLinks,
            to = toLinks
        ) {
            equality { a, b ->
                a.build.project.name == b.build.project.name && a.qualifier == b.qualifier
            }
            // New link in TO
            onCreation { from ->
                changes += LinkChange(
                    project = from.build.project,
                    qualifier = from.qualifier,
                    from = from.build,
                    to = null,
                )
            }
            // Obsolete link in FROM
            onDeletion { to ->
                changes += LinkChange(
                    project = to.build.project,
                    qualifier = to.qualifier,
                    from = null,
                    to = to.build,
                )
            }
            // Changed link
            onModification { from, to ->
                changes += LinkChange(
                    project = from.build.project,
                    qualifier = from.qualifier,
                    from = from.build,
                    to = to.build,
                )
            }
        }

        return changes
    }

}