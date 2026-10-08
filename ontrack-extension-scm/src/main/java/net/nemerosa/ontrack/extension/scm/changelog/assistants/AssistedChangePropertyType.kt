package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractPropertyType
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.exceptions.PropertyValidationException
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.BuildCreate
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/**
 * Assisted change of a build, computed by Yontrack from the change log since the previous build on the
 * branch (see [AssistedChangeService]), or set by the CI.
 *
 * The first time it is written with at least one assistant, whoever writes it, the
 * [build_assisted][EventFactory.BUILD_ASSISTED] event is posted. It is never posted again for the build.
 *
 * Never rename this class: its FQCN is the storage ID of the property.
 */
@Component
class AssistedChangePropertyType(
    extensionFeature: SCMExtensionFeature,
    @Lazy private val eventPostService: EventPostService,
    @Lazy private val eventFactory: EventFactory,
    @Lazy private val eventQueryService: EventQueryService,
) : AbstractPropertyType<AssistedChangeProperty>(extensionFeature) {

    override val name: String = "Assisted change"

    override val description: String =
        "Whether the commits of the build since the previous build on its branch were written with assistants (agent kinds): computed by Yontrack from the change log, or set by the CI."

    override val supportedEntityTypes: Set<ProjectEntityType> = setOf(ProjectEntityType.BUILD)

    override fun createConfigJsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType =
        jsonTypeBuilder.toType(AssistedChangeProperty::class)

    /**
     * Set by the CI, like the commit of the build.
     */
    override fun canEdit(entity: ProjectEntity, securityService: SecurityService): Boolean =
        securityService.isProjectFunctionGranted(entity, BuildCreate::class.java)

    override fun canView(entity: ProjectEntity, securityService: SecurityService): Boolean = true

    override fun fromClient(node: JsonNode): AssistedChangeProperty {
        val value = try {
            node.parse<AssistedChangeProperty>()
        } catch (any: Exception) {
            throw PropertyValidationException("Cannot parse the assisted change: ${any.message}")
        }
        return AssistedChangeProperty.validated(value)
    }

    override fun fromStorage(node: JsonNode): AssistedChangeProperty = node.parse()

    override fun replaceValue(
        value: AssistedChangeProperty,
        replacementFunction: (String) -> String,
    ): AssistedChangeProperty = value

    /**
     * Posts the `build_assisted` event the first time the build is found assisted.
     */
    override fun onPropertyChanged(entity: ProjectEntity, value: AssistedChangeProperty) {
        if (entity is Build && value.assisted) {
            val alreadyPosted = eventQueryService.getLastEventSignature(
                ProjectEntityType.BUILD,
                entity.id,
                EventFactory.BUILD_ASSISTED,
            ) != null
            if (!alreadyPosted) {
                eventPostService.post(
                    eventFactory.buildAssisted(
                        build = entity,
                        assistants = value.assistants,
                        assistedCommits = value.assistedCommits,
                        totalCommits = value.totalCommits,
                        sessionLinks = value.sessionLinks,
                    )
                )
            }
        }
    }
}
