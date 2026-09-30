import {CountTag} from "@components/framework/validation-run-data/CountTag";
import {Space} from "antd";
import FindingSeverityCountTag from "@components/extension/findings/FindingSeverityCountTag";
import {FINDING_SEVERITIES} from "@components/extension/findings/findingsModel";

// The known severities; the unknown one is only shown when there is any
const LEVELS = FINDING_SEVERITIES.filter(it => it !== 'UNKNOWN')

// {"levels":{"CRITICAL":1,"HIGH":0,"MEDIUM":2,"LOW":0},"unknown":1,"accepted":3}
export default function FindingsValidationDataType({levels, unknown, accepted}) {
    return (
        <Space size={0}>
            {
                LEVELS.map(severity =>
                    typeof levels?.[severity] === 'number' &&
                    <FindingSeverityCountTag
                        key={severity}
                        severity={severity}
                        count={levels[severity]}
                        short={true}
                    />
                )
            }
            {
                unknown > 0 &&
                <FindingSeverityCountTag severity="UNKNOWN" count={unknown} short={true}/>
            }
            {
                accepted > 0 &&
                <CountTag count={accepted} color="green" title="# of accepted findings"/>
            }
        </Space>
    )
}
