import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

export default function SelectValidationDataType({value, onChange, onValidationDataTypeSelected, allowClear = false}) {

    const {data: types, loading, finished} = useQuery(
        gql`
            query GetValidationTypes {
                validationDataTypes {
                    value: id
                    label: displayName
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.validationDataTypes,
        }
    )

    const onLocalChange = (value) => {
        if (onChange) onChange(value)
        if (onValidationDataTypeSelected) {
            onValidationDataTypeSelected(value)
        }
    }

    return (
        <>
            <Select
                options={types ?? []}
                loading={loading || !finished}
                value={value}
                onChange={onLocalChange}
                allowClear={allowClear}
            />
        </>
    )

}
