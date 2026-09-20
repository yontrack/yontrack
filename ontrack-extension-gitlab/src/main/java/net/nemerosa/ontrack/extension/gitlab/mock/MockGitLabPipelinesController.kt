package net.nemerosa.ontrack.extension.gitlab.mock

import net.nemerosa.ontrack.common.RunProfile
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/extension/gitlab/mock/pipelines")
@Profile(RunProfile.DEV)
class MockGitLabPipelinesController(
    private val mockGitLabPipelinesRecorder: MockGitLabPipelinesRecorder,
) {

    /**
     * Pipeline runs recorded for a configuration and a project, oldest first.
     */
    @GetMapping("")
    fun getRuns(
        @RequestParam config: String,
        @RequestParam project: String,
    ): List<MockGitLabPipelineRun> =
        mockGitLabPipelinesRecorder.findRuns(config, project)

}
