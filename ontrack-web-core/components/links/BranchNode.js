import {Handle, Position} from "reactflow";
import {Card, Flex, Space, Tooltip, Typography} from "antd";
import {branchUri, buildLinksUri} from "@components/common/Links";
import {FaArrowCircleLeft, FaCompressArrowsAlt, FaLink, FaProjectDiagram} from "react-icons/fa";
import Timestamp from "@components/common/Timestamp";
import Link from "next/link";
import BuildRef from "@components/links/BuildRef";
import BuildPromotions from "@components/links/BuildPromotions";
import BranchDisplayNameLink from "@components/links/BranchDisplayNameLink";
import {NodeSection} from "@components/links/NodeSection";
import ProjectLink from "@components/projects/ProjectLink";

export default function BranchNode({data}) {

    const {branch, selected, visible, focused, onToggleFocus} = data

    const toggleFocus = (event) => {
        event.stopPropagation()
        if (onToggleFocus) {
            onToggleFocus({id: String(branch.id)})
        }
    }

    const linkToBranchLinks = <Link
        href={`${branchUri(branch)}/links`}
        title="Links for this branch"
    ><FaLink/></Link>

    const latestBuild = branch.latestBuilds ? branch.latestBuilds[0] : undefined

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
                    border: selected ? 'solid 3px black' : 'solid 1px gray',
                }}
                data-testid={`ot-branch-node-${branch.project.name}`}
            >
                <Space orientation="vertical" className="ot-line">
                    <Typography.Text>
                        {branch && <ProjectLink project={branch.project} shorten={true}/>}
                    </Typography.Text>
                    <Flex justify="space-between" align="center" style={{width: '100%'}}>
                        <BranchDisplayNameLink branch={branch}>
                            &nbsp;
                            {linkToBranchLinks}
                        </BranchDisplayNameLink>
                        <Tooltip title={focused ? "Restores the full graph" : "Focus on this project"}>
                            <span style={{marginLeft: '8px'}}>
                                <FaCompressArrowsAlt
                                    color={focused ? 'blue' : undefined}
                                    onClick={toggleFocus}
                                />
                            </span>
                        </Tooltip>
                    </Flex>
                    {
                        latestBuild &&
                        <NodeSection
                            icon={<FaArrowCircleLeft/>}
                            title="Latest build"
                        >
                            <Space>
                                <BuildRef build={latestBuild}/>
                                <Tooltip title="Graph of build links">
                                    <Link href={buildLinksUri(latestBuild)}><FaProjectDiagram size="12"/></Link>
                                </Tooltip>
                            </Space>
                            <Timestamp value={latestBuild?.creation?.time}/>
                            <BuildPromotions build={latestBuild}/>
                        </NodeSection>
                    }
                </Space>
            </Card>
        </div>
    )
}