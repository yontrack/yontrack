package net.nemerosa.ontrack.model.buildfilter;

import java.time.LocalDate;

public interface StandardFilterDataBuilder<T extends StandardFilterDataBuilder<T>> {

    T withSincePromotionLevel(String sincePromotionLevel);

    T withWithPromotionLevel(String withPromotionLevel);

    T withAfterDate(LocalDate afterDate);

    T withBeforeDate(LocalDate beforeDate);

    T withSinceValidationStamp(String sinceValidationStamp);

    T withSinceValidationStampStatus(String sinceValidationStampStatus);

    T withWithValidationStamp(String withValidationStamp);

    T withWithValidationStampStatus(String withValidationStampStatus);

    T withWithProperty(String withProperty);

    T withWithPropertyValue(String withPropertyValue);

    T withSinceProperty(String sinceProperty);

    T withSincePropertyValue(String sincePropertyValue);

    T withLinkedFrom(String linkedFrom);

    T withLinkedFromPromotion(String linkedFromPromotion);

    T withLinkedTo(String linkedTo);

    T withLinkedToPromotion(String linkedToPromotion);

    T withWithDisplayName(String withDisplayName);

    /**
     * Whether the build was assisted: {@code YES}, {@code NO} or {@code UNKNOWN} (#2036).
     */
    T withAssisted(String assisted);

    /**
     * Actor who created the build: {@code HUMAN}, {@code AGENT} or the identifier of one agent,
     * {@code <slug>[agent]} (#2036).
     */
    T withActor(String actor);
}
