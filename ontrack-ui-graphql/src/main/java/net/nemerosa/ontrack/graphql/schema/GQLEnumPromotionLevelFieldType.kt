package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.model.structure.PromotionLevelFieldType
import org.springframework.stereotype.Component

@Component
class GQLEnumPromotionLevelFieldType : AbstractGQLEnum<PromotionLevelFieldType>(
    PromotionLevelFieldType::class,
    PromotionLevelFieldType.entries.toTypedArray(),
    "Type of a configurable field on a promotion level",
)
