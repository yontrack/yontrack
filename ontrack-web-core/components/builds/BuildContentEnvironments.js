import {Button, Space} from "antd"
import {FaPlay} from "react-icons/fa"
import {useRouter} from "next/router"
import GridCell from "@components/grid/GridCell"
import {useQuery} from "@components/services/GraphQL"
import Freshness, {useFreshness} from "@components/extension/environments/shared/Freshness"
import SlotDrawer from "@components/extension/environments/shared/SlotDrawer"
import {useSlotDrawer} from "@components/extension/environments/shared/useSlotDrawer"
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog"
import BuildJourneyStrip from "@components/extension/environments/journey/BuildJourneyStrip"
import {gqlBuildJourney} from "@components/extension/environments/journey/buildJourneyGraphQL"
import {canDeployFromJourney} from "@components/extension/environments/journey/buildJourneyModel"
import {slotPipelineUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import {buildKnownName} from "@components/common/Titles"

/**
 * The build page's "Environments" cell: the build's journey strip (#1794).
 *
 * The cell is deliberately thin. It owns the query, the polling, the drawer and the deploy dialog;
 * everything it draws is the strip, and everything a reader can do from it leads into one of the
 * three shared components the redesign settled on. The three pieces it used to own - a per-slot
 * deploy button, a per-slot "currently deployed" line and an expandable deployment panel - are all
 * in the drawer a chip opens.
 */
export default function BuildContentEnvironments({build}) {

    const router = useRouter()

    const freshness = useFreshness()

    const {data: journey, loading, finished} = useQuery(
        gqlBuildJourney,
        {
            variables: {buildId: Number(build.id)},
            deps: [build.id, freshness.refreshCount],
            initialData: null,
            dataFn: data => data.build?.journey ?? [],
        }
    )

    const slotDrawer = useSlotDrawer()

    /*
     * The deploy dialog opened from the build: it offers every slot of the project, with the rule
     * refusing the ones that refuse. That is the same dialog the "Start deployment" user menu
     * action opens, which is the point - the redesign collapsed four entry points into one.
     */
    const deployDialog = useDeployDialog({
        onSuccess: (pipelineId) => {
            if (pipelineId) router.push(slotPipelineUri(pipelineId))
        },
    })

    return (
        <>
            <GridCell
                id="environments"
                title="Environments"
                // `finished` as well as `loading`: `useQuery` starts with `loading` false, and a
                // strip which said "This project has no deployment slot" for a tick before its
                // chips arrived would be read as an answer rather than as a wait.
                loading={loading || !finished}
                padding={true}
                extra={
                    <Space>
                        {
                            canDeployFromJourney(journey) &&
                            <Button
                                size="small"
                                icon={<FaPlay color="green"/>}
                                title={`Start deploying ${buildKnownName(build)}`}
                                data-testid="build-journey-deploy"
                                onClick={() => deployDialog.start({build})}
                            >
                                Deploy
                            </Button>
                        }
                        <Freshness
                            refreshedAt={freshness.refreshedAt}
                            refresh={freshness.refresh}
                            testId="build-journey-freshness"
                        />
                    </Space>
                }
            >
                <BuildJourneyStrip journey={journey} onSlotClick={slotDrawer.openSlot}/>
            </GridCell>
            <SlotDrawer
                slotId={slotDrawer.slotId}
                open={slotDrawer.open}
                onClose={slotDrawer.close}
                onDeploy={(slot, deployedBuild) => deployDialog.start({slot, build: deployedBuild})}
            />
            <DeployDialog dialog={deployDialog}/>
        </>
    )
}
