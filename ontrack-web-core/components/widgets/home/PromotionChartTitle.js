import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import {Space, Typography} from "antd";
import ChartOptions from "@components/charts/ChartOptions";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import BranchLink from "@components/branches/BranchLink";
import ProjectLink from "@components/projects/ProjectLink";

/**
 * Title of the promotion chart widgets: lead time, stability, frequency and TTR.
 *
 * `project` and `branch` are the plain names the widget was configured with, while `promotionLevel`
 * is the object its own query loaded - only the latter carries the ids the links need. `usePromotionLevel`
 * starts that object as `{}`, which is truthy, so every widget renders this title once with nothing
 * resolved: each half falls back to the configured names rather than dereferencing an absent branch.
 *
 * The deleted-promotion-level case does NOT reach here - the query resolves to `null` and the call
 * sites skip `setTitle` entirely, leaving the widget captioned "Loading..." for good. That, and the
 * empty first render above, are #1694; this component only has to survive them.
 *
 * The name is kept bold inside the link with a plain `<b>`: `Typography.Text` sets its own colour,
 * which would take the link blue away from the one word most likely to be clicked.
 */
export default function PromotionChartTitle({prefix, project, branch, promotionLevel, interval, period}) {
    return (
        <Space size={4}>
            {prefix}
            {
                promotionLevel.id ?
                    <PromotionLevelLink
                        promotionLevel={promotionLevel}
                        text={<b>{promotionLevel.name}</b>}
                    /> :
                    <>
                        <PromotionLevelImage promotionLevel={promotionLevel}/>
                        <Typography.Text strong>{promotionLevel.name}</Typography.Text>
                    </>
            }
            on
            {
                promotionLevel.branch ?
                    <>
                        <BranchLink branch={promotionLevel.branch}/>@<ProjectLink
                        project={promotionLevel.branch.project}/>
                    </> :
                    <>{branch}@{project}</>
            }
            &nbsp;<ChartOptions interval={interval} period={period}/>
        </Space>
    )
}
