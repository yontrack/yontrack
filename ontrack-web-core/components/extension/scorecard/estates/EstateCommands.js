import {gql} from "graphql-request";
import {Modal, Typography} from "antd";
import {FaPencilAlt, FaPlus, FaSync, FaTrash} from "react-icons/fa";
import {Command} from "@components/common/Commands";
import {callGraphQL} from "@components/services/GraphQL";
import {processGraphQLErrors} from "@components/services/graphql-utils";
import {useMessageApi} from "@components/providers/MessageProvider";
import {useRecomputePolling} from "@components/extension/scorecard/ScorecardRecompute";
import {latestEstateComputedAt} from "@components/extension/scorecard/estates/estateModel";

const {confirm} = Modal

/**
 * "New estate", opening the estate dialog for a new estate.
 */
export function EstateCreateCommand({dialog}) {
    return (
        <Command
            icon={<FaPlus/>}
            text="New estate"
            testId="estate-create"
            action={() => dialog.start({})}
        />
    )
}

/**
 * Edition of an estate in the estate dialog, its whole definition being replaced. Its snapshots are
 * kept.
 */
export function EstateUpdateCommand({estate, dialog}) {
    return (
        <Command
            icon={<FaPencilAlt/>}
            title={`Edit the ${estate.name} estate`}
            testId={`estate-edit-${estate.name}`}
            action={() => dialog.start({estate})}
        />
    )
}

/**
 * Deletion of an estate, after a confirmation: its snapshots go with it.
 */
export function EstateDeleteCommand({estate, onChange}) {

    const messageApi = useMessageApi()

    const onAction = () => {
        confirm({
            title: `Do you really want to delete the "${estate.name}" estate?`,
            content: <Typography.Text>
                Its readings, today&apos;s and past ones, are deleted with it. Its projects and labels are kept.
            </Typography.Text>,
            okText: "Delete",
            okType: "danger",
            onOk: async () => {
                const data = await callGraphQL({
                    query: gql`
                        mutation DeleteEstate($id: Int!) {
                            deleteEstate(input: {id: $id}) {
                                errors {
                                    message
                                }
                            }
                        }
                    `,
                    variables: {id: Number(estate.id)},
                })
                if (processGraphQLErrors(data, 'deleteEstate', messageApi)) {
                    if (onChange) onChange()
                }
            },
        })
    }

    return (
        <Command
            icon={<FaTrash/>}
            title={`Delete the ${estate.name} estate`}
            testId={`estate-delete-${estate.name}`}
            action={onAction}
        />
    )
}

const gqlRecomputeEstate = gql`
    mutation RecomputeEstate($id: Int!) {
        recomputeEstate(input: {id: $id}) {
            errors {
                message
            }
        }
    }
`

/**
 * Recompute of the readings of every project of an estate, queued as a job, the estates being
 * reloaded (`refresh`) until readings computed after the latest ones known show up.
 *
 * @return `{recompute, polling, error}`
 */
export const useEstateRecompute = ({estate, refresh}) =>
    useRecomputePolling({
        latest: latestEstateComputedAt(estate),
        refresh,
        request: async () => {
            const data = await callGraphQL({query: gqlRecomputeEstate, variables: {id: Number(estate.id)}})
            return data?.recomputeEstate?.errors?.[0]?.message ?? null
        },
    })

/**
 * "Recompute" of an estate, spinning while its new readings are awaited. An estate selecting no
 * project has nothing to recompute.
 */
export function EstateRecomputeCommand({estate, recompute, polling}) {
    const empty = (estate.projects ?? []).length === 0
    return (
        <Command
            icon={<FaSync className={polling ? 'anticon-spin' : undefined}/>}
            title={
                empty ?
                    'The estate selects no project: there is nothing to recompute' :
                    polling ?
                        'Recomputing the readings of the projects of the estate' :
                        `Recompute the readings of the projects of the ${estate.name} estate, overwriting those of the day`
            }
            testId={`estate-recompute-${estate.name}`}
            disabled={empty || polling}
            action={recompute}
        />
    )
}
