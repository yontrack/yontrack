import {Space, Typography} from "antd";
import ProjectLink from "@components/projects/ProjectLink";
import BranchDisplayNameLink from "@components/links/BranchDisplayNameLink";
import BuildLink from "@components/builds/BuildLink";
import Timestamp from "@components/common/Timestamp";
import BuildPromotions from "@components/links/BuildPromotions";

export default function BuildNodePopoverContent({build}) {
    return (
        <>
            <Space orientation="vertical">
                <Typography.Text>
                    {build && <ProjectLink project={build.branch.project} shorten={true}/>}
                </Typography.Text>
                <BranchDisplayNameLink branch={build.branch}/>
                <BuildLink build={build} displayTooltip={false}></BuildLink>
                <Timestamp value={build?.creation?.time}/>
                <BuildPromotions build={build}/>
            </Space>
        </>
    )
}