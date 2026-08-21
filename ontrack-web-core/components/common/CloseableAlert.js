import {Alert} from "antd";
import {useEffect, useState} from "react";
import {getLocalCloseableAlertClosed, setLocalCloseableAlertClosed} from "@components/storage/local";

export default function CloseableAlert({id, message, type = "warning"}) {

    // Starting as closed, so that the alert does not flash before the local storage is read
    const [closed, setClosed] = useState(true)
    useEffect(() => {
        setClosed(getLocalCloseableAlertClosed(id))
    }, [id])

    const onClose = () => {
        setLocalCloseableAlertClosed(id, true)
        setClosed(true)
    }

    return (
        <>
            {
                !closed &&
                <Alert
                    showIcon={true}
                    closable={true}
                    message={message}
                    type={type}
                    onClose={onClose}
                />
            }
        </>
    )
}