package net.nemerosa.ontrack.kdsl.spec.extension.general

import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.ValidationStamp

/**
 * FQCN of the validation on promotion data type, the `on-promotion` stamps.
 */
const val ON_PROMOTION_VALIDATION_DATA_TYPE = "net.nemerosa.ontrack.extension.general.validation.OnPromotionValidationDataType"

/**
 * Creates a validation stamp of the validation on promotion data type: a build of this branch is validated
 * as soon as the build it links to, in [project] and with [qualifier], reaches the [promotion].
 *
 * @param name Name of the validation stamp
 * @param description Description of the validation stamp
 * @param project Name of the project of the linked build
 * @param qualifier Qualifier of the build link, empty for the default link
 * @param promotion Name of the promotion level the linked build must reach
 * @return Created validation stamp
 */
fun Branch.createOnPromotionValidationStamp(
    name: String,
    description: String = "",
    project: String,
    qualifier: String = "",
    promotion: String,
): ValidationStamp = createValidationStamp(
    name = name,
    description = description,
    dataType = ON_PROMOTION_VALIDATION_DATA_TYPE,
    dataTypeConfig = mapOf(
        "project" to project,
        "qualifier" to qualifier,
        "promotion" to promotion,
    ),
)
