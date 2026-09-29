// The one place allowed to import antd's Table
// eslint-disable-next-line no-restricted-imports
import {Table as AntdTable} from "antd";

/**
 * The `sticky` every table gets unless it passes its own (#1932): antd's sticky options, or `false`
 * for a header which does not stick.
 *
 * The header sticks to its nearest scroll container: the body of a grid section, or the window
 * for a plain page. The navbar is not fixed, hence no offset.
 *
 * This constant is the one switch for the whole UI: turning it off stops every table from
 * sticking, and keeps the wrapper.
 */
export const DEFAULT_STICKY = {offsetHeader: 0}

/**
 * antd's `Table`, whose header sticks by default.
 *
 * Use it instead of importing `Table` from `antd` - ESLint flags the import.
 *
 * @param sticky `false` to opt out, or antd's own `sticky` options (see https://ant.design/components/table).
 * Defaults to {@link DEFAULT_STICKY}.
 * @param tableLayout Defaults to `auto`: antd lays a sticky table out with fixed column widths, which
 * splits the width evenly between the columns and wraps whatever does not fit, where every table
 * of the UI sized its columns by their content before its header stuck. A table whose columns use
 * `ellipsis` needs `fixed`.
 */
export default function Table({sticky = DEFAULT_STICKY, tableLayout = 'auto', ...props}) {
    return <AntdTable sticky={sticky} tableLayout={tableLayout} {...props}/>
}

Table.Column = AntdTable.Column
Table.ColumnGroup = AntdTable.ColumnGroup
Table.Summary = AntdTable.Summary
Table.SELECTION_COLUMN = AntdTable.SELECTION_COLUMN
Table.SELECTION_ALL = AntdTable.SELECTION_ALL
Table.SELECTION_INVERT = AntdTable.SELECTION_INVERT
Table.SELECTION_NONE = AntdTable.SELECTION_NONE
Table.EXPAND_COLUMN = AntdTable.EXPAND_COLUMN
