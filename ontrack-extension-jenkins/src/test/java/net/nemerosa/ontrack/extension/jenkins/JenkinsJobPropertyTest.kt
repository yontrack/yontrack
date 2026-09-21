package net.nemerosa.ontrack.extension.jenkins

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JenkinsJobPropertyTest {

    @Test
    fun getUrlForAnOrganizationBranch() {
        val property = JenkinsJobProperty(
            JenkinsConfiguration("Test", "https://host", "", ""),
            "organization/job/Repository/job/Branch"
        )
        assertEquals(
            "https://host/job/organization/job/Repository/job/Branch",
            property.url
        )
    }

    @Test
    fun getUrlForAnOrganizationBranchWithTrailingSlash() {
        val property = JenkinsJobProperty(
            JenkinsConfiguration("Test", "https://host", "", ""),
            "organization/job/Repository/job/Branch/"
        )
        assertEquals(
            "https://host/job/organization/job/Repository/job/Branch",
            property.url
        )
    }

    @Test
    fun getPathComponentsForSimpleJob() {
        val property = JenkinsJobProperty(
            JenkinsConfiguration("Test", "http://jenkins", "", ""),
            "test"
        )
        assertEquals(listOf("test"), property.pathComponents)
    }

    @Test
    fun getPathComponentsForFolderJob() {
        val property = JenkinsJobProperty(
            JenkinsConfiguration("Test", "http://jenkins", "", ""),
            "test/test-master/test-master-build"
        )
        assertEquals(listOf("test", "test-master", "test-master-build"), property.pathComponents)
    }

    @Test
    fun getPathComponentsForCompleteFolderJob() {
        val property = JenkinsJobProperty(
            JenkinsConfiguration("Test", "http://jenkins", "", ""),
            "test/job/test-master/job/test-master-build"
        )
        assertEquals(listOf("test", "test-master", "test-master-build"), property.pathComponents)
    }
}