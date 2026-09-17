import {gql} from "graphql-request";
import {Select} from "antd";
import {useQuery} from "@components/services/GraphQL";
import LabelChip, {labelDisplay} from "@components/labels/LabelChip";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";

/**
 * Selection of one label, identified by its display string (`category:name`, or
 * `name` alone when it has no category) - the same string the label filters of
 * the API take.
 */
export default function SelectLabel({value, onChange}) {

    const {data, loading} = useQuery(
        gql`
            query GetLabels {
                labels {
                    ...labelFragment
                }
            }

            ${gqlLabelFragment}
        `,
        {
            dataFn: data => data.labels,
        }
    )

    const options = (data ?? []).map(label => ({
        value: labelDisplay(label),
        label: <LabelChip label={label} link={false}/>,
    }))

    return (
        <>
            <Select
                value={value}
                onChange={onChange}
                options={options}
                loading={loading}
                allowClear={true}
            />
        </>
    )
}
