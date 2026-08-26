import {Space, Typography} from "antd";
import ValidationStamp from "@components/validationStamps/ValidationStamp";
import PromotionLevel from "@components/promotionLevels/PromotionLevel";

export default function Display({property}) {
    return (
        <>
            <Space direction="vertical">
                {
                    property.value.validationStamps.length > 0 &&
                    <>
                        <Typography.Text strong>Validations</Typography.Text>
                        <Space direction="vertical">
                            {
                                property.value.validationStamps.map(vs => (
                                    <>
                                        <ValidationStamp
                                            key={vs.id}
                                            validationStamp={vs}
                                        />
                                    </>
                                ))
                            }
                        </Space>
                    </>
                }
                {
                    property.value.promotionLevels.length > 0 &&
                    <>
                        <Typography.Text strong>Promotions</Typography.Text>
                        <Space direction="vertical">
                            {
                                property.value.promotionLevels.map(pl => (
                                    <>
                                        <PromotionLevel
                                            key={pl.id}
                                            promotionLevel={pl}
                                            displayText={true}
                                        />
                                    </>
                                ))
                            }
                        </Space>
                    </>
                }
                {
                    property.value.autoRevoke &&
                    <Typography.Text type="secondary">
                        Revoked when a prerequisite is no longer valid
                    </Typography.Text>
                }
            </Space>
        </>
    )
}