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
}
