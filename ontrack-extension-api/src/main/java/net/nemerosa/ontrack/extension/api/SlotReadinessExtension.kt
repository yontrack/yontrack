package net.nemerosa.ontrack.extension.api

import net.nemerosa.ontrack.model.extension.Extension
import net.nemerosa.ontrack.model.readiness.Readiness
import net.nemerosa.ontrack.model.structure.Build

/**
 * Computes the readiness of a build for a slot, owned by the extension which manages the slots.
 */
interface SlotReadinessExtension : Extension {

    /**
     * What the [build] still lacks to be deployed in the slot identified by [slotId].
     *
     * @throws net.nemerosa.ontrack.model.readiness.ReadinessInputException When the slot is not a
     * slot of the build's project
     */
    fun getSlotReadiness(build: Build, slotId: String): Readiness
}
