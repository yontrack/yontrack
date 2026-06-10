import {useEffect, useState} from "react";
import {Button, Popover, Skeleton, Space, Spin, Table, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import BuildLink from "@components/builds/BuildLink";
import Decorations from "@components/framework/decorations/Decorations";
import ValidationRunLink from "@components/validationRuns/ValidationRunLink";
import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import ValidationRunData from "@components/framework/validation-run-data/ValidationRunData";
import RunInfo from "@components/common/RunInfo";
import TimestampText from "@components/common/TimestampText";
import TableColumnFilterDropdown from "@components/common/table/TableColumnFilterDropdown";
import SelectValidationRunPassedState from "@components/validationStamps/SelectValidationRunPassedState";
import {FaExchangeAlt, FaSearch} from "react-icons/fa";
import useRangeSelection from "@components/common/RangeSelection";
import {scmChangeLogUri} from "@components/common/Links";
import {useRouter} from "next/router";
import RangeSelector from "@components/common/RangeSelector";

const {Column} = Table

export default function ValidationStampHistory({validationStamp}) {

    const router = useRouter()

    const [runs, setRuns] = useState([])

    const [pagination, setPagination] = useState({
        offset: 0,
        size: 5,
    })
    const [pageInfo, setPageInfo] = useState({})
    const [scmChangeLogEnabled, setScmChangeLogEnabled] = useState('')
    const [filter, setFilter] = useState({
        passed: null,
    })

    const {data: rawData, loading} = useQuery(
        `
            query GetValidationStampHistory(
                $id: Int!,
                $offset: Int!,
                $size: Int!,
                $passed: Boolean,
            ) {
                validationStamp(id: $id) {
                    branch {
                        scmBranchInfo {
                            changeLogs
                        }
                    }
                    validationRunsPaginated(
                        offset: $offset,
                        size: $size,
                        passed: $passed,
                    ) {
                        pageInfo {
                            nextPage {
                                offset
                                size
                            }
                        }
                        pageItems {
                            id
                            description
                            annotatedDescription
                            runOrder
                            lastStatus {
                                statusID {
                                    id
                                    name
                                    passed
                                }
                                description
                                annotatedDescription
                            }
                            build {
                                id
                                name
                                releaseProperty {
                                    value
                                }
                                decorations {
                                    decorationType
                                    error
                                    data
                                    feature {
                                        id
                                    }
                                }
                            }
                            creation {
                                user
                                time
                            }
                            runInfo {
                                runTime
                                sourceType
                                sourceUri
                                triggerType
                                triggerData
                            }
                            data {
                                descriptor {
                                    id
                                    displayName
                                }
                                data
                            }
                        }
                    }
                }
            }
        `,
        {
            variables: {
                id: validationStamp ? Number(validationStamp.id) : undefined,
                offset: pagination.offset,
                size: pagination.size,
                passed: filter.passed,
            },
            deps: [validationStamp, pagination, filter],
            condition: !!validationStamp,
        }
    )

    useEffect(() => {
        if (rawData) {
            setScmChangeLogEnabled(rawData.validationStamp.branch.scmBranchInfo?.changeLogs)
            setPageInfo(rawData.validationStamp.validationRunsPaginated.pageInfo)
            if (pagination.offset > 0) {
                setRuns(prev => [...prev, ...rawData.validationStamp.validationRunsPaginated.pageItems])
            } else {
                setRuns(rawData.validationStamp.validationRunsPaginated.pageItems)
            }
        }
    }, [rawData]) // eslint-disable-line react-hooks/exhaustive-deps

    const onLoadMore = () => {
        if (pageInfo.nextPage) {
            setPagination(pageInfo.nextPage)
        }
    }

    const onTableChange = (_, filters) => {
        setFilter({
            passed: filters.status && filters.status[0],
        })
    }

    const rangeSelection = useRangeSelection()

    const onChangeLog = () => {
        if (scmChangeLogEnabled && rangeSelection.isComplete()) {
            const [runIdFrom, runIdTo] = rangeSelection.selection
            const runFrom = runs.find(it => it.id === runIdFrom)
            const runTo = runs.find(it => it.id === runIdTo)
            if (runFrom && runTo) {
                router.push(scmChangeLogUri(runFrom.build.id, runTo.build.id))
            }
        }
    }

    return (
        <>
            <Skeleton loading={loading && pagination.offset === 0} active>
                <Table
                    dataSource={runs}
                    pagination={false}
                    onChange={onTableChange}
                    footer={() => (
                        <>
                            <Space>
                                <Popover
                                    content={
                                        (pageInfo && pageInfo.nextPage) ?
                                            "There are more validations to be loaded" :
                                            "There are no more validations to be loaded"
                                    }
                                >
                                    <Button
                                        onClick={onLoadMore}
                                        disabled={loading || !pageInfo || !pageInfo.nextPage}
                                    >
                                        <Space>
                                            {
                                                loading && pagination.offset && <Spin size="small"/>
                                            }
                                            {
                                                !loading && <FaSearch/>
                                            }
                                            <Typography.Text>Load more...</Typography.Text>
                                        </Space>
                                    </Button>
                                </Popover>
                                {
                                    scmChangeLogEnabled &&
                                    <Popover
                                        content="Change between two selected validations."
                                    >
                                        <Button
                                            disabled={!rangeSelection || !rangeSelection.isComplete()}
                                            onClick={onChangeLog}
                                        >
                                            <Space>
                                                <FaExchangeAlt/>
                                                <Typography.Text>Change log</Typography.Text>
                                            </Space>
                                        </Button>
                                    </Popover>
                                }
                            </Space>
                        </>
                    )}
                >
                    {
                        scmChangeLogEnabled &&
                        <Column
                            key="range"
                            width={32}
                            render={(_, run) =>
                                <RangeSelector
                                    id={run.id}
                                    title="Select this run as a boundary for a change log."
                                    rangeSelection={rangeSelection}
                                />
                            }
                        />
                    }

                    <Column
                        title="Build"
                        key="build"
                        render={(_, run) =>
                            <Space>
                                <BuildLink
                                    build={run.build}
                                />
                                <Decorations entity={run.build}/>
                            </Space>
                        }
                    />

                    <Column
                        title="Run"
                        key="run"
                        render={(_, run) =>
                            <ValidationRunLink run={run}/>
                        }
                    />

                    <Column
                        title="Last status"
                        key="status"
                        render={(_, run) =>
                            <ValidationRunStatus
                                id={run.id}
                                status={run.lastStatus}
                                displayText={false}
                                tooltip={true}
                            />
                        }
                        filterDropdown={({setSelectedKeys, selectedKeys, confirm, clearFilters}) =>
                            <TableColumnFilterDropdown
                                confirm={confirm}
                                clearFilters={clearFilters}
                            >
                                <SelectValidationRunPassedState
                                    value={selectedKeys}
                                    onChange={value => setSelectedKeys([value])}
                                />
                            </TableColumnFilterDropdown>
                        }
                        filteredValue={filter.passed}
                    />

                    <Column
                        title="Description"
                        key="description"
                        render={(_, run) =>
                            <AnnotatedDescription
                                entity={run.lastStatus}
                                disabled={false}
                            />
                        }
                    />

                    <Column
                        title="Data"
                        key="data"
                        render={(_, run) =>
                            run.data &&
                            <ValidationRunData data={run.data}/>
                        }
                    />

                    <Column
                        title="Creation"
                        key="creation"
                        render={(_, run) =>
                            <Space>
                                <TimestampText value={run.creation.time}/>
                                <Typography.Text disabled>
                                    ({run.creation.user})
                                </Typography.Text>
                            </Space>
                        }
                    />

                    <Column
                        title="Run info"
                        key="run-info"
                        render={(_, run) =>
                            run.runInfo &&
                            <RunInfo info={run.runInfo}/>
                        }
                    />

                </Table>
            </Skeleton>
        </>
    )
}