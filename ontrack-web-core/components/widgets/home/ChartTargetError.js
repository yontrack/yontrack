import {Alert} from "antd";
import PaddedContent from "@components/common/PaddedContent";

/**
 * Body of a chart widget whose target could not be resolved because the query failed.
 *
 * Distinct from `ChartTargetNotFound`: a network or server error says nothing about whether the
 * configured entity exists, so the title keeps the configured names unmarked and the body carries
 * the error instead of staying empty.
 */
export default function ChartTargetError({error}) {
    return (
        <PaddedContent>
            <Alert
                type="error"
                showIcon
                title="The chart target could not be loaded."
                description={error}
            />
        </PaddedContent>
    )
}
