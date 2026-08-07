import {Form, Input, Typography} from "antd";
import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {gql} from "graphql-request";

const applyDashboardsMutation = gql`
    mutation ApplyDashboards($yaml: String!) {
        applyDashboards(input: { yaml: $yaml }) {
            errors { message }
        }
    }
`

export const useDashboardYamlImportDialog = ({onSuccess}) =>
    useFormDialog({
        onSuccess,
        query: applyDashboardsMutation,
        userNode: 'applyDashboards',
    })

export default function DashboardYamlImportDialog({dialog}) {
    return (
        <FormDialog
            dialog={dialog}
            okText="Import"
            width={800}
            header={
                <Typography.Text type="secondary">
                    Paste one or more dashboard definitions in YAML format.
                    Existing dashboards with the same name will be updated; others will be created as shared dashboards.
                </Typography.Text>
            }
        >
            <Form.Item
                name="yaml"
                label="YAML"
                rules={[{required: true, message: 'YAML content is required.'}]}
            >
                <Input.TextArea
                    rows={20}
                    style={{fontFamily: 'monospace', fontSize: '13px'}}
                    placeholder={`- name: "My Dashboard"\n  widgets:\n    - key: "home/LastActiveProjects"\n      layout: {x: 0, y: 0, w: 6, h: 25}\n      config: {count: 10}`}
                />
            </Form.Item>
        </FormDialog>
    )
}
