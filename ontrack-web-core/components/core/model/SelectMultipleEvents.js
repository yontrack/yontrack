import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select, Space, Tag} from "antd";

export default function SelectMultipleEvents({id, value, onChange, style}) {

    const {data: eventTypes, loading, finished} = useQuery(
        gql`
            query Events {
                eventTypes {
                    value: id
                    label: description
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.eventTypes,
        }
    )

    return (
        <>
            <Select
                id={id}
                data-testid={id}
                style={style}
                loading={loading || !finished}
                options={eventTypes}
                allowClear={true}
                mode="multiple"
                value={value}
                onChange={onChange}
                optionRender={(option) =>
                    <Space>
                        <Tag>{option.value}</Tag>
                        {option.label}
                    </Space>
                }
            />
        </>
    )
}