import {Popover, Space, Table, Typography} from "antd";
import React from "react";
import ValidationChip from "@components/primitives/ValidationChip";
import {validationStampUri} from "@components/common/Links";
import ValidationRunLink from "@components/validationRuns/ValidationRunLink";
import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import {FaInfoCircle} from "react-icons/fa";
import Timestamp from "@components/common/Timestamp";
import RunInfo from "@components/common/RunInfo";
import ValidationRunData from "@components/framework/validation-run-data/ValidationRunData";
import BuildLink from "@components/builds/BuildLink";
import PromotionRuns from "@components/promotionRuns/PromotionRuns";

export default function ValidationRunTable({
                                               validationRuns,
                                               pagination = false,
                                               onChange,
                                               filtering,
                                               displayBuild = false,
                                               displayPromotionRuns = false,
                                           }) {

    // Definition of the columns

    const columns = []

    if (displayBuild) {
        columns.push(
            {
                title: "Build",
                render: (_, run) => <BuildLink build={run.build}/>,
            }
        )
        if (displayPromotionRuns) {
            columns.push(
                {
                    title: "Promotions",
                    render: (_, run) => <PromotionRuns promotionRuns={run.build.promotionRuns}/>
                }
            )
        }
    }

    columns.push(
        {
            title: "Validation",
            key: 'validation',
            // The chip, not a plain stamp: it tints its outline with the run's
            // state, so the column can be scanned by colour. The status is
            // spelled out by the "Status" column two along, so the chip's own
            // pill is suppressed rather than saying it twice - the state stays
            // in the chip's accessible name either way.
            render: (_, run) => <Popover
                title={run.validationStamp?.name}
                content={<AnnotatedDescription entity={run.validationStamp}/>}
                placement="rightBottom"
            >
                <span>
                    <ValidationChip
                        id={`validation-chip-${run.id}`}
                        validationStamp={run.validationStamp}
                        statusID={run.lastStatus?.statusID}
                        displayStatus={false}
                        href={run.validationStamp ? validationStampUri(run.validationStamp) : undefined}
                    />
                </span>
            </Popover>,
            filters: filtering?.validationStamps,
            filterSearch: true,
            filterMultiple: false,
            filteredValue: filtering?.filteredInfo?.validation || null,
        },
        {
            title: "Run",
            key: 'run',
            render: (_, run) => <ValidationRunLink run={run}/>
        },
        {
            title: "Status",
            key: 'status',
            render: (_, run) => <ValidationRunStatus status={run.lastStatus}/>,
            filters: filtering?.statuses,
            filterSearch: true,
            filteredValue: filtering?.filteredInfo?.status || null,
        },
        {
            title: "Description",
            key: 'description',
            render: (_, run) => <AnnotatedDescription entity={run.lastStatus}/>,
        },
        {
            title: "Creation",
            key: 'creation',
            render: (_, run) => <Popover
                content={
                    <Space direction="vertical">
                        <Typography.Text>Created by {run.lastStatus.creation.user}</Typography.Text>
                        <AnnotatedDescription entity={run.lastStatus}/>
                    </Space>
                }
            >
                <Space>
                    <FaInfoCircle/>
                    <Timestamp value={run.lastStatus.creation.time} fontSize="100%"></Timestamp>
                </Space>
            </Popover>
        },
        {
            title: "Run info",
            key: 'run-info',
            render: (_, run) => run.runInfo ? <RunInfo info={run.runInfo} mode="minimal"/> : undefined
        },
        {
            title: "Data",
            key: 'data',
            render: (_, run) => <ValidationRunData data={run.data}/>
        }
    )

    return (
        <>
            <Table
                dataSource={validationRuns}
                columns={columns}
                pagination={pagination}
                onChange={onChange}
                size="small"
            />
        </>
    )
}