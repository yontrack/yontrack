import {useContext} from "react"
import Link from "next/link"
import {Button, Table, Typography} from "antd"
import {FaPlus} from "react-icons/fa"
import {UserContext} from "@components/providers/UserProvider"
import {slotSetupUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import NewSlotDialog, {useNewSlotDialog} from "@components/extension/environments/NewSlotDialog"

/**
 * The **Slots** tab of Setup: every slot of the instance, grouped by project.
 *
 * Grouped by project and not by environment, unlike everything else in the feature. The Environments
 * tab is already the per-environment reading; somebody who comes here is asking "what is configured
 * for *my* project", and a list ordered by environment makes them read every group to find out.
 *
 * Each row links to the slot's own Setup tab rather than carrying its rules inline: the counts say
 * whether there is anything to look at, and what the rules *are* is a screen of its own.
 *
 * @param {Array} environments The environments with their slots, as the page fetched them.
 * @param {function} onChange Called after a slot is created.
 */
export default function SetupSlotsTab({environments}) {

    const user = useContext(UserContext)
    // The global half of the `slot` context: "can create a slot on at least one project". The
    // per-project right still decides the creation itself.
    const canCreate = !!user.authorizations?.slot?.create

    const newSlotDialog = useNewSlotDialog()

    // Computed in the render body: it is a function of `environments` and of nothing else, and a
    // table briefly empty before an effect filled it would read as "no slot configured".
    const rows = slotRows(environments)

    return (
        <>
            <Table
                dataSource={rows}
                rowKey={row => row.id}
                pagination={false}
                size="small"
                data-testid="setup-slots"
                onRow={row => ({'data-testid': `setup-slot-${row.id}`})}
                footer={() =>
                    canCreate &&
                    <Button
                        icon={<FaPlus/>}
                        data-testid="setup-new-slot"
                        onClick={() => newSlotDialog.start({})}
                    >
                        New slot
                    </Button>
                }
                columns={[
                    {
                        key: 'project',
                        title: 'Project',
                        render: (_, row) => <Link href={`/project/${row.project.id}`}>{row.project.name}</Link>,
                        /*
                         * The grouping, as a row span rather than as nested tables: one flat body
                         * keeps the columns aligned down the whole page, which is what makes "how
                         * many rules does each of my slots have" readable at a glance.
                         */
                        onCell: (row) => ({rowSpan: row.projectRowSpan}),
                    },
                    {
                        key: 'qualifier',
                        title: 'Qualifier',
                        render: (_, row) => row.qualifier
                            ? <Typography.Text code>{row.qualifier}</Typography.Text>
                            : <Typography.Text type="secondary">—</Typography.Text>,
                    },
                    {
                        key: 'environment',
                        title: 'Environment',
                        render: (_, row) => row.environment.name,
                    },
                    {
                        key: 'rules',
                        title: 'Admission rules',
                        render: (_, row) => <Typography.Text data-testid={`setup-slot-rules-${row.id}`}>
                            {row.rules}
                        </Typography.Text>,
                    },
                    {
                        key: 'workflows',
                        title: 'Workflows',
                        render: (_, row) => <Typography.Text data-testid={`setup-slot-workflows-${row.id}`}>
                            {row.workflows}
                        </Typography.Text>,
                    },
                    {
                        key: 'setup',
                        title: 'Setup',
                        render: (_, row) => <Link
                            href={slotSetupUri({id: row.id})}
                            data-testid={`setup-slot-link-${row.id}`}
                        >
                            Configure
                        </Link>,
                    },
                ]}
            />
            <NewSlotDialog newSlotDialog={newSlotDialog}/>
        </>
    )
}

/**
 * One flat row per slot, ordered by project and then by environment, with the row span each project
 * group needs.
 *
 * The server answers by environment, because that is how slots are stored; this turns that inside
 * out once, here, rather than in the component's JSX where it would be re-derived on every render
 * of every cell.
 */
export const slotRows = (environments) => {
    const rows = []
    ;(environments ?? []).forEach(environment => {
        (environment.slots ?? []).forEach(slot => {
            rows.push({
                id: slot.id,
                qualifier: slot.qualifier,
                project: slot.project,
                environment,
                rules: (slot.admissionRules ?? []).length,
                workflows: (slot.workflows ?? []).length,
            })
        })
    })

    rows.sort((a, b) => {
        const byProject = a.project.name.localeCompare(b.project.name)
        if (byProject !== 0) return byProject
        const byOrder = (a.environment.order ?? 0) - (b.environment.order ?? 0)
        if (byOrder !== 0) return byOrder
        return (a.qualifier ?? '').localeCompare(b.qualifier ?? '')
    })

    // The first row of each project group carries the whole group's span; the others carry 0, which
    // is how Ant Design is told not to draw their cell at all.
    let index = 0
    while (index < rows.length) {
        let end = index
        while (end < rows.length && rows[end].project.id === rows[index].project.id) end++
        rows[index].projectRowSpan = end - index
        for (let i = index + 1; i < end; i++) rows[i].projectRowSpan = 0
        index = end
    }

    return rows
}
