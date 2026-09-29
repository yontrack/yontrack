import {Space, Typography} from "antd";
import SelectProject from "@components/projects/SelectProject";
import SelectBranch from "@components/branches/SelectBranch";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import SelectPromotionLevel from "@components/promotionLevels/SelectPromotionLevel";

export default function SelectProjectBranchPromotionLevel({value, onChange}) {

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

    const setPromotionLevelName = (name) => {
        if (value?.project && value?.branch) {
            onChange({
                ...value,
                promotionLevel: name,
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
                <SelectPromotionLevel
                    disabled={!value?.project || !value?.branch || !branch}
                    branch={branch}
                    useName={true}
                    allowClear={true}
                    value={value?.promotionLevel}
                    onChange={setPromotionLevelName}
                />
            </Space>
        </>
    )
}