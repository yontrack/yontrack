import AutoPromotionConditions, {
    PromotionLevelAutoPromotionConditions
} from "@components/promotionLevels/AutoPromotionConditions";

export default function Display({property, entityType, entityId}) {
    // The effective conditions are resolved on the branch - without the promotion level,
    // falls back on the raw property, which only knows the explicitly named stamps
    return entityType === 'PROMOTION_LEVEL' && entityId ?
        // Keyed on the value so that editing the property reloads the conditions
        <PromotionLevelAutoPromotionConditions key={JSON.stringify(property.value)} promotionLevelId={entityId}/> :
        <AutoPromotionConditions conditions={property.value}/>
}
