import {Popover, Space, Typography} from "antd";
import TimestampText from "@components/common/TimestampText";
import CheckStatus from "@components/common/CheckStatus";

export default function GitHubConfigAppToken({appToken}) {
    return (
        <>
            <Popover content={
                <Space orientation="vertical">
                    <Typography.Paragraph>Created at <TimestampText value={appToken.createdAt}/></Typography.Paragraph>
                    <Typography.Paragraph>Valid until <TimestampText
                        value={appToken.validUntil}/></Typography.Paragraph>
                </Space>
            }>
                <div>
                    <CheckStatus
                        value={appToken.valid}
                        text="Valid"
                        noText="Invalid"
                    />
                </div>
            </Popover>
        </>
    )
}