package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.model.structure.ProjectEntity
import org.springframework.stereotype.Component

@Component
class PlainEventRenderer : AbstractEventRenderer() {

    override val id: String = "text"
    override val name: String = "Text"

    override fun render(projectEntity: ProjectEntity, name: String): String = name

    override fun renderStrong(value: String): String = value

    override fun renderLink(text: String, href: String): String = text

    override fun renderList(list: List<String>): String =
        list.joinToString("\n") { "* $it" }

    override fun renderSpace(): String = "\n\n"

    override fun renderSection(title: String, content: String): String =
        """
            |$title
            |
            |$content
        """.trimMargin()

    companion object {
        @JvmField
        val INSTANCE: EventRenderer = PlainEventRenderer()
    }
}