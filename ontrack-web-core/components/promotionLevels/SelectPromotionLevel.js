import {Select, Space, Typography} from "antd";
import {gql} from "graphql-request";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import InlineError from "@components/common/InlineError";
import {useQuery} from "@components/services/GraphQL";

export default function SelectPromotionLevel({
                                                 branch,
                                                 value,
                                                 onChange,
                                                 useName = false,
                                                 allowClear = false,
                                                 disabled = false,
                                                 placeholder = "Promotion level",
                                                 multiple = false,
                                                 id,
                                             }) {

    const {data: promotionLevels, error} = useQuery(
        gql`
            query GetPromotionLevels($branchId: Int!) {
                branches(id: $branchId) {
                    promotionLevels {
                        id
                        name
                        image
                        description
                        annotatedDescription
                    }
                }
            }
        `,
        {
            variables: {branchId: branch ? Number(branch.id) : undefined},
            deps: [branch?.id],
            condition: !!branch,
            dataFn: data => data.branches[0].promotionLevels,
        }
    )

    const options = (promotionLevels ?? []).map(pl => ({
        value: useName ? pl.name : pl.id,
        label: <Space>
            <PromotionLevelImage promotionLevel={pl}/>
            <Typography.Text>{pl.name}</Typography.Text>
        </Space>
    }))

    // Without this, a failed lookup was indistinguishable from a branch with no promotion levels.
    // With no branch, there is nothing to load from: an error left over from a previous branch is dropped.
    if (branch && error) {
        return <InlineError message={error}/>
    }

    return (
        <Select
            id={id}
            data-testid={id}
            disabled={disabled}
            placeholder={placeholder}
            options={options}
            value={value}
            onChange={onChange}
            allowClear={allowClear}
            mode={multiple ? "multiple" : undefined}
        />
    )
}
