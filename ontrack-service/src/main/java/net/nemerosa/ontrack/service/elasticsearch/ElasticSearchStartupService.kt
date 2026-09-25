package net.nemerosa.ontrack.service.elasticsearch

import net.nemerosa.ontrack.model.support.StartupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ElasticSearchStartupService(
        private val searchService: ElasticSearchServiceImpl
) : StartupService {

    override fun getName(): String = "Creation of ElasticSearch indexes"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION - 1 // Just before the jobs

    override fun start() {
        searchService.indexInit()
    }

}
