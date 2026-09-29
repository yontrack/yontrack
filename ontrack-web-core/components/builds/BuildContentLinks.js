import GridCell from "@components/grid/GridCell";
import GridCellCommand from "@components/grid/GridCellCommand";
import {FaProjectDiagram, FaSearch} from "react-icons/fa";
import {buildLinksUri} from "@components/common/Links";
import {Button, Input, Popover, Space, Typography} from "antd";
import Table from "@components/common/table/Table";
import {useEffect, useState} from "react";
import ProjectLink from "@components/projects/ProjectLink";
import BuildLink from "@components/builds/BuildLink";
import {useQuery} from "@components/services/GraphQL";

const {Column} = Table

export default function BuildContentLinks({build, id, title, fieldName}) {

    const [pagination, setPagination] = useState({
        offset: 0,
        size: 10,
    })

    const [pageInfo, setPageInfo] = useState({
        nextPage: null,
    })

    const [links, setLinks] = useState([])
    const [projectFilter, setProjectFilter] = useState(null)

    const {data, loading} = useQuery(
        `
                query GetBuildLinks(
                    $id: Int!,
                    $offset: Int!,
                    $size: Int!,
                    $projectName: String,
                ) {
                    build(id: $id) {
                        ${fieldName}(offset: $offset, size: $size, project: $projectName, projectFragment: true) {
                            pageInfo {
                                nextPage {
                                    offset
                                    size
                                }
                            }
                            pageItems {
                                qualifier
                                build {
                                    id
                                    name
                                    releaseProperty {
                                        value
                                    }
                                    branch {
                                        id
                                        name
                                        project {
                                            id
                                            name
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            `,
        {
            variables: {
                id: Number(build.id),
                offset: pagination.offset,
                size: pagination.size,
                projectName: projectFilter,
            },
            initialData: {},
            deps: [pagination, id, projectFilter],
        }
    )

    useEffect(() => {
        if (data && data.build) {
            setPageInfo(data.build[fieldName].pageInfo)
            if (pagination.offset > 0) {
                setLinks((links) => [...links, ...data.build[fieldName].pageItems])
            } else {
                setLinks(data.build[fieldName].pageItems)
            }
        }
    }, [data, fieldName])

    const onLoadMore = () => {
        if (pageInfo.nextPage) {
            setPagination(pageInfo.nextPage)
        }
    }

    const onProjectFilter = (value) => {
        setPagination({offset: 0, size: 10})
        setProjectFilter(value ? value : null)
    }

    return (
        <>
            <GridCell id={id}
                      title={title}
                      extra={
                          <>
                              <Input.Search
                                  placeholder="Project filter"
                                  allowClear
                                  onSearch={onProjectFilter}
                              />
                              <GridCellCommand
                                  icon={<FaProjectDiagram/>}
                                  title="Graph of build links"
                                  href={buildLinksUri(build)}
                              />
                          </>
                      }
            >
                <Table
                    loading={loading}
                    dataSource={links}
                    pagination={false}
                    footer={() => (
                        <>
                            <Space>
                                <Popover
                                    content={
                                        (pageInfo && pageInfo.nextPage) ?
                                            "There are more entries to be loaded" :
                                            "There are no more entries to be loaded"
                                    }
                                >
                                    <Button
                                        onClick={onLoadMore}
                                        disabled={!pageInfo || !pageInfo.nextPage}
                                    >
                                        <Space>
                                            <FaSearch/>
                                            <Typography.Text>Load more...</Typography.Text>
                                        </Space>
                                    </Button>
                                </Popover>
                            </Space>
                        </>
                    )}
                >
                    <Column
                        key="project"
                        title="Project"
                        render={(_, link) => <ProjectLink project={link.build.branch.project}/>}
                    />
                    <Column
                        key="qualifier"
                        title="Qualifier"
                        dataIndex="qualifier"
                    />
                    <Column
                        key="build"
                        title="Build"
                        render={(_, link) => <BuildLink build={link.build}/>}
                    />
                </Table>
            </GridCell>
        </>
    )
}