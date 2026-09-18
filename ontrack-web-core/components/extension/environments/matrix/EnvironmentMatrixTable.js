import {Space, Table, Tag, Typography} from "antd"
import ProjectLink from "@components/projects/ProjectLink"
import SlotCell from "@components/extension/environments/shared/SlotCell"
import EnvironmentImage from "@components/extension/environments/shared/EnvironmentImage"
import {
    groupEnvironments,
    matrixRows,
    slotAt,
} from "@components/extension/environments/matrix/environmentMatrixModel"

/**
 * The matrix itself: projects down, environments across, a slot cell where the two meet.
 *
 * A *pure* component, like the cell it is made of - it is handed a matrix and draws it, and asks the
 * server for nothing. That is what lets the page, the two dashboard widgets and (later) the
 * project-scoped view all draw the same table from three different queries.
 *
 * The qualifier rows are antd tree children rather than a second table: the empty qualifier *is* the
 * project row, so a project with only the default one is a single flat line and nothing expands,
 * while a project with several gets an arrow and its rows nest under it.
 *
 * @param {Object} matrix What `environmentMatrix` answered
 * @param {function} onSlotClick Called with a slot when a cell is activated - the screen opens the
 *   drawer on it
 * @param {boolean} loading Whether the matrix is being (re)fetched
 * @param {?Object} pagination antd pagination config, or false for a table which does not page
 * @param {Array<string>} expandedRowKeys Which project rows are open
 * @param {function} onExpandedRowsChange Called with the new set
 */
export default function EnvironmentMatrixTable({
                                                   matrix,
                                                   onSlotClick,
                                                   loading = false,
                                                   pagination = false,
                                                   expandedRowKeys,
                                                   onExpandedRowsChange,
                                               }) {

    const environments = matrix?.environments ?? []
    const rows = matrixRows(matrix?.projects)
    const groups = groupEnvironments(environments)

    const environmentColumn = (environment) => ({
        key: environment.id,
        title: (
            <Space size={6} data-testid={`matrix-column-${environment.id}`}>
                <EnvironmentImage environment={environment}/>
                <Typography.Text>{environment.name}</Typography.Text>
            </Space>
        ),
        render: (_, row) => {
            const slot = slotAt(row, environment.id)
            // No slot for this project in this environment: an empty cell, not a dash. "There is no
            // such slot" and "there is a slot and nothing is in it" are different answers, and only
            // the second one is a slot cell.
            return slot ? <SlotCell slot={slot} onClick={onSlotClick}/> : null
        },
    })

    const columns = [
        {
            key: 'project',
            title: "Project",
            fixed: 'left',
            width: '16em',
            render: (_, row) => (
                <Space size={6} data-testid={`matrix-row-${row.key}`}>
                    {
                        // A qualifier row is drawn by its qualifier alone: the project's name is
                        // already on the row above it, and repeating it makes the nesting invisible.
                        row.qualifier ?
                            <Tag data-testid={`matrix-qualifier-${row.project.id}-${row.qualifier}`}>
                                {row.qualifier}
                            </Tag> :
                            <ProjectLink project={row.project}/>
                    }
                </Space>
            ),
        },
        ...groups.map(group => (
            group.tag ?
                {
                    key: `tag-${group.tag}`,
                    title: group.tag,
                    children: group.environments.map(environmentColumn),
                } :
                // An untagged run of environments is drawn without a heading rather than under an
                // empty one, which antd would render as a blank band across the header.
                group.environments.map(environmentColumn)
        )).flat(),
    ]

    return (
        <Table
            data-testid="environment-matrix"
            loading={loading}
            dataSource={rows}
            columns={columns}
            pagination={pagination}
            size="small"
            scroll={{x: 'max-content'}}
            expandable={{
                expandedRowKeys,
                onExpandedRowsChange,
            }}
        />
    )
}
