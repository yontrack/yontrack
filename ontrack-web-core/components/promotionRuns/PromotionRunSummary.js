import {Card, Descriptions, Space, Typography} from "antd";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import BuildLink from "@components/builds/BuildLink";
import BranchLink from "@components/branches/BranchLink";
import ProjectLink from "@components/projects/ProjectLink";
import TimestampText from "@components/common/TimestampText";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import PromotionRunFieldValues from "@components/promotionRuns/PromotionRunFieldValues";

/**
 * Compact summary of a promotion run.
 *
 * Deliberately no "Build X has been promoted to Y." sentence: the page header already says it, and
 * this page's primary content is the workflows below, not the summary.
 */
export default function PromotionRunSummary({run}) {

    const items = [
        {
            key: 'promotion',
            label: "Promotion",
            // No `PromotionLevelImage` beside the link: `PromotionLevelLink` already renders the
            // medal inside its `<Link>`. Adding one here drew the medal twice (#1777), and the
            // extra one sat outside the link, so only the name was clickable.
            children: <PromotionLevelLink promotionLevel={run.promotionLevel}/>,
        },
        {
            key: 'build',
            label: "Build",
            children: <BuildLink build={run.build}/>,
        },
        {
            key: 'branch',
            label: "Branch",
            children: <BranchLink branch={run.build.branch}/>,
        },
        {
            key: 'project',
            label: "Project",
            children: <ProjectLink project={run.build.branch.project}/>,
        },
    ]

    if (run.creation) {
        items.push({
            key: 'creation',
            label: "Promoted",
            children: <Space size="small">
                <TimestampText value={run.creation.time}/>
                <Typography.Text disabled>{`(${run.creation.user})`}</Typography.Text>
            </Space>,
        })
    }

    if (run.description) {
        items.push({
            key: 'description',
            label: "Description",
            span: 2,
            children: <AnnotatedDescription entity={run}/>,
        })
    }

    return (
        <Card size="small" data-testid="promotion-run-summary">
            <Space orientation="vertical" className="ot-line">
                <Descriptions items={items} column={2} size="small"/>
                {
                    run.fieldValues?.length > 0 &&
                    <PromotionRunFieldValues
                        fields={run.promotionLevel.fields}
                        fieldValues={run.fieldValues}
                    />
                }
            </Space>
        </Card>
    )
}
