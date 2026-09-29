import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

export default function SelectPropertyType({projectEntityType = null, value, onChange, width = "16em"}) {

    const {data, loading, finished} = useQuery(
        gql`
            query PropertyTypes($projectEntityType: ProjectEntityType) {
                properties(projectEntityType: $projectEntityType) {
                    value: typeName
                    label: name
                }
            }
        `,
        {
            variables: {projectEntityType},
            dataFn: data => data.properties,
        }
    )

    return (
        <>
            <Select
                loading={loading || !finished}
                options={data}
                allowClear={true}
                value={value}
                onChange={onChange}
                style={{width: width}}
            />
        </>
    )

}