package net.nemerosa.ontrack.extension.jenkins.mock

import net.nemerosa.ontrack.extension.jenkins.JenkinsExtensionConfigProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/extension/jenkins/mock")
@ConditionalOnProperty(name = [JenkinsExtensionConfigProperties.MOCK_ENABLED], havingValue = "true", matchIfMissing = false)
class MockJenkinsController(
    private val mockJenkinsClients: MockJenkinsClients,
) {

    @GetMapping("/{config}/job")
    fun getJobByPath(@PathVariable config: String, @RequestParam path: String): MockJenkinsJob? =
        mockJenkinsClients.findClient(config)?.jobs?.get(path)

}
