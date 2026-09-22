import {useContext, useEffect, useState} from "react";
import {DashboardContext} from "@components/dashboards/DashboardContextProvider";
import DashboardWidgetCell from "@components/dashboards/DashboardWidgetCell";
import {Alert, Button, Divider, Space} from "antd";
import {v4} from "uuid";
import GridTable from "@components/grid/GridTable";
import {FaPlus} from "react-icons/fa";
import WidgetSelectionDialog, {useWidgetSelectionDialog} from "@components/dashboards/WidgetSelectionDialog";

export default function DashboardView() {

    /**
     * Height of a row when it comes to align scale the widgets vertically
     */
    const rowHeight = 10

    const {
        dashboard,
        recordLayout,
        edition,
        saving,
        saveEdition,
        cancelEdition,
        addWidget,
    } = useContext(DashboardContext)

    const [layout, setLayout] = useState([])
    const [items, setItems] = useState([])

    const computeLayout = () => {
        return dashboard.widgets.map(widget => ({
            i: widget.uuid,
            ...widget.layout,
        }))
    }

    const maxYH = () => {
        let maxYH = 0
        layout.forEach(it => {
            maxYH = Math.max(maxYH, it.y + it.h)
        })
        return maxYH
    }

    const computeNewLayoutPosition = (preferredHeight) => {
        return {
            x: 0,
            y: maxYH() + 1,
            w: 12,
            h: preferredHeight,
        }
    }

    const buildWidgetItems = (widgets) => widgets.map(widget => ({
        id: widget.uuid,
        content: <DashboardWidgetCell widget={widget}/>,
    }))

    useEffect(() => {
        if (dashboard) {
            // console.log("[dashboard] Recomputing layout and items", dashboard)
            const computedLayout = computeLayout()
            // console.log("[dashboard] Recomputed layout", computedLayout)
            setLayout(computedLayout)
            setItems(buildWidgetItems(dashboard.widgets))
        }
    }, [dashboard]);

    const changeLayout = (newLayout) => {
        /**
         * Both the actual layout and the underlying widgets must be adapted
         */
        setLayout(newLayout)
        recordLayout(newLayout)
    }

    const onSave = () => {
        saveEdition(layout)
    }

    const onStopEdition = () => {
        cancelEdition()
    }

    const onAddWidget = (widgetDef) => {
        // New widget
        const uuid = `new-${v4()}`;
        const widget = {
            uuid: uuid,
            key: widgetDef.key,
            config: widgetDef.defaultConfig,
            layout: computeNewLayoutPosition(widgetDef.preferredHeight),
        }
        // Adding the widget to the current dashboard
        addWidget(widget)
    }

    const widgetSelectionDialog = useWidgetSelectionDialog({onAddWidget})

    const openWidgetDialog = () => {
        widgetSelectionDialog.start()
    }

    return (
        <>
            {
                dashboard && !edition && layout && items &&
                <GridTable
                    layout={layout}
                    items={items}
                    rowHeight={rowHeight}
                    isResizable={false}
                    isDraggable={false}
                />
            }
            {
                dashboard && edition &&
                <>
                    <Space orientation="vertical" className="ot-line">
                        <Alert
                            type="info"
                            title="Dashboard in edition mode"
                            action={
                                <Space>
                                    <Button
                                        type="default"
                                        icon={<FaPlus/>}
                                        onClick={openWidgetDialog}
                                    >
                                        Add a widget...
                                    </Button>
                                    <Divider orientation="vertical"/>
                                    <Button
                                        type="primary"
                                        onClick={onSave}
                                        disabled={
                                            saving || !dashboard?.widgets?.length
                                        }
                                    >
                                        Save dashboard
                                    </Button>
                                    <Button danger onClick={onStopEdition}>
                                        Cancel changes
                                    </Button>
                                </Space>
                            }
                        />
                        <GridTable
                            layout={layout}
                            items={items}
                            rowHeight={rowHeight}
                            onLayoutChange={changeLayout}
                        />
                    </Space>
                </>
            }
            <WidgetSelectionDialog widgetSelectionDialog={widgetSelectionDialog}/>
        </>
    )
}