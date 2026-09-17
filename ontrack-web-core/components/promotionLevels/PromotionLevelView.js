import {useContext, useEffect, useState} from "react";
import Head from "next/head";
import {promotionLevelTitle} from "@components/common/Titles";
import StoredGridLayoutContextProvider from "@components/grid/StoredGridLayoutContext";
import MainPage from "@components/layouts/MainPage";
import {Skeleton, Space, Typography} from "antd";
import {promotionLevelBreadcrumbs} from "@components/common/Breadcrumbs";
import StoredGridLayout from "@components/grid/StoredGridLayout";
import PromotionLevelLeadTimeChart from "@components/promotionLevels/PromotionLevelLeadTimeChart";
import GridCell from "@components/grid/GridCell";
import PromotionLevelTTRChart from "@components/promotionLevels/PromotionLevelTTRChart";
import {useChartOptionsCommand} from "@components/charts/ChartOptionsDialog";
import StoredGridLayoutResetCommand from "@components/grid/StoredGridLayoutResetCommand";
import PromotionLevelChangeImageCommand from "@components/promotionLevels/PromotionLevelChangeImageCommand";
import PromotionLevelUpdateCommand from "@components/promotionLevels/PromotionLevelUpdateCommand";
import {useEventsForRefresh} from "@components/common/EventsContext";
import PromotionLevelDeleteCommand from "@components/promotionLevels/PromotionLevelDeleteCommand";
import {usePromotionLevelById} from "@components/services/fragments";
import {isAuthorized} from "@components/common/authorizations";
import {UserContext} from "@components/providers/UserProvider";
import PromotionLevelBulkUpdateCommand from "@components/promotionLevels/PromotionLevelBulkUpdateCommand";
import PromotionLevelFrequencyChart from "@components/promotionLevels/PromotionLevelFrequencyChart";
import PromotionLevelStabilityChart from "@components/promotionLevels/PromotionLevelStabilityChart";
import PromotionLevelHistory from "@components/promotionLevels/PromotionLevelHistory";
import UserMenuActions from "@components/entities/UserMenuActions";
import PromotionLevelViewTitle from "@components/promotionLevels/PromotionLevelViewTitle";
import InfoViewDrawer from "@components/common/InfoViewDrawer";
import {CloseCommand} from "@components/common/Commands";
import {branchPromotionLevelsUri} from "@components/common/Links";
import PromotionLevelAutoVersioningTargets from "@components/promotionLevels/PromotionLevelAutoVersioningTargets";

export default function PromotionLevelView({id}) {

    const chartLeadTime = "chart-lead-time"
    const chartTTR = "chart-ttr"
    const chartFrequency = "chart-frequency"
    const chartStability = "chart-stability"
    const sectionHistory = "section-history"
    const sectionAutoVersioning = "section-auto-versioning"

    const [commands, setCommands] = useState([])

    const refreshCount = useEventsForRefresh(["promotionLevel.updated", "promotionLevel.image"])

    const user = useContext(UserContext)

    const {promotionLevel, loading: loadingPromotionLevel} = usePromotionLevelById({id, refreshCount})

    useEffect(() => {
        if (promotionLevel) {
            const commands = [
                <InfoViewDrawer
                    key="details"
                    id="promotion-level-info"
                    entityType="PROMOTION_LEVEL"
                    entityName="promotion level"
                    entity={promotionLevel}
                />,
                <UserMenuActions key="userMenuActions" actions={promotionLevel.userMenuActions}/>
            ]
            if (isAuthorized(promotionLevel, 'promotion_level', 'edit')) {
                commands.push(<PromotionLevelChangeImageCommand key="change-image" id={id}/>)
                commands.push(<PromotionLevelUpdateCommand key="update" id={id}/>)
            }
            if (isAuthorized(promotionLevel, 'promotion_level', 'delete')) {
                commands.push(<PromotionLevelDeleteCommand key="delete" id={id}/>)
            }
            if (user.authorizations?.promotion_level?.bulkUpdate) {
                commands.push(<PromotionLevelBulkUpdateCommand key="bulk-update" id={id}/>)
            }
            commands.push(<StoredGridLayoutResetCommand key="reset"/>)
            commands.push(<CloseCommand href={branchPromotionLevelsUri(promotionLevel.branch)}/>)
            setCommands(commands)
        }
    }, [promotionLevel])

    const defaultLayout = [
        // Charts - 2 x 2
        {i: chartLeadTime, x: 0, y: 0, w: 6, h: 12},
        {i: chartFrequency, x: 6, y: 0, w: 6, h: 12},
        {i: chartTTR, x: 0, y: 12, w: 6, h: 12},
        {i: chartStability, x: 6, y: 12, w: 6, h: 12},
        // History - table
        {i: sectionHistory, x: 0, y: 18, w: 12, h: 12},
        // Auto-versioning - table
        {i: sectionAutoVersioning, x: 0, y: 30, w: 12, h: 12},
    ]

    const {command, interval, period} = useChartOptionsCommand()

    const items = [
        {
            id: chartLeadTime,
            content: <GridCell
                id={chartLeadTime}
                title="Lead time to promotion"
                extra={command}
            >
                <PromotionLevelLeadTimeChart
                    promotionLevel={promotionLevel}
                    interval={interval}
                    period={period}
                />
            </GridCell>,
        },
        {
            id: chartFrequency,
            content: <GridCell
                id={chartFrequency}
                title="Frequency to promotion"
                extra={command}
            >
                <PromotionLevelFrequencyChart
                    promotionLevel={promotionLevel}
                    interval={interval}
                    period={period}
                />
            </GridCell>,
        },
        {
            id: chartTTR,
            content: <GridCell
                id={chartTTR}
                title="Time to recovery to promotion"
                extra={command}
            >
                <PromotionLevelTTRChart
                    promotionLevel={promotionLevel}
                    interval={interval}
                    period={period}
                />
            </GridCell>,
        },
        {
            id: chartStability,
            content: <GridCell
                id={chartStability}
                title="Stability of promotion"
                extra={command}
            >
                <PromotionLevelStabilityChart
                    promotionLevel={promotionLevel}
                    interval={interval}
                    period={period}
                />
            </GridCell>,
        },
        {
            id: sectionHistory,
            content: <GridCell
                id={sectionHistory}
                title="Promotion history"
            >
                <PromotionLevelHistory
                    promotionLevel={promotionLevel}
                />
            </GridCell>,
        },
        {
            id: sectionAutoVersioning,
            content: <GridCell
                id={sectionAutoVersioning}
                title="Auto-versioning trail"
            >
                <Space className="ot-line" direction="vertical">
                    <Typography.Text type="secondary" style={{padding: 8, display: "block"}}>
                        Lists all branches which are targets for auto-versioning
                        from this promotion.
                    </Typography.Text>
                    <PromotionLevelAutoVersioningTargets
                        promotionLevel={promotionLevel}
                    />
                </Space>
            </GridCell>,
        },
    ]

    return (
        <>
            <Head>
                {promotionLevel ? promotionLevelTitle(promotionLevel) : ""}
            </Head>
            <StoredGridLayoutContextProvider>
                <MainPage
                    title={
                        promotionLevel ? <PromotionLevelViewTitle promotionLevel={promotionLevel}/> : ""
                    }
                    breadcrumbs={promotionLevel ? promotionLevelBreadcrumbs(promotionLevel) : []}
                    commands={commands}
                    description={promotionLevel?.description}
                >
                    <Skeleton loading={loadingPromotionLevel} active>
                        <StoredGridLayout
                            id="page-promotion-level-layout"
                            defaultLayout={defaultLayout}
                            items={items}
                            rowHeight={30}
                            isDraggable={true}
                        />
                    </Skeleton>
                </MainPage>
            </StoredGridLayoutContextProvider>
        </>
    )
}