import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

export default function SelectHookState({id, value, onChange}) {
    const {data: options, loading, finished} = useQuery(
        gql`
            query HookStates {
                hookRecordFilterInfo {
                    states
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.hookRecordFilterInfo?.states?.map(hook => ({
                value: hook,
                label: hook,
            })) ?? []
        }
    )

    return (
        <>
            <Select
                id={id}
                value={value}
                onChange={onChange}
                allowClear
                style={{width: '12em'}}
                options={options}
                loading={loading || !finished}
            />
        </>
    )
}