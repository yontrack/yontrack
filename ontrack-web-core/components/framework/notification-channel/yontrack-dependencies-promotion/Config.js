import {Space, Typography} from "antd";
import {FaArrowRight} from "react-icons/fa";
import YesNo from "@components/common/YesNo";

export default function YontrackDependenciesPromotionNotificationChannelConfig({
                                                                                   project,
                                                                                   qualifier,
                                                                                   promotion,
                                                                                   fields = [],
                                                                                   waitForPromotion,
                                                                                   waitForPromotionTimeout,
                                                                               }) {
    return (
        <Space orientation="vertical">
            <Space>
                Project:
                {
                    project ?
                        <Typography.Text code>{project}</Typography.Text> :
                        <Typography.Text type="secondary">All projects</Typography.Text>
                }
            </Space>
            <Space>
                Qualifier:
                {
                    qualifier === undefined || qualifier === null ?
                        <Typography.Text type="secondary">All links</Typography.Text> :
                        qualifier === '' ?
                            <Typography.Text type="secondary">Unqualified links only</Typography.Text> :
                            <Typography.Text code>{qualifier}</Typography.Text>
                }
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
    )
}
