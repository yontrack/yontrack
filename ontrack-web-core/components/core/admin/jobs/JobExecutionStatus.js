import {useState} from "react";
import {callGraphQL, useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Alert, Button, Popover, Space, Spin} from "antd";

export default function JobExecutionStatus() {

    const [reloadCount, setReloadCount] = useState(0)

    const {data: jobExecutionStatus} = useQuery(
        gql`
            query JobExecutionStatus {
                jobExecutionStatus {
                    paused
                }
            }
        `,
        {
            deps: [reloadCount],
            dataFn: data => data.jobExecutionStatus,
        }
    )

    const pauseAllJobs = () => {
        callGraphQL({
            query: gql`
                mutation PauseAllJobs {
                    pauseAllJobs {
                        ok
                        error
                    }
                }
            `,
        }).finally(() => {
            setReloadCount(previous => previous + 1)
        })
    }

    const resumeAllJobs = () => {
        callGraphQL({
            query: gql`
                mutation ResumeAllJobs {
                    resumeAllJobs {
                        ok
                        error
                    }
                }
            `,
        }).finally(() => {
            setReloadCount(previous => previous + 1)
        })
    }

    return (
        <>
            {
                !jobExecutionStatus && <Alert
                    type="info"
                    title={
                        <Space>
                            <Spin size="small"/>
                            Loading the job execution status
                        </Space>
                    }
                />
            }
            {
                jobExecutionStatus && <>
                    {
                        jobExecutionStatus.paused && <Alert
                            showIcon
                            type="warning"
                            title="Execution of all jobs is paused. They can be launched manually."
                            action={
                                <Popover
                                    title="Resume execution of all jobs"
                                    content="All schedules will be restored."
                                >
                                    <Button size="small" danger onClick={resumeAllJobs}>
                                        Resume execution of all jobs
                                    </Button>
                                </Popover>
                            }
                        />
                    }
                    {
                        !jobExecutionStatus.paused && <Alert
                            showIcon
                            type="success"
                            title="Jobs are running normally."
                            action={
                                <Popover
                                    title="Pause execution of all jobs"
                                    content="All schedules will be cancelled. No job will run automatically, but they can still be launched manually."
                                >
                                    <Button size="small" danger onClick={pauseAllJobs}>
                                        Pause execution of all jobs
                                    </Button>
                                </Popover>
                            }
                        />
                    }
                </>
            }
        </>
    )
}