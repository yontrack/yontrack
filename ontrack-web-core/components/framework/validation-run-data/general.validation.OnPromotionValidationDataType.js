import {Space, Typography} from "antd";
import Link from "next/link";
import {buildUri, promotionRunUri} from "@components/common/Links";
import {FaArrowRight} from "react-icons/fa";

export default function OnPromotionValidationDataType({buildId, project, build, promotion, promotionRunId}) {
    return (
        <Space data-testid="on-promotion-run-data">
            <Link href={buildUri({id: buildId})} title="Linked build">
                <Typography.Text strong>{project}</Typography.Text> {build}
            </Link>
            <FaArrowRight/>
            <Link href={promotionRunUri({id: promotionRunId})} title="Promotion of the linked build">
                {promotion}
            </Link>
        </Space>
    )
}
