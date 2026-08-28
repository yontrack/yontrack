import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {FaBan, FaCog, FaCogs, FaPauseCircle, FaSpinner} from "react-icons/fa";
import {useRefData} from "@components/providers/RefDataProvider";
import {Popover, Space} from "antd";

const jobStateIcons = {
    IDLE: <FaCog color="blue"/>,
    RUNNING: <FaSpinner color="green"/>,
    PAUSED: <FaPauseCircle/>,
    DISABLED: <FaCogs color="var(--ot-icon-muted)"/>,
    INVALID: <FaBan color="red"/>,
}

export const useJobStates = () => {
    const client = useGraphQLClient()
    const [states, setStates] = useState({
        list: [],
        index: {},
    })
    useEffect(() => {
        if (client) {
            client.request(
                gql`
                    query JobStates {
                        jobStateInfos {
                            name
                            displayName
                            description
                        }
                    }
                `
            ).then(data => {
                const infos = data.jobStateInfos.map(info => ({
                    ...info,
                    icon: jobStateIcons[info.name],
                }))
                const index = {}
                infos.forEach(info => {
                    index[info.name] = info
                })
                setStates({
                    list: infos,
                    index,
                })
            })
        }
    }, [client]);

    return states
}

export default function JobState({value, displayName, tooltip = true}) {
    const {jobStates} = useRefData()
    const info = jobStates.index[value]

    return (
        <>
            {
                tooltip &&
                <Popover
                    title={info?.displayName}
                    content={info?.description}
                >
                    <Space>
                        {
                            info?.icon && info.icon
                        }
                        {
                            (!info?.icon || displayName) && info?.displayName
                        }
                    </Space>
                </Popover>
            }
            {
                !tooltip && <Space>
                    {
                        info?.icon && info.icon
                    }
                    {
                        (!info?.icon || displayName) && info?.displayName
                    }
                </Space>
            }
        </>
    )
}