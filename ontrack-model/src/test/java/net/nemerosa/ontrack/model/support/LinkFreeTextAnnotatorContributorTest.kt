package net.nemerosa.ontrack.model.support

import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.Project
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LinkFreeTextAnnotatorContributorTest {

    private val contributor = LinkFreeTextAnnotatorContributor()

    @Test
    fun `Null text`() {
        null gives ""
    }

    @Test
    fun `Empty text`() {
        "" gives ""
    }

    @Test
    fun `Blank text`() {
        "  " gives ""
    }

    @Test
    fun `Text with no link`() {
        "Some text" gives "Some text"
    }

    @Test
    fun `Link only`() {
        "https://en.wikipedia.org" gives link("https://en.wikipedia.org")
    }

    @Test
    fun `Link at beginning`() {
        "https://en.wikipedia.org link" gives """${link("https://en.wikipedia.org")} link"""
    }

    @Test
    fun `Link at end`() {
        "Link: https://en.wikipedia.org" gives """Link: ${link("https://en.wikipedia.org")}"""
    }

    @Test
    fun `Link in middle`() {
        "Link: https://en.wikipedia.org (click)" gives """Link: ${link("https://en.wikipedia.org")} (click)"""
    }

    // TODO Different types of links
    @Test
    fun `Several types of links`() {
        "Hello www.google.com World http://yahoo.com" gives
                "Hello ${link("www.google.com")} World ${link("http://yahoo.com")}"
        "https://www.google.com.tr/admin/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services" gives
                link("https://www.google.com.tr/admin/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services")
        "text and https://google.com.tr/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services in text" gives
                "text and ${link("https://google.com.tr/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services")} in text"
        "http://google.com/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services" gives
                link("http://google.com/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services")
        "and ftp://google.com/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services and" gives
                "and ${link("ftp://google.com/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services")} and"
        "www.google.com.tr/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services" gives
                link("www.google.com.tr/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services")
        "www.google.com/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services" gives
                link("www.google.com/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services")
        "drive.google.com/test/subPage?qs1=sss1&qs2=sss2&qs3=sss3#Services" gives
                "drive.google.com/test/subPage?qs1=sss1&amp;qs2=sss2&amp;qs3=sss3#Services"
    }

    private fun link(link: String) =
            """<a href="${escapeHtml4(link)}" target="_blank">${escapeHtml4(link)}</a>"""

    private infix fun String?.gives(expected: String?) {
        val actual = MessageAnnotationUtils.annotate(
                this,
                contributor.getMessageAnnotators(Project.of(NameDescription.nd("P", "")))
        )
        assertEquals(
                expected,
                actual
        )
    }

}