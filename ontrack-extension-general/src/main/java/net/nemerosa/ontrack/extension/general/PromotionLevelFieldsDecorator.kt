package net.nemerosa.ontrack.extension.general

import net.nemerosa.ontrack.extension.api.DecorationExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.Decoration
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component
import java.util.EnumSet

@Component
class PromotionLevelFieldsDecorator(
    extensionFeature: GeneralExtensionFeature,
    private val structureService: StructureService,
) : AbstractExtension(extensionFeature), DecorationExtension<Boolean> {

    override fun getScope(): EnumSet<ProjectEntityType> =
        EnumSet.of(ProjectEntityType.PROMOTION_LEVEL)

    override fun getDecorations(entity: ProjectEntity): List<Decoration<Boolean>> {
        if (entity !is PromotionLevel) return emptyList()
        val fields = structureService.getPromotionLevel(entity.id).fields
        return if (fields.isNotEmpty()) {
            listOf(Decoration.of(this, true))
        } else {
            emptyList()
        }
    }
}
