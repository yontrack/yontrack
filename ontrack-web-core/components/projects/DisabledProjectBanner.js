import {Alert} from "antd";

export default function DisabledProjectBanner({project}) {
    return (
        <>
            {
                project.disabled &&
                <Alert
                    data-testid="banner-disabled"
                    type="warning"
                    title={
                        <>
                            This project is <b>disabled</b>. No auto-versioning or notifications
                            until it&apos;s enabled again.
                        </>
                    }
                />
            }
        </>
    )
}