package net.nemerosa.ontrack.extension.git.model

import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationProperty
import net.nemerosa.ontrack.extension.git.mocking.LocalGitProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.issues.mock.TestIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.model.toIdentifier
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.support.MessageAnnotationUtils
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

@AsAdminTest
class GitFreeTextAnnotatorContributorIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var gitFreeTextAnnotatorContributor: GitFreeTextAnnotatorContributor

    @Test
    fun `No Git configuration`() {
        project {
            expects("Text with #123" to "Text with #123")
        }
    }

    @Test
    fun `Git configuration without any issue service`() {
        project {
            localGitConfig(issueServiceConfigurationIdentifier = null)
            expects("Text with #123" to """Text with #123""")
        }
    }

    @Test
    fun `Git configuration with an issue service`() {
        project {
            localGitConfig(issueServiceConfigurationIdentifier = TestIssueServiceConfiguration.INSTANCE.toIdentifier().format())
            expects("Text with #123" to """Text with <a href="http://issue/123">#123</a>""")
        }
    }

    private fun Project.localGitConfig(issueServiceConfigurationIdentifier: String?) {
        setProperty(
                this,
                LocalGitProjectConfigurationPropertyType::class.java,
                LocalGitProjectConfigurationProperty(
                        name = uid("C"),
                        remote = "file:///not/used",
                        issueServiceConfigurationIdentifier = issueServiceConfigurationIdentifier,
                )
        )
    }

    private fun Project.expects(transformation: Pair<String, String>) {
        val input = transformation.first
        val expected = transformation.second
        // Gets the annotators
        val annotators = gitFreeTextAnnotatorContributor.getMessageAnnotators(this)
        // Annotation
        val actual = MessageAnnotationUtils.annotate(input, annotators)
        // Comparison
        assertEquals(expected, actual)
    }

}