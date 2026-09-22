import {Space, Typography} from "antd";
import AutoVersioningConfigPath from "@components/extension/auto-versioning/AutoVersioningConfigPath";

export default function AutoVersioningAdditionalPaths({additionalPaths}) {
    return (
        <>
            {
                additionalPaths && additionalPaths.length > 0 && <Space orientation="vertical">
                    {
                        additionalPaths.map((configPath, index) =>
                            <div key={index} className="ot-form-list-item">
                                <AutoVersioningConfigPath configPath={configPath}/>
                            </div>
                        )
                    }
                </Space>
            }
            {
                (!additionalPaths || additionalPaths.length === 0) && <Typography.Text italic>None</Typography.Text>
            }
        </>
    )
}