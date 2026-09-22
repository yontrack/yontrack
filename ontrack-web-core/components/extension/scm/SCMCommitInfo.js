import PageSection from "@components/common/PageSection";
import {Divider, Space, Typography} from "antd";
import SafeHTMLComponent from "@components/common/SafeHTMLComponent";
import TimestampText from "@components/common/TimestampText";
import SCMBranchInfos from "@components/extension/scm/SCMBranchInfos";

export default function SCMCommitInfo({scmCommitInfo}) {
    return (
        <>
            <PageSection
                title={
                    <Space size="small">
                        Commit
                        <Typography.Link
                            code
                            href={scmCommitInfo.scmDecoratedCommit.commit.link}
                            copyable
                        >{scmCommitInfo.scmDecoratedCommit.commit.id}</Typography.Link>
                    </Space>
                }
                padding={true}
            >
                <Space orientation="vertical">
                    <Typography.Paragraph>
                        <SafeHTMLComponent htmlContent={scmCommitInfo.scmDecoratedCommit.annotatedMessage}/>
                    </Typography.Paragraph>
                    <Space size="small">
                        <Typography.Text
                            type="secondary">{scmCommitInfo.scmDecoratedCommit.commit.author}</Typography.Text>
                        <Divider orientation="vertical"/>
                        <Typography.Text
                            type="secondary">
                            <TimestampText value={scmCommitInfo.scmDecoratedCommit.commit.timestamp}/>
                        </Typography.Text>
                    </Space>

                    {
                        scmCommitInfo.branchInfos && scmCommitInfo.branchInfos.length > 0 && <>
                            <Typography.Paragraph>
                                This commit is available in the following branches:
                            </Typography.Paragraph>

                            <SCMBranchInfos branchInfos={scmCommitInfo.branchInfos}/>
                        </>
                    }
                </Space>
            </PageSection>
        </>
    )
}