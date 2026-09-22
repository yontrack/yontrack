import Head from "next/head";
import {title} from "@components/common/Titles";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {homeUri} from "@components/common/Links";
import MainPage from "@components/layouts/MainPage";
import StandardTable from "@components/common/table/StandardTable";
import {gql} from "graphql-request";
import {Form, Input, Space, Typography} from "antd";
import JobState from "@components/core/admin/jobs/JobState";
import JobActions from "@components/core/admin/jobs/JobActions";
import JobDetails from "@components/core/admin/jobs/JobDetails";
import TimestampText from "@components/common/TimestampText";
import {useState} from "react";
import SelectJobState from "@components/core/admin/jobs/SelectJobState";
import JobErrorIndicator from "@components/core/admin/jobs/JobErrorIndicator";
import SelectJobError from "@components/core/admin/jobs/SelectJobError";
import SelectJobTimeout from "@components/core/admin/jobs/SelectJobTimeout";
import JobTimeoutIndicator from "@components/core/admin/jobs/JobTimeoutIndicator";
import JobCategoriesContextProvider from "@components/core/admin/jobs/JobCategoriesContext";
import SelectJobCategory from "@components/core/admin/jobs/SelectJobCategory";
import SelectJobType from "@components/core/admin/jobs/SelectJobType";
import JobExecutionStatus from "@components/core/admin/jobs/JobExecutionStatus";
import {AutoRefreshButton, AutoRefreshContextProvider} from "@components/common/AutoRefresh";
import JobHistogramChart from "@components/core/admin/jobs/JobHistogramChart";

export default function JobsView() {

    const query = gql`
        query SystemJobs(
            $offset: Int!,
            $size: Int!,
            $description: String,
            $state: JobState,
            $error: Boolean,
            $timeout: Boolean,
            $category: String,
            $type: String,
        ) {
            jobs(
                offset: $offset,
                size: $size,
                description: $description,
                state: $state,
                error: $error,
                timeout: $timeout,
                category: $category,
                type: $type,
            ) {
                pageInfo {
                    nextPage {
                        offset
                        size
                    }
                }
                pageItems {
                    key: id
                    id
                    jobKey: key {
                        id
                        type {
                            key
                            name
                            category {
                                key
                                name
                            }
                        }
                    }
                    description
                    state
                    canRun
                    canPause
                    canResume
                    canBeStopped
                    canBeDeleted
                    schedule {
                        periodText
                    }
                    runCount
                    lastRunDurationMs
                    lastErrorCount
                    lastTimeoutCount
                    lastRunDate
                    nextRunDate
                    jobHistogram(interval: "1d", period: "14d") {
                        items {
                            from
                            to
                            count
                            errorCount
                            error
                            avgDurationMs
                            minDurationMs
                            maxDurationMs
                        }
                    }
                }
            }
        }
    `

    const [reloadCount, setReloadCount] = useState(0)
    const refresh = () => {
        setReloadCount(previous => previous + 1)
    }

    const [category, setCategory] = useState()

    const onFilterFormValuesChanged = (values) => {
        setCategory(values.category)
    }

    return (
        <>
            <Head>
                {title("Background jobs")}
            </Head>
            <MainPage
                title="Background jobs"
                breadcrumbs={homeBreadcrumbs()}
                commands={[
                    <CloseCommand key="close" href={homeUri()}/>
                ]}
            >
                <Space orientation="vertical" className="ot-line">
                    <JobExecutionStatus/>
                    <JobCategoriesContextProvider>
                        <AutoRefreshContextProvider onRefresh={refresh}>
                            <StandardTable
                                query={query}
                                queryNode="jobs"
                                size={30}
                                reloadCount={reloadCount}
                                filter={{}}
                                onFilterFormValuesChanged={onFilterFormValuesChanged}
                                filterForm={[
                                    <Form.Item
                                        key="state"
                                        name="state"
                                    >
                                        <SelectJobState
                                            style={{
                                                width: '15em',
                                            }}
                                            allowClear={true}
                                            placeholder="Any state"
                                        />
                                    </Form.Item>,
                                    <Form.Item
                                        key="category"
                                        name="category"
                                    >
                                        <SelectJobCategory
                                            style={{
                                                width: '15em',
                                            }}
                                            allowClear={true}
                                            placeholder="Any category"
                                        />
                                    </Form.Item>,
                                    <Form.Item
                                        key="type"
                                        name="type"
                                    >
                                        <SelectJobType
                                            selectedCategory={category}
                                            style={{
                                                width: '15em',
                                            }}
                                            placeholder="Any type"
                                        />
                                    </Form.Item>,
                                    <Form.Item
                                        key="error"
                                        name="error"
                                    >
                                        <SelectJobError
                                            style={{
                                                width: '15em',
                                            }}
                                            allowClear={true}
                                            placeholder="Any error state"
                                        />
                                    </Form.Item>,
                                    <Form.Item
                                        key="timeout"
                                        name="timeout"
                                    >
                                        <SelectJobTimeout
                                            style={{
                                                width: '15em',
                                            }}
                                            allowClear={true}
                                            placeholder="Any timeout state"
                                        />
                                    </Form.Item>,
                                    <Form.Item
                                        key="description"
                                        name="description"
                                    >
                                        <Input
                                            placeholder="Any description"
                                        />
                                    </Form.Item>,
                                ]}
                                columns={[
                                    {
                                        key: 'id',
                                        title: 'ID',
                                        dataIndex: 'id',
                                    },
                                    {
                                        key: 'category',
                                        title: 'Category',
                                        render: (_, job) => job.jobKey.type.category.name,
                                    },
                                    {
                                        key: 'type',
                                        title: 'Type',
                                        render: (_, job) => job.jobKey.type.name,
                                    },
                                    {
                                        key: 'description',
                                        title: 'Description',
                                        render: (_, job) => <Space orientation="vertical">
                                            <Typography.Text
                                                code>{job.jobKey.type.category.key}/{job.jobKey.type.key}/{job.jobKey.id}</Typography.Text>
                                            <Typography.Text type="secondary">{job.description}</Typography.Text>
                                        </Space>,
                                    },
                                    {
                                        key: 'state',
                                        title: 'State',
                                        render: (_, job) => <JobState value={job.state}/>,
                                    },
                                    {
                                        key: 'error',
                                        title: "Error",
                                        render: (_, job) => <JobErrorIndicator job={job}/>,
                                    },
                                    {
                                        key: 'timeout',
                                        title: "Timeout",
                                        render: (_, job) => <JobTimeoutIndicator job={job}/>,
                                    },
                                    {
                                        key: 'actions',
                                        title: 'Actions',
                                        render: (_, job) => <JobActions job={job} onDone={refresh}/>,
                                    },
                                    {
                                        key: 'schedule',
                                        title: 'Schedule',
                                        render: (_, job) => job.schedule.periodText,
                                    },
                                    {
                                        key: 'lastRunDate',
                                        title: 'Last run',
                                        render: (_, job) => <TimestampText value={job.lastRunDate}/>,
                                    },
                                    {
                                        key: 'history',
                                        title: "History",
                                        width: '200px',
                                        render: (_, job) => <JobHistogramChart histogram={job.jobHistogram}/>
                                    }
                                ]}
                                expandable={{
                                    expandedRowRender: (job) => (
                                        <JobDetails job={job}/>
                                    )
                                }}
                                footerExtra={
                                    <AutoRefreshButton/>
                                }
                            />
                        </AutoRefreshContextProvider>
                    </JobCategoriesContextProvider>
                </Space>
            </MainPage>
        </>
    )
}