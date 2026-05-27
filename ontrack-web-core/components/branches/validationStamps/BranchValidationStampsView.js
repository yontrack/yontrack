import {useContext, useEffect, useState} from "react";
import Head from "next/head";
import {subBranchTitle} from "@components/common/Titles";
import {downToBranchBreadcrumbs} from "@components/common/Breadcrumbs";
import MainPage from "@components/layouts/MainPage";
import {Input, List, Skeleton, Space, Typography} from "antd";
import {gql} from "graphql-request";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import ValidationStampLink from "@components/validationStamps/ValidationStampLink";
import {isAuthorized} from "@components/common/authorizations";
import ValidationStampCreateCommand from "@components/validationStamps/ValidationStampCreateCommand";
import {EventsContext, useEventForRefresh} from "@components/common/EventsContext";
import ValidationDataType from "@components/framework/validation-data-type/ValidationDataType";
import {useMutation, useQuery} from "@components/services/GraphQL";
import SortableList, {SortableItem, SortableKnob} from "react-easy-sort";
import ConfirmCommand from "@components/common/ConfirmCommand";
import {FaTrash} from "react-icons/fa";

export default function BranchValidationStampsView({id}) {

    const eventsContext = useContext(EventsContext)

    const [commands, setCommands] = useState([])
    const [filterText, setFilterText] = useState('')

    const refreshCreationCount = useEventForRefresh("validationStamp.created")
    const refreshReorderCount = useEventForRefresh("validationStamp.reordered")
    const refreshDeleteCount = useEventForRefresh("validationStamp.deleted")

    const {data: branch, loading} = useQuery(
        gql`
            query BranchValidationStamps($id: Int!) {
                branch(id: $id) {
                    id
                    name
                    project {
                        id
                        name
                    }
                    authorizations {
                        name
                        action
                        authorized
                    }
                    validationStamps {
                        id
                        name
                        description
                        image
                        dataType {
                            descriptor {
                                id
                                displayName
                            }
                            config
                        }
                        authorizations {
                            name
                            action
                            authorized
                        }
                    }
                }
            }
        `,
        {
            variables: {id: Number(id)},
            deps: [refreshCreationCount, refreshReorderCount, refreshDeleteCount],
            initialData: null,
            dataFn: data => data.branch,
        }
    )

    useEffect(() => {
        if (branch && !loading) {
            const cmds = []
            if (isAuthorized(branch, 'validation_stamp', 'create')) {
                cmds.push(
                    <ValidationStampCreateCommand key="create" branch={branch}/>
                )
            }
            cmds.push(
                <CloseCommand key="close" href={branchUri(branch)}/>
            )
            setCommands(cmds)
        }
    }, [branch, loading])

    const {mutate: reorderMutation} = useMutation(
        gql`
            mutation ReorderValidationStamps(
                $branchId: Int!,
                $oldName: String!,
                $newName: String!,
            ) {
                reorderValidationStampById(input: {
                    branchId: $branchId,
                    oldName: $oldName,
                    newName: $newName,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        {
            userNodeName: 'reorderValidationStampById',
            onSuccess: () => eventsContext.fireEvent("validationStamp.reordered"),
        }
    )

    const onSortEnd = (oldIndex, newIndex) => {
        const oldName = branch.validationStamps[oldIndex].name
        const newName = branch.validationStamps[newIndex].name
        reorderMutation({
            branchId: Number(branch.id),
            oldName,
            newName,
        })
    }

    const filteredStamps = (branch?.validationStamps ?? []).filter(vs =>
        !filterText || vs.name.toLowerCase().includes(filterText.toLowerCase())
    )

    return (
        <>
            <Head>
                {subBranchTitle(branch, "Validation stamps")}
            </Head>
            <Skeleton loading={loading} active>
                <MainPage
                    title="Validation stamps"
                    breadcrumbs={branch ? downToBranchBreadcrumbs({branch}) : []}
                    commands={commands}
                >
                    <Input.Search
                        placeholder="Filter by name"
                        allowClear
                        value={filterText}
                        onChange={e => setFilterText(e.target.value)}
                        onSearch={value => setFilterText(value)}
                        style={{marginBottom: 16, maxWidth: 320}}
                    />
                    <SortableList onSortEnd={onSortEnd} handle=".drag-handle" allowDrag={!filterText}>
                        <List
                            itemLayout="horizontal"
                            dataSource={filteredStamps}
                            renderItem={(vs, index) => (
                                <SortableItem key={vs.id} index={index}>
                                    <List.Item className="no-select">
                                        <SortableKnob><div style={{cursor: 'grab', marginRight: 8}}>☰</div></SortableKnob>
                                        <List.Item.Meta
                                            title={
                                                <Space>
                                                    <ValidationStampLink validationStamp={vs}/>
                                                    {vs.description && <Typography.Text type="secondary">{vs.description}</Typography.Text>}
                                                </Space>
                                            }
                                            description={
                                                <div style={{display: 'flex', gap: 32, alignItems: 'flex-start'}}>
                                                    {vs.dataType && <Typography.Text type="secondary">{vs.dataType.descriptor.displayName}</Typography.Text>}
                                                    {vs.dataType && <div><ValidationDataType dataType={vs.dataType}/></div>}
                                                </div>
                                            }
                                        />
                                        {isAuthorized(vs, 'validation_stamp', 'delete') && (
                                            <ConfirmCommand
                                                icon={<FaTrash/>}
                                                text="Delete"
                                                confirmTitle={`Delete "${vs.name}"?`}
                                                confirmText="All data associated with this validation stamp will be permanently deleted."
                                                confirmOkText="Delete"
                                                confirmOkType="danger"
                                                gqlQuery={gql`
                                                    mutation DeleteValidationStamp($id: Int!) {
                                                        deleteValidationStampById(input: {id: $id}) {
                                                            errors { message }
                                                        }
                                                    }
                                                `}
                                                gqlVariables={{id: Number(vs.id)}}
                                                gqlUserNode="deleteValidationStampById"
                                                onSuccess={() => eventsContext.fireEvent("validationStamp.deleted")}
                                            />
                                        )}
                                    </List.Item>
                                </SortableItem>
                            )}
                        />
                    </SortableList>
                </MainPage>
            </Skeleton>
        </>
    )
}
