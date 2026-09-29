import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

export const useJiraConfigurationUrl = (configName) => {

    const {data} = useQuery(
        gql`
            query GetJiraConfiguration($config: String!) {
                jiraConfiguration(name: $config) {
                    url
                }
            }
        `,
        {
            variables: {config: configName},
            deps: [configName],
            condition: !!configName,
            initialData: '',
            dataFn: data => data.jiraConfiguration?.url,
        }
    )

    return data ?? ''
}