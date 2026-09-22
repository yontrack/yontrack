import {useState} from "react"
import Head from "next/head"
import {useRouter} from "next/router"
import {Alert, Card, Skeleton, Space, Tabs} from "antd"
import {pageTitle} from "@components/common/Titles"
import MainPage from "@components/layouts/MainPage"
import {CloseCommand} from "@components/common/Commands"
import {useQuery} from "@components/services/GraphQL"
import {isAuthorized} from "@components/common/authorizations"
import {
    environmentsBreadcrumbs,
    environmentsUri,
    SLOT_TAB_PARAM,
    slotTitle,
} from "@components/extension/environments/EnvironmentsLinksUtils"
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog"
import SlotSummary from "@components/extension/environments/shared/SlotSummary"
import SlotDeploymentsTab from "@components/extension/environments/slot/SlotDeploymentsTab"
import SlotEligibleBuildsTab from "@components/extension/environments/slot/SlotEligibleBuildsTab"
import SlotSetupTab from "@components/extension/environments/slot/SlotSetupTab"
import {gqlSlotPage} from "@components/extension/environments/slot/slotGraphQL"

/**
 * The slot's home - "production · petclinic [canary]".
 *
 * What it replaces is four sections stacked in an 8/16 split: details and eligible builds down the
 * left, deployments down the right, then admission rules and workflows full-width underneath.
 * Operation and configuration shared the screen, and the question the page is actually opened with -
 * *what is in this environment right now* - was a `Descriptions` table in the smaller column.
 *
 * It is now a header and three tabs:
 *
 * 1. **The header** is the slot drawer's Now / In flight / Next, the same component. Somebody who
 *    clicked through from a matrix cell sees the drawer they were looking at, in full, rather than a
 *    different summary of the same slot.
 * 2. **Deployments** - the full history, filtered and paged.
 * 3. **Eligible builds** - what could go next, each with Deploy.
 * 4. **Setup** - the rules and workflows, hidden without `slot.edit`, and Delete slot with them.
 *
 * The tab is in the URL (`?tab=setup`), which is what lets the Setup page link straight at a slot's
 * configuration, and what makes a reload keep the reader where they were.
 *
 * @param {string} id The slot's id, from the route.
 */
export default function SlotView({id}) {

    const router = useRouter()

    /*
     * One counter for the whole page. A rule added in the Setup tab changes what the header's
     * checks say and what the Deployments tab shows, so the page reloads as a whole rather than
     * each tab keeping its own idea of when it last asked.
     */
    const [reloadCount, setReloadCount] = useState(0)
    const reload = () => setReloadCount(count => count + 1)

    const {data: slot, loading, finished, error} = useQuery(
        gqlSlotPage,
        {
            variables: {id},
            deps: [id, reloadCount],
            condition: !!id,
            dataFn: data => data.slotById,
        }
    )

    const deployDialog = useDeployDialog({onSuccess: reload})

    const title = slot ? slotTitle(slot) : 'Slot'

    const rawTab = router?.query?.[SLOT_TAB_PARAM]
    const requestedTab = Array.isArray(rawTab) ? rawTab[0] : rawTab

    const canEdit = slot ? isAuthorized(slot, 'slot', 'edit') : false

    const selectTab = (key) => {
        router.push(
            {pathname: router.pathname, query: {...router.query, [SLOT_TAB_PARAM]: key}},
            undefined,
            {shallow: true},
        )
    }

    // Computed in the render body: which tabs exist is a function of the answer and of the rights on
    // it, and a tab list filled in by an effect would flash the wrong tab selected.
    const items = slot ? [
        {
            key: 'deployments',
            label: "Deployments",
            children: <SlotDeploymentsTab slot={slot} reloadCount={reloadCount}/>,
        },
        {
            key: 'builds',
            label: "Eligible builds",
            children: <SlotEligibleBuildsTab
                slot={slot}
                onChange={reload}
                onDeploy={(slot, build) => deployDialog.start({slot, build})}
            />,
        },
        ...(canEdit ? [{
            key: 'setup',
            label: "Setup",
            children: <SlotSetupTab slot={slot} reloadCount={reloadCount} onChange={reload}/>,
        }] : []),
    ] : []

    // A `?tab=` naming a tab this reader does not have - Setup without `slot.edit`, or a typo - falls
    // back to the first one rather than selecting nothing.
    const activeTab = items.some(item => item.key === requestedTab) ? requestedTab : 'deployments'

    return (
        <>
            <Head>
                {pageTitle(title)}
            </Head>
            <MainPage
                title={title}
                breadcrumbs={environmentsBreadcrumbs()}
                commands={[
                    <CloseCommand key="close" href={environmentsUri}/>,
                ]}
            >
                {
                    error &&
                    <Alert type="error" showIcon title="Could not load the slot."
                           description={error} data-testid="slot-load-error"/>
                }
                {
                    !error && (loading || !finished) && !slot &&
                    <Skeleton active paragraph={{rows: 6}}/>
                }
                {
                    !error && finished && !slot &&
                    <Alert type="warning" showIcon title="No such slot." data-testid="slot-missing"/>
                }
                {
                    slot &&
                    <Space orientation="vertical" size={16} className="ot-line" data-testid={`slot-${slot.id}`}>
                        <Card size="small">
                            {/*
                              * The drawer's own component, minus "Recent": the Deployments tab below
                              * is the whole history, and five rows of it above the tab holding all of
                              * them is the stacked-sections page coming back.
                              */}
                            <SlotSummary
                                slotId={slot.id}
                                recent={false}
                                testId="slot-header"
                                onChange={reload}
                                onDeploy={(slot, build) => deployDialog.start({slot, build})}
                            />
                        </Card>
                        <Tabs
                            data-testid="slot-tabs"
                            activeKey={activeTab}
                            onChange={selectTab}
                            items={items}
                        />
                    </Space>
                }
                <DeployDialog dialog={deployDialog}/>
            </MainPage>
        </>
    )
}
