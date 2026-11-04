package net.nemerosa.ontrack.extension.config.model

import com.fasterxml.jackson.annotation.JsonIgnore
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.mergeList
import net.nemerosa.ontrack.extension.config.extensions.CIConfigExtensionService
import net.nemerosa.ontrack.model.structure.PromotionLevelConfiguration

@APIDescription("Branch configuration")
data class BranchConfiguration(
    override val properties: List<PropertyConfiguration> = emptyList(),
    override val extensions: List<ExtensionConfiguration> = emptyList(),
    val validations: List<ValidationStampConfiguration> = emptyList(),
    val promotions: List<PromotionLevelConfiguration> = emptyList(),
) : PropertiesConfiguration, ExtensionsConfiguration {
    @JsonIgnore
    fun isNotEmpty(): Boolean =
        properties.isNotEmpty() || validations.isNotEmpty() || promotions.isNotEmpty() || extensions.isNotEmpty()

    fun merge(
        branch: BranchConfiguration,
        ciConfigExtensionService: CIConfigExtensionService,
    ) = BranchConfiguration(
        properties = mergeProperties(this.properties, branch.properties),
        validations = mergeList(this.validations, branch.validations, { it.name }, { a, b -> a.merge(b) }),
        promotions = mergeList(this.promotions, branch.promotions, { it.name }, { a, b -> a.merge(b) }),
        extensions = ciConfigExtensionService.merge(this.extensions, branch.extensions),
    )

}
