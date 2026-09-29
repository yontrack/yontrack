import {Skeleton, Space, Tree} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {buildDownstreamTreeData, buildQueryDownstreamOnly} from "@components/links/BuildLinksUtils";
import BuildLinksTreeNode from "@components/links/BuildLinksTreeNode";
import CloseableAlert from "@components/common/CloseableAlert";

const NO_TREE_DATA = []

export default function BuildLinksTree({build, changeDependencyLinksMode}) {

    const {data: treeData, loading, finished} = useQuery(
        buildQueryDownstreamOnly,
        {
            variables: {buildId: Number(build?.id)},
            deps: [build],
            condition: !!build,
            initialData: NO_TREE_DATA,
            dataFn: data => [buildDownstreamTreeData(data.build)],
        }
    )

    function switchToGraphView() {
        if (changeDependencyLinksMode) changeDependencyLinksMode('graph')
    }

    return (
        <>
            <Skeleton active loading={loading || !finished}>
                <Space orientation="vertical" className="ot-line">
                    <CloseableAlert
                        id="tree-view-alert"
                        message={
                            <>
                                The tree view below displays only downstream dependencies. To get also
                                upstream dependencies, switch to the <a onClick={switchToGraphView}>graph</a> view.
                            </>
                        }
                        type="info"
                    />
                    <Tree
                        showIcon={true}
                        defaultExpandAll={true}
                        treeData={treeData ?? NO_TREE_DATA}
                        blockNode={true}
                        titleRender={node => (
                            <>
                                <BuildLinksTreeNode build={node.build} qualifier={node.qualifier}/>
                            </>
                        )}
                        showLine={true}
                    />
                </Space>
            </Skeleton>
        </>
    )
}