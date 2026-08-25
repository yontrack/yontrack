import {Select, Space, Typography} from "antd";
import {useEffect, useState} from "react";
import {gql} from "graphql-request";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import InlineError from "@components/common/InlineError";
import {genericGraphQLErrorMessage} from "@components/services/GraphQL";

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

    const client = useGraphQLClient()

    const [options, setOptions] = useState([])
    const [error, setError] = useState()

    useEffect(() => {
        if (branch && client) {
            client.request(
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
                {branchId: Number(branch.id)}
            ).then(data => {
                setError(undefined)
                setOptions(data.branches[0].promotionLevels.map(pl => {
                    return {
                        value: useName ? pl.name : pl.id,
                        label: <Space>
                            <PromotionLevelImage promotionLevel={pl}/>
                            <Typography.Text>{pl.name}</Typography.Text>
                        </Space>
                    }
                }))
            }).catch(ex => {
                // Without this, the rejection was swallowed and the dropdown just stayed empty,
                // indistinguishable from a branch with no promotion levels.
                setError(ex.message || genericGraphQLErrorMessage)
                setOptions([])
            })
        } else {
            // nothing to load from: drop any error left over from a previous branch
            setError(undefined)
        }
    }, [client, branch]);

    if (error) {
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