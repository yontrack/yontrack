import {Dynamic} from "@components/common/Dynamic";

export default function WebhookAuthenticatorSummary({authenticationType}) {
    return (
        <>
            <Dynamic
                path={`framework/http-authenticator/${authenticationType}/Summary.js`}
                props={{}}
            />
        </>
    )
}