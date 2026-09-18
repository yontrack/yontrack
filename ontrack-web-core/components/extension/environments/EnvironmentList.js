import {Button, Card, Empty, Form, Select, Space} from "antd";
import EnvironmentCard from "@components/extension/environments/EnvironmentCard";
import {useEffect, useState} from "react";
import {FaSearch} from "react-icons/fa";
import LoadingContainer from "@components/common/LoadingContainer";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEventForRefresh} from "@components/common/EventsContext";
import {gql} from "graphql-request";
import {gqlSlotData} from "@components/extension/environments/EnvironmentGraphQL";
import SelectProject from "@components/projects/SelectProject";
import {gqlSlotCellData} from "@components/extension/environments/shared/environmentsSharedGraphQL";
import SlotDrawer from "@components/extension/environments/shared/SlotDrawer";
import {useSlotDrawer} from "@components/extension/environments/shared/useSlotDrawer";
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog";

export default function EnvironmentList() {

    const client = useGraphQLClient()

    const [loading, setLoading] = useState(false)
    const [environments, setEnvironments] = useState([])

    const environmentCreated = useEventForRefresh("environment.created")
    const environmentDeleted = useEventForRefresh("environment.deleted")
    const slotCreated = useEventForRefresh("slot.created")

    const [filter, setFilter] = useState({
        projects: null,
        tags: null,
    })

    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    query EnvironmentList(
                        $filterProjects: [String!],
                        $filterTags: [String!],
                    ) {
                        environments(filter: {
                            projects: $filterProjects,
                            tags: $filterTags,
                        }) {
                            id
                            name
                            description
                            order
                            tags
                            image
                            slots(projects: $filterProjects) {
                                ...SlotData
                                ...SlotCellData
                            }
                        }
                    }

                    ${gqlSlotData}
                    ${gqlSlotCellData}
                `,
                {
                    filterProjects: filter.projects,
                    filterTags: filter.tags,
                }
            ).then(data => {
                setEnvironments(data.environments)
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, environmentCreated, environmentDeleted, slotCreated, filter])

    /*
     * The slot drawer, reachable from this screen before the matrix that replaces it arrives in
     * phase 2 (#1797). It lives here rather than in `EnvironmentCard` so that one drawer serves
     * every card, and so its `?slot=` address is owned by the screen rather than by a card.
     */
    const slotDrawer = useSlotDrawer()
    const deployDialog = useDeployDialog()

    const [form] = Form.useForm()

    const onFilter = () => {
        const values = form.getFieldsValue()
        setFilter({
            projects: values.project ? [values.project] : null,
            tags: values.tags ? values.tags : null,
        })
    }

    const onClearFilter = () => {
        form.resetFields()
        onFilter()
    }

    return (
        <>
            <LoadingContainer loading={loading}>
                <Space direction="vertical" className="ot-line">
                    <>
                        <Card
                        >
                            <Form
                                form={form}
                                layout="inline"
                                onSubmit={onFilter}
                                onValuesChange={onFilter}
                            >
                                <Form.Item
                                    label="Tags"
                                    name="tags"
                                >
                                    <Select
                                        mode="tags"
                                        style={{width: "20em"}}
                                    />
                                </Form.Item>
                                <Form.Item
                                    label="Project"
                                    name="project"
                                >
                                    <SelectProject
                                        idAsValue={false}
                                    />
                                </Form.Item>
                                <Form.Item>
                                    <Button type="primary" htmlType="submit">
                                        <Space>
                                            <FaSearch/>
                                            Filter
                                        </Space>
                                    </Button>
                                </Form.Item>
                                <Form.Item>
                                    <Button type="link" onClick={onClearFilter}>
                                        <Space>
                                            Reset
                                        </Space>
                                    </Button>
                                </Form.Item>
                            </Form>
                        </Card>
                        {
                            environments.length === 0 &&
                            <Empty
                                description="No environment has been created yet or the filter is too restrictive."
                            />
                        }
                        {
                            environments.map(environment => (
                                <EnvironmentCard
                                    key={environment.id}
                                    environment={environment}
                                    onSlotClick={slotDrawer.openSlot}
                                />
                            ))
                        }
                    </>
                </Space>
            </LoadingContainer>
            <SlotDrawer
                slotId={slotDrawer.slotId}
                open={slotDrawer.open}
                onClose={slotDrawer.close}
                onDeploy={(slot, build) => deployDialog.start({slot, build})}
            />
            <DeployDialog dialog={deployDialog}/>
        </>
    )
}