import {Space, Typography} from "antd";

export default function Display({property}) {
    return (
        <Space orientation="vertical">
            {/*  Disabling  */}
            {property.value.disablingDuration &&
                <Typography.Text strong type="warning">
                    Inactive branches are disabled after {property.value.disablingDuration} day(s).
                </Typography.Text>
            }
            {/*  Deletion  */}
            {property.value.disablingDuration &&
                property.value.deletingDuration &&
                <Typography.Text strong type="danger">
                    Inactive branches are deleted
                    after {property.value.disablingDuration + property.value.deletingDuration} day(s).
                </Typography.Text>
            }
            {/*  Includes  */}
            {property.value.includes &&
                <>
                    <Typography.Text>
                        Keeping branches matching:
                        <Typography.Text code>{property.value.includes}</Typography.Text>
                    </Typography.Text>
                    {
                        property.value.excludes &&
                        <>
                            but excluding branches matching:
                            <Typography.Text code>{property.value.excludes}</Typography.Text>
                        </>
                    }
                </>
            }
            {/*  Promotions to keep  */}
            {property.value.promotionsToKeep &&
                <Typography.Text>
                    Keeping branches promoted to: {property.value.promotionsToKeep.join(", ")}
                </Typography.Text>
            }
        </Space>
    )
}