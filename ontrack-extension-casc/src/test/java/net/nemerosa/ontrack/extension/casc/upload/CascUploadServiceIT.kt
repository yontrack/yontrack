package net.nemerosa.ontrack.extension.casc.upload

import tools.jackson.databind.node.StringNode
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CascUploadServiceIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var cascUploadService: CascUploadService

    @Autowired
    private lateinit var storageService: StorageService

    @Test
    fun `Checking the storage after upload`() {
        asAdmin {
            cascUploadService.upload(sampleYaml)
            assertNotNull(
                storageService.findJson(
                    "net.nemerosa.ontrack.extension.casc.upload.CascUploadServiceImpl",
                    "default"
                )
            ) { stored ->
                assertIs<StringNode>(stored) { text ->
                    assertEquals(sampleYaml, text.asText())
                }
            }
        }
    }

    @Test
    fun `Uploading requires Global Settings rights`() {
        asUser {
            assertFailsWith<AccessDeniedException> {
                cascUploadService.upload(sampleYaml)
            }
        }
    }

    companion object {
        private val sampleYaml = """
            ontrack:
              config:
                settings:
                  security:
                    grantProjectViewToAll: true
                    grantProjectParticipationToAll: true
        """.trimIndent()
    }

}