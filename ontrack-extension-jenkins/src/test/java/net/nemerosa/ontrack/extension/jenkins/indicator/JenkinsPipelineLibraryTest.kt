package net.nemerosa.ontrack.extension.jenkins.indicator

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JenkinsPipelineLibraryTest {

    @Test
    fun `Double quote single library without a version`() {
        val jenkinsfile = """
            @Library("double-quote-single-library-no-version") _
        """.trimIndent()
        val libraries = JenkinsPipelineLibrary.extractLibraries(jenkinsfile)
        assertEquals(
            listOf(
                JenkinsPipelineLibrary("double-quote-single-library-no-version", null),
            ),
            libraries
        )
    }

    @Test
    fun `Double quote single library with version`() {
        val jenkinsfile = """
            @Library("double-quote-single-library-with-version@1.0") _
        """.trimIndent()
        val libraries = JenkinsPipelineLibrary.extractLibraries(jenkinsfile)
        assertEquals(
            listOf(
                JenkinsPipelineLibrary("double-quote-single-library-with-version", "1.0"),
            ),
            libraries
        )
    }

    @Test
    fun `Double quote single library with complex version`() {
        val jenkinsfile = """
            @Library("double-quote-single-library-with-complex-version@feature/CORE-1234-awesome") _
        """.trimIndent()
        val libraries = JenkinsPipelineLibrary.extractLibraries(jenkinsfile)
        assertEquals(
            listOf(
                JenkinsPipelineLibrary("double-quote-single-library-with-complex-version", "feature/CORE-1234-awesome"),
            ),
            libraries
        )
    }

    @Test
    fun `Single quote single library without a version`() {
        val jenkinsfile = """
            @Library('single-quote-single-library-no-version') _
        """.trimIndent()
        val libraries = JenkinsPipelineLibrary.extractLibraries(jenkinsfile)
        assertEquals(
            listOf(
                JenkinsPipelineLibrary("single-quote-single-library-no-version", null),
            ),
            libraries
        )
    }

    @Test
    fun `Single quote single library with version`() {
        val jenkinsfile = """
            @Library('single-quote-single-library-with-version@1.0') _
        """.trimIndent()
        val libraries = JenkinsPipelineLibrary.extractLibraries(jenkinsfile)
        assertEquals(
            listOf(
                JenkinsPipelineLibrary("single-quote-single-library-with-version", "1.0"),
            ),
            libraries
        )
    }

    @Test
    fun `Single quote single library with complex version`() {
        val jenkinsfile = """
            @Library('single-quote-single-library-with-complex-version@feature/CORE-1234-awesome') _
        """.trimIndent()
        val libraries = JenkinsPipelineLibrary.extractLibraries(jenkinsfile)
        assertEquals(
            listOf(
                JenkinsPipelineLibrary("single-quote-single-library-with-complex-version", "feature/CORE-1234-awesome"),
            ),
            libraries
        )
    }

    @Test
    fun `Multiline libraries`() {
        val jenkinsfile = """
            @Library("multiline-first-library@1.0.0")
            @Library("multiline-second-library@2.0.0") _
        """.trimIndent()
        val libraries = JenkinsPipelineLibrary.extractLibraries(jenkinsfile)
        assertEquals(
            listOf(
                JenkinsPipelineLibrary("multiline-first-library", "1.0.0"),
                JenkinsPipelineLibrary("multiline-second-library", "2.0.0"),
            ),
            libraries
        )
    }

    @Test
    fun `Array of libraries`() {
        val jenkinsfile = """
            @Library(['array-library-1', 'array-library-2@main']) _
        """.trimIndent()
        val libraries = JenkinsPipelineLibrary.extractLibraries(jenkinsfile)
        assertEquals(
            listOf(
                JenkinsPipelineLibrary("array-library-1", null),
                JenkinsPipelineLibrary("array-library-2", "main"),
            ),
            libraries
        )
    }

}