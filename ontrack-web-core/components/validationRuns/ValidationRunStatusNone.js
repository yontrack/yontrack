import ValidationRunStatusIcon from "@components/validationRuns/ValidationRunStatusIcon";
import {Space} from "antd";

export default function ValidationRunStatusNone({
                                                    disabled = false,
                                                    onClick,
                                                }) {
    return (
        <>
            <Space size={8} className={disabled ? undefined : "ot-command"} onClick={onClick}>
                {/* NONE is not in the status config table and resolves to the
                    neutral fallback mark. The name is given explicitly so the
                    accessible name is not the raw "NONE" id. */}
                <ValidationRunStatusIcon statusID={{id: 'NONE', name: 'No status'}}/>
            </Space>
        </>
    )
}