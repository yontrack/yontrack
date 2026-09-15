package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.exceptions.PropertyUnsupportedEntityTypeException
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@AsAdminTest
class BitbucketCloudProjectDecoratorIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var decorator: BitbucketCloudProjectDecorator

    @Test
    fun `Project scope`() {
        assertEquals(
            EnumSet.of(ProjectEntityType.PROJECT),
            decorator.getScope()
        )
    }

    @Test
    fun `Not a projet`() {
        project {
            branch {
                assertFailsWith<PropertyUnsupportedEntityTypeException> {
                    decorator.getDecorations(this)
                }
            }
        }
    }

    @Test
    fun `No decoration when no Bitbucket Cloud configuration`() {
        project {
            val list = decorator.getDecorations(this)
            assertTrue(list.isEmpty(), "No Bitbucket Cloud decoration on a project which is not configured")
        }
    }

    @Test
    fun `Decoration when Bitbucket Cloud configuration`() {
        asAdmin {
            withDisabledConfigurationTest {
                project {
                    val config = bitbucketCloudTestConfigMock()
                    bitbucketCloudConfigurationService.newConfiguration(config)
                    setBitbucketCloudProperty(config, "my-repository", workspace = "my-workspace")
                    val list = decorator.getDecorations(this)
                    assertEquals(1, list.size)
                    val decoration = list.first()
                    assertEquals(
                        "my-workspace/my-repository",
                        decoration.data
                    )
                }
            }
        }
    }
}