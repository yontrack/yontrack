import {Space} from "antd";
import SelectProject from "@components/projects/SelectProject";
import SelectBranch from "@components/branches/SelectBranch";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import SelectValidationStamp from "@components/validationStamps/SelectValidationStamp";

export default function SelectProjectBranchValidationStamp({value, onChange}) {

    const setProjectName = (name) => {
        onChange({
            ...value,
            project: name,
        })
    }

    const setBranchName = (name) => {
        if (value?.project) {
            onChange({
                ...value,
                branch: name,
            })
        }
    }

    const setValidationStampName = (name) => {
        if (value?.project && value?.branch) {
            onChange({
                ...value,
                validationStamp: name,
            })
        }
    }

    const hasBranch = !!(value?.project && value?.branch)
    const {data: loadedBranch} = useQuery(
        gql`
            query BranchByName($project: String!, $branch: String!) {
                branches(project: $project, name: $branch) {
                    id
                    name
                }
            }
        `,
        {
            variables: {project: value?.project, branch: value?.branch},
            deps: [value?.project, value?.branch],
            condition: hasBranch,
            dataFn: data => data.branches?.[0],
        }
    )
    const branch = hasBranch ? loadedBranch : null

    return (
        <>
            <Space>
                <SelectProject
                    value={value?.project}
                    onChange={setProjectName}
                />
                <SelectBranch
                    project={value?.project}
                    value={value?.branch}
                    onChange={setBranchName}
                    disabled={!value?.project}
                />
                <SelectValidationStamp
                    disabled={!value?.project || !value?.branch || !branch}
                    branch={branch}
                    useName={true}
                    allowClear={true}
                    value={value?.validationStamp}
                    onChange={setValidationStampName}
                    width="16em"
                />
            </Space>
        </>
    )
}