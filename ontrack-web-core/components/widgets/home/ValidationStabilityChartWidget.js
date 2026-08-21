import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import ValidationStampStabilityChart from "@components/validationStamps/ValidationStampStabilityChart";
import ValidationChartTitle from "@components/widgets/home/ValidationChartTitle";
import {useValidationStampByName} from "@components/widgets/home/ValidationChartUtils";

export default function ValidationStabilityChartWidget({project, branch, validationStamp, interval, period}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)

    const {validationStampObject} = useValidationStampByName(project, branch, validationStamp)

    useEffect(() => {
        if (validationStampObject) {
            setTitle(
                <>
                    <ValidationChartTitle
                        prefix="Stability of"
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
                <ValidationStampStabilityChart
                    validationStamp={validationStampObject}
                    interval={interval}
                    period={period}
                />
            }
        </>
    )
}