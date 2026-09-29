import {message, Space} from "antd";
import InlineCommand from "@components/common/InlineCommand";
import {FaPause, FaPlay, FaRedo, FaStop, FaTrash} from "react-icons/fa";
import {callGraphQL} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {useState} from "react";

export default function JobActions({job, onDone}) {

    const [messageApi, contextHolder] = message.useMessage()
    const [loading, setLoading] = useState(false)

    const jobAction = (mutation, verb) => {
        setLoading(true)
        callGraphQL({
            query: gql`
                mutation JobAction($id: Int!) {
                    ${mutation}(id: $id) {
                        ok
                        error
                    }
                }
            `,
            variables: {
                id: Number(job.id)
            },
        }).then(data => {
            const result = data[mutation]
            if (result.ok) {
                messageApi.success(`Job ${verb} successfully.`)
            } else if (result.error) {
                messageApi.error(`Job could not be ${verb}: ${result.error}`)
            } else {
                messageApi.error(`Job could not be ${verb}.`)
            }
            if (onDone) onDone()
        }).finally(() => {
            setLoading(false)
        })
    }

    const launchJob = () => {
        jobAction("launchJob", "launched")
    }

    const pauseJob = () => {
        jobAction("pauseJob", "paused")
    }

    const resumeJob = () => {
        jobAction("resumeJob", "resumed")
    }

    const stopJob = () => {
        jobAction("stopJob", "stopped")
    }

    const deleteJob = () => {
        jobAction("deleteJob", "deleted")
    }

    return (
        <>
            {contextHolder}
            <Space size={0}>
                {
                    job.canRun && <InlineCommand
                        icon={<FaPlay/>}
                        title="Launches this job immediately"
                        onClick={launchJob}
                        loading={loading}
                    />
                }
                {
                    job.canPause && <InlineCommand
                        icon={<FaPause/>}
                        title="Pauses this job - automatic schedule will be on hold"
                        onClick={pauseJob}
                        loading={loading}
                    />
                }
                {
                    job.canResume && <InlineCommand
                        icon={<FaRedo/>}
                        title="Resumes this job - automatic schedule will be restored"
                        onClick={resumeJob}
                        loading={loading}
                    />
                }
                {
                    job.canBeStopped && <InlineCommand
                        icon={<FaStop/>}
                        title="Stops this job"
                        onClick={stopJob}
                        loading={loading}
                    />
                }
                {
                    job.canBeDeleted && <InlineCommand
                        icon={<FaTrash/>}
                        title="Deleted this job"
                        onClick={deleteJob}
                        loading={loading}
                    />
                }
            </Space>
        </>
    )
}