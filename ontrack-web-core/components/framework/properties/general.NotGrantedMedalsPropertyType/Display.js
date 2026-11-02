import {Typography} from "antd";

export default function Display({property}) {
    return (
        <>
            {
                property.value && <>
                    {
                        property.value.enabled &&
                        <Typography.Text>The medals of the auto-promoted levels not granted are shown.</Typography.Text>
                    }
                    {
                        !property.value.enabled &&
                        <Typography.Text>The medals of the auto-promoted levels not granted are hidden.</Typography.Text>
                    }
                </>
            }
        </>
    )
}
