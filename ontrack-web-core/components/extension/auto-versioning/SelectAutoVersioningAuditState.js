import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import {Select} from "antd";

const noStates = []

export default function SelectAutoVersioningAuditState({value, onChange}) {

    const {data} = useQuery(
        gql`
            query AutoVersioningAuditStates {
                autoVersioningAuditStates
            }
        `,
        {
            initialData: noStates,
            dataFn: data => data.autoVersioningAuditStates.map(id => ({
                value: id,
                label: id,
            })),
        }
    )
    const states = data ?? noStates

    return (
        <>
            <Select
                options={states}
                value={value}
                onChange={onChange}
                style={{
                    width: '20em',
                }}
            />
        </>
    )
}