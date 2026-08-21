import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {usePromotionLevel} from "@components/widgets/home/promotionChartUtils";
import PromotionLevelStabilityChart from "@components/promotionLevels/PromotionLevelStabilityChart";
import PromotionChartTitle from "@components/widgets/home/PromotionChartTitle";

export default function PromotionStabilityChartWidget({project, branch, promotionLevel, interval, period}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)

    const promotionLevelObject = usePromotionLevel(project, branch, promotionLevel)

    useEffect(() => {
        if (promotionLevelObject) {
            setTitle(
                <>
                    <PromotionChartTitle
                        prefix="Stability of"
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
                <PromotionLevelStabilityChart
                    promotionLevel={promotionLevelObject}
                    interval={interval}
                    period={period}
                />
            }
        </>
    )
}