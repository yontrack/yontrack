import {Popover, Tooltip, Typography} from "antd";
import {FaBolt} from "react-icons/fa";
import {PromotionLevelAutoPromotionConditions} from "@components/promotionLevels/AutoPromotionConditions";

export default function AutoPromotionPropertyDecorator({entity}) {
    const icon = <Typography.Text data-testid="auto-promotion-decoration">
        <FaBolt color="#FC0"/>
    </Typography.Text>
    // The conditions are loaded from the promotion level when the popover opens
    return entity?.id ?
        <Popover
            title="Auto promotion"
            content={<PromotionLevelAutoPromotionConditions promotionLevelId={entity.id}/>}
        >
            {icon}
        </Popover> :
        <Tooltip title="Auto promotion enabled">
            {icon}
        </Tooltip>
}
