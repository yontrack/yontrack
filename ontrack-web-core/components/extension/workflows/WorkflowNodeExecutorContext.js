import {createContext, useContext, useEffect, useState} from "react";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const WorkflowNodeExecutorContext = createContext([])

const NO_EXECUTORS = []

export const useWorkflowNodeExecutor = (executorId, dependencies = []) => {
    const [executor, setExecutor] = useState({})
    const context = useContext(WorkflowNodeExecutorContext)
    useEffect(() => {
        if (executorId && context) {
            setExecutor(context.find(it => it.id === executorId))
        }
    }, [dependencies, executorId, context]);
    return executor
}

export default function WorkflowNodeExecutorContextProvider({children}) {

    const {data} = useQuery(
        gql`
            query WorkflowNodeExecutors {
                workflowNodeExecutors(enabled: true) {
                    id
                    displayName
                }
            }
        `,
        {
            initialData: NO_EXECUTORS,
            dataFn: data => data.workflowNodeExecutors,
        }
    )
    const executors = data ?? NO_EXECUTORS

    return (
        <>
            <WorkflowNodeExecutorContext.Provider value={executors}>
                {children}
            </WorkflowNodeExecutorContext.Provider>
        </>
    )
}