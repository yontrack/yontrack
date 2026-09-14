package net.nemerosa.ontrack.extension.bitbucket.cloud

/**
 * Content of the fixture repository of the test workspace.
 *
 * The files live in `src/test/resources/bitbucket-cloud-fixture/`, from where the wizard uploads them.
 */
object BitbucketCloudTestFixture {

    const val RESOURCE_DIR = "/bitbucket-cloud-fixture"

    const val MAIN_BRANCH = "main"

    const val PIPELINES_FILE = "bitbucket-pipelines.yml"

    /** File edited by the auto-versioning tests. */
    const val VERSION_FILE = "gradle.properties"
    const val VERSION_PROPERTY = "version"

    /** Echoes its `MESSAGE` variable. */
    const val PIPELINE_ECHO = "yontrack-echo"

    /** Fails when its `FAIL` variable is `true`. */
    const val PIPELINE_FAIL = "yontrack-fail"

    /** Shaped for auto-versioning post-processing: commits `VERSION` to `UPGRADE_BRANCH`. */
    const val PIPELINE_AUTO_VERSIONING = "yontrack-auto-versioning"

    val AUTO_VERSIONING_VARIABLES = listOf(
        "REPOSITORY",
        "UPGRADE_BRANCH",
        "DOCKER_IMAGE",
        "DOCKER_COMMAND",
        "COMMIT_MESSAGE",
        "VERSION",
    )
}
