import {Divider, Space} from "antd";
import RunInfoSource from "@components/common/RunInfoSource";
import RunInfoTime from "@components/common/RunInfoTime";

export default function RunInfo({info, mode = "complete"}) {
    if (!info) return null

    // Both children render nothing when their half of the info is missing, and a divider with
    // nothing on one side of it is a stray mark rather than a separator. This matters since
    // builds carry run info too (#1671): theirs records which CI run produced the build, and has
    // no run time at all. The two conditions below mirror the children's own.
    const hasSource = Boolean(info.sourceType && info.sourceUri)
    const hasTime = Boolean(info.runTime)

    return (
        <>
            <Space size={mode === "complete" ? 4 : 1}>
                <RunInfoSource info={info} mode={mode}/>
                {hasSource && hasTime && <Divider type="vertical"/>}
                <RunInfoTime info={info} mode={mode}/>
            </Space>
        </>
    )
}
