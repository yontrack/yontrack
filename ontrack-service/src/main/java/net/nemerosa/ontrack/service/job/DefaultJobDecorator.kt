package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.job.Job
import net.nemerosa.ontrack.job.JobDecorator
import net.nemerosa.ontrack.job.Task
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.stereotype.Component

@Component
class DefaultJobDecorator(private val securityService: SecurityService) : JobDecorator {

    override fun decorate(job: Job, task: Task): Task {
        return {
            securityService.asAdmin("job:${job.key.type.category.key}/${job.key.type.key}", task)
        }
    }

}
