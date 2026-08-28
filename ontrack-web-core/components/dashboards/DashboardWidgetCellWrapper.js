import GridCell from "@components/grid/GridCell";
import {Dynamic} from "@components/common/Dynamic";
import {useContext} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {FaPencilAlt, FaRegSave, FaTrash, FaWindowClose} from "react-icons/fa";

import GridCellCommand from "@components/grid/GridCellCommand";
import PaddedContent from "@components/common/PaddedContent";

export default function DashboardWidgetCellWrapper({widget}) {

    const {
        title,
        extra,
        dashboardEdition,
        widgetEdition,
        widgetSaving,
        startWidgetEdition,
        saveWidgetEdition,
        cancelWidgetEdition,
        deleteWidget,
    } = useContext(DashboardWidgetCellContext)

    return (
        <>
            <GridCell
                id={widget.uuid}
                title={title}
                isDraggable={!!dashboardEdition}
                extra={
                    <>
                        {!dashboardEdition && extra}
                        {/* Dashboard edition */}
                        {
                            dashboardEdition &&
                            <>
                                <GridCellCommand
                                    condition={!widgetEdition}
                                    title="Configure this widget"
                                    icon={<FaPencilAlt/>}
                                    onAction={startWidgetEdition}
                                />
                                <GridCellCommand
                                    condition={widgetEdition}
                                    disabled={widgetSaving}
                                    title="Saves the changes for this widget"
                                    icon={<FaRegSave/>}
                                    onAction={saveWidgetEdition}
                                />
                                <GridCellCommand
                                    condition={widgetEdition}
                                    disabled={widgetSaving}
                                    title="Cancels the changes for this widget"
                                    icon={<FaWindowClose/>}
                                    onAction={cancelWidgetEdition}
                                />
                                <GridCellCommand
                                    condition={!widgetEdition}
                                    title="Remove this widget from the dashboard"
                                    icon={<FaTrash/>}
                                    onAction={deleteWidget}
                                />
                            </>
                        }
                    </>
                }
            >
                <div className="ot-block">
                    {
                        !widgetEdition &&
                        <Dynamic
                            path={`widgets/${widget.key}Widget`}
                            props={widget.config}
                        />
                    }
                    {
                        widgetEdition &&
                        <PaddedContent>
                            <Dynamic
                                path={`widgets/${widget.key}WidgetForm`}
                                props={widget.config}
                            />
                        </PaddedContent>
                    }
                </div>
            </GridCell>
        </>
    )
}