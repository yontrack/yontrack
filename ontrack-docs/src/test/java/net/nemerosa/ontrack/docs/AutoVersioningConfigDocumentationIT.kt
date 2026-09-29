package net.nemerosa.ontrack.docs

import net.nemerosa.ontrack.extension.av.config.AutoVersioningSourceConfig
import net.nemerosa.ontrack.model.docs.getFieldsDocumentation
import org.junit.jupiter.api.Test

class AutoVersioningConfigDocumentationIT : AbstractDocGenIT() {

    @Test
    fun `Auto-versioning config`() {
        docGenSupport.inDirectory("snippets/auto-versioning") {
            writeFile(
                fileName = "config",
            ) { s ->
                val fields = getFieldsDocumentation(
                    AutoVersioningSourceConfig::class,
                )
                s.writeFields(fields)
            }
        }
    }

}