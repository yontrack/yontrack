package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.buildfilter.StandardFilterDataBuilder
import java.time.LocalDate

data class StandardBuildFilterData(
    val count: Int,
    val sincePromotionLevel: String? = null,
    val withPromotionLevel: String? = null,
    val afterDate: LocalDate? = null,
    val beforeDate: LocalDate? = null,
    val sinceValidationStamp: String? = null,
    val sinceValidationStampStatus: String? = null,
    val withValidationStamp: String? = null,
    val withValidationStampStatus: String? = null,
    val withProperty: String? = null,
    val withPropertyValue: String? = null,
    val sinceProperty: String? = null,
    val sincePropertyValue: String? = null,
    val linkedFrom: String? = null,
    val linkedFromPromotion: String? = null,
    val linkedTo: String? = null,
    val linkedToPromotion: String? = null,
    val withDisplayName: String? = null,
) : StandardFilterDataBuilder<StandardBuildFilterData> {

    override fun withSincePromotionLevel(sincePromotionLevel: String?) =
        copy(sincePromotionLevel = sincePromotionLevel)

    override fun withWithPromotionLevel(withPromotionLevel: String?) =
        copy(withPromotionLevel = withPromotionLevel)

    override fun withAfterDate(afterDate: LocalDate?) =
        copy(afterDate = afterDate)

    override fun withBeforeDate(beforeDate: LocalDate?) =
        copy(beforeDate = beforeDate)

    override fun withSinceValidationStamp(sinceValidationStamp: String?) =
        copy(sinceValidationStamp = sinceValidationStamp)

    override fun withSinceValidationStampStatus(sinceValidationStampStatus: String?) =
        copy(sinceValidationStampStatus = sinceValidationStampStatus)

    override fun withWithValidationStamp(withValidationStamp: String?) =
        copy(withValidationStamp = withValidationStamp)

    override fun withWithValidationStampStatus(withValidationStampStatus: String?) =
        copy(withValidationStampStatus = withValidationStampStatus)

    override fun withWithProperty(withProperty: String?) =
        copy(withProperty = withProperty)

    override fun withWithPropertyValue(withPropertyValue: String?) =
        copy(withPropertyValue = withPropertyValue)

    override fun withSinceProperty(sinceProperty: String?) =
        copy(sinceProperty = sinceProperty)

    override fun withSincePropertyValue(sincePropertyValue: String?) =
        copy(sincePropertyValue = sincePropertyValue)

    override fun withLinkedFrom(linkedFrom: String?) =
        copy(linkedFrom = linkedFrom)

    override fun withLinkedFromPromotion(linkedFromPromotion: String?) =
        copy(linkedFromPromotion = linkedFromPromotion)

    override fun withLinkedTo(linkedTo: String?) =
        copy(linkedTo = linkedTo)

    override fun withLinkedToPromotion(linkedToPromotion: String?) =
        copy(linkedToPromotion = linkedToPromotion)

    override fun withWithDisplayName(withDisplayName: String?) =
        copy(withDisplayName = withDisplayName)

    companion object {
        @JvmStatic
        fun of(count: Int) = StandardBuildFilterData(count)
    }
}
