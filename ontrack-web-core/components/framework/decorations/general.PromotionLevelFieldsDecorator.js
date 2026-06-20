import {Tooltip, Typography} from "antd";
import {FaClipboardList} from "react-icons/fa";

export default function PromotionLevelFieldsDecorator({decoration}) {
    return (
        <Tooltip title="This promotion level has fields">
            <Typography.Text>
                <FaClipboardList/>
            </Typography.Text>
        </Tooltip>
    )
}
