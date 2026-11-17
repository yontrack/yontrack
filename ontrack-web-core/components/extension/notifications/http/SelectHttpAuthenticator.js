import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

export default function SelectHttpAuthenticator({id, value, onChange, onTypeChange}) {
    const {loading, finished, data: options} = useQuery(
        gql`
            query SelectHttpAuthenticator {
                httpAuthenticators {
                    value: type
                    label: displayName
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.httpAuthenticators,
        }
    )

    const onLocalChange = (value) => {
        if (onTypeChange) onTypeChange(value)
        if (onChange) onChange(value)
    }

    return (
        <Select
            id={id}
            data-testid="http-authentication-type"
            options={options}
            loading={loading || !finished}
            value={value}
            onChange={onLocalChange}
        />
    )
}
