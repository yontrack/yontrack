import {Select, Space, Typography} from "antd";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import InlineError from "@components/common/InlineError";
import useItemsOfBranches from "@components/branches/useItemsOfBranches";

/**
 * @param branch Branch to select a promotion level of
 * @param branches Several branches, in place of `branch`: their levels are listed once per name
 */
export default function SelectPromotionLevel({
                                                 branch,
                                                 branches,
                                                 value,
                                                 onChange,
                                                 useName = false,
                                                 allowClear = false,
                                                 disabled = false,
                                                 placeholder = "Promotion level",
                                                 multiple = false,
                                                 id,
                                             }) {

    const {items: promotionLevels, error} = useItemsOfBranches({
        name: "GetPromotionLevels",
        field: "promotionLevels",
        selection: "id name image description annotatedDescription",
        branch,
        branches,
    })

    const options = promotionLevels.map(pl => ({
        value: useName ? pl.name : pl.id,
        label: <Space>
            <PromotionLevelImage promotionLevel={pl}/>
            <Typography.Text>{pl.name}</Typography.Text>
        </Space>
    }))

    // Without this, a failed lookup was indistinguishable from a branch with no promotion levels.
    // With no branch, there is nothing to load from: an error left over from a previous branch is dropped.
    if ((branch || branches) && error) {
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
