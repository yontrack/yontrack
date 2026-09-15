package net.nemerosa.ontrack.extension.bitbucket.cloud.property

import net.nemerosa.ontrack.extension.api.DecorationExtension
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.Decoration
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component
import java.util.*

@Component
class BitbucketCloudProjectDecorator(
    extensionFeature: BitbucketCloudExtensionFeature,
    private val propertyService: PropertyService,
) : AbstractExtension(extensionFeature), DecorationExtension<String> {

    override fun getScope(): EnumSet<ProjectEntityType> = EnumSet.of(ProjectEntityType.PROJECT)

    override fun getDecorations(entity: ProjectEntity): List<Decoration<String>> =
        propertyService.getProperty(entity, BitbucketCloudProjectConfigurationPropertyType::class.java).value
            ?.let { property ->
                listOf(
                    Decoration.of(
                        this,
                        property.fullName
                    )
                )
            }
            ?: emptyList()

}
