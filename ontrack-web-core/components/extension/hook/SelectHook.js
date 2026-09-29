import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

export default function SelectHook({id, value, onChange}) {
    const {data: options, loading, finished} = useQuery(
        gql`
            query Hooks {
                hookRecordFilterInfo {
                    hooks
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.hookRecordFilterInfo?.hooks?.map(hook => ({
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