package net.nemerosa.ontrack.docs

import net.nemerosa.ontrack.common.doc.MetricsDocumentation
import net.nemerosa.ontrack.common.doc.MetricsMeterDocumentation
import net.nemerosa.ontrack.model.annotations.getAPITypeDescription
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.jvmName

class MetricsDocumentationIT : AbstractDocGenIT() {

    @Test
    fun `Metrics documentation`() {
        val metrics = findAllBeansAnnotatedWith(MetricsDocumentation::class)

        docGenSupport.inDirectory("metrics") {

            writeFile(
                fileName = "index",
            ) { s ->
                s.title("List of exported metrics by Yontrack.")
                for (metricObject in metrics.sortedBy { getAPITypeName(it::class) }) {
                    val id = metricObject::class.jvmName
                    val name = getAPITypeName(metricObject::class)
                    s.tocItem(name, fileName = "${id}.md")
                }
            }

            metrics.forEach { metricObject ->
                generateMetricsDoc(this, metricObject)
            }

        }
    }

    private fun generateMetricsDoc(directoryContext: DocGenDirectoryContext, metricObject: Any) {
        val id = metricObject::class.jvmName
        val name = getAPITypeName(metricObject::class)
        val description = getAPITypeDescription(metricObject::class)

        directoryContext.writeFile(
            fileId = id,
            title = name,
        ) { s ->

            if (!description.isNullOrBlank()) {
                s.paragraph(description.trimIndent())
            }

            val metrics = getObjectFields<MetricsMeterDocumentation>(metricObject)

            for (metric in metrics) {
                s.paragraph("`${metric.value}`")
                metric.description?.let {
                    s.definition(it)
                }
                metric.annotation?.let {
                    s.definition("Type: `${it.type.type}`")
                    if (it.tags.isNotEmpty()) {
                        s.definition("Tags:")
                        it.tags.forEach { tag ->
                            s.definition("* `${tag.name}` - ${tag.description}")
                        }
                    }
                    if (it.fields.isNotEmpty()) {
                        s.definition("Fields:")
                        it.fields.forEach { field ->
                            s.definition("* `${field.name}` - ${field.description}")
                        }
                    }
                }
            }

        }
    }

}