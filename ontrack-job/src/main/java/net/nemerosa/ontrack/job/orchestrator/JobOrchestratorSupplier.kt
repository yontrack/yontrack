package net.nemerosa.ontrack.job.orchestrator

import net.nemerosa.ontrack.job.JobRegistration

interface JobOrchestratorSupplier {
    val jobRegistrations: Collection<JobRegistration>
}
