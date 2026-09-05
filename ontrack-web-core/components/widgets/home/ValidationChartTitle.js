import {Space, Typography} from "antd";
import ChartOptions from "@components/charts/ChartOptions";
import ValidationStampImage from "@components/validationStamps/ValidationStampImage";
import ValidationStampLink from "@components/validationStamps/ValidationStampLink";
import BranchLink from "@components/branches/BranchLink";
import ProjectLink from "@components/projects/ProjectLink";

/**
 * Title of the validation chart widgets: stability and metrics.
 *
 * The same shape as `PromotionChartTitle`: `project` and `branch` are the configured names, while
 * `validationStamp` is the loaded object carrying the ids the links need.
 *
 * The fallback to the configured names is deliberately kept even though these two widgets cannot
 * currently reach it. `useValidationStampByName` passes no `initialData`, so its data starts
 * `undefined` rather than `{}`, and both call sites skip `setTitle` until it resolves - which is why
 * they show no title at all where the promotion widgets show a half-empty one (#1694). Keeping the
 * two titles symmetrical means fixing that hook cannot turn into a crash here.
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
