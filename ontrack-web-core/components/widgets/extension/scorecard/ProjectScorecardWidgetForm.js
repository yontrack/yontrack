import {useContext} from "react";
import {gql} from "graphql-request";
import {Form, Select} from "antd";
import SelectProject from "@components/projects/SelectProject";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import {useQuery} from "@components/services/GraphQL";
import {orderedSets, PROJECT_SET_PARAM} from "@components/extension/scorecard/scorecardModel";

const gqlProjectScorecardSets = gql`
    query ProjectScorecardSets($name: String!) {
        projects(name: $name) {
            scorecard {
                sets {
                    name
                    estate {
                        name
                    }
                }
            }
        }
    }
`

/**
 * The set selector of the widget: "Default", "Project", then the estates of the selected project,
 * by name.
 */
function SelectScorecardSet({project, value, onChange}) {

    const {data: sets} = useQuery(
        gqlProjectScorecardSets,
        {
            variables: {name: project},
            deps: [project],
            condition: !!project,
            initialData: [],
            dataFn: data => orderedSets(data.projects?.[0]?.scorecard),
        }
    )

    const estates = (sets ?? []).filter(it => it.estate).map(it => it.estate.name)

    return (
        <Select
            id="set"
            value={value ?? ''}
            onChange={it => onChange(it || null)}
            style={{width: '16em'}}
            options={[
                {value: '', label: 'Default'},
                {value: PROJECT_SET_PARAM, label: 'Project'},
                ...estates.map(name => ({value: name, label: name})),
            ]}
        />
    )
}

export default function ProjectScorecardWidgetForm({project, set}) {

    const {widgetEditionForm} = useContext(DashboardWidgetCellContext)
    const selectedProject = Form.useWatch('project', widgetEditionForm) ?? project

    return (
        <Form
            layout="vertical"
            form={widgetEditionForm}
        >
            <Form.Item
                name="project"
                label="Project"
                initialValue={project}
                rules={[{required: true, message: 'The project is required.'}]}
                extra="Project whose scorecard to display"
            >
                <SelectProject/>
            </Form.Item>
            <Form.Item
                name="set"
                label="Set"
                initialValue={set ?? null}
                extra="Set the widget opens on. By default, the first estate of the project by name, or the Project set for a project in no estate."
            >
                <SelectScorecardSet project={selectedProject}/>
            </Form.Item>
        </Form>
    )
}
