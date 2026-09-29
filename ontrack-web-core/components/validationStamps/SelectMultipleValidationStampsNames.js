import MultipleSelectSearch from "@components/common/MultipleSelectSearch";
import {gql} from "graphql-request";
import {callGraphQL} from "@components/services/GraphQL";

export default function SelectMultipleValidationStampsNames({value, onChange}) {

    const fetchValidationNames = async (token) => {
        return callGraphQL({
            query: gql`
                query ValidationStampNames($token: String!) {
                    validationStampNames(token: $token)
                }
            `,
            variables: {token},
        }).then(data => data.validationStampNames.map(name => ({
            value: name,
            label: name,
        })))
    }

    return (
        <>
            <MultipleSelectSearch
                mode="multiple"
                value={value}
                placeholder="Select validations"
                fetchOptions={fetchValidationNames}
                onChange={(newValue) => {
                    const values = newValue ? newValue.map(it => it.value) : []
                    if (onChange) onChange(values)
                }}
            />
        </>
    )
}