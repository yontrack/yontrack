import {Tag} from "antd";

// {"warningLevel":{"level":"HIGH","value":1},"failedLevel":{"level":"CRITICAL","value":1},"warningPassesAutoPromotion":false}
export default function CHMLValidationDataType({warningLevel, failedLevel, warningPassesAutoPromotion}) {
    return (
        <>
            {
                warningLevel &&
                <Tag>
                    Warning if {warningLevel.level} &ge; {warningLevel.value}
                </Tag>
            }
            {
                failedLevel &&
                <Tag>
                    Failed if {failedLevel.level} &ge; {failedLevel.value}
                </Tag>
            }
            {
                warningPassesAutoPromotion &&
                <Tag data-testid="chml-warning-passes-auto-promotion">
                    Warning passes auto-promotion
                </Tag>
            }
        </>
    )
}