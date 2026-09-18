import {Checkbox, Input, Segmented, Select, Space} from "antd"
import {useQuery} from "@components/services/GraphQL"
import {
    gqlEnvironmentTags,
    gqlProjectLabels,
} from "@components/extension/environments/matrix/environmentMatrixGraphQL"
import {
    labelName,
    SCOPE_ALL,
    SCOPE_FAVOURITES,
} from "@components/extension/environments/matrix/environmentMatrixModel"
import Freshness from "@components/extension/environments/shared/Freshness"

/**
 * The matrix toolbar: what to look at, and how fresh it is.
 *
 * Every control writes straight through to the URL (see `useMatrixFilter`) rather than holding a
 * draft of its own, so what the screen shows and what the address says can never differ. The only
 * exception is the search box, which applies on Enter or on the search button: applying a project
 * search on every keystroke would push a history entry per letter.
 *
 * @param {Object} filter The current filter
 * @param {function} onFilter Called with the changed fields
 * @param {Object} freshness The `useFreshness` state driving the "Updated N s ago" line
 */
export default function EnvironmentMatrixToolbar({filter, onFilter, freshness}) {

    const {data: labels} = useQuery(gqlProjectLabels, {
        initialData: [],
        dataFn: data => data.labels,
    })

    const {data: tags} = useQuery(gqlEnvironmentTags, {
        initialData: [],
        dataFn: data => {
            const all = new Set()
            data.environments.forEach(environment => (environment.tags ?? []).forEach(tag => all.add(tag)))
            return Array.from(all).sort()
        },
    })

    return (
        <Space wrap size="middle" data-testid="matrix-toolbar">
            <Input.Search
                placeholder="Project"
                allowClear
                defaultValue={filter.project}
                style={{width: "16em"}}
                data-testid="matrix-search-project"
                onSearch={value => onFilter({project: value ?? ''})}
            />
            <Segmented
                value={filter.scope}
                data-testid="matrix-scope"
                options={[
                    {label: "Favourites", value: SCOPE_FAVOURITES},
                    {label: "All", value: SCOPE_ALL},
                ]}
                onChange={value => onFilter({scope: value})}
            />
            <Select
                placeholder="Label"
                allowClear
                value={filter.label ?? undefined}
                style={{width: "14em"}}
                data-testid="matrix-label"
                options={labels.map(label => ({value: label.id, label: labelName(label)}))}
                onChange={value => onFilter({label: value ?? null})}
            />
            <Select
                mode="multiple"
                placeholder="Tags"
                allowClear
                value={filter.tags ?? []}
                style={{minWidth: "14em"}}
                data-testid="matrix-tags"
                options={tags.map(tag => ({value: tag, label: tag}))}
                onChange={value => onFilter({tags: value ?? []})}
            />
            <Checkbox
                checked={!!filter.activity}
                data-testid="matrix-activity"
                onChange={event => onFilter({activity: event.target.checked})}
            >
                Only with activity
            </Checkbox>
            <Freshness
                refreshedAt={freshness.refreshedAt}
                refresh={freshness.refresh}
                testId="matrix-freshness"
            />
        </Space>
    )
}
