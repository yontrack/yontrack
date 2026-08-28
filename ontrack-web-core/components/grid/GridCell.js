import {Space} from "antd";
import {FaArrowsAlt, FaCompressArrowsAlt, FaExpandArrowsAlt} from "react-icons/fa";

import GridCellCommand from "@components/grid/GridCellCommand";
import {useContext} from "react";
import {GridTableContext} from "@components/grid/GridTableContext";
import PageSection from "@components/common/PageSection";

export default function GridCell({
                                     id,
                                     title,
                                     loading,
                                     isDraggable,
                                     extra,
                                     padding = false,
                                     children
                                 }) {

    const {expandable, draggable, expandedId, toggleExpandedId} = useContext(GridTableContext)

    const toggleExpansion = () => {
        if (id) {
            toggleExpandedId(id)
        }
    }

    return (
        <>
            <PageSection
                id={id}
                title={loading ? "Loading..." : title}
                extra={
                    <Space>
                        {extra}
                        {expandable && id &&
                            <>
                                <GridCellCommand
                                    condition={expandedId !== id}
                                    title="Makes the widget full size"
                                    icon={<FaExpandArrowsAlt/>}
                                    onAction={toggleExpansion}
                                />
                                <GridCellCommand
                                    condition={expandedId === id}
                                    title="Makes the widget to its regular size"
                                    icon={<FaCompressArrowsAlt/>}
                                    onAction={toggleExpansion}
                                />
                            </>
                        }
                        {/* An explicit `isDraggable` on the cell wins over the grid default: a cell
                            must be able to hide the handle in a grid which is draggable elsewhere,
                            like a dashboard widget outside of the edition mode. */}
                        <GridCellCommand
                            condition={isDraggable ?? draggable}
                            icon={<FaArrowsAlt/>}
                            title="Use this handle to drag the widget into another position"
                            className="ot-rgl-draggable-handle"
                        />
                    </Space>
                }
                loading={loading}
                padding={padding}
            >
                {
                    children
                }
            </PageSection>
        </>
    )
}
