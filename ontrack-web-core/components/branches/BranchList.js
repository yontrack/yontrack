import {Space} from "antd";
import BranchRow from "@components/branches/BranchRow";

export default function BranchList({branches, showProject}) {
    return (
        <>
            <Space orientation="vertical" size={16} style={{width: '100%'}}>
                {branches.map(branch => <BranchRow key={branch.id} branch={branch} showProject={showProject}/>)}
            </Space>
        </>
    )
}