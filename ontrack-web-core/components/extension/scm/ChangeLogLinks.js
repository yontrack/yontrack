import GridCell from "@components/grid/GridCell";
import {Table} from "antd";
import ProjectLink from "@components/projects/ProjectLink";
import BuildLink from "@components/builds/BuildLink";
import ChangeLogSignLink from "@components/extension/scm/ChangeLogSignLink";

const {Column} = Table

export default function ChangeLogLinks({id, loading, linkChanges = []}) {

    return (
        <>
            <GridCell id={id} title="Links" loading={loading} padding={0}>
                <Table
                    dataSource={linkChanges}
                    size="small"
                >
                    <Column
                        key="project"
                        title="Project"
                        render={(_, change) =>
                            <ProjectLink project={change.project}/>
                        }
                    />
                    <Column
                        key="qualifier"
                        title="Qualifier"
                        dataIndex="qualifier"
                    />
                    <Column
                        key="from"
                        title="Before"
                        render={(_, change) =>
                            change.from ? <BuildLink build={change.from}/> : "-"
                        }
                    />
                    <Column
                        key="to"
                        title="After"
                        render={(_, change) =>
                            change.to ? <BuildLink build={change.to}/> : "-"
                        }
                    />
                    <Column
                        key="change"
                        title=""
                        render={(_, change) =>
                            <ChangeLogSignLink
                                from={change.from}
                                to={change.to}
                            />
                        }
                    />
                </Table>
            </GridCell>
        </>
    )
}