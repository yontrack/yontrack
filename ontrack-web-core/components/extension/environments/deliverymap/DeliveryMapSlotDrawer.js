import {useRouter} from "next/router"
import SlotDrawer from "@components/extension/environments/shared/SlotDrawer"
import {useSlotDrawer} from "@components/extension/environments/shared/useSlotDrawer"
import DeployDialog, {useDeployDialog} from "@components/extension/environments/shared/DeployDialog"
import {slotPipelineUri} from "@components/extension/environments/EnvironmentsLinksUtils"
import {DeliveryMapSlotContext} from "@components/extension/environments/deliverymap/deliveryMapSlotContext"

/**
 * The slot drawer, on the delivery map (#1796).
 *
 * The map is core and the slots on it are contributed by this extension, so the extension owns both
 * ends of the seam: the checkpoint which asks for the drawer and the drawer which answers. Core
 * renders this one component around its graph and needs to know nothing else about slots.
 *
 * One drawer for the whole map rather than one per checkpoint - the drawer is addressed by
 * `?slot=<id>`, so a drawer per node would mean every slot's drawer opening at once - and the deploy
 * dialog comes with it, because the drawer offers Deploy on the builds a slot is waiting for and
 * that button must do something.
 */
export default function DeliveryMapSlotDrawer({children}) {

    const router = useRouter()

    const slotDrawer = useSlotDrawer()

    const deployDialog = useDeployDialog({
        onSuccess: (pipelineId) => {
            if (pipelineId) router.push(slotPipelineUri(pipelineId))
        },
    })

    return (
        <DeliveryMapSlotContext.Provider value={{onSlotClick: slotDrawer.openSlot}}>
            {children}
            <SlotDrawer
                slotId={slotDrawer.slotId}
                open={slotDrawer.open}
                onClose={slotDrawer.close}
                onDeploy={(slot, build) => deployDialog.start({slot, build})}
            />
            <DeployDialog dialog={deployDialog}/>
        </DeliveryMapSlotContext.Provider>
    )
}
