import {Space, Tag, Typography} from "antd";

/**
 * The version is the build's release label when it has one, its name otherwise.
 */
export default function VersionPatternAdmissionRuleSummary({includes, excludes}) {
    const hasIncludes = includes && includes.length > 0
    const hasExcludes = excludes && excludes.length > 0
    return (
        <>
            <Space wrap>
                <Typography.Text>Version</Typography.Text>
                {
                    !hasIncludes && !hasExcludes &&
                    <Typography.Text type="secondary">any</Typography.Text>
                }
                {
                    hasIncludes && <>
                        <Typography.Text>includes</Typography.Text>
                        {
                            includes.map((regex, index) => (
                                <Tag key={`include-${index}`}>{regex}</Tag>
                            ))
                        }
                    </>
                }
                {
                    hasExcludes && <>
                        <Typography.Text>excludes</Typography.Text>
                        {
                            excludes.map((regex, index) => (
                                <Tag key={`exclude-${index}`}>{regex}</Tag>
                            ))
                        }
                    </>
                }
            </Space>
        </>
    )
}
