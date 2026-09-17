import {gql} from "graphql-request";
import {Select} from "antd";
import {useQuery} from "@components/services/GraphQL";
import LabelChip, {labelDisplay} from "@components/labels/LabelChip";
import {gqlLabelFragment} from "@components/labels/LabelGraphQLFragments";

/**
 * Selection of labels, identified by their display string (`category:name`, or `name` alone when
 * they have no category) - the same strings the label filters of the API take.
 *
 * With `multiple`, the value is an array of display strings, which is what the label filter of a
 * project list sends to `paginatedProjects(labels:)`; without it, the value is one display string.
 */
export default function SelectLabel({value, onChange, multiple = false, placeholder, style}) {

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
                mode={multiple ? "multiple" : undefined}
                value={value}
                onChange={onChange}
                options={options}
                loading={loading}
                placeholder={placeholder}
                allowClear={true}
                style={style}
                // The options carry a chip as their label, so the text to match must be taken
                // from the value - the display string - instead
                optionFilterProp="value"
            />
        </>
    )
}
