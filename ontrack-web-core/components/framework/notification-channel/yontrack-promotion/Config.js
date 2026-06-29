import {Space, Typography} from "antd";
import {FaArrowRight} from "react-icons/fa";
import YesNo from "@components/common/YesNo";

export default function OntrackValidationNotificationChannelConfig({project, branch, build, promotion, fields = [], waitForPromotion, waitForPromotionTimeout}) {
    return (
        <>
            <Space direction="vertical">
                <Space>
                    Project:
                    <Typography.Text code>{project}</Typography.Text>
                </Space>
                <Space>
                    Branch:
                    <Typography.Text code>{branch}</Typography.Text>
                </Space>
                <Space>
                    Build:
                    <Typography.Text code>{build}</Typography.Text>
                </Space>
                <Space>
                    Promotion:
                    <Typography.Text code>{promotion}</Typography.Text>
                </Space>
                {
                    fields && fields.length > 0 &&
                    <>
                        <Typography.Text>Fields:</Typography.Text>
                        <ul>
                            {fields.map(({name, value}) => (
                                <li key={name}>
                                    <Space>
                                        <Typography.Text code>{name}</Typography.Text>
                                        <FaArrowRight/>
                                        <Typography.Text code>{value}</Typography.Text>
                                    </Space>
                                </li>
                            ))}
                        </ul>
                    </>
                }
                <Space>
                    Waiting:
                    <YesNo value={waitForPromotion}/>
                </Space>
                <Space>
                    Waiting timeout:
                    <Typography.Text code>{waitForPromotionTimeout}</Typography.Text>
                </Space>
            </Space>
        </>
    )
}