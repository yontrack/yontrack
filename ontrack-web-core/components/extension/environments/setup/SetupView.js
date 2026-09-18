import Head from "next/head"
import {Alert, Skeleton, Tabs} from "antd"
import {pageTitle} from "@components/common/Titles"
import MainPage from "@components/layouts/MainPage"
import {CloseCommand} from "@components/common/Commands"
import {useQuery} from "@components/services/GraphQL"
import {useEventsForRefresh} from "@components/common/EventsContext"
import {environmentsBreadcrumbs, environmentsUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import EnvironmentsWarning from "@components/extension/environments/EnvironmentsWarning"
import {gqlSetup} from "@components/extension/environments/setup/setupGraphQL"
import SetupEnvironmentsTab from "@components/extension/environments/setup/SetupEnvironmentsTab"
import SetupSlotsTab from "@components/extension/environments/setup/SetupSlotsTab"

/**
 * **Setup** - where configuring environments and slots lives, out of the operational screens.
 *
 * The redesign's principle is that configuration is *set aside, not removed*: in practice it is done
 * as code, and having "New environment" and "New slot" in the middle of the screen somebody opened
 * to see what is running in production served neither job. Everything creational moved here, behind
 * one command on the matrix.
 *
 * Three things follow from that and are deliberate:
 *
 * - **The "still under experiment" banner lives here and nowhere else.** It used to be on every page
 *   of the feature, which is how a warning stops being read. On the page where somebody is about to
 *   create things it still means something.
 * - **This is the only screen allowed to say *slot*.** Everywhere else a slot is named
 *   "production · petclinic [canary]". Here the reader is configuring the object, so the object gets
 *   its name.
 * - **It does not poll.** Nothing here changes while it is being looked at, unlike the matrix, the
 *   drawer and the deployment page - so no `Freshness`, and a reload after each change instead.
 */
export default function SetupView() {

    // Both dialogs and the delete button announce themselves through the event bus rather than
    // through callbacks, because they are shared with screens that have no page to reload.
    const refreshCount = useEventsForRefresh([
        "environment.created",
        "environment.deleted",
        "slot.created",
    ])

    const {data, loading, finished, error} = useQuery(
        gqlSetup,
        {
            deps: [refreshCount],
            dataFn: data => data.environments,
        }
    )

    // Computed in the render body rather than kept in state: it is a function of the answer alone.
    const environments = [...(data ?? [])].sort((a, b) => (a.order ?? 0) - (b.order ?? 0))

    return (
        <>
            <Head>
                {pageTitle("Environments setup")}
            </Head>
            <MainPage
                title="Setup"
                warning={<EnvironmentsWarning/>}
                breadcrumbs={environmentsBreadcrumbs()}
                commands={[
                    <CloseCommand key="close" href={environmentsUri}/>,
                ]}
            >
                {
                    error &&
                    <Alert type="error" showIcon message="Could not load the environments."
                           description={error} data-testid="setup-error"/>
                }
                {
                    !error && (loading || !finished) &&
                    <Skeleton active paragraph={{rows: 6}}/>
                }
                {
                    !error && finished &&
                    <Tabs
                        data-testid="setup-tabs"
                        defaultActiveKey="environments"
                        items={[
                            {
                                key: 'environments',
                                label: "Environments",
                                children: <SetupEnvironmentsTab environments={environments}/>,
                            },
                            {
                                key: 'slots',
                                label: "Slots",
                                children: <SetupSlotsTab environments={environments}/>,
                            },
                        ]}
                    />
                }
            </MainPage>
        </>
    )
}
