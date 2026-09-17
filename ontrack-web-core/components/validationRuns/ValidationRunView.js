import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import Head from "next/head";
import {buildKnownName, pageTitle, validationStampTitleName} from "@components/common/Titles";
import MainPage from "@components/layouts/MainPage";
import {Empty, Space, Typography} from "antd";
import ValidationStampLink from "@components/validationStamps/ValidationStampLink";
import {downToBuildBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {buildUri} from "@components/common/Links";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import LoadingContainer from "@components/common/LoadingContainer";
import GridCell from "@components/grid/GridCell";
import StoredGridLayoutContextProvider from "@components/grid/StoredGridLayoutContext";
import StoredGridLayout from "@components/grid/StoredGridLayout";
import StoredGridLayoutResetCommand from "@components/grid/StoredGridLayoutResetCommand";
import {gqlValidationRunContent} from "@components/validationRuns/ValidationRunGraphQLFragments";
import ValidationRunStatusList from "@components/validationRuns/ValidationRunStatusList";
import ValidationRunData from "@components/framework/validation-run-data/ValidationRunData";
import RunInfo from "@components/common/RunInfo";
import InfoBox from "@components/common/InfoBox";
import ValidationDataType from "@components/framework/validation-data-type/ValidationDataType";
import {isAuthorized} from "@components/common/authorizations";
import ValidationRunStatusChange from "@components/validationRuns/ValidationRunStatusChange";
import {useRefresh} from "@components/common/RefreshUtils";
import InfoViewDrawer from "@components/common/InfoViewDrawer";
import {gqlInformationFragment, gqlPropertiesFragment} from "@components/services/fragments";

export default function ValidationRunView({id}) {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(true)
    const [run, setRun] = useState({})
    const [commands, setCommands] = useState([])

    const [refreshState, refresh] = useRefresh()

    useEffect(() => {
        if (client && id) {
            setLoading(true)
            client.request(
                gql`
                    query GetValidationRun($id: Int!) {
                        validationRuns(id: $id) {
                            ...ValidationRunContent
                            properties {
                                ...propertiesFragment
                            }
                            information {
                                ...informationFragment
                            }
                            validationStamp {
                                id
                                name
                                image
                                branch {
                                    id
                                    name
                                    project {
                                        id
                                        name
                                    }
                                }
                                dataType {
                                    descriptor {
                                        id
                                    }
                                    config
                                }
                            }
                            build {
                                id
                                name
                                branch {
                                    id
                                    name
                                    project {
                                        id
                                        name
                                    }
                                }
                                releaseProperty {
                                    value
                                }
                            }
                        }
                    }
                    ${gqlValidationRunContent}
                    ${gqlPropertiesFragment}
                    ${gqlInformationFragment}
                `,
                {id}
            ).then(data => {
                const run = data.validationRuns[0]
                setRun(run)
                setCommands([
                    <InfoViewDrawer
                        key="details"
                        id="validation-run-info"
                        entityType="VALIDATION_RUN"
                        entityName="validation run"
                        entity={run}
                    />,
                    <StoredGridLayoutResetCommand key="reset"/>,
                    <CloseCommand key="close" href={buildUri(run.build)}/>,
                ])
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, id, refreshState])

    const tableRunStatuses = "table-run-statuses"
    const sectionRunData = "section-run-data"
    const sectionRunInfo = "section-run-info"

    const defaultLayout = [
        {i: tableRunStatuses, x: 0, y: 0, w: 6, h: 12},
        {i: sectionRunData, x: 6, y: 0, w: 6, h: 6},
        {i: sectionRunInfo, x: 6, y: 6, w: 6, h: 6},
    ]

    const items = [
        {
            id: tableRunStatuses,
            content: <GridCell
                id={tableRunStatuses}
                title="Statuses"
            >
                <Space direction="vertical">
                    {
                        isAuthorized(run, 'validation_run', 'status_change') &&
                        <ValidationRunStatusChange
                            run={run}
                            onStatusChanged={refresh}
                        />
                    }
                    <ValidationRunStatusList
                        run={run}
                        onRunChanged={refresh}
                    />
                </Space>
            </GridCell>,
        },
        {
            id: sectionRunData,
            content: <GridCell
                id={sectionRunData}
                title="Data"
                padding={true}
            >
                <Space direction="vertical">
                    {
                        run.validationStamp && run.validationStamp.dataType &&
                        <InfoBox>
                            <ValidationDataType dataType={run.validationStamp.dataType}/>
                        </InfoBox>
                    }
                    {
                        run.data &&
                        <ValidationRunData data={run.data}/>
                    }
                    {
                        !run.data && <Empty description="No data associated with this validation."/>
                    }
                </Space>
            </GridCell>
        },
        {
            id: sectionRunInfo,
            content: <GridCell
                id={sectionRunInfo}
                title="Run info"
                padding={true}
            >
                {
                    run.runInfo &&
                    <RunInfo info={run.runInfo}/>
                }
                {
                    !run.runInfo && <Empty description="No run info associated with this validation."/>
                }
            </GridCell>
        },
    ]

    return (
        <>
            <Head>
                {
                    run?.validationStamp &&
                    run?.build &&
                    pageTitle(`${validationStampTitleName(run.validationStamp)} --> ${buildKnownName(run.build)}`)
                }
            </Head>
            <StoredGridLayoutContextProvider>
                <MainPage
                    title={
                        run?.validationStamp && <>
                            <Space>
                                <Typography.Text>Validation to</Typography.Text>
                                <ValidationStampLink validationStamp={run.validationStamp}/>
                            </Space>
                        </>
                    }
                    commands={commands}
                    breadcrumbs={downToBuildBreadcrumbs(run)}
                >
                    <LoadingContainer loading={loading}>
                        <Space direction="vertical" className="ot-line">
                            <AnnotatedDescription entity={run}/>
                            <StoredGridLayout
                                id="page-validation-run-layout"
                                defaultLayout={defaultLayout}
                                items={items}
                                rowHeight={30}
                                isDraggable={true}
                            />
                        </Space>
                    </LoadingContainer>
                </MainPage>
            </StoredGridLayoutContextProvider>
        </>
    )
}