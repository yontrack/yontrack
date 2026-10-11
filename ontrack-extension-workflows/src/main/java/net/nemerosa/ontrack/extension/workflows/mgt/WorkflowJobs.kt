package net.nemerosa.ontrack.extension.workflows.mgt

import net.nemerosa.ontrack.job.JobCategory

object WorkflowJobs {

    private val CATEGORY = JobCategory.of("workflows").withName("Workflows")

    val TYPE_CLEANUP = CATEGORY.getType("cleanup-instances").withName("Cleanup of instances")

    val TYPE_RECOVERY = CATEGORY.getType("recover-nodes").withName("Recovery of nodes")

}