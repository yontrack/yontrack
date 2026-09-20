package net.nemerosa.ontrack.extension.gitlab

import net.nemerosa.ontrack.extension.gitlab.autoversioning.AbstractGitLabPostProcessing

/**
 * Content of the fixture project of the GitLab test group.
 *
 * The files live in `src/test/resources/gitlab-fixture/`, from where the wizard
 * (`scripts/gitlab-test-project.sh`) commits them.
 */
object GitLabTestFixture {

    const val RESOURCE_DIR = "/gitlab-fixture"

    const val MAIN_BRANCH = "main"

    /** The pipeline definition of the fixture project. */
    const val CI_FILE = ".gitlab-ci.yml"

    /** File edited by the auto-versioning tests. */
    const val VERSION_FILE = "gradle.properties"
    const val VERSION_PROPERTY = "version"

    /**
     * The pipeline job of the fixture project: it ends as [VARIABLE_RESULT] asks, after
     * [VARIABLE_DURATION] seconds, echoing [VARIABLE_MESSAGE].
     */
    const val JOB_MOCK = "mock"

    /**
     * The auto-versioning post-processing job of the fixture project.
     *
     * It stands in for the pipeline a user writes to run an upgrade command on the upgrade branch: it checks
     * that it received every variable the post-processing sends, then runs [AbstractGitLabPostProcessing.VAR_DOCKER_COMMAND]
     * - which is what lets a test ask for a success (`true`) or a failure (`false`).
     *
     * It deliberately does **not** push anything back. What Yontrack answers for is triggering the pipeline
     * with the right variables on the right ref, waiting for it and reporting it; committing is the
     * pipeline's own business, and doing it here would mean a write token as a CI/CD variable of the
     * fixture project, with its own rotation.
     */
    const val JOB_AV = "av"

    /**
     * Variables the auto-versioning post-processing passes, which [JOB_AV] requires.
     *
     * Read from the post-processing itself rather than copied, so that renaming one there breaks
     * `GitLabTestFixtureTest` rather than the pipeline at run time.
     */
    val POST_PROCESSING_VARIABLES = listOf(
        AbstractGitLabPostProcessing.VAR_REPOSITORY,
        AbstractGitLabPostProcessing.VAR_UPGRADE_BRANCH,
        AbstractGitLabPostProcessing.VAR_DOCKER_IMAGE,
        AbstractGitLabPostProcessing.VAR_DOCKER_COMMAND,
        AbstractGitLabPostProcessing.VAR_COMMIT_MESSAGE,
        AbstractGitLabPostProcessing.VAR_VERSION,
    )

    /**
     * The post-processing variable which triggers [JOB_AV], and the pipeline with it.
     */
    val VARIABLE_UPGRADE_BRANCH = AbstractGitLabPostProcessing.VAR_UPGRADE_BRANCH

    /** Command [JOB_AV] runs, and therefore what decides whether the pipeline succeeds. */
    val VARIABLE_DOCKER_COMMAND = AbstractGitLabPostProcessing.VAR_DOCKER_COMMAND

    /**
     * A [VARIABLE_DOCKER_COMMAND] which succeeds. `false` is the one which fails, should a test ever need
     * to pay a compute minute for a failing post-processing.
     */
    const val COMMAND_SUCCESS = "true"

    /**
     * Asks the mock job for its outcome: [RESULT_SUCCESS] or [RESULT_FAILURE].
     *
     * It doubles as the trigger of the whole pipeline: no pipeline is created at all unless it is
     * passed, so that a branch the tests push runs nothing and costs no compute minute.
     */
    const val VARIABLE_RESULT = "MOCK_RESULT"

    /** Asks the mock job to take that many seconds. */
    const val VARIABLE_DURATION = "MOCK_DURATION"

    /** Echoed by the mock job, so that a test can tell its own pipeline apart in the logs. */
    const val VARIABLE_MESSAGE = "MOCK_MESSAGE"

    const val RESULT_SUCCESS = "success"
    const val RESULT_FAILURE = "failure"
}
