import {useContext, useEffect} from "react";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {Empty} from "antd";
import EnvironmentMatrix from "@components/extension/environments/matrix/EnvironmentMatrix";
import {
    defaultMatrixFilter,
    SCOPE_ALL,
} from "@components/extension/environments/matrix/environmentMatrixModel";

/**
 * The "Environment" widget - one environment, as a matrix of one column.
 *
 * Its own `SlotCard` rendering is gone. It said less than a slot cell does (no in-flight overlay, no
 * blocked dot, no behind badge), it said it differently, and a dashboard showing one environment
 * beside a dashboard showing several had two vocabularies for the same thing. One column of the same
 * matrix is the same reading, narrower.
 *
 * Same widget key as before, because widget keys live inside people's dashboards.
 *
 * @param {string} name The environment to show. The widget is a single column, and this names it.
 * @param {Array<string>} projects Project names to restrict the rows to
 * @param {number} rowLimit How many projects to show
 */
export default function EnvironmentWidget({name = "", projects = [], rowLimit = 10}) {

    const {setTitle} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle(name ? `${name} environment` : "Environment")
    }, [name])

    if (!name) {
        return <Empty description="Environment name has not been configured."/>
    }

    const filter = {
        ...defaultMatrixFilter(),
        scope: SCOPE_ALL,
        projects,
        // The one column. The server restricts the matrix to it, so the widget asks for one column
        // rather than asking for every environment and hiding all but one.
        environments: [name],
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
