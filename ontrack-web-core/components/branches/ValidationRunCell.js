import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import {Space, Typography} from "antd";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import Timestamp from "@components/common/Timestamp";
import Duration from "@components/common/Duration";
import React from "react";
import ValidationRunStatusNone from "@components/validationRuns/ValidationRunStatusNone";
import ValidationRunHistoryDialog, {
    useValidationRunHistoryDialog
} from "@components/validationRuns/ValidationRunHistoryDialog";
import {isAuthorized} from "@components/common/authorizations";
import BuildValidateDialog, {useBuildValidateDialog} from "@components/builds/BuildValidateDialog";

export default function ValidationRunCell({build, validationStamp, onChange}) {

    const validation = build.validations.find(v => v.validationStamp.id === validationStamp.id)
    let run = undefined
    if (validation && validation.validationRuns.length > 0) {
        run = validation.validationRuns[0]
    }

    const validationRunHistoryDialog = useValidationRunHistoryDialog();

    const displayRunStatuses = () => {
        validationRunHistoryDialog.start(run)
    }

    const buildValidateDialog = useBuildValidateDialog({
        onSuccess: onChange,
    })

    const createValidation = () => {
        if (isAuthorized(build, 'build', 'validate')) {
            buildValidateDialog.start({
                build,
                validationStamp,
            })
        }
    }

    return (
        <>
            {/* Not run */}
            {
                !run && <>
                    <ValidationRunStatusNone
                        disabled={!isAuthorized(build, 'build', 'validate')}
                        onClick={createValidation}
                    />
                    <BuildValidateDialog buildValidateDialog={buildValidateDialog}/>
                </>
            }
            {/* Last status */}
            {
                run && <>
                    <ValidationRunStatus
                        id={`${build.id}-${validationStamp.id}`}
                        onClick={displayRunStatuses}
                        status={run.lastStatus}
                        displayText={false}
                        tooltip={true}
                        tooltipContent={
                            <Space orientation="vertical" size={0}>
                                {/* Description */}
                                {
                                    (run.lastStatus.description || run.lastStatus.annotatedDescription) &&
                                    <AnnotatedDescription entity={run.lastStatus} disabled={false}/>
                                }
                                {/* Creation of the status */}
                                <Timestamp
                                    prefix="Created on"
                                    suffix={`by ${run.lastStatus.creation.user}`}
                                    value={run.lastStatus.creation.time}
                                />
                                {/* Run info */}
                                {
                                    run.runInfo && run.runInfo.runTime &&
                                    <Typography.Text>
                                        Ran in <Duration
                                        seconds={run.runInfo.runTime}
                                        displaySeconds={true}
                                        displaySecondsInTooltip={false}
                                    />
                                    </Typography.Text>
                                }
                            </Space>
                        }
                    />
                    <ValidationRunHistoryDialog
                        dialog={validationRunHistoryDialog}
                        onChange={onChange}
                    />
                </>
            }
        </>
    )
}