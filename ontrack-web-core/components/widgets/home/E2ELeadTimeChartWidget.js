import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {usePromotionLevel} from "@components/widgets/home/promotionChartUtils";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import {Space} from "antd";
import ChartOptions from "@components/charts/ChartOptions";
import ProjectLink from "@components/projects/ProjectLink";
import BranchLink from "@components/branches/BranchLink";
import E2ELeadTimeChart from "@components/promotionLevels/E2ELeadTimeChart";

export default function E2ELeadTimeChartWidget({
                                                   project,
                                                   branch,
                                                   promotionLevel,
                                                   targetProject,
                                                   targetBranch,
                                                   targetPromotionLevel,
                                                   maxDepth = 5,
                                                   interval,
                                                   period
                                               }) {

    const {setTitle} = useContext(DashboardWidgetCellContext)

    const promotionLevelObject = usePromotionLevel(project, branch, promotionLevel)
    const targetPromotionLevelObject = usePromotionLevel(targetProject, targetBranch, targetPromotionLevel)

    useEffect(() => {
        if (promotionLevelObject && targetPromotionLevelObject) {
            setTitle(
                <>
                    {
                        promotionLevelObject.branch &&
                        targetPromotionLevelObject.branch &&
                        <Space size={4}>
                            Lead time from
                            <ProjectLink project={promotionLevelObject.branch.project}/>/<BranchLink
                            branch={promotionLevelObject.branch}/>/<PromotionLevelLink
                            promotionLevel={promotionLevelObject}
                            text={<b>{promotionLevelObject.name}</b>}/>
                            to
                            <ProjectLink project={targetPromotionLevelObject.branch.project}/>/<BranchLink
                            branch={targetPromotionLevelObject.branch}/>/<PromotionLevelLink
                            promotionLevel={targetPromotionLevelObject}
                            text={<b>{targetPromotionLevelObject.name}</b>}/>
                            &nbsp;<ChartOptions interval={interval} period={period}/>
                        </Space>
                    }
                </>
            )
        }
    }, [promotionLevelObject, targetPromotionLevelObject, interval, period]);

    return (
        <>
            {
                promotionLevelObject && targetPromotionLevelObject &&
                <E2ELeadTimeChart
                    promotionLevel={promotionLevelObject}
                    targetPromotionLevel={targetPromotionLevelObject}
                    maxDepth={maxDepth}
                    interval={interval}
                    period={period}
                />
            }
        </>
    )
}