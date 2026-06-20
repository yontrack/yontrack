import React, {useState} from "react";
import {Popover, Space, Timeline, Typography} from "antd";
import dayjs from "dayjs";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import PromotionLevel from "@components/promotionLevels/PromotionLevel";
import BuildPromoteAction from "@components/builds/BuildPromoteAction";
import {isAuthorized} from "@components/common/authorizations";
import PromotionRunDeleteAction from "@components/promotionRuns/PromotionRunDeleteAction";
import {useQuery} from "@components/services/GraphQL";
import GridCell from "@components/grid/GridCell";
import PromotionRunLink from "@components/promotionRuns/PromotionRunLink";
import {FaCog} from "react-icons/fa";
import EntityNotificationsBadge from "@components/extension/notifications/EntityNotificationsBadge";
import {promotionLevelUri, promotionRunUri} from "@components/common/Links";
import Link from "next/link";
import TimestampText from "@components/common/TimestampText";
import PromotionRunFieldValues from "@components/promotionRuns/PromotionRunFieldValues";

const query = `
    query BuildPromotions($buildId: Int!) {
        build(id: $buildId) {
            authorizations {
                name
                action
                authorized
            }
            branch {
                promotionLevels {
                    id
                    name
                    image
                    description
                    annotatedDescription
                }
            }
            promotionRuns {
                id
                creation {
                    time
                    user
                }
                authorizations {
                    name
                    action
                    authorized
                }
                description
                annotatedDescription
                promotionLevel {
                    id
                    fields {
                        name
                        displayName
                        type
                    }
                }
                fieldValues {
                    name
                    value
                }
            }
        }
    }
`

/**
 * Listing the promotions and only the promotions of a build.
 */
export default function BuildContentPromotions({build}) {

    const [reloadCount, setReloadCount] = useState(0)

    const reload = () => {
        setReloadCount(prev => prev + 1)
    }

    const {data, loading} = useQuery(query, {
        variables: {buildId: Number(build.id)},
        deps: [build.id, reloadCount],
    })

    const buildData = data?.build
    const promotionLevels = buildData?.branch?.promotionLevels ?? []
    const runs = buildData?.promotionRuns ?? []

    const items = promotionLevels.flatMap(promotionLevel => {
        const plRuns = runs
            .filter(run => run.promotionLevel.id === promotionLevel.id)
            .sort((a, b) => (a.creation?.time ?? '').localeCompare(b.creation?.time ?? ''))

        if (plRuns.length > 0) {
            return plRuns.map(run => ({
                label: <Space className={`promotion-run-pl-${run.promotionLevel.id}`}>
                    {/* Information about the promotion */}
                    <Popover content={
                        <div data-testid={`build-promotion-run-popover-${run.id}`}>
                            <Space direction="vertical">
                                <Typography.Text>Promoted by {run.creation?.user}</Typography.Text>
                                <TimestampText value={run.creation?.time}/>
                                <AnnotatedDescription entity={run}/>
                                {
                                    run.fieldValues?.length > 0 &&
                                    <PromotionRunFieldValues
                                        fields={run.promotionLevel.fields}
                                        fieldValues={run.fieldValues}
                                    />
                                }
                            </Space>
                        </div>
                    }>
                        <span data-testid={`build-promotion-run-trigger-${run.promotionLevel.id}`}>
                            {run.creation?.time ? dayjs(run.creation.time).format("YYYY MMM DD, HH:mm") : ''}
                        </span>
                    </Popover>
                    {/* Repeating the promotion */}
                    {
                        isAuthorized(buildData, 'build', 'promote') ?
                            <BuildPromoteAction
                                build={build}
                                promotionLevel={promotionLevel}
                                tooltip={`Promotes the build again to ${promotionLevel.name}`}
                                onPromotion={reload}
                            /> : undefined
                    }
                    {/* Link to the promotion run */}
                    {
                        run &&
                        <PromotionRunLink
                            promotionRun={run}
                            text={<FaCog/>}
                        />
                    }
                    {/* Deleting the promotion */}
                    {
                        isAuthorized(run, 'promotion_run', 'delete') ?
                            <PromotionRunDeleteAction
                                promotionRun={run}
                                onDeletion={reload}
                            /> : undefined
                    }
                </Space>,
                children: <Space>
                    <Popover title={promotionLevel.name}
                             content={<AnnotatedDescription entity={promotionLevel}/>}>
                        <Link href={promotionLevelUri(promotionLevel)}>
                            <Typography.Text>{promotionLevel.name}</Typography.Text>
                        </Link>
                    </Popover>
                    <EntityNotificationsBadge
                        entityType="PROMOTION_RUN"
                        entityId={run.id}
                        href={promotionRunUri(run)}
                    />
                </Space>,
                dot: <PromotionLevel
                    promotionLevel={promotionLevel}
                    size={16}
                    displayTooltip={false}
                />,
            }))
        } else {
            return [{
                label: isAuthorized(buildData, 'build', 'promote') ?
                    <BuildPromoteAction
                        build={build}
                        promotionLevel={promotionLevel}
                        onPromotion={reload}
                    /> : undefined,
                children: <Popover title={promotionLevel.name}
                                   content={<AnnotatedDescription entity={promotionLevel}/>}>
                    <Link href={promotionLevelUri(promotionLevel)}>
                        <Typography.Text type="secondary">{promotionLevel.name}</Typography.Text>
                    </Link>
                </Popover>,
                dot: <PromotionLevel
                    promotionLevel={promotionLevel}
                    size={16}
                    displayTooltip={false}
                />,
            }]
        }
    })

    return (
        <>
            <GridCell id="promotions" title="Promotions" loading={loading} padding={true}>
                <Timeline
                    mode="right"
                    reverse={true}
                    items={items}
                />
            </GridCell>
        </>
    )
}
