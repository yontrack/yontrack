import {Form, Select, Space} from "antd";
import {
    FINDING_KINDS,
    FINDING_SEVERITIES,
    FINDING_STATES,
    kindName,
    severityName,
    stateName,
} from "@components/extension/findings/findingsModel";

function FilterSelect({label, field, value, options, onChange}) {
    return (
        <Form.Item label={label} style={{marginBottom: 0}}>
            <Select
                data-testid={`findings-filter-${field}`}
                aria-label={label}
                value={value}
                onChange={(newValue) => onChange(field, newValue)}
                allowClear={true}
                placeholder="Any"
                options={options}
                style={{minWidth: '10em'}}
                popupMatchSelectWidth={false}
            />
        </Form.Item>
    )
}

/**
 * The filter of the findings of a project: severity, state, branch, scanner and kind.
 *
 * The branches and the scanners to choose from are the ones the findings of the project know of.
 */
export default function ProjectFindingsFilter({filter, branches = [], scanners = [], onChange}) {
    return (
        <Space wrap size={16} data-testid="findings-filter">
            <FilterSelect
                label="Severity"
                field="severity"
                value={filter.severity}
                options={FINDING_SEVERITIES.map(it => ({value: it, label: severityName(it)}))}
                onChange={onChange}
            />
            <FilterSelect
                label="State"
                field="state"
                value={filter.state}
                options={FINDING_STATES.map(it => ({value: it, label: stateName(it)}))}
                onChange={onChange}
            />
            <FilterSelect
                label="Branch"
                field="branch"
                value={filter.branch}
                options={branches.map(it => ({value: it, label: it}))}
                onChange={onChange}
            />
            <FilterSelect
                label="Scanner"
                field="scanner"
                value={filter.scanner}
                options={scanners.map(it => ({value: it, label: it}))}
                onChange={onChange}
            />
            <FilterSelect
                label="Kind"
                field="kind"
                value={filter.kind}
                options={FINDING_KINDS.map(it => ({value: it, label: kindName(it)}))}
                onChange={onChange}
            />
        </Space>
    )
}
