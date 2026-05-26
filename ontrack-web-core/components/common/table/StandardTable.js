import {Space, Table} from "antd";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import TablePaginationFooter from "@components/common/table/TablePaginationFooter";
import FilterForm from "@components/common/table/FilterForm";
import {useRefresh} from "@components/common/RefreshUtils";
import {AutoRefreshButton, AutoRefreshContextProvider} from "@components/common/AutoRefresh";

/**
 * Table whose content is fetched using a GraphQL query.
 *
 * @param id HTML ID and `data-testid` to set on the table
 * @param query GraphQL query to run to get the data
 * @param queryNode Name of the node which contains a _paginated list_ of item under the `data` GraphQL root node
 * @param variables Variables to pass to the GraphQL query
 * @param reloadCount Counter state to use to refresh the table data
 * @param columns List of Antd Table Column definitions
 * @param expandable Expandable content of rows (see https://ant.design/components/table)
 * @param size Default number of items per page
 * @param filter Filter data to pass to the query
 * @param onFilterChange Function to call whenever the filter changes
 * @param onFilterFormDataChange Function to call whenever the form filter data changes
 * @param footerExtra Extra information to display in the footer
 * @param rowKey Computing each row key (needed for expandable content)
 * @param filterForm List of Antd Form items to put in a form on top of the table (not displayed if empty)
 * @param filterExtraButtons List of extra buttons to put in the form on top of the table
 * @param autoRefresh Whether the table should contain an option for auto-refresh
 */
export default function StandardTable({
                                          id,
                                          query,
                                          queryNode,
                                          variables = {},
                                          reloadCount = 0,
                                          columns = [],
                                          expandable = false,
                                          tableSize,
                                          size = 10,
                                          initialFilter = {},
                                          filter = {},
                                          onFilterChange = (_) => {
                                          },
                                          onFilterFormValuesChanged = (_) => {
                                          },
                                          footerExtra = '',
                                          rowKey,
                                          filterForm = [],
                                          filterExtraButtons = [],
                                          autoRefresh = false,
                                          scroll,
                                      }) {

    const [localReloadCount, localReload] = useRefresh()

    const client = useGraphQLClient()

    const [filterFormData, setFilterFormData] = useState(initialFilter ?? {})

    const [loading, setLoading] = useState(true)
    const [items, setItems] = useState([])

    const [pagination, setPagination] = useState({
        offset: 0,
        size: size,
    })

    const [pageInfo, setPageInfo] = useState({})

    useEffect(() => {
        if (client && query && variables !== undefined) {
            setLoading(true)
            client.request(
                query,
                {
                    ...variables,
                    ...filter,
                    ...filterFormData,
                    offset: pagination.offset,
                    size: pagination.size,
                }
            ).then(data => {
                const userNode = typeof queryNode === 'function' ? queryNode(data, filterFormData) : data[queryNode]
                const newItems = userNode.pageItems;
                setPageInfo(userNode.pageInfo)
                if (pagination.offset > 0) {
                    setItems((entries) => [...entries, ...newItems])
                } else {
                    setItems(newItems)
                }
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, query, pagination, filter, filterFormData, reloadCount, localReloadCount]);

    const onTableChange = (_, filters) => {
        if (onFilterChange) {
            onFilterChange(filters)
        }
    }

    return (
        <>
            <AutoRefreshContextProvider onRefresh={localReload}>
                <Space direction="vertical" className="ot-line" size={0}>
                    {
                        (filterForm.length > 0 || autoRefresh) &&
                        <FilterForm
                            initialFilter={initialFilter}
                            filterForm={filterForm}
                            setFilterFormData={setFilterFormData}
                            onFilterFormValuesChanged={onFilterFormValuesChanged}
                            filterExtraButtons={filterExtraButtons}
                            extraComponents={
                                <>
                                {
                                    autoRefresh && <AutoRefreshButton/>
                                }
                                </>
                            }
                        />
                    }
                    <Table
                        id={id}
                        data-testid={id}
                        loading={loading}
                        dataSource={items}
                        pagination={false}
                        columns={columns}
                        expandable={expandable}
                        onChange={onTableChange}
                        rowKey={rowKey}
                        size={tableSize}
                        scroll={scroll}
                        footer={() =>
                            <Space>
                                <TablePaginationFooter
                                    pageInfo={pageInfo}
                                    setPagination={setPagination}
                                />
                                {footerExtra}
                            </Space>
                        }
                    />
                </Space>
            </AutoRefreshContextProvider>
        </>
    )
}