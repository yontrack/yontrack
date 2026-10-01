package net.nemerosa.ontrack.extension.issues.support

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssueServiceUtilsTest {

    @Test
    fun `Issue groups - no types, no groups`() {
        assertTrue(IssueServiceUtils.getIssueGroups(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `Issue groups - no types, groups`() {
        assertTrue(IssueServiceUtils.getIssueGroups(emptyList(), mapOf("Bug" to setOf("bug"))).isEmpty())
    }

    @Test
    fun `Issue groups - one group`() {
        assertEquals(setOf("Bug"), IssueServiceUtils.getIssueGroups(listOf("bug"), mapOf("Bug" to setOf("bug"))))
    }

    @Test
    fun `Issue groups - one group among many`() {
        assertEquals(
            setOf("Bugs"),
            IssueServiceUtils.getIssueGroups(
                listOf("bug"),
                mapOf(
                    "Bugs" to setOf("bug"),
                    "Features" to setOf("feature"),
                )
            )
        )
    }

    @Test
    fun `Issue groups - no group among many`() {
        assertEquals(
            emptySet(),
            IssueServiceUtils.getIssueGroups(
                listOf("other"),
                mapOf(
                    "Bugs" to setOf("bug"),
                    "Features" to setOf("feature"),
                )
            )
        )
    }

    @Test
    fun `Issue groups - one group among many, with several types`() {
        assertEquals(
            setOf("Bugs"),
            IssueServiceUtils.getIssueGroups(
                listOf("bug", "gui"),
                mapOf(
                    "Bugs" to setOf("bug"),
                    "Features" to setOf("feature"),
                )
            )
        )
    }

    @Test
    fun `Issue groups - two groups`() {
        assertEquals(
            setOf("Bugs", "Features"),
            IssueServiceUtils.getIssueGroups(
                listOf("bug", "feature"),
                mapOf(
                    "Bugs" to setOf("bug"),
                    "Features" to setOf("feature"),
                )
            )
        )
    }

    @Test
    fun `Hash issue keys - repository paths and URLs are excluded`() {
        assertEquals(
            setOf("10"),
            IssueServiceUtils.extractHashIssueKeys(
                "owner/repo#1 (group/sub.group/project#2, https://host/page#3 http://host/#4 www.host.com/page#5 #10"
            )
        )
    }

    @Test
    fun `Hash issue keys - a bare slash is no repository path`() {
        assertEquals(
            setOf("12", "13", "14"),
            IssueServiceUtils.extractHashIssueKeys("#12/#13 Fix in feature/#14")
        )
    }

    @Test
    fun `Hash issue keys - none in a blank message`() {
        assertTrue(IssueServiceUtils.extractHashIssueKeys("").isEmpty())
        assertTrue(IssueServiceUtils.extractHashIssueKeys(null).isEmpty())
    }

}
