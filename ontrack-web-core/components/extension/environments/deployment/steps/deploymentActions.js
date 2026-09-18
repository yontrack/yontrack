import {useState} from "react";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {getGraphQLErrors} from "@components/services/graphql-utils";

const useDeploymentAction = ({deployment, mutation, userNodeName, statusNodeName, onError, onSuccess}) => {
    const client = useGraphQLClient()
    const [loading, setLoading] = useState(false)

    const action = async (config = {variables: {}}) => {
        setLoading(true)
        try {
            const data = await client.request(
                mutation,
                {
                    id: deployment.id,
                    ...config.variables,
                }
            )
            const errors = getGraphQLErrors(data, userNodeName)
            if (errors && errors.length > 0 && onError) {
                onError(errors)
            } else {
                const userNode = data[userNodeName]
                if (statusNodeName) {
                    const statusNode = userNode[statusNodeName]
                    // Status
                    if (statusNode.ok) {
                        if (onSuccess) {
                            onSuccess()
                        }
                    } else {
                        onError(statusNode.message)
                    }
                } else if (onSuccess) {
                    onSuccess()
                }
            }
        } finally {
            setLoading(false)
        }
    }

    return {
        action,
        loading,
    }
}

export const useDeploymentRunAction = ({deployment, onSuccess, onError}) => {
    return useDeploymentAction({
        deployment,
        mutation: gql`
            mutation RunDeployment($id: String!) {
                startSlotPipelineDeployment(input: {
                    pipelineId: $id,
                }) {
                    deploymentStatus {
                        ok
                        message
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        userNodeName: 'startSlotPipelineDeployment',
        statusNodeName: 'deploymentStatus',
        onSuccess: onSuccess,
        onError: onError,
    })
}

export const useDeploymentFinishAction = ({deployment, onSuccess, onError}) => {
    return useDeploymentAction({
        deployment,
        mutation: gql`
            mutation FinishDeployment($id: String!) {
                finishSlotPipelineDeployment(input: {
                    pipelineId: $id,
                    forcing: false,
                    message: null,
                }) {
                    finishStatus {
                        ok
                        message
                    }
                    errors {
                        message
                    }
                }
            }
        `,
        userNodeName: 'finishSlotPipelineDeployment',
        statusNodeName: 'finishStatus',
        onSuccess: onSuccess,
        onError: onError,
    })
}

export const useDeploymentCancelAction = ({deployment, onSuccess, onError}) => {
    return useDeploymentAction({
        deployment,
        mutation: gql`
            mutation CancelDeployment($id: String!, $reason: String!) {
                cancelSlotPipeline(input: {
                    pipelineId: $id,
                    reason: $reason,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNodeName: 'cancelSlotPipeline',
        statusNodeName: undefined,
        onSuccess: onSuccess,
        onError: onError,
    })
}
