import {Space, Tag, Typography} from "antd";

export default function VersionPatternAdmissionRuleCheck({ruleConfig}) {
    const includes = ruleConfig.includes ?? []
    const excludes = ruleConfig.excludes ?? []
    return (
        <>
            <Typography.Text>Build version (release label, or build name) must be in:</Typography.Text>
            <ul>
                <li>
                    <Space wrap>
                        Inclusions:
                        {
                            includes.length > 0 ?
                                includes.map((regex, index) => <Tag key={`include-${index}`}>{regex}</Tag>) :
                                <Typography.Text type="secondary">any version</Typography.Text>
                        }
                    </Space>
                </li>
                {
                    excludes.length > 0 &&
                    <li>
                        <Space wrap>
                            Exclusions:
                            {
                                excludes.map((regex, index) => <Tag key={`exclude-${index}`}>{regex}</Tag>)
                            }
                        </Space>
                    </li>
                }
            </ul>
        </>
    )
}
