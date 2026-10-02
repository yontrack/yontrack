package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
class BranchAutoEnableBuildEventListener(
    private val structureService: StructureService,
    private val securityService: SecurityService,
) : EventListener {

    override fun onEvent(event: Event) {
        if (event.eventType == EventFactory.NEW_BUILD) {
            val branch = event.getEntity<Branch>(ProjectEntityType.BRANCH)
            if (branch.isDisabled) {
                securityService.asAdmin("branch-auto-enable") {
                    structureService.enableBranch(branch)
                }
            }
        }
    }
}