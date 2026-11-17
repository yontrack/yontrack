import {Dynamic} from "@components/common/Dynamic";

export default function HttpAuthenticationSummary({authenticationType}) {
    return (
        <>
            {
                authenticationType &&
                <Dynamic
                    path={`framework/http-authenticator/${authenticationType}/Summary.js`}
                    props={{}}
                />
            }
        </>
    )
}
