import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import ValidationChartTitle from "@components/widgets/home/ValidationChartTitle";
import {useValidationStampByName} from "@components/widgets/home/ValidationChartUtils";
import ValidationStampMetricsChart from "@components/validationStamps/ValidationStampMetricsChart";

export default function ValidationMetricsChartWidget({project, branch, validationStamp, interval, period}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)

    const {validationStampObject} = useValidationStampByName(project, branch, validationStamp)

    useEffect(() => {
        if (validationStampObject) {
            setTitle(
                <>
                    <ValidationChartTitle
                        prefix="Metrics of"
                        project={project}
                        branch={branch}
                        validationStamp={validationStampObject}
                        interval={interval}
                        period={period}
                    />
                </>
            )
        }
    }, [validationStampObject, interval, period]);

    return (
        <>
            {
                validationStampObject &&
                <ValidationStampMetricsChart
                    validationStamp={validationStampObject}
                    interval={interval}
                    period={period}
                />
            }
        </>
    )
}