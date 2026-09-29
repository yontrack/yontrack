import {useQuery} from "@components/services/GraphQL";
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

const noJobStates = {
    list: [],
    index: {},
}

export const useJobStates = () => {
    const {data: states} = useQuery(
        gql`
            query JobStates {
                jobStateInfos {
                    name
                    displayName
                    description
                }
            }
        `,
        {
            initialData: noJobStates,
            dataFn: data => {
                const infos = data.jobStateInfos.map(info => ({
                    ...info,
                    icon: jobStateIcons[info.name],
                }))
                const index = {}
                infos.forEach(info => {
                    index[info.name] = info
                })
                return {
                    list: infos,
                    index,
                }
            },
        }
    )

    return states ?? noJobStates
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