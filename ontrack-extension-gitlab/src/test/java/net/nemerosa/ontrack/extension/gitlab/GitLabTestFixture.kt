package net.nemerosa.ontrack.extension.gitlab

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
     * The only job of the fixture project: it ends as [VARIABLE_RESULT] asks, after
     * [VARIABLE_DURATION] seconds, echoing [VARIABLE_MESSAGE].
     */
    const val JOB_MOCK = "mock"

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
