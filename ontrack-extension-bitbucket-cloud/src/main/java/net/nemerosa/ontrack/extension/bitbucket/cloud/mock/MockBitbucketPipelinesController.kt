package net.nemerosa.ontrack.extension.bitbucket.cloud.mock

import net.nemerosa.ontrack.common.RunProfile
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/extension/bitbucket-cloud/mock/pipelines")
@Profile(RunProfile.DEV)
class MockBitbucketPipelinesController(
    private val mockBitbucketPipelinesRecorder: MockBitbucketPipelinesRecorder,
) {

    /**
     * Pipeline runs recorded for a configuration and a repository, oldest first.
     */
    @GetMapping("")
    fun getRuns(
        @RequestParam config: String,
        @RequestParam workspace: String,
        @RequestParam repository: String,
    ): List<MockBitbucketPipelineRun> =
        mockBitbucketPipelinesRecorder.findRuns(config, workspace, repository)

}
