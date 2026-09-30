package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.common.api.APIDescription

data class SlotPipelineDeploymentStatusProgress(
    @APIDescription("Are all the checks OK?")
    val ok: Boolean,
    @APIDescription("Was there a check being overridden?")
    val overridden: Boolean,
    @APIDescription("Number of checks being OK")
    val successCount: Int,
    @APIDescription("Total number of checks")
    val totalCount: Int,
    @APIDescription("Are the checks OK, still pending, or has one of them failed?")
    val state: SlotDeploymentCheckState = if (ok) SlotDeploymentCheckState.OK else SlotDeploymentCheckState.FAILED,
) {
    val percentage: Int = if (totalCount > 0) {
        successCount * 100 / totalCount
    } else {
        100
    }
}
