import {useEffect, useState} from "react";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {Select} from "antd";

export default function SelectValidationDataType({value, onChange, onValidationDataTypeSelected, allowClear = false}) {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(true)
    const [types, setTypes] = useState([])

    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    query GetValidationTypes {
                        validationDataTypes {
                            value: id
                            label: displayName
                        }
                    }
                `
            ).then(data => {
                setTypes(data.validationDataTypes)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client])

    const onLocalChange = (value) => {
        if (onChange) onChange(value)
        if (onValidationDataTypeSelected) {
            onValidationDataTypeSelected(value)
        }
    }

    return (
        <>
            <Select
                options={types}
                loading={loading}
                value={value}
                onChange={onLocalChange}
                allowClear={allowClear}
            />
        </>
    )

}