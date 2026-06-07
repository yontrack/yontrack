import {Select, Space, Typography} from "antd";
import {gql} from "graphql-request";
import ValidationStampImage from "@components/validationStamps/ValidationStampImage";
import {useQuery} from "@components/services/GraphQL";

export default function SelectValidationStamp({
                                                  disabled,
                                                  branch, value, onChange, onValidationStampSelected,
                                                  useName = false,
                                                  allowClear = false,
                                                  multiple = false,
                                                  width,
                                              }) {

    const {data: validationStamps = []} = useQuery(
        gql`
            query GetValidationStamps($branchId: Int!) {
                branches(id: $branchId) {
                    validationStamps {
                        id
                        name
                        image
                        description
                        annotatedDescription
                        dataType {
                            descriptor {
                                id
                            }
                            config
                        }
                    }
                }
            }
        `,
        {
            variables: {branchId: branch ? Number(branch.id) : undefined},
            deps: [branch?.id],
            condition: !!branch,
            dataFn: (data) => [...data.branches[0].validationStamps].sort((a, b) => a.name.localeCompare(b.name)),
        }
    )

    const options = (validationStamps ?? []).map(vs => ({
        value: useName ? vs.name : vs.id,
        label: <Space>
            <ValidationStampImage validationStamp={vs}/>
            <Typography.Text>{vs.name}</Typography.Text>
        </Space>
    }))

    const onLocalChange = (value) => {
        if (onChange) onChange(value)
        if (onValidationStampSelected) {
            const vs = (validationStamps ?? []).find(it => {
                if (useName) {
                    return it.name === value
                } else {
                    return it.id === value
                }
            })
            onValidationStampSelected(vs)
        }
    }

    return (
        <Select
            disabled={disabled}
            options={options}
            value={value}
            onChange={onLocalChange}
            allowClear={allowClear}
            mode={multiple ? "multiple" : undefined}
            style={{width}}
        />
    )
}