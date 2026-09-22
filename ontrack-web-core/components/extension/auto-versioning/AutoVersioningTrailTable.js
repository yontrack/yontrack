import StandardTable from "@components/common/table/StandardTable";
import {Form, Input, Space, Switch, Typography} from "antd";
import ProjectLink from "@components/projects/ProjectLink";
import BranchLink from "@components/branches/BranchLink";
import Link from "next/link";
import {branchAutoVersioningUri} from "@components/common/Links";
import {FaBan, FaCog} from "react-icons/fa";
import CheckStatus from "@components/common/CheckStatus";
import AutoVersioningApproval from "@components/extension/auto-versioning/AutoVersioningApproval";
import AutoVersioningAuditEntryLink from "@components/extension/auto-versioning/AutoVersioningAuditEntryLink";

export default function AutoVersioningTrailTable({query, variables, queryNode, extraColumns = []}) {

    const columns = [
        {
            key: "branch",
            title: "Target branch",
            render: (_, branchTrail) => (
                <>
                    <Space>
                        <ProjectLink project={branchTrail.branch.project}/>/<BranchLink
                        branch={branchTrail.branch}/>
                        {
                            branchTrail.configuration &&
                            <Link href={branchAutoVersioningUri(branchTrail.branch)}
                                  title="Auto-versioning config"><FaCog/></Link>
                        }
                    </Space>
                </>
            )
        },
        {
            key: "state",
            title: "State",
            render: (_, branchTrail) => (
                <>
                    <Space orientation="vertical">
                        <CheckStatus
                            value={!branchTrail.rejectionReason}
                            text="Eligible"
                            noText="Not eligible"
                        />
                        {
                            branchTrail.rejectionReason &&
                            <Typography.Text
                                type="secondary">{branchTrail.rejectionReason}</Typography.Text>
                        }
                    </Space>
                </>
            )
        },
        {
            key: "approval",
            title: "Approval",
            render: (_, {configuration}) =>
                <>
                    {
                        configuration &&
                        <AutoVersioningApproval
                            autoApproval={configuration.autoApproval}
                            autoApprovalMode={configuration.autoApprovalMode}
                        />
                    }
                </>
        },
        {
            key: "targetPath",
            title: "Target path",
            render: (_, {configuration}) =>
                <>
                    {
                        configuration &&
                        <Typography.Text code>{configuration.targetPath}</Typography.Text>
                    }
                </>
        },
        ...extraColumns,
    ]

    return (
        <StandardTable
            id="auto-versioning-trail-table"
            tableSize="small"
            query={query}
            variables={variables}
            queryNode={queryNode}
            filter={{}}
            columns={columns}
            filterForm={[
                <Form.Item
                    key="onlyEligible"
                    name="onlyEligible"
                    label="Only eligible"
                    initialValue={true}
                >
                    <Switch/>
                </Form.Item>,
                <Form.Item
                    key="projectName"
                    name="projectName"
                    label="Project"
                >
                    <Input placeholder="Project name" allowClear/>
                </Form.Item>,
            ]}
        />
    )
}

export const autoVersioningTrailAuditColumn = {
    key: "audit",
    title: "Audit",
    render: (_, branchTrail) => (
        <>
            {
                !branchTrail.orderId && <Space>
                    <FaBan/>
                    No AV process was scheduled
                </Space>
            }
            {
                branchTrail.orderId &&
                <AutoVersioningAuditEntryLink uuid={branchTrail.orderId}/>
            }
        </>
    )
}
