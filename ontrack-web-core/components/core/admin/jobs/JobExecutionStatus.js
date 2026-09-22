import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {Alert, Button, Popover, Space, Spin} from "antd";

export default function JobExecutionStatus() {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(true)
    const [jobExecutionStatus, setJobExecutionStatus] = useState()
    const [reloadCount, setReloadCount] = useState(0)

    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    query JobExecutionStatus {
                        jobExecutionStatus {
                            paused
                        }
                    }
                `
            ).then(data => {
                setJobExecutionStatus(data.jobExecutionStatus)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, reloadCount])

    const pauseAllJobs = () => {
        setLoading(true)
        client.request(
            gql`
                mutation PauseAllJobs {
                    pauseAllJobs {
                        ok
                        error
                    }
                }
            `
        ).finally(() => {
            setLoading(false)
            setReloadCount(previous => previous + 1)
        })
    }

    const resumeAllJobs = () => {
        setLoading(true)
        client.request(
            gql`
                mutation ResumeAllJobs {
                    resumeAllJobs {
                        ok
                        error
                    }
                }
            `
        ).finally(() => {
            setLoading(false)
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