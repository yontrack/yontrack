import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import {Space, Typography} from "antd";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import TimestampText from "@components/common/TimestampText";
import Duration from "@components/common/Duration";
import React from "react";
import ValidationRunStatusNone from "@components/validationRuns/ValidationRunStatusNone";
import ValidationRunQuickTransition from "@components/validationRuns/ValidationRunQuickTransition";
import {isAuthorized} from "@components/common/authorizations";
import BuildValidateDialog, {useBuildValidateDialog} from "@components/builds/BuildValidateDialog";
import ActorBadge from "@components/common/actors/ActorBadge";

export default function ValidationRunCell({build, validationStamp, onChange}) {

    const validation = build.validations.find(v => v.validationStamp.id === validationStamp.id)
    let run = undefined
    if (validation && validation.validationRuns.length > 0) {
        run = validation.validationRuns[0]
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
                run && <ValidationRunQuickTransition
                    run={run}
                    label={`${validationStamp.name} \u2014 ${run.lastStatus.statusID.name}`}
                    onChange={onChange}
                >
                    {({open}) => <ValidationRunStatus
                        id={`${build.id}-${validationStamp.id}`}
                        status={run.lastStatus}
                        displayText={false}
                        // The hover summary steps aside while the quick-transition popover is open
                        tooltip={!open}
                        tooltipContent={
                            <Space orientation="vertical" size={0}>
                                {/* Description */}
                                {
                                    (run.lastStatus.description || run.lastStatus.annotatedDescription) &&
                                    <AnnotatedDescription entity={run.lastStatus} disabled={false}/>
                                }
                                {/* Creation of the status */}
                                <Typography.Text type="secondary" italic style={{fontSize: '75%'}}>
                                    <TimestampText prefix="Created on" value={run.lastStatus.creation.time}/>
                                    {' '}
                                    <ActorBadge signature={run.lastStatus.creation} prefix="by"/>
                                </Typography.Text>
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
                    />}
                </ValidationRunQuickTransition>
            }
        </>
    )
}