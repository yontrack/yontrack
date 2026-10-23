package net.nemerosa.ontrack.extension.jenkins.mock

data class MockJenkinsJob(
    val job: String,
) {

    private val _builds = mutableListOf<MockJenkinsBuild>()

    val builds: List<MockJenkinsBuild>
        @Synchronized get() = _builds.toList()

    /** Synchronized: two concurrent builds of the job must not get the same number. */
    @Synchronized
    fun build(parameters: Map<String, String>): MockJenkinsBuild {
        val build = MockJenkinsBuild(
            number = _builds.size + 1,
            parameters = parameters,
        )
        _builds += build
        return build
    }
}