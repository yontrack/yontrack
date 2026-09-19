package net.nemerosa.ontrack.extension.gitlab

import net.nemerosa.ontrack.extension.git.model.GitFreeTextAnnotatorContributor
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationProperty
import net.nemerosa.ontrack.extension.gitlab.property.GitLabProjectConfigurationPropertyType
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.issues.mock.TestIssueServiceConfiguration
import net.nemerosa.ontrack.extension.issues.model.toIdentifier
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.support.MessageAnnotationUtils
import net.nemerosa.ontrack.test.TestUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@AsAdminTest
class GitLabFreeTextAnnotatorContributorIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var gitLabConfigurationService: GitLabConfigurationService

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
            gitLabConfig(issueServiceConfigurationIdentifier = null)
            // GitLab issue system will be taken by default
            expects("Text with #123" to """Text with <a href="https://gitlab.com/nemerosa/test/issues/123">#123</a>""")
        }
    }

    @Test
    fun `Git configuration with an issue service`() {
        project {
            gitLabConfig(issueServiceConfigurationIdentifier = TestIssueServiceConfiguration.INSTANCE.toIdentifier().format())
            expects("Text with #123" to """Text with <a href="http://issue/123">#123</a>""")
        }
    }

    private fun Project.gitLabConfig(issueServiceConfigurationIdentifier: String?) {
        // Create a Git configuration
        val gitConfigurationName = TestUtils.uid("C")
        val gitConfiguration = withDisabledConfigurationTest {
            asUser().with(GlobalSettings::class.java).call {
                gitLabConfigurationService.newConfiguration(
                        GitLabConfiguration(
                                name = gitConfigurationName,
                                url = "https://gitlab.com",
                                token = "token",
                        )
                )
            }
        }
        setProperty(
                this,
                GitLabProjectConfigurationPropertyType::class.java,
                GitLabProjectConfigurationProperty(
                        gitConfiguration,
                        issueServiceConfigurationIdentifier,
                        "nemerosa/test",
                        0
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
        kotlin.test.assertEquals(expected, actual)
    }
}