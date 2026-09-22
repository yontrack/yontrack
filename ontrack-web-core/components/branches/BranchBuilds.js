import BuildBox from "@components/builds/BuildBox";
import {Button, Col, Popover, Row, Space, Spin, Table, Typography} from "antd";
import {FaCheckSquare, FaEyeSlash, FaSearch, FaSquare} from "react-icons/fa";
import RangeSelector from "@components/common/RangeSelector";
import {useRouter} from "next/router";
import BuildFilterDropdown from "@components/branches/filters/builds/BuildFilterDropdown";
import ValidationStampFilterDropdown from "@components/branches/filters/validationStamps/ValidationStampFilterDropdown";
import ValidationStampHeader from "@components/branches/ValidationStampHeader";
import ValidationRunCell from "@components/branches/ValidationRunCell";
import {useContext} from "react";
import ValidationGroups from "@components/validationRuns/ValidationGroups";
import {ValidationStampFilterContext} from "@components/branches/filters/validationStamps/ValidationStampFilterContext";
import {gql} from "graphql-request";
import {buildUri, scmChangeLogUri} from "@components/common/Links";
import EntityNotificationsBadge from "@components/extension/notifications/EntityNotificationsBadge";
import PromotionRunStep from "@components/promotionRuns/PromotionRunStep";
import {useQuery} from "@components/services/GraphQL";

const {Column} = Table;

/**
 * @param branch Target branch
 * @param builds List of builds to display
 * @param loadingBuilds Build loading indicator
 * @param pageInfo Page info for the builds
 * @param onLoadMore Callback when wanting to get more builds
 * @param rangeSelection Callback for the range selection
 * @param validationStamps List of validation stamps
 * @param loadingValidationStamps Validation stamps loading indicator
 * @param onChange Callback when builds, runs, statuses, etc. need to be reloaded after a user action
 * @param selectedBuildFilter Currently selected build filter (can be undefined)
 */
export default function BranchBuilds({
                                         branch,
                                         builds,
                                         loadingBuilds,
                                         pageInfo,
                                         onLoadMore,
                                         rangeSelection,
                                         validationStamps,
                                         onChange,
                                         selectedBuildFilter,
                                         onSelectedBuildFilter,
                                         onPermalinkBuildFilter,
                                     }) {

    const router = useRouter()
    const vsfContext = useContext(ValidationStampFilterContext)

    const {data: scmChangeLogEnabled} = useQuery(
        gql`
            query BranchScmInfo($id: Int!) {
                branch(id: $id) {
                    scmBranchInfo {
                        changeLogs
                    }
                }
            }
        `,
        {
            variables: {id: Number(branch.id)},
            condition: branch,
            dataFn: data => data.branch.scmBranchInfo?.changeLogs,
        }
    )

    const onChangeLog = () => {
        if (scmChangeLogEnabled && rangeSelection.isComplete()) {
            const [from, to] = rangeSelection.selection
            router.push(scmChangeLogUri(from, to))
        }
    }

    // Preferences for the display (filters, options...)

    return (
        <>
            <Space className="ot-line" orientation="vertical" size={8}>
                <Table
                    className={
                        vsfContext.inlineEdition ? "ot-validation-stamp-filter-edition" : undefined
                    }
                    size="small"
                    dataSource={builds}
                    footer={() => (
                        <>
                            <Space>
                                <Popover
                                    content={
                                        (pageInfo && pageInfo.nextPage) ?
                                            "There are more builds to be loaded" :
                                            "There are no more builds to be loaded"
                                    }
                                >
                                    <Button
                                        onClick={onLoadMore}
                                        disabled={!pageInfo || !pageInfo.nextPage || loadingBuilds}
                                    >
                                        <Space>
                                            {
                                                loadingBuilds ? <Spin size="small"/> : <FaSearch/>
                                            }
                                            <Typography.Text>Load more...</Typography.Text>
                                        </Space>
                                    </Button>
                                </Popover>
                            </Space>
                        </>
                    )}
                    pagination={false} // Pagination is managed by the "load more"
                    title={() => {
                        return vsfContext.inlineEdition && vsfContext.selectedFilter ?
                            <Row>
                                <Col span={24} align="right">
                                    <Space>
                                        <Button onClick={vsfContext.toggleAll}
                                                className="ot-validation-stamp-filter-edition">
                                            <Space>
                                                <FaCheckSquare/>
                                                <Typography.Text>
                                                    Select all for&nbsp;
                                                    <Typography.Text
                                                        strong>{vsfContext.selectedFilter.name}</Typography.Text>
                                                </Typography.Text>
                                            </Space>
                                        </Button>
                                        <Button onClick={vsfContext.toggleNone}
                                                className="ot-validation-stamp-filter-edition">
                                            <Space>
                                                <FaSquare/>
                                                <Typography.Text>
                                                    Select none for&nbsp;
                                                    <Typography.Text
                                                        strong>{vsfContext.selectedFilter.name}</Typography.Text>
                                                </Typography.Text>
                                            </Space>
                                        </Button>
                                        <Button className="ot-validation-stamp-filter-edition"
                                                onClick={vsfContext.stopInlineEdition}>
                                            <Space>
                                                <FaEyeSlash/>
                                                <Typography.Text>
                                                    <Typography.Text
                                                        strong>{vsfContext.selectedFilter.name}</Typography.Text>
                                                    &nbsp;done editing
                                                </Typography.Text>
                                            </Space>
                                        </Button>
                                    </Space>
                                </Col>
                            </Row> : undefined
                    }}
                    scroll={{ x: 'max-content' }}
                >
                    <Column
                        width="2em"
                        key="header"
                        colSpan={3}
                        align="left"
                        fixed="left"
                        title={
                            <Space className="ot-branch-builds-toolbar">
                                {/* Build filter */}
                                <BuildFilterDropdown
                                    branch={branch}
                                    selectedBuildFilter={selectedBuildFilter}
                                    onSelectedBuildFilter={onSelectedBuildFilter}
                                    onPermalink={onPermalinkBuildFilter}
                                />
                                {/* Change log */}
                                {
                                    scmChangeLogEnabled &&
                                    <Popover
                                        title="Change log between two builds"
                                        content={
                                            (!rangeSelection || !rangeSelection.isComplete()) &&
                                            <Typography.Text disabled>
                                                Select two builds in order to get their change log
                                            </Typography.Text>
                                        }
                                    >
                                        <Button
                                            disabled={!rangeSelection || !rangeSelection.isComplete()}
                                            onClick={onChangeLog}
                                        >
                                            <Space>
                                                <Typography.Text>Change log</Typography.Text>
                                            </Space>
                                        </Button>
                                    </Popover>
                                }
                                {/* VS filter */}
                                <ValidationStampFilterDropdown
                                    branch={branch}
                                />
                                {/* Loading indicator */}
                                {
                                    loadingBuilds &&
                                    <Popover data-testid="loading-builds" content="Loading builds...">
                                        <Spin/>
                                    </Popover>
                                }
                            </Space>
                        }
                        render={(_, build) =>
                            <RangeSelector
                                id={build.id}
                                title="Select this build as a boundary for a change log."
                                rangeSelection={rangeSelection}
                            />
                        }
                    />
                    <Column
                        key="build"
                        fixed="left"
                        colSpan={0} // Header managed by the "header" column
                        render={(_, build) =>
                            <BuildBox build={build} displayDecorations={true}>
                                <EntityNotificationsBadge
                                    entityType="BUILD"
                                    entityId={build.id}
                                    href={buildUri(build)}
                                />
                            </BuildBox>
                        }
                    />
                    <Column
                        key="promotions"
                        fixed="left"
                        colSpan={0} // Header managed by the "header" column
                        render={(_, build) =>
                            <Space size={8}>
                                {
                                    build.promotionRuns.map(promotionRun =>
                                        <PromotionRunStep
                                            key={promotionRun.id}
                                            run={promotionRun}
                                        />
                                    )
                                }
                            </Space>
                        }
                    />
                    {/* One column per validation stamp */}
                    {
                        validationStamps
                            .filter(validationStamp => {
                                // If we are in inline edition mode, we display ALL the validation stamps
                                if (vsfContext.inlineEdition) {
                                    return true
                                }
                                // If a filter is selected, we use only this filter
                                else if (vsfContext.selectedFilter) {
                                    // Checking the filter
                                    return vsfContext.selectedFilter.vsNames.indexOf(validationStamp.name) >= 0
                                }
                                    // If grouping is selected, we keep only the validation stamps which are
                                // part of the current filter
                                else {
                                    return !vsfContext.grouping
                                }
                            })
                            .map(validationStamp => (
                                <Column
                                    key={validationStamp.name}
                                    className="ot-validation-stamp"
                                    title={
                                        <>
                                            <ValidationStampHeader
                                                key={validationStamp.id}
                                                validationStamp={validationStamp}
                                                selectable={vsfContext.inlineEdition}
                                                tooltip={
                                                    vsfContext.inlineEdition && vsfContext.selectedFilter ?
                                                        `Select/unselect this "${validationStamp.name}" validation to add/remove it from the "${vsfContext.selectedFilter.name}" filter` :
                                                        undefined
                                                }
                                                selected={
                                                    vsfContext.selectedFilter && vsfContext.selectedFilter.vsNames.indexOf(validationStamp.name) >= 0
                                                }
                                                onSelect={() => vsfContext.toggleValidationStamp(validationStamp)}
                                            />
                                        </>
                                    }
                                    render={(_, build) =>
                                        <ValidationRunCell
                                            build={build}
                                            validationStamp={validationStamp}
                                            onChange={onChange}
                                        />
                                    }
                                />
                            ))
                    }
                    {/* Grouping per validation status */}
                    {
                        !vsfContext.inlineEdition && vsfContext.grouping && <Column
                            key="groups"
                            render={(_, build) =>
                                <ValidationGroups build={build}/>
                            }
                        />
                    }
                </Table>
            </Space>
        </>
    )
}