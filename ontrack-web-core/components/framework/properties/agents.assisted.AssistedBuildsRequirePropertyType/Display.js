import {Space, Tag, Typography} from "antd";
import LicenceNotice from "./LicenceNotice";

/**
 * *Assisted builds require* on a promotion level: the validation stamps an assisted build must pass
 * before being promoted to it.
 */
export default function Display({property}) {
    const stamps = property.value?.validationStamps ?? []
    return (
        <Space orientation="vertical" size={4}>
            {
                stamps.length > 0 ?
                    <>
                        <Typography.Text>
                            If the build is assisted, these validations must pass first:
                        </Typography.Text>
                        <Space wrap data-testid="assisted-builds-require-stamps">
                            {stamps.map(name => <Tag key={name}>{name}</Tag>)}
                        </Space>
                        <Typography.Text type="secondary">
                            A build whose assisted change is unknown, or not computed yet, counts as assisted.
                        </Typography.Text>
                    </> :
                    <Typography.Text type="secondary" data-testid="assisted-builds-require-none">
                        No validation is required of assisted builds.
                    </Typography.Text>
            }
            <LicenceNotice/>
        </Space>
    )
}
