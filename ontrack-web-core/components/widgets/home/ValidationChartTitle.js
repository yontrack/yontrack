import {Space, Typography} from "antd";
import ChartOptions from "@components/charts/ChartOptions";
import ValidationStampImage from "@components/validationStamps/ValidationStampImage";
import ValidationStampLink from "@components/validationStamps/ValidationStampLink";
import BranchLink from "@components/branches/BranchLink";
import ProjectLink from "@components/projects/ProjectLink";

/**
 * Title of the validation chart widgets: stability and metrics.
 *
 * The same rules as `PromotionChartTitle`, for the validation stamp: `project` and `branch` are the
 * configured names, `validationStamp` is the loaded object carrying the ids the links need, and each
 * half falls back to the configured names while that object is empty - on the first render, and for
 * good when the configured stamp was deleted (#1694).
 */
export default function ValidationChartTitle({prefix, project, branch, validationStamp, interval, period}) {
    return (
        <Space size={4}>
            {prefix}
            {
                validationStamp.id ?
                    <ValidationStampLink
                        validationStamp={validationStamp}
                        text={<b>{validationStamp.name}</b>}
                    /> :
                    <>
                        <ValidationStampImage validationStamp={validationStamp}/>
                        <Typography.Text strong>{validationStamp.name}</Typography.Text>
                    </>
            }
            on
            {
                validationStamp.branch ?
                    <>
                        <BranchLink branch={validationStamp.branch}/>@<ProjectLink
                        project={validationStamp.branch.project}/>
                    </> :
                    <>{branch}@{project}</>
            }
            &nbsp;<ChartOptions interval={interval} period={period}/>
        </Space>
    )
}
