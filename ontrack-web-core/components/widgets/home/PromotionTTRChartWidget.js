import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import PromotionLevelTTRChart from "@components/promotionLevels/PromotionLevelTTRChart";
import {usePromotionLevel} from "@components/widgets/home/promotionChartUtils";
import PromotionChartTitle from "@components/widgets/home/PromotionChartTitle";

export default function PromotionTTRChartWidget({project, branch, promotionLevel, interval, period}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)

    const promotionLevelObject = usePromotionLevel(project, branch, promotionLevel)

    useEffect(() => {
        if (promotionLevelObject) {
            setTitle(
                <>
                    <PromotionChartTitle
                        prefix="TTR to"
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
                <PromotionLevelTTRChart
                    promotionLevel={promotionLevelObject}
                    interval={interval}
                    period={period}
                />
            }
        </>
    )
}