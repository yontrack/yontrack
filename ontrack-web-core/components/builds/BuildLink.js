import {buildUri} from "@components/common/Links";
import {buildKnownName} from "@components/common/Titles";
import Link from "next/link";
import {Popover, Space, Typography} from "antd";

export default function BuildLink({build, buildNameOnly, text, displayTooltip, tooltipText = "Link to the build"}) {
    return (
        <>
            {
                displayTooltip && <Popover content={
                    <Space orientation="vertical">
                        <Typography.Text>{tooltipText}</Typography.Text>
                        <Typography.Text>
                            {build.name}
                            {
                                build.releaseProperty?.value &&
                                <Typography.Text strong> ({build.releaseProperty.value.name})</Typography.Text>
                            }
                        </Typography.Text>
                    </Space>
                }>
                    <Link
                        href={buildUri(build)}
                    >{text ?? buildKnownName(build, buildNameOnly)}</Link>
                </Popover>
            }
            {
                !displayTooltip && <Link
                    href={buildUri(build)}
                    title={tooltipText}
                >{text ?? buildKnownName(build, buildNameOnly)}</Link>
            }
        </>
    )
}