package net.nemerosa.ontrack.model.support

import net.nemerosa.ontrack.model.support.tree.support.Markup
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

class MessageAnnotationUtilsTest {

    private val linkFreeTextAnnotatorContributor =
        RegexMessageAnnotator("((https?://|ftp://|www\\.)\\S+)".toRegex()) { link ->
            MessageAnnotation.of("a")
                .attr("href", link)
                .attr("target", "_blank")
                .text(link)
        }

    private val jiraIssueMessageAnnotator =
        RegexMessageAnnotator("([A-Z]+-\\d+)".toRegex()) { match ->
            MessageAnnotation.of("a").attr("href", "http://jira/browse/$match").text(match)
        }

    private val issueMessageAnnotator =
        RegexMessageAnnotator("(#\\d+)".toRegex()) { match ->
            val id = match.substring(1)
            MessageAnnotation.of("link").attr("url", "http://test/id/$id").text(match)
        }

    private val numberMessageAnnotator =
        RegexMessageAnnotator("(\\d+)".toRegex()) { match ->
            MessageAnnotation.of("emphasis").text(match)
        }

    @Test
    fun `Link with dashes`() {
        val html = MessageAnnotationUtils.annotate(
                "Description for https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4",
                listOf(
                        linkFreeTextAnnotatorContributor
                )
        )
        assertEquals("""Description for <a href="https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4" target="_blank">https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4</a>""", html)
    }

    @Test
    fun `Link with dashes and JIRA annotator second`() {
        val html = MessageAnnotationUtils.annotate(
                "Description for https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4",
                listOf(
                        linkFreeTextAnnotatorContributor,
                        jiraIssueMessageAnnotator
                )
        )
        assertEquals("""Description for <a href="https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4" target="_blank">https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4</a>""", html)
    }

    @Test
    fun `Link with dashes and JIRA annotator first`() {
        val html = MessageAnnotationUtils.annotate(
                "Description for https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4",
                listOf(
                        linkFreeTextAnnotatorContributor,
                        jiraIssueMessageAnnotator
                )
        )
        assertEquals("""Description for <a href="https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4" target="_blank">https://www.example.com/asset/11f90cd5-d3c9-46f4-9b7c-9773284e3ad4</a>""", html)
    }

    @Test
    fun annotate_as_node_issue_with_one_match() {
        val root = MessageAnnotationUtils.annotateAsNode(
                "#177 One match",
                listOf(
                        issueMessageAnnotator
                )
        )
        // Root
        assertNotNull(root)
        assertNull(root.data)
        assertFalse(root.isLeaf)
        // Children
        val children = root.children.toList()
        assertEquals(2, children.size.toLong())
        run {
            val link = children[0]
            assertEquals(
                    Markup.of("link").attr("url", "http://test/id/177"),
                    link.data
            )
            assertFalse(link.isLeaf)
            val linkChildren = link.children.toList()
            assertEquals(1, linkChildren.size.toLong())
            run {
                val key = linkChildren[0]
                assertEquals(
                        Markup.text("#177"),
                        key.data
                )
                assertTrue(key.isLeaf)
            }
        }
        run {
            val child = children[1]
            assertEquals(
                    Markup.text(" One match"),
                    child.data
            )
            assertTrue(child.isLeaf)
        }
    }

    @Test
    fun annotate_as_html_issue_with_no_match() {
        val html = MessageAnnotationUtils.annotate(
                "No match",
                listOf(
                        issueMessageAnnotator
                )
        )
        assertEquals("No match", html)
    }

    @Test
    fun annotate_as_html_issue_with_one_match() {
        val html = MessageAnnotationUtils.annotate(
                "#177 One match",
                listOf(
                        issueMessageAnnotator
                )
        )
        assertEquals("<link url=\"http://test/id/177\">#177</link> One match", html)
    }

    @Test
    fun annotate_as_node_issue_and_number_with_one_match() {
        val root = MessageAnnotationUtils.annotateAsNode(
                "#177 One match",
                listOf(
                        issueMessageAnnotator,
                        numberMessageAnnotator
                )
        )
        // Root
        assertNotNull(root)
        assertNull(root.data)
        assertFalse(root.isLeaf)
        // Children
        val children = root.children.toList()
        assertEquals(2, children.size.toLong())
        run {
            val link = children[0]
            assertEquals(
                    Markup.of("link").attr("url", "http://test/id/177"),
                    link.data
            )
            assertFalse(link.isLeaf)
            val linkChildren = link.children.toList()
            assertEquals(2, linkChildren.size.toLong())
            run {
                val sharp = linkChildren[0]
                assertEquals(Markup.text("#"), sharp.data)
            }
            run {
                val em = linkChildren[1]
                assertEquals(Markup.of("emphasis"), em.data)
                val emChildren = em.children.toList()
                assertEquals(1, emChildren.size.toLong())
                run {
                    val number = emChildren[0]
                    assertEquals(Markup.text("177"), number.data)
                }
            }
        }
        run {
            val reminder = children[1]
            assertEquals(
                    Markup.text(" One match"),
                    reminder.data
            )
            assertTrue(reminder.isLeaf)
        }
    }

    @Test
    fun annotate_as_html_issue_and_number_with_one_match() {
        val html = MessageAnnotationUtils.annotate(
                "#177 One match",
                listOf(
                        issueMessageAnnotator,
                        numberMessageAnnotator
                )
        )
        assertEquals("<link url=\"http://test/id/177\">#<emphasis>177</emphasis></link> One match", html)
    }

}
