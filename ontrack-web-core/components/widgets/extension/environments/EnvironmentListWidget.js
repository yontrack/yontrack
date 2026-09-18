import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import EnvironmentMatrix from "@components/extension/environments/matrix/EnvironmentMatrix";
import {
    defaultMatrixFilter,
    SCOPE_ALL,
} from "@components/extension/environments/matrix/environmentMatrixModel";

/**
 * The "Environments list" widget - now the matrix, in a dashboard cell.
 *
 * Same widget key, because widget keys are stored inside people's dashboards and changing one would
 * silently empty every dashboard carrying it. What changed is what it draws: the old table put
 * environments down the side and a column per qualified project across, which is the matrix
 * transposed and reading the wrong way round for the question it is on a dashboard to answer.
 *
 * Its filter is *pinned*, not steerable: a dashboard cell says which projects and tags it is about,
 * and a toolbar inside it would be a second place to change something the widget's own configuration
 * already decides.
 *
 * @param {string} title Overrides the cell's title
 * @param {Array<string>} tags Environment tags to restrict the columns to
 * @param {Array<string>} projects Project names to restrict the rows to
 * @param {number} rowLimit How many projects to show. A dashboard cell is short, so the widget shows
 *   its first rows and stops rather than paging inside a tile.
 */
export default function EnvironmentListWidget({title = '', tags = [], projects = [], rowLimit = 10}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(title ? title : "Environments")
    }, [title])

    const filter = {
        ...defaultMatrixFilter(),
        // Every project the widget was configured with, favourites or not: a dashboard is shared and
        // "my favourites" is not a property of the dashboard.
        scope: SCOPE_ALL,
        tags,
        projects,
    }

    return (
        <EnvironmentMatrix
            filter={filter}
            pageSize={rowLimit}
            paged={false}
            emptyStates={false}
        />
    )
}
