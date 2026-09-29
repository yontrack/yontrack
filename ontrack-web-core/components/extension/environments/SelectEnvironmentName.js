import {Select} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

const noEnvironments = []

export default function SelectEnvironmentName({id = "environment", projects = [], value, onChange}) {

    const {data, loading} = useQuery(
        gql`
            query SelectEnvironments(
                $projects: [String!],
            ) {
                environments(filter: {projects: $projects}) {
                    name
                }
            }
        `,
        {
            variables: {projects},
            // Loaded once, as before: a change of `projects` does not reload the list
            deps: [],
            initialData: noEnvironments,
            dataFn: data => data.environments.map(env => ({
                value: env.name,
                label: env.name,
            })),
        }
    )
    const environments = data ?? noEnvironments

    return (
        <>
            <Select
                id={id}
                data-testid={id}
                optionFilterProp="label"
                placeholder="Select environment"
                options={environments}
                loading={loading}
                value={value}
                allowClear
                onChange={onChange}
            />
        </>
    )
}