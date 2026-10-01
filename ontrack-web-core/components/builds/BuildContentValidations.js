import React, {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {Button, Space, Typography} from "antd";
import {FaEraser, FaGavel} from "react-icons/fa";
import ValidationRunSortingMode from "@components/validationRuns/ValidationRunSortingMode";
import BuildValidateAction from "@components/builds/BuildValidateAction";
import {isAuthorized} from "@components/common/authorizations";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import GridCell from "@components/grid/GridCell";
import {gqlValidationRunTableContent} from "@components/validationRuns/ValidationRunGraphQLFragments";
import ValidationRunTable from "@components/validationRuns/ValidationRunTable";

export default function BuildContentValidations({build}) {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(true)
    const [validationRuns, setValidationRuns] = useState([])

    const [pageRequest, setPageRequest] = useState({
        offset: 0,
        size: 10,
    })

    // Filters
    const [filteredInfo, setFilteredInfo] = useState({})

    const resetFilters = () => {
        setFilteredInfo({})
    }

    // Sorting
    const [sortingMode, setSortingMode] = useState("ID")
    const onSortingModeChanged = (value) => {
        setPageRequest({
            offset: 0,
            size: 5,
        })
        setSortingMode(value)
    }

    const onTableChange = (pagination, filters, sorter, extra) => {
        if (extra.action === 'paginate') {
            setPageRequest({
                offset: (pagination.current - 1) * pagination.pageSize,
                size: pagination.pageSize,
            })
        } else if (extra.action === 'filter') {
            setPageRequest({offset: 0, size: 5}) // Resetting the pagination
            setFilteredInfo(filters)
        }
    }

    const [pagination, setPagination] = useState({
        current: 1,
        pageSize: 10,
        total: 0,
    })

    const sortingByText = (a, b) => {
        if (a.text < b.text) {
            return -1
        } else if (a.text > b.text) {
            return 1
        } else {
            return 0
        }
    }

    const [reloadCount, setReloadCount] = useState(0)

    const reload = () => {
        setReloadCount(reloadCount + 1)
    }

    const [statuses, setStatuses] = useState([])
    const [validationStamps, setValidationStamps] = useState([])
    useEffect(() => {
        if (client) {
            client.request(
                gql`
                    query ValidationRunFilters($branchId: Int!) {
                        validationRunStatusIDList {
                            value: id
                            text: name
                        }
                        branches(id: $branchId) {
                            validationStamps {
                                value: name
                                text: name
                            }
                        }
                    }
                `, {branchId: Number(build.branch.id)}
            ).then(data => {
                setStatuses(data.validationRunStatusIDList.sort(sortingByText))
                setValidationStamps(data.branches[0].validationStamps.sort(sortingByText))
            })
        }
    }, [client, build.branch.id]);

    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    ${gqlValidationRunTableContent}
                    query BuildValidations(
                        $buildId: Int!,
                        $offset: Int!,
                        $size: Int!,
                        $sortingMode: ValidationRunSortingMode!,
                        $statuses: [String],
                        $validationStamp: String,
                    ) {
                        build(id: $buildId) {
                            validationRunsPaginated(
                                sortingMode: $sortingMode,
                                offset: $offset,
                                size: $size,
                                statuses: $statuses,
                                validationStamp: $validationStamp
                            ) {
                                pageInfo {
                                    pageIndex
                                    totalSize
                                    currentSize
                                    previousPage {
                                        offset
                                        size
                                    }
                                    nextPage {
                                        offset
                                        size
                                    }
                                }
                                pageItems {
                                    ...ValidationRunTableContent
                                }
                            }
                        }
                    }
                `, {
                    buildId: Number(build.id),
                    offset: pageRequest.offset,
                    size: pageRequest.size,
                    statuses: filteredInfo.status,
                    validationStamp: filteredInfo.validation ? filteredInfo.validation[0] : null,
                    sortingMode: sortingMode,
                }
            ).then(data => {
                setValidationRuns(data.build.validationRunsPaginated.pageItems)
                const pageInfo = data.build.validationRunsPaginated.pageInfo;
                setPagination({
                    ...pagination,
                    current: pageInfo.pageIndex + 1,
                    pageSize: pageRequest.size,
                    total: pageInfo.totalSize,
                })
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, build, pageRequest, filteredInfo, sortingMode, reloadCount]);

    return (
        <>
            <GridCell id="validations"
                      title="Validations"
                      titleWidth={6}
                      loading={loading}
                      padding={false}
                      extra={
                          <>
                              <Space>
                                  {/* Validates the current build */}
                                  {
                                      isAuthorized(build, 'build', 'validate') &&
                                      <BuildValidateAction
                                          build={build}
                                          onValidation={reload}
                                      >
                                          <Button>
                                              <Space>
                                                  <FaGavel/>
                                                  <Typography.Text>Validate</Typography.Text>
                                              </Space>
                                          </Button>
                                      </BuildValidateAction>
                                  }
                                  {/* Reset filter */}
                                  <Button onClick={resetFilters}>
                                      <Space>
                                          <FaEraser/>
                                          <Typography.Text>Reset filters</Typography.Text>
                                      </Space>
                                  </Button>
                                  {/* Sorting mode */}
                                  <Typography.Text>Sort:</Typography.Text>
                                  <ValidationRunSortingMode value={sortingMode} onChange={onSortingModeChanged}/>
                              </Space>
                          </>
                      }
            >
                <ValidationRunTable
                    validationRuns={validationRuns}
                    pagination={pagination}
                    onChange={onTableChange}
                    onStatusChanged={reload}
                    filtering={{
                        validationStamps,
                        statuses,
                        filteredInfo,
                    }}
                />
            </GridCell>
        </>
    )
}