import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import PromotionLevelLeadTimeChart from "@components/promotionLevels/PromotionLevelLeadTimeChart";
import {usePromotionLevel} from "@components/widgets/home/promotionChartUtils";
import PromotionChartTitle from "@components/widgets/home/PromotionChartTitle";

export default function PromotionLeadTimeChartWidget({project, branch, promotionLevel, interval, period}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)

    const promotionLevelObject = usePromotionLevel(project, branch, promotionLevel)

    useEffect(() => {
        if (promotionLevelObject) {
            setTitle(
                <>
                    <PromotionChartTitle
                        prefix="Lead time to"
                        project={project}
                        branch={branch}
                        promotionLevel={promotionLevelObject}
                        interval={interval}
                        period={period}
                    />
                </>
            )
        }
    }, [promotionLevelObject, interval, period]);

    return (
        <>
            {
                promotionLevelObject &&
                <PromotionLevelLeadTimeChart
                    promotionLevel={promotionLevelObject}
                    interval={interval}
                    period={period}
                />
            }
        </>
    )
}