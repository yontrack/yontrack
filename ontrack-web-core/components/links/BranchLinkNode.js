import {Handle, Position} from "reactflow";
import {Card, Space, Typography} from "antd";
import {FaLink, FaMagic} from "react-icons/fa";
import AutoVersioningInfo from "@components/extension/auto-versioning/AutoVersioningInfo";
import {NodeSection} from "@components/links/NodeSection";
import LatestLinkInfo from "@components/links/LatestLinkInfo";
import ProjectLink from "@components/projects/ProjectLink";

export default function BranchLinkNode({data}) {

    const {link, sourceBranch, targetBranch, visible, selected} = data

    const {qualifier, sourceBuild, targetBuild, autoVersioning} = link

    const sourceBranchLatest = sourceBranch.latestBuilds ? sourceBranch.latestBuilds[0] : undefined
    const targetBranchLatest = targetBranch.latestBuilds ? targetBranch.latestBuilds[0] : undefined

    const latestOk = sourceBranchLatest && targetBranchLatest &&
        (sourceBranchLatest.id === sourceBuild.id) &&
        (targetBranchLatest.id === targetBuild.id)

    return (
        <div style={{
            opacity: visible ? 1 : 0,
            cursor: 'pointer',
        }}>
            <Handle type="target" position={Position.Left}/>
            <Handle type="source" position={Position.Right}/>
            <Handle type="target" position={Position.Top}/>
            <Handle type="source" position={Position.Bottom}/>
            <Card
                title={undefined}
                size="small"
                style={{
                    border: selected
                        ? 'solid 3px var(--ot-graph-node-border-selected)'
                        : 'dashed 1px var(--ot-graph-node-border)',
                    backgroundColor: latestOk ? undefined : 'var(--ot-graph-node-bg-error)'
                }}
                data-testid={`ot-branch-link-node-${sourceBranch.project.name}-${targetBranch.project.name}`}
            >
                <Space direction="vertical">
                    <Space>
                        <FaLink/>
                        <ProjectLink project={targetBuild.branch.project}/>
                        {
                            qualifier &&
                            <Typography.Text>{qualifier}</Typography.Text>
                        }
                    </Space>
                    {/* Latest link */}
                    <LatestLinkInfo
                        sourceBuild={sourceBuild}
                        latestOk={latestOk}
                        targetBuild={targetBuild}
                        lastTargetBuild={targetBranchLatest}
                    />
                    {/* AV information */}
                    {
                        autoVersioning &&
                        <NodeSection
                            icon={<FaMagic/>}
                            title="Auto versioning"
                        >
                            <AutoVersioningInfo
                                autoVersioning={autoVersioning}
                                branchLink={link}
                            />
                        </NodeSection>
                    }
                </Space>
            </Card>
        </div>
    )
}