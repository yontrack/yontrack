package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.api.DecorationExtension
import net.nemerosa.ontrack.extension.environments.EnvironmentsExtensionFeature
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Decoration
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component
import java.util.*

@Component
class BuildEnvironmentsDecorations(
    extensionFeature: EnvironmentsExtensionFeature,
    private val slotService: SlotService,
) : AbstractExtension(extensionFeature), DecorationExtension<List<BuildEnvironmentsDecorationsData>> {

    override fun getDecorations(entity: ProjectEntity): List<Decoration<List<BuildEnvironmentsDecorationsData>>> =
        if (entity is Build) {
            val pipelines = slotService.findHighestDeployedSlotPipelinesByBuildAndQualifier(entity)
            if (pipelines.isNotEmpty()) {
                listOf(
                    Decoration.of(
                        this,
                        pipelines.map {
                            BuildEnvironmentsDecorationsData(
                                environmentId = it.slot.environment.id,
                                environmentName = it.slot.environment.name,
                                environmentOrder = it.slot.environment.order,
                                environmentImage = it.slot.environment.image,
                                slotId = it.slot.id,
                                qualifier = it.slot.qualifier,
                                pipelineId = it.id,
                            )
                        }
                    )
                )
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

    override fun getScope(): EnumSet<ProjectEntityType> = EnumSet.of(ProjectEntityType.BUILD)
}