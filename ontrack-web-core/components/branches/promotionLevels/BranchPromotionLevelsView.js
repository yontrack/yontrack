import {useContext, useState} from "react";
import {callGraphQL, useQuery} from "@components/services/GraphQL";
import Head from "next/head";
import {subBranchTitle} from "@components/common/Titles";
import {downToBranchBreadcrumbs} from "@components/common/Breadcrumbs";
import MainPage from "@components/layouts/MainPage";
import {Skeleton, Space} from "antd";
import {gql} from "graphql-request";
import {CloseCommand} from "@components/common/Commands";
import {branchUri} from "@components/common/Links";
import PromotionLevelLink from "@components/promotionLevels/PromotionLevelLink";
import {gqlDecorationFragment} from "@components/services/fragments";
import Decorations from "@components/framework/decorations/Decorations";
import {isAuthorized} from "@components/common/authorizations";
import PromotionLevelCreateCommand from "@components/promotionLevels/PromotionLevelCreateCommand";
import {EventsContext, useEventForRefresh} from "@components/common/EventsContext";
import SortableList, {SortableItem, SortableKnob} from "react-easy-sort";
import EntitySubscriptions from "@components/extension/notifications/EntitySubscriptions";
import ItemList from "@components/common/ItemList";

const noBranch = {project: {}}

export default function BranchPromotionLevelsView({id}) {

    const [reordering, setReordering] = useState(false)

    const eventsContext = useContext(EventsContext)
    const refreshCreationCount = useEventForRefresh("promotionLevel.created")
    const refreshReorderCount = useEventForRefresh("promotionLevel.reordered")

    const {data: queriedBranch, loading, finished} = useQuery(
        gql`
            query BranchPromotionLevels($id: Int!) {
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
                    promotionLevels {
                        id
                        name
                        description
                        image
                        properties {
                            type {
                                typeName
                            }
                            value
                        }
                        decorations {
                            ...decorationContent
                        }
                    }
                }
            }

            ${gqlDecorationFragment}
        `,
        {
            variables: {id: Number(id)},
            deps: [id, refreshCreationCount, refreshReorderCount],
            condition: !!id,
            initialData: noBranch,
            dataFn: data => data.branch,
        }
    )
    const branch = queriedBranch ?? noBranch

    const commands = []
    if (branch.id) {
        if (isAuthorized(branch, 'promotion_level', 'create')) {
            commands.push(
                <PromotionLevelCreateCommand key="create" branch={branch}/>
            )
        }
        commands.push(
            <CloseCommand key="close" href={branchUri(branch)}/>
        )
    }

    const onSortEnd = (oldIndex, newIndex) => {
        setReordering(true)
        const oldName = branch.promotionLevels[oldIndex].name
        const newName = branch.promotionLevels[newIndex].name
        callGraphQL({
            query: gql`
                mutation ReorderPromotionLevels(
                    $branchId: Int!,
                    $oldName: String!,
                    $newName: String!,
                ) {
                    reorderPromotionLevelById(input: {
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
            variables: {
                branchId: Number(branch.id),
                oldName,
                newName,
            },
        }).then(() => {
            eventsContext.fireEvent("promotionLevel.reordered")
        }).finally(() => {
            setReordering(false)
        })
    }

    return (
        <>
            <Head>
                {subBranchTitle(branch, "Promotion levels")}
            </Head>
            <Skeleton loading={loading || !finished || reordering} active>
                <MainPage
                    title="Promotion levels"
                    breadcrumbs={downToBranchBreadcrumbs({branch})}
                    commands={commands}
                >
                    <ItemList component={SortableList} as="ul" onSortEnd={onSortEnd}>
                        {
                            (branch.promotionLevels ?? []).map((pl, index) => (
                                <SortableItem key={pl.id} index={index}>
                                    <ItemList.Item
                                        className="no-select"
                                        data-testid={`promotion-level-item-${pl.name}`}
                                        avatar={
                                            <SortableKnob><div style={{cursor: 'grab'}}>☰</div></SortableKnob>
                                        }
                                        title={
                                            <Space>
                                                <PromotionLevelLink promotionLevel={pl}/>
                                                <Decorations entity={pl}/>
                                            </Space>
                                        }
                                        description={pl.description}
                                    >
                                        <div style={{width: '50%'}}>
                                            <EntitySubscriptions type="PROMOTION_LEVEL" id={pl.id}/>
                                        </div>
                                    </ItemList.Item>
                                </SortableItem>
                            ))
                        }
                    </ItemList>
                </MainPage>
            </Skeleton>
        </>
    )
}