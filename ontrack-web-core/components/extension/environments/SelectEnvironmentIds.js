import {Select} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {gqlEnvironmentData} from "@components/extension/environments/EnvironmentGraphQL";

const noEnvironments = []

export default function SelectEnvironmentIds({id = "environments", value, onChange}) {

    const {data, loading} = useQuery(
        gql`
            query SelectEnvironments {
                environments {
                    ...EnvironmentData
                }
            }
            ${gqlEnvironmentData}
        `,
        {
            initialData: noEnvironments,
            dataFn: data => data.environments.map(env => ({
                value: env.id,
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
                mode="multiple"
                optionFilterProp="label"
                placeholder="Select environments"
                options={environments}
                loading={loading}
                value={value}
                allowClear
                onChange={onChange}
            />
        </>
    )
}