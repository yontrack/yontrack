import {useState} from "react";
import {Modal, Space, Typography} from "antd";
import {gql} from "graphql-request";
import LoadingContainer from "@components/common/LoadingContainer";
import BuildLink from "@components/builds/BuildLink";
import ValidationStampLink from "@components/validationStamps/ValidationStampLink";
import PageSection from "@components/common/PageSection";
import ValidationRunLink from "@components/validationRuns/ValidationRunLink";
import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import Rows from "@components/common/Rows";
import ValidationDataType from "@components/framework/validation-data-type/ValidationDataType";
import InfoBox from "@components/common/InfoBox";
import {useQuery} from "@components/services/GraphQL";
import {gqlValidationRunContent} from "@components/validationRuns/ValidationRunGraphQLFragments";
import ValidationRun from "@components/validationRuns/ValidationRun";

export function useValidationRunHistoryDialog() {
    const [open, setOpen] = useState(false)
    const [run, setRun] = useState({})

    const start = (run) => {
        setRun(run)
        setOpen(true)
    }

    const close = () => {
        setOpen(false)
    }

    return {
        open, // State of the dialog
        start, // Opens the dialog
        close, // Closes the dialog
        run, // Selected run
    }
}

export default function ValidationRunHistoryDialog({dialog, onChange}) {

    const [runsReload, setRunsReload] = useState(0)

    // The run gives the build and the validation stamp whose runs are listed
    const {data: target, loading: targetLoading, finished: targetFinished} = useQuery(
        gql`
            query GetValidationRun($runId: Int!) {
                validationRuns(id: $runId) {
                    build {
                        id
                        releaseProperty {
                            value
                        }
                    }
                    validationStamp {
                        name
                    }
                }
            }
        `,
        {
            variables: {runId: dialog.run?.id ? Number(dialog.run.id) : undefined},
            deps: [dialog.open, dialog.run, runsReload],
            condition: !!(dialog.open && dialog.run?.id),
            dataFn: data => ({
                buildId: data.validationRuns[0].build.id,
                validationStampName: data.validationRuns[0].validationStamp.name,
            }),
        }
    )

    const {data: history, loading: historyLoading, error: historyError} = useQuery(
        gql`
            query GetValidationHistory(
                $buildId: Int!,
                $validationStampName: String!,
                $offset: Int!,
                $size: Int!,
            ) {
                build(id: $buildId) {
                    id
                    name
                    validations(validationStamp: $validationStampName) {
                        validationStamp {
                            id
                            name
                            image
                            dataType {
                                descriptor {
                                    id
                                    feature {
                                        id
                                    }
                                }
                                config
                            }
                            validationRunsPaginated(buildId: $buildId, offset: $offset, size: $size) {
                                pageInfo {
                                    nextPage {
                                        offset
                                        size
                                    }
                                }
                                pageItems {
                                    ...ValidationRunContent
                                }
                            }
                        }
                    }
                }
            }
            ${gqlValidationRunContent}
        `,
        {
            variables: {
                buildId: target ? Number(target.buildId) : undefined,
                validationStampName: target?.validationStampName,
                offset: 0,
                size: 10,
            },
            deps: [target],
            condition: !!target,
            dataFn: data => ({
                target,
                build: data.build,
                validationStamp: data.build.validations[0].validationStamp,
                runs: data.build.validations[0].validationStamp.validationRunsPaginated.pageItems,
            }),
        }
    )

    // Loading until the history of the current target has been loaded (or has failed to)
    const loading = !targetFinished || targetLoading || historyLoading ||
        (!!target && !historyError && history?.target !== target)

    const build = history?.build
    const validationStamp = history?.validationStamp

    // A run changed in the dialog is applied locally, on top of the history it was changed in,
    // until the next history replaces it
    const [changedRuns, setChangedRuns] = useState({source: null, runs: []})
    const runs = (history && changedRuns.source === history) ? changedRuns.runs : (history?.runs ?? [])

    const onOk = async () => {
        dialog.close()
        if (runsReload > 0 && onChange) {
            onChange()
        }
    }

    const reloadOnStatusChanged = () => {
        setRunsReload(runsReload + 1)
    }

    const onRunChanged = (run) => {
        setChangedRuns({
            source: history,
            runs: runs.map(oldRun => {
                if (oldRun.id === run.id) {
                    return {
                        ...oldRun,
                        ...run,
                    }
                } else {
                    return oldRun
                }
            }),
        })
    }

    return (
        <>
            <Modal
                open={dialog.open}
                closable={false}
                destroyOnHidden={true}
                cancelButtonProps={{style: {display: 'none'}}}
                onOk={onOk}
                onCancel={onOk}
                width={800}
            >
                <LoadingContainer loading={loading}>
                    <Rows>
                        <Typography.Title level={4}>
                            Runs for <ValidationStampLink validationStamp={validationStamp}/> in build <BuildLink
                            build={build}/>
                        </Typography.Title>

                        {/* Validation stamp data config */}
                        {
                            validationStamp && validationStamp.dataType &&
                            <InfoBox>
                                <ValidationDataType dataType={validationStamp.dataType}/>
                            </InfoBox>
                        }
                        {
                            runs.map(run =>
                                <PageSection
                                    key={run.id}
                                    title={
                                        <Space>
                                            <ValidationRunStatus status={run.lastStatus} displayText={false}
                                                                 tooltip={false}/>
                                            <ValidationRunLink run={run} text={`Run #${run.runOrder}`}/>
                                        </Space>
                                    }
                                    padding={false}
                                >
                                    <ValidationRun
                                        run={run}
                                        onStatusChanged={reloadOnStatusChanged}
                                        onRunChanged={onRunChanged}
                                    />
                                </PageSection>
                            )
                        }

                    </Rows>
                </LoadingContainer>
            </Modal>
        </>
    )
}