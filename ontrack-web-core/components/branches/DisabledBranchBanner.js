import {Alert} from "antd";

export default function DisabledBranchBanner({branch}) {
    return (
        <>
            {
                branch.disabled &&
                <Alert
                    data-testid="banner-disabled"
                    type="warning"
                    title={
                        <>
                            This branch is <b>disabled</b>. No auto-versioning or notifications
                            until it&apos;s enabled again.
                        </>
                    }
                />
            }
        </>
    )
}