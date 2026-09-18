import {Segmented, Select, Space, Typography} from "antd"
import Freshness from "@components/extension/environments/shared/Freshness"
import {
    hasSeveralQualifiers,
    VIEW_GRAPH,
    VIEW_MATRIX,
} from "@components/extension/environments/project/projectEnvironmentsModel"

/**
 * What to look at on a project's environments screen, and how fresh it is.
 *
 * Every control writes straight through to the URL (see `useProjectEnvironmentsView`) rather than
 * holding a draft of its own, so what the screen shows and what the address says can never differ -
 * the same rule as the matrix toolbar.
 *
 * @param {string} view Which of the two views is showing
 * @param {string} qualifier Which qualifier the graph is drawn for
 * @param {Array<{value: string, label: string}>} qualifiers The project's qualifiers, as options
 * @param {function} onChange Called with the changed fields
 * @param {Object} freshness The `useFreshness` state driving the "Updated N s ago" line
 */
export default function ProjectEnvironmentsToolbar({view, qualifier, qualifiers, onChange, freshness}) {

    return (
        <Space wrap size="middle" data-testid="project-environments-toolbar">
            <Segmented
                value={view}
                data-testid="project-environments-view"
                options={[
                    {label: "Graph", value: VIEW_GRAPH},
                    {label: "Matrix", value: VIEW_MATRIX},
                ]}
                onChange={value => onChange({view: value})}
            />
            {
                /*
                 * The selector belongs to the graph alone. `slotGraph(qualifier:)` answers for one
                 * qualifier, so the graph has to be told which; the matrix nests *every* qualifier
                 * of the project as rows of its own and needs no telling. Two controls saying
                 * different things about the same word would be worse than one that comes and goes
                 * with the view it belongs to.
                 */
                view === VIEW_GRAPH && hasSeveralQualifiers(qualifiers) &&
                <Space size={6}>
                    <Typography.Text type="secondary">Qualifier</Typography.Text>
                    <Select
                        value={qualifier}
                        style={{width: "12em"}}
                        data-testid="project-environments-qualifier"
                        options={qualifiers}
                        onChange={value => onChange({qualifier: value ?? ''})}
                    />
                </Space>
            }
            <Freshness
                refreshedAt={freshness.refreshedAt}
                refresh={freshness.refresh}
                testId="project-environments-freshness"
            />
        </Space>
    )
}
