import {useContext, useEffect, useState} from "react";
import Head from "next/head";
import {subBranchTitle} from "@components/common/Titles";
import {downToBranchBreadcrumbs} from "@components/common/Breadcrumbs";
import MainPage from "@components/layouts/MainPage";
import {List, Skeleton, Space, Typography} from "antd";
import {gql} from "graphql-request";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import ValidationStampLink from "@components/validationStamps/ValidationStampLink";
import {isAuthorized} from "@components/common/authorizations";
import ValidationStampCreateCommand from "@components/validationStamps/ValidationStampCreateCommand";
import {EventsContext, useEventForRefresh} from "@components/common/EventsContext";
import ValidationDataType from "@components/framework/validation-data-type/ValidationDataType";
import {useQuery} from "@components/services/GraphQL";
import SortableList, {SortableItem, SortableKnob} from "react-easy-sort";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";

export default function BranchValidationStampsView({id}) {

    const client = useGraphQLClient()
    const eventsContext = useContext(EventsContext)

    const [commands, setCommands] = useState([])

    const refreshCreationCount = useEventForRefresh("validationStamp.created")
    const refreshReorderCount = useEventForRefresh("validationStamp.reordered")

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
                    }
                }
            }
        `,
        {
            variables: {id: Number(id)},
            deps: [refreshCreationCount, refreshReorderCount],
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

    const onSortEnd = (oldIndex, newIndex) => {
        const oldName = branch.validationStamps[oldIndex].name
        const newName = branch.validationStamps[newIndex].name
        client.request(
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
                branchId: Number(branch.id),
                oldName,
                newName,
            }
        ).then(() => {
            eventsContext.fireEvent("validationStamp.reordered")
        })
    }

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
                    <SortableList onSortEnd={onSortEnd} handle=".drag-handle">
                        <List
                            itemLayout="horizontal"
                            dataSource={branch?.validationStamps ?? []}
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
