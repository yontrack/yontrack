import {useState} from "react"
import Link from "next/link"
import {Input, Select, Space, Tooltip, Typography} from "antd"
import {FaBan, FaExclamationCircle} from "react-icons/fa"
import StandardTable from "@components/common/table/StandardTable"
import BuildLink from "@components/builds/BuildLink"
import PromotionRuns from "@components/promotionRuns/PromotionRuns"
import TimestampText from "@components/common/TimestampText"
import SlotPipelineStatusLabel from "@components/extension/environments/SlotPipelineStatusLabel"
import {slotPipelineUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import {formatDuration} from "@components/extension/environments/slot/slotDeploymentsModel"
import {gqlSlotDeployments} from "@components/extension/environments/slot/slotGraphQL"

/**
 * **Deployments** - the slot's full history.
 *
 * The slot page used to give this sixteen of its twenty-four columns and no filters at all; the
 * header block above now answers "what is happening right now", so the tab is free to be what its
 * name says - the archive, with the three questions somebody actually brings to an archive:
 *
 * - **Status** - "show me the cancelled ones".
 * - **Build** - "where did 1.4.3 go wrong".
 * - **User** - "what did I deploy last week". It matches anybody on the deployment's audit trail,
 *   not only whoever started it: on a deployment that was started by CI, approved by one person and
 *   completed by another, "who" has three answers and picking one of them would hide the other two.
 *
 * All three are filtered by the server and the list stays paginated, so the answers do not depend on
 * how much of the history happens to be on the current page.
 *
 * @param {Object} slot The slot.
 * @param {number} reloadCount Bumped by the page after any change.
 */
export default function SlotDeploymentsTab({slot, reloadCount = 0}) {

    /*
     * The filter is one object handed to `StandardTable`, which resets the paging whenever it
     * changes. Kept in state rather than in the URL: unlike the matrix's filters, these narrow a
     * list somebody is reading rather than describing a screen they would share.
     */
    const [filter, setFilter] = useState({})

    const set = (key, value) => setFilter(current => {
        const next = {...current}
        if (value === undefined || value === null || value === '') {
            delete next[key]
        } else {
            next[key] = value
        }
        return next
    })

    return (
        <Space orientation="vertical" size={16} className="ot-line">

            <Space wrap data-testid="slot-deployments-filter">
                <Select
                    data-testid="slot-deployments-status"
                    style={{width: 180}}
                    allowClear
                    placeholder="Status"
                    value={filter.status}
                    onChange={value => set('status', value)}
                    options={[
                        {value: 'CANDIDATE', label: "Candidate"},
                        {value: 'RUNNING', label: "Running"},
                        {value: 'DONE', label: "Deployed"},
                        {value: 'CANCELLED', label: "Cancelled"},
                    ]}
                />
                {/*
                  * `data-testid` on an antd 6 `Input.Search` lands on its `Space.Compact` wrapper,
                  * not on the `<input>`: a test types into the searchbox inside it.
                  */}
                <Input.Search
                    data-testid="slot-deployments-build"
                    style={{width: 220}}
                    allowClear
                    placeholder="Build"
                    defaultValue={filter.buildName}
                    onSearch={value => set('buildName', value)}
                />
                <Input.Search
                    data-testid="slot-deployments-user"
                    style={{width: 220}}
                    allowClear
                    placeholder="User"
                    defaultValue={filter.user}
                    onSearch={value => set('user', value)}
                />
            </Space>

            <StandardTable
                /*
                 * Remounted when the filter changes, which resets the paging with it. `StandardTable`
                 * keeps its offset across a filter change and *appends* whenever the offset is not
                 * zero, so paging through a history and then filtering it would otherwise leave the
                 * previous page's rows above the new answer.
                 */
                key={JSON.stringify(filter)}
                id={`slot-pipelines-${slot.id}`}
                query={gqlSlotDeployments}
                queryNode={data => data.slotById.pipelines}
                reloadCount={reloadCount}
                filter={filter}
                variables={{
                    id: slot.id,
                }}
                rowKey={(item) => item.id}
                columns={[
                    {
                        key: 'number',
                        title: '#',
                        render: (_, item) => <Link href={slotPipelineUri(item.id)}>{`#${item.number}`}</Link>,
                    },
                    {
                        key: 'build',
                        title: 'Build',
                        render: (_, item) => <Space>
                            <BuildLink build={item.build}/>
                            <PromotionRuns promotionRuns={item.build.promotionRuns}/>
                        </Space>
                    },
                    {
                        key: 'status',
                        title: 'Status',
                        render: (_, item) => <SlotPipelineStatusLabel status={item.status}/>,
                    },
                    {
                        key: 'user',
                        title: 'Who',
                        render: (_, item) => <Typography.Text type="secondary">
                            {item.lastChange?.user ?? ''}
                        </Typography.Text>,
                    },
                    {
                        key: 'start',
                        title: 'Started',
                        render: (_, item) => <TimestampText value={item.start} relative={true}/>,
                    },
                    {
                        key: 'duration',
                        title: 'Duration',
                        render: (_, item) => <Typography.Text type="secondary">
                            {formatDuration(item.start, item.end)}
                        </Typography.Text>,
                    },
                    {
                        key: 'error',
                        title: 'Error',
                        render: (_, item) => item.errorMessage
                            ? <Tooltip title={item.errorMessage}><FaExclamationCircle color="red"/></Tooltip>
                            : <Tooltip title="No error"><FaBan color="gray"/></Tooltip>,
                    },
                ]}
            />
        </Space>
    )
}
