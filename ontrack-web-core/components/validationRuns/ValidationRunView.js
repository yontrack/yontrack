import {useQuery} from "@components/services/GraphQL";
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
import {isFindingsRun} from "@components/extension/findings/findingsModel";
import ValidationRunFindings from "@components/extension/findings/run/ValidationRunFindings";
import {showEvidenceCell} from "@components/extension/audit-trail/evidenceModel";
import ValidationRunEvidence from "@components/extension/audit-trail/ValidationRunEvidence";

const EMPTY_RUN = {}

export default function ValidationRunView({id}) {

    const [refreshState, refresh] = useRefresh()

    const {data, loading, finished} = useQuery(
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
                    evidence {
                        id
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
                                authorizations {
                                    name
                                    action
                                    authorized
                                }
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
        {
            variables: {id},
            deps: [id, refreshState],
            condition: !!id,
            dataFn: data => data.validationRuns[0],
        }
    )
    const run = data ?? EMPTY_RUN

    const commands = data ? [
        <InfoViewDrawer
            key="details"
            id="validation-run-info"
            entityType="VALIDATION_RUN"
            entityName="validation run"
            entity={run}
        />,
        <StoredGridLayoutResetCommand key="reset"/>,
        <CloseCommand key="close" href={buildUri(run.build)}/>,
    ] : []

    const tableRunStatuses = "table-run-statuses"
    const sectionRunData = "section-run-data"
    const sectionRunInfo = "section-run-info"
    const tableRunFindings = "table-run-findings"
    const tableRunEvidence = "table-run-evidence"

    // The findings of a security scan, for a user granted their view only
    const showFindings = isFindingsRun(run) &&
        !!run.build?.branch?.project &&
        isAuthorized(run.build.branch.project, 'findings', 'view')

    // The evidence of the run, when it has some or when the user may upload some
    const showEvidence = showEvidenceCell(run)

    const defaultLayout = [
        {i: tableRunStatuses, x: 0, y: 0, w: 6, h: 12},
        {i: sectionRunData, x: 6, y: 0, w: 6, h: 6},
        {i: sectionRunInfo, x: 6, y: 6, w: 6, h: 6},
        ...(showFindings ? [{i: tableRunFindings, x: 0, y: 12, w: 12, h: 14}] : []),
        ...(showEvidence ? [{i: tableRunEvidence, x: 0, y: showFindings ? 26 : 12, w: 12, h: 10}] : []),
    ]

    const items = [
        {
            id: tableRunStatuses,
            content: <GridCell
                id={tableRunStatuses}
                title="Statuses"
            >
                <Space orientation="vertical">
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
                <Space orientation="vertical">
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
        ...(showFindings ? [{
            id: tableRunFindings,
            content: <GridCell
                id={tableRunFindings}
                title="Findings"
            >
                <ValidationRunFindings run={run}/>
            </GridCell>
        }] : []),
        ...(showEvidence ? [{
            id: tableRunEvidence,
            content: <GridCell
                id={tableRunEvidence}
                title="Evidence"
                padding={true}
            >
                <ValidationRunEvidence run={run}/>
            </GridCell>
        }] : []),
    ]

    // A security scan has a layout of its own, with its findings, and so has a run showing its
    // evidence, so that a layout stored for one kind of run never lacks the cell of another one.
    // The key remounts the layout when the kind of the run is known, reading the stored layout of
    // that kind.
    const layoutId = `page-validation-run${showFindings ? '-findings' : ''}${showEvidence ? '-evidence' : ''}-layout`

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
                    <LoadingContainer loading={loading || !finished}>
                        <Space orientation="vertical" className="ot-line">
                            <AnnotatedDescription entity={run}/>
                            <StoredGridLayout
                                key={layoutId}
                                id={layoutId}
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