import SearchResultComponent from "@components/framework/search/SearchResultComponent";
import {Space, Tag, Typography} from "antd";
import Link from "next/link";
import ProjectLink from "@components/projects/ProjectLink";
import BranchLink from "@components/branches/BranchLink";
import {findingUri} from "@components/common/Links";

/**
 * Search result of a finding: its external ID linking to the finding page, its project, and the
 * branches it is exposed on.
 */
export default function Result({data}) {
    const {finding, project, branches = []} = data
    return <SearchResultComponent
        title={
            <Space size="small" wrap>
                <Link href={findingUri(finding)}>
                    <Typography.Text code>{finding.externalId}</Typography.Text>
                </Link>
                <Tag>{finding.maxSeverity}</Tag>
                <ProjectLink project={project}/>
            </Space>
        }
        description={
            <Space orientation="vertical" size={0}>
                <Typography.Text type="secondary">{finding.title}</Typography.Text>
                {
                    finding.location &&
                    <Typography.Text type="secondary" code>{finding.location}</Typography.Text>
                }
                {
                    branches.length > 0 ?
                        <Space size="small" wrap>
                            <Typography.Text type="secondary">Exposed on</Typography.Text>
                            {
                                branches.map(branch =>
                                    <Space key={branch.id} size={4}>
                                        <BranchLink branch={branch}/>
                                        {
                                            branch.state === 'ACCEPTED' &&
                                            <Typography.Text type="secondary">accepted</Typography.Text>
                                        }
                                    </Space>
                                )
                            }
                        </Space> :
                        <Typography.Text type="secondary">Not exposed on any branch</Typography.Text>
                }
            </Space>
        }
    />
}
