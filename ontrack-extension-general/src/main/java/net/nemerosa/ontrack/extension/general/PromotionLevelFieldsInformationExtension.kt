package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.api.EntityInformationExtension
import net.nemerosa.ontrack.extension.api.model.EntityInformation
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component

@Component
class PromotionLevelFieldsInformationExtension(
    extensionFeature: GeneralExtensionFeature,
) : AbstractExtension(extensionFeature), EntityInformationExtension {

    override val title: String = "Fields"

    override fun getInformation(entity: ProjectEntity): EntityInformation? =
        if (entity is PromotionLevel && entity.fields.isNotEmpty()) {
            EntityInformation(this, entity.fields)
        } else {
            null
        }
}
