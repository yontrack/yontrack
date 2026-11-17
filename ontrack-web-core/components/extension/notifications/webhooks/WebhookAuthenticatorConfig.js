import {Dynamic} from "@components/common/Dynamic";

export default function WebhookAuthenticatorConfig({authenticationType, prefix, creation}) {
    return (
        <>
            <Dynamic
                path={`framework/http-authenticator/${authenticationType}/Form.js`}
                props={{prefix, creation}}
            />
        </>
    )
}