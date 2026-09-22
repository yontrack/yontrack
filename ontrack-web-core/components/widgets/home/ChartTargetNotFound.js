import {Alert} from "antd";
import PaddedContent from "@components/common/PaddedContent";

/**
 * Body of a chart widget whose configured target does not exist.
 *
 * Replaces the chart, which would have nothing to draw, with the reason and the way out. There is
 * no "reconfigure" button on purpose: a widget can only be saved while the whole dashboard is in
 * edition mode, where the cell already carries its configure command.
 *
 * @param entity What was looked up, capitalised: "Promotion level" or "Validation stamp"
 */
export default function ChartTargetNotFound({entity, name, project, branch}) {
    return (
        <PaddedContent>
            <Alert
                type="warning"
                showIcon
                title={`${entity} ${name} does not exist on branch ${branch} of project ${project}.`}
                description="Edit the dashboard to reconfigure this widget."
            />
        </PaddedContent>
    )
}
