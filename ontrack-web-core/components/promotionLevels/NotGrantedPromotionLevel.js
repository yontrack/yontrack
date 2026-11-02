import {Space, Tag} from "antd";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import PromotionLevel from "@components/promotionLevels/PromotionLevel";
import {BuildAutoPromotionConditions} from "@components/promotionLevels/AutoPromotionConditions";

/**
 * Title of the popover of a promotion level which a build does not have.
 */
export function NotGrantedPromotionLevelTitle({promotionLevel}) {
    return (
        <Space data-testid={`promotion-level-not-granted-title-${promotionLevel.id}`}>
            <PromotionLevel
                promotionLevel={promotionLevel}
                displayText={true}
                displayTooltip={false}
            />
            <Tag>Not granted</Tag>
        </Space>
    )
}

/**
 * Content of the popover of a promotion level which a build does not have: the description of the
 * level and its auto promotion conditions, with their state for the build.
 *
 * The conditions are queried when this is mounted, which a popover only does on its first opening.
 */
export function NotGrantedPromotionLevelContent({buildId, promotionLevel, testId}) {
    return (
        <div data-testid={testId}>
            <Space orientation="vertical">
                <AnnotatedDescription entity={promotionLevel}/>
                <BuildAutoPromotionConditions
                    buildId={buildId}
                    promotionLevelId={promotionLevel.id}
                />
            </Space>
        </div>
    )
}
