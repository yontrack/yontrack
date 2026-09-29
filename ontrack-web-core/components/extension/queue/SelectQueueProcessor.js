import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

export default function SelectQueueProcessor({value, onChange}) {

    const {data: options, loading, finished} = useQuery(
        gql`
            query QueueProcessors {
                queueRecordFilterInfo {
                    processors
                }
            }
        `,
        {
            dataFn: data => {
                const options = [
                    {
                        value: '',
                        label: 'Any',
                    }
                ]
                options.push(
                    ...data.queueRecordFilterInfo.processors.map(it => ({
                        value: it,
                        label: it,
                    }))
                )
                return options
            }
        }
    )

    return (
        <>
            <Select
                loading={loading || !finished}
                options={options}
                value={value}
                onChange={onChange}
                style={{
                    width: "10em",
                }}
            />
        </>
    )

}