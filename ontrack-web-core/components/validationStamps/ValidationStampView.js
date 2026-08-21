import Head from "next/head";
import {useContext, useEffect, useState} from "react";
import {validationStampTitle} from "@components/common/Titles";
import StoredGridLayoutContextProvider from "@components/grid/StoredGridLayoutContext";
import MainPage from "@components/layouts/MainPage";
import {Skeleton, Space} from "antd";
import {validationStampBreadcrumbs} from "@components/common/Breadcrumbs";
import StoredGridLayout from "@components/grid/StoredGridLayout";
import {UserContext} from "@components/providers/UserProvider";
import {useValidationStampById} from "@components/services/fragments";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import GridCell from "@components/grid/GridCell";
import ValidationStampHistory from "@components/validationStamps/ValidationStampHistory";
import StoredGridLayoutResetCommand from "@components/grid/StoredGridLayoutResetCommand";
import InfoBox from "@components/common/InfoBox";
import ValidationDataType from "@components/framework/validation-data-type/ValidationDataType";
import {isAuthorized} from "@components/common/authorizations";
import ValidationStampChangeImageCommand from "@components/validationStamps/ValidationStampChangeImageCommand";
import ValidationStampUpdateCommand from "@components/validationStamps/ValidationStampUpdateCommand";
import ValidationStampDeleteCommand from "@components/validationStamps/ValidationStampDeleteCommand";
import ValidationStampBulkUpdateCommand from "@components/validationStamps/ValidationStampBulkUpdateCommand";
import {useChartOptionsCommand} from "@components/charts/ChartOptionsDialog";
import ValidationStampLeadTimeChart from "@components/validationStamps/ValidationStampLeadTimeChart";
import ValidationStampFrequencyChart from "@components/validationStamps/ValidationStampFrequencyChart";
import ValidationStampStabilityChart from "@components/validationStamps/ValidationStampStabilityChart";
import ValidationStampMetricsChart from "@components/validationStamps/ValidationStampMetricsChart";
import ValidationStampViewDrawer from "@components/validationStamps/ValidationStampViewDrawer";
import UserMenuActions from "@components/entities/UserMenuActions";
import ValidationStampViewTitle from "@components/validationStamps/ValidationStampViewTitle";
import {useRefresh} from "@components/common/RefreshUtils";
import {useEventForRefresh} from "@components/common/EventsContext";

export default function ValidationStampView({id}) {

    const [commands, setCommands] = useState([])

    const chartLeadTime = "chart-lead-time"
    const chartFrequency = "chart-frequency"
    const chartMetrics = "chart-metrics"
    const chartStability = "chart-stability"
    const sectionHistory = "section-history"

    const [defaultLayout, setDefaultLayout] = useState([])

    const [refreshCount, refresh] = useRefresh()

    const user = useContext(UserContext)

    const validationStampImageUpdated = useEventForRefresh("validationStamp.image")
    const {validationStamp, loading} = useValidationStampById({id, refreshCount, deps: [validationStampImageUpdated]})

    const {command, interval, period} = useChartOptionsCommand()

    const metricsChart = validationStamp?.charts?.find(it => it.id === 'validation-stamp-metrics')

    useEffect(() => {
        if (validationStamp) {
            const commands = [
                <UserMenuActions key="userMenuActions" actions={validationStamp.userMenuActions}/>
            ]
            if (isAuthorized(validationStamp, 'validation_stamp', 'edit')) {
                commands.push(<ValidationStampChangeImageCommand key="change-image" id={id}/>)
                commands.push(<ValidationStampUpdateCommand key="update" validationStamp={validationStamp} refresh={refresh}/>)
            }
            if (isAuthorized(validationStamp, 'validation_stamp', 'delete')) {
                commands.push(<ValidationStampDeleteCommand key="delete" id={id}/>)
            }
            if (user.authorizations?.validation_stamp?.bulkUpdate) {
                commands.push(<ValidationStampBulkUpdateCommand key="bulk-update" id={id}/>)
            }
            commands.push(<StoredGridLayoutResetCommand key="reset"/>)
            commands.push(<CloseCommand key="close" href={branchUri(validationStamp.branch)}/>)
            setCommands(commands)

            // Charts
            const layout = [
                {i: sectionHistory, x: 0, y: 0, w: 12, h: 12},
            ]
            if (metricsChart) {
                layout.push(
                    {i: chartLeadTime, x: 0, y: 12, w: 6, h: 12},
                    {i: chartFrequency, x: 6, y: 12, w: 6, h: 12},
                    {i: chartMetrics, x: 0, y: 24, w: 6, h: 12},
                    {i: chartStability, x: 6, y: 24, w: 6, h: 12},
                )
            } else {
                layout.push(
                    {i: chartLeadTime, x: 0, y: 12, w: 4, h: 12},
                    {i: chartFrequency, x: 4, y: 12, w: 4, h: 12},
                    {i: chartStability, x: 8, y: 24, w: 4, h: 12},
                )
            }

            setDefaultLayout(layout)
        }
    }, [validationStamp])

    // The items are computed at each rendering, so that the charts always use
    // the current chart options (interval & period)
    const getItems = () => {
        if (!validationStamp) return []

        const items = [
            {
                id: sectionHistory,
                content: <GridCell
                    id={sectionHistory}
                    title="Validation history"
                >
                    <ValidationStampHistory
                        validationStamp={validationStamp}
                    />
                </GridCell>,
            },
            {
                id: chartLeadTime,
                content: <GridCell
                    id={chartLeadTime}
                    title="Lead time to validation"
                    extra={command}
                >
                    <ValidationStampLeadTimeChart
                        validationStamp={validationStamp}
                        interval={interval}
                        period={period}
                    />
                </GridCell>,
            },
            {
                id: chartFrequency,
                content: <GridCell
                    id={chartFrequency}
                    title="Frequency to validation"
                    extra={command}
                >
                    <ValidationStampFrequencyChart
                        validationStamp={validationStamp}
                        interval={interval}
                        period={period}
                    />
                </GridCell>,
            },
            {
                id: chartStability,
                content: <GridCell
                    id={chartStability}
                    title="Stability of validation"
                    extra={command}
                >
                    <ValidationStampStabilityChart
                        validationStamp={validationStamp}
                        interval={interval}
                        period={period}
                    />
                </GridCell>,
            },
        ]

        if (metricsChart) {
            items.push({
                id: chartMetrics,
                content: <GridCell
                    id={chartMetrics}
                    title="Validation metrics"
                    extra={command}
                >
                    <ValidationStampMetricsChart
                        validationStamp={validationStamp}
                        interval={interval}
                        period={period}
                    />
                </GridCell>,
            })
        }

        return items
    }

    const items = getItems()

    return (
        <>
            <Head>
                {validationStamp && validationStampTitle(validationStamp)}
            </Head>
            <StoredGridLayoutContextProvider>
                <MainPage
                    title={validationStamp && <ValidationStampViewTitle validationStamp={validationStamp}/>}
                    breadcrumbs={validationStamp ? validationStampBreadcrumbs(validationStamp) : []}
                    commands={commands}
                    description={
                        validationStamp &&
                        <Space direction="vertical">
                            {validationStamp?.description}
                            {/* Validation stamp data config */}
                            {
                                validationStamp && validationStamp.dataType &&
                                <InfoBox>
                                    <ValidationDataType dataType={validationStamp.dataType}/>
                                </InfoBox>
                            }
                        </Space>
                    }
                >
                    <Skeleton loading={loading} active>
                        <StoredGridLayout
                            id={`page-validation-stamp-layout-${id}`}
                            defaultLayout={defaultLayout}
                            items={items}
                            rowHeight={30}
                            isDraggable={true}
                        />
                        {
                            validationStamp &&
                            <ValidationStampViewDrawer validationStamp={validationStamp} loading={loading}/>
                        }
                    </Skeleton>
                </MainPage>
            </StoredGridLayoutContextProvider>
        </>
    )
}