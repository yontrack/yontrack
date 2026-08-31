package net.nemerosa.ontrack.extension.config

object EnvFixtures {

    /**
     * The project name has no default on purpose. It used to be the fixed string `yontrack`, which
     * every caller then shared as one project row against the shard's database - across modules,
     * which a sharded CI run may execute concurrently. Two flakes came out of that (#1657), so the
     * name is now the caller's to choose, and it should be unique to the run.
     */
    fun generic(
        projectName: String,
        scmBranch: String = TEST_BRANCH,
        extraEnv: Map<String, String> = emptyMap(),
    ) = mapOf(
        "PROJECT_NAME" to projectName,
        "BRANCH_NAME" to scmBranch,
        "BUILD_NUMBER" to TEST_BUILD_NUMBER,
        "BUILD_REVISION" to TEST_COMMIT,
        "VERSION" to TEST_VERSION,
    ) + extraEnv

    fun jenkins(
        scmBranch: String = TEST_BRANCH,
        extraEnv: Map<String, String> = emptyMap(),
    ) = mapOf(
        "JENKINS_URL" to JENKINS_URL,
        "GIT_URL" to "https://github.com/nemerosa/ontrack.git",
        "BRANCH_NAME" to scmBranch,
        "BUILD_NUMBER" to TEST_BUILD_NUMBER,
        "BUILD_URL" to "$JENKINS_URL/job/nemerosa/job/ontrack/job/main/23/",
        "JOB_NAME" to "nemerosa/ontrack/main",
    ) + extraEnv

    fun gitHub(
        projectName: String = "yontrack",
        scmBranch: String = TEST_BRANCH,
        workflowName: String = GITHUB_WORKFLOW,
        runId: Long = GITHUB_RUN_ID,
        runNumber: Long = 96L,
        eventName: String = GITHUB_EVENT_NAME,
        extraEnv: Map<String, String> = emptyMap(),
    ) = mapOf(
        "GITHUB_SERVER_URL" to "https://github.com",
        "GITHUB_REPOSITORY" to "yontrack/$projectName",
        "GITHUB_WORKFLOW" to workflowName,
        "GITHUB_REF_NAME" to scmBranch,
        "GITHUB_ACTIONS" to "true",
        "GITHUB_RUN_ID" to runId.toString(),
        "GITHUB_RUN_NUMBER" to runNumber.toString(),
        "GITHUB_SHA" to TEST_COMMIT,
        "GITHUB_EVENT_NAME" to eventName,
    ) + extraEnv

    const val GITHUB_RUN_ID = 18289595913L
    const val GITHUB_WORKFLOW = "Workflow name"
    const val GITHUB_EVENT_NAME = "push"

    const val TEST_BRANCH = "release/1.51"
    const val TEST_COMMIT = "7c0b1745f513b9162791651582c0044d7b6d2a83"
    const val TEST_VERSION = "5.1.2"
    const val TEST_BUILD_NUMBER = "23"

    const val JENKINS_URL = "https://jenkins.dev.yontrack.com"

}