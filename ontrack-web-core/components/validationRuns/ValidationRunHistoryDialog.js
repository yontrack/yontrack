import {useEffect, useState} from "react";
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
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
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

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(true)
    const [build, setBuild] = useState()
    const [validationStamp, setValidationStamp] = useState()
    const [pageInfo, setPageInfo] = useState()
    const [runs, setRuns] = useState([])
    const [runsReload, setRunsReload] = useState(0)

    useEffect(() => {
        if (client && dialog.open && dialog.run?.id) {
            setLoading(true)
            client.request(
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
                {runId: Number(dialog.run.id)}
            ).then(data => {
                const buildId = data.validationRuns[0].build.id
                const validationStampName = data.validationRuns[0].validationStamp.name
                return client.request(
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
                    `, {
                        buildId: Number(buildId),
                        validationStampName,
                        offset: 0,
                        size: 10,
                    }
                )
            }).then(data => {
                setBuild(data.build)
                setValidationStamp(data.build.validations[0].validationStamp)
                setPageInfo(data.build.validations[0].validationStamp.validationRunsPaginated.pageInfo)
                setRuns(data.build.validations[0].validationStamp.validationRunsPaginated.pageItems)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, dialog.open, dialog.run, runsReload]);

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
        setRuns(runs => runs.map(oldRun => {
            if (oldRun.id === run.id) {
                return {
                    ...oldRun,
                    ...run,
                }
            } else {
                return oldRun
            }
        }))
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