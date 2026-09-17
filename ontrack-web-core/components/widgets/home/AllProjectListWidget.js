import {useCallback, useContext, useEffect, useState} from "react";
import {gql} from "graphql-request";
import {gqlDecorationFragment} from "@components/services/fragments";
import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import PaddedContent from "@components/common/PaddedContent";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";
import {useQuery} from "@components/services/GraphQL";
import SimpleProjectList from "@components/projects/SimpleProjectList";
import {Button, Form, Input, Space} from "antd";
import {FaBackwardStep, FaForwardStep} from "react-icons/fa6";
import SelectLabel from "@components/labels/SelectLabel";

/**
 * Filter of the widget, displayed in its header: the project name and the labels.
 *
 * It keeps its own state because the header is set once, when the widget mounts: a node built
 * from the widget's state would be captured there and never show a new value. It only notifies
 * the widget of the changes, and the widget owns the filtering.
 *
 * Both criteria are transient, like the name filter has always been: they are not saved in the
 * widget configuration, and not in the URL.
 */
function AllProjectListFilter({onChange}) {

    const [form] = Form.useForm()
    const [labels, setLabels] = useState([])

    const onName = values => {
        onChange({projectName: values.projectName})
    }

    const onLabels = values => {
        setLabels(values)
        onChange({labels: values})
    }

    return (
        <Space>
            <Form layout="inline" form={form} onFinish={onName}>
                <Form.Item name="projectName">
                    <Input
                        placeholder="Project name"
                        allowClear
                        onClear={() => onChange({projectName: null})}
                    />
                </Form.Item>
            </Form>
            <div data-testid="project-labels-filter" style={{minWidth: '12em'}}>
                <SelectLabel
                    multiple
                    value={labels}
                    onChange={onLabels}
                    placeholder="Labels"
                    style={{width: '100%'}}
                />
            </div>
        </Space>
    )
}

export default function AllProjectListWidget() {

    const [pagination, setPagination] = useState({
        offset: 0,
        size: 20,
    })

    const [filter, setFilter] = useState({
        projectName: null,
        labels: [],
    })

    const {data, loading} = useQuery(
        gql`
            query AllProjectListWidget($offset: Int! = 0, $size: Int! = 20, $name: String = null, $labels: [String!] = null) {
                paginatedProjects(offset: $offset, size: $size, name: $name, labels: $labels) {
                    pageInfo {
                        previousPage {
                            offset
                            size
                        }
                        nextPage {
                            offset
                            size
                        }
                    }
                    pageItems {
                        ...ProjectContent
                        favourite
                        decorations {
                            ...decorationContent
                        }
                    }
                }
            }
            ${gqlDecorationFragment}
            ${gqlProjectContentFragment}
        `,
        {
            variables: {
                ...pagination,
                name: filter.projectName,
                labels: filter.labels,
            },
            deps: [pagination, filter],
            initialData: {pageItems: []},
            dataFn: data => data.paginatedProjects,
        }
    )

    // Read straight from the data: a state filled by an effect would be one render behind the
    // list it pages through
    const pageInfo = data?.pageInfo ?? {previousPage: null, nextPage: null}

    // Any change of the filter goes back to the first page - the page the user was on has no
    // reason to exist in the filtered list.
    const onFilterChange = useCallback(changes => {
        setPagination({
            offset: 0,
            size: 20,
        })
        setFilter(filter => ({...filter, ...changes}))
    }, [])

    const {setTitle, setExtra} = useContext(DashboardWidgetCellContext)
    useEffect(() => {
        setTitle("All projects")
        setExtra(<AllProjectListFilter onChange={onFilterChange}/>)
    }, [])

    // Derived from the filter, not a state: an empty list under a filter is not the same message
    // as an instance with no project at all
    const filtering = !!filter.projectName || filter.labels.length > 0

    const onPrevious = () => {
        if (pageInfo.previousPage) {
            setPagination(pageInfo.previousPage)
        }
    }

    const onNext = () => {
        if (pageInfo.nextPage) {
            setPagination(pageInfo.nextPage)
        }
    }

    return (
        <PaddedContent>
            <SimpleProjectList
                projects={data.pageItems}
                emptyText={
                    filtering ?
                        <>No project matches this filter.</> :
                        <>
                            No project has been created in Ontrack yet.
                            You can start <a
                            href="https://static.nemerosa.net/ontrack/release/latest/docs/doc/index.html#feeding">feeding
                            information</a> in Ontrack
                            automatically from your CI engine, using its API or other means.
                        </>
                }
                before={
                    pageInfo && pageInfo.previousPage &&
                    <Button onClick={onPrevious} type="link" loading={loading}
                            icon={<FaBackwardStep/>}>Previous</Button>
                }
                after={
                    pageInfo && pageInfo.nextPage &&
                    <Button onClick={onNext} type="link" loading={loading} icon={<FaForwardStep/>}>Next</Button>
                }
            />
        </PaddedContent>
    )
}
