package net.nemerosa.ontrack.extension.jenkins.indicator

import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class JenkinsPipelineLibraryVersionTest {

    @Test
    fun `Two non semantic versions are compared based on the text`() {
        assertTrue(version("def") > version("abc"))
    }

    @Test
    fun `A semantic version is always greater than a non semantic one`() {
        assertTrue(version("1.0.0") > version("abc"))
    }

    @Test
    fun `3rd digit`() {
        assertTrue(version("1.0.1") > version("1.0.0"))
    }

    @Test
    fun `2nd digit`() {
        assertTrue(version("1.1.0") > version("1.0.20"))
    }

    @Test
    fun `1st digit`() {
        assertTrue(version("2.0.0") > version("1.20.0"))
    }

    private fun version(value: String) = JenkinsPipelineLibraryVersion(value)

}