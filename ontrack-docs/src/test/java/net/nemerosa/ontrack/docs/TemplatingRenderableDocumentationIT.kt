package net.nemerosa.ontrack.docs

import net.nemerosa.ontrack.model.annotations.getAPITypeDescription
import net.nemerosa.ontrack.model.docs.getDocumentationExampleCode
import net.nemerosa.ontrack.model.docs.getFieldsDocumentation
import net.nemerosa.ontrack.model.templating.TemplatingRenderableDoc
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class TemplatingRenderableDocumentationIT : AbstractDocGenIT() {

    @Autowired
    private lateinit var templatingRenderableDocs: List<TemplatingRenderableDoc>

    @Test
    fun `Templating renderables`() {

        fun getTRDFileId(trd: TemplatingRenderableDoc): String =
            "templating-renderable-${trd.id}"

        fun getTRDTitle(trd: TemplatingRenderableDoc): String =
            "${trd.displayName} (${trd.id})"

        fun generateTRD(directoryContext: DocGenDirectoryContext, trd: TemplatingRenderableDoc) {
            val description = getAPITypeDescription(trd::class)
            val example = getDocumentationExampleCode(trd::class)

            val fileId = getTRDFileId(trd)

            directoryContext.writeFile(
                fileId = fileId,
                title = getTRDTitle(trd),
                header = description,
                fields = emptyList(),
                example = example,
                links = emptyList(),
                linksPrefix = "../../../",
                extendedConfig = { s ->
                    // Context
                    s.append("Context: ${trd.contextName}\n\n")
                    // Fields
                    s.h2("Fields")
                    trd.fields.forEach { field ->
                        s.append("* `${field.name}`: ${field.description}\n\n")
                        field.config?.let {
                            val list = getFieldsDocumentation(it)
                            s.writeFields(list, level = 2)
                        }
                    }
                },
            )
        }


        docGenSupport.inDirectory("templating/renderables") {

            writeFile(
                fileName = "index",
            ) { s ->
                s.title("List of special templating objects")
                for (trd in templatingRenderableDocs.sortedBy { getTRDTitle(it) }) {
                    s.tocItem(getTRDTitle(trd), fileName = "${getTRDFileId(trd)}.md")
                }
            }

            templatingRenderableDocs.forEach { trd ->
                generateTRD(this, trd)
            }

        }
    }

}