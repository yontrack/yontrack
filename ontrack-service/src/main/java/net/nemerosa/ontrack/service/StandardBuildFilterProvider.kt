package net.nemerosa.ontrack.service

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.getDateField
import net.nemerosa.ontrack.json.getIntField
import net.nemerosa.ontrack.json.getTextField
import net.nemerosa.ontrack.model.exceptions.PropertyTypeNotFoundException
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.repository.CoreBuildFilterInvalidException
import net.nemerosa.ontrack.repository.CoreBuildFilterRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

@Component
@Transactional
class StandardBuildFilterProvider(
    private val structureService: StructureService,
    private val propertyService: PropertyService,
    private val coreBuildFilterRepository: CoreBuildFilterRepository,
    private val buildDisplayNameService: BuildDisplayNameService,
) : AbstractBuildFilterProvider<StandardBuildFilterData>() {

    override val type: String = StandardBuildFilterProvider::class.java.name

    override val name: String = "Standard filter"

    override val isPredefined: Boolean = false

    override fun filterBranchBuilds(branch: Branch, data: StandardBuildFilterData?): List<Build> =
        try {
            coreBuildFilterRepository.standardFilter(
                branch,
                data ?: StandardBuildFilterData.of(10),
                buildDisplayNameService.displayNameProperty,
            ) { type -> propertyService.getPropertyTypeByName<Any>(type) }
        } catch (_: CoreBuildFilterInvalidException) {
            emptyList()
        }

    override fun filterBranchBuildsWithPagination(
        branch: Branch,
        data: StandardBuildFilterData?,
        offset: Int,
        size: Int,
    ): PaginatedList<Build> = try {
        coreBuildFilterRepository.standardFilterPagination(
            branch,
            data ?: StandardBuildFilterData.of(size),
            offset,
            size,
            buildDisplayNameService.displayNameProperty,
        ) { type -> propertyService.getPropertyTypeByName<Any>(type) }
    } catch (_: CoreBuildFilterInvalidException) {
        PaginatedList.empty()
    }

    override fun parse(data: JsonNode): StandardBuildFilterData? =
        StandardBuildFilterData.of(data.getIntField("count") ?: 10)
            .withSincePromotionLevel(data.getTextField("sincePromotionLevel"))
            .withWithPromotionLevel(data.getTextField("withPromotionLevel"))
            .withAfterDate(data.getDateField("afterDate"))
            .withBeforeDate(data.getDateField("beforeDate"))
            .withSinceValidationStamp(data.getTextField("sinceValidationStamp"))
            .withSinceValidationStampStatus(data.getTextField("sinceValidationStampStatus"))
            .withWithValidationStamp(data.getTextField("withValidationStamp"))
            .withWithValidationStampStatus(data.getTextField("withValidationStampStatus"))
            .withSinceProperty(data.getTextField("sinceProperty"))
            .withSincePropertyValue(data.getTextField("sincePropertyValue"))
            .withWithProperty(data.getTextField("withProperty"))
            .withWithPropertyValue(data.getTextField("withPropertyValue"))
            .withLinkedFrom(data.getTextField("linkedFrom"))
            .withLinkedFromPromotion(data.getTextField("linkedFromPromotion"))
            .withLinkedTo(data.getTextField("linkedTo"))
            .withLinkedToPromotion(data.getTextField("linkedToPromotion"))
            .withWithDisplayName(data.getTextField("withDisplayName"))

    override fun validateData(branch: Branch, data: StandardBuildFilterData?): String? =
        if (data != null) {
            // Since promotion
            validatePromotion(branch, data.sincePromotionLevel, "Since promotion")
            // With promotion
                ?: validatePromotion(branch, data.withPromotionLevel, "With promotion")
                // Since validation
                ?: validateValidation(branch, data.sinceValidationStamp, "Since validation")
                // With validation
                ?: validateValidation(branch, data.withValidationStamp, "With validation")
                // Since property
                ?: validateProperty(data.sinceProperty, "Since property")
                // With property
                ?: validateProperty(data.withProperty, "With property")
                // With display name
                ?: validateRegex(data.withDisplayName, "With display name")
        } else {
            null
        }

    /**
     * Checks that [regex] can be compiled as a regular expression.
     *
     * Note that this cannot catch every pattern rejected by the database, since the
     * filtering is ultimately performed by Postgres, whose regular expression dialect
     * differs from the JVM one. The repository layer is the safety net for those.
     */
    private fun validateRegex(regex: String?, field: String): String? =
        if (regex.isNullOrBlank()) {
            null
        } else {
            try {
                Pattern.compile(regex)
                null
            } catch (ex: PatternSyntaxException) {
                """Invalid regular expression for filter "$field": ${ex.description}."""
            }
        }

    private fun validateProperty(property: String?, field: String): String? {
        return property
            ?.let {
                try {
                    propertyService.getPropertyTypeByName<Any>(property)
                    null
                } catch (_: PropertyTypeNotFoundException) {
                    """Property "$property" does not exist for filter "$field"."""
                }
            }
    }

    private fun validateValidation(branch: Branch, validationStamp: String?, field: String): String? {
        return validationStamp
            ?.let {
                if (structureService.findValidationStampByName(
                        branch.project.name,
                        branch.name,
                        it
                    ).isPresent
                ) {
                    null
                } else {
                    """Validation stamp $validationStamp does not exist for filter "$field"."""
                }
            }
    }

    private fun validatePromotion(branch: Branch, promotionLevel: String?, field: String): String? {
        return promotionLevel
            ?.let {
                if (structureService.findPromotionLevelByName(
                        branch.project.name,
                        branch.name,
                        it
                    ).isPresent
                ) {
                    null
                } else {
                    """Promotion level $promotionLevel does not exist for filter "$field"."""
                }
            }
    }

}
