import {Descriptions, Space, Tag} from "antd";
import AutoDisablingBranchPatternsMode from "@components/extension/stale/AutoDisablingBranchPatternsMode";

export default function Display({property}) {
    return (
        <Space orientation="vertical">
            {
                property.value.items.map((item, index) => (
                    <>
                        <div key={index} className="ot-form-list-item">
                            <Descriptions
                                column={12}
                                items={[
                                    {
                                        key: "includes",
                                        label: "Includes",
                                        children: item.includes.map((regex, i) => (
                                            <Tag key={i}>{regex}</Tag>
                                        )),
                                        span: 12,
                                    },
                                    {
                                        key: "excludes",
                                        label: "Excludes",
                                        children: item.excludes.map((regex, i) => (
                                            <Tag key={i}>{regex}</Tag>
                                        )),
                                        span: 12,
                                    },
                                    {
                                        key: "mode",
                                        label: "Mode",
                                        children: <AutoDisablingBranchPatternsMode mode={item.mode}/>,
                                        span: 12,
                                    },
                                    {
                                        key: "keepLast",
                                        label: "Keep last",
                                        children: item.keepLast,
                                        span: 12,
                                    }
                                ]}
                            />
                        </div>
                    </>
                ))
            }
        </Space>
    )
}