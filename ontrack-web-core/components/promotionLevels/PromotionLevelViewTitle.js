import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import {Space} from "antd";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import Decorations from "@components/framework/decorations/Decorations";

export default function PromotionLevelViewTitle({promotionLevel, link = false}) {
    return (
        <>
            {
                link && <PromotionLevelLink promotionLevel={promotionLevel}/>
            }
            {
                !link &&
                <Space>
                    <PromotionLevelImage promotionLevel={promotionLevel}/>
                    {promotionLevel.name}
                    <Decorations entity={promotionLevel}/>
                </Space>
            }
        </>
    )
}