import {CountTag} from "@components/framework/validation-run-data/CountTag";
import {Space} from "antd";

// {"levels":{"CRITICAL":1,"HIGH":0,"MEDIUM":2,"LOW":0},"unknown":1,"accepted":3}
export default function FindingsValidationDataType({levels, unknown, accepted}) {
    return (
        <Space size={0}>
            <CountTag count={levels?.CRITICAL} color="error" title="# of critical findings"/>
            <CountTag count={levels?.HIGH} color="warning" title="# of high severity findings"/>
            <CountTag count={levels?.MEDIUM} color="blue" title="# of medium severity findings"/>
            <CountTag count={levels?.LOW} color="default" title="# of low severity findings"/>
            {
                unknown > 0 &&
                <CountTag count={unknown} color="default" title="# of findings of unknown severity"/>
            }
            {
                accepted > 0 &&
                <CountTag count={accepted} color="green" title="# of accepted findings"/>
            }
        </Space>
    )
}
