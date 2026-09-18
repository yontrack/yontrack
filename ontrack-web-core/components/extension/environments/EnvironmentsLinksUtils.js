import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import Link from "next/link";
import {slotDisplayName} from "@components/extension/environments/shared/slotCellModel";

export const slotPipelineUri = (id) => `/extension/environments/pipeline/${id}`

/**
 * How a slot is named on screen: "production · petclinic [canary]".
 *
 * It used to be "Slot production - petclinic [canary]". The redesign's vocabulary rule is that the
 * word *slot* appears only in Setup (#1793), and this string is what the breadcrumb, the page title
 * and the browser tab of every operational screen are built from - so it was the last place the
 * word survived outside Setup.
 *
 * The same string as the cell's and the drawer's, from the same function, so a reader following a
 * cell into a page does not see the thing they clicked renamed on arrival.
 */
export const slotTitle = (slot) => slotDisplayName(slot)

export const slotUri = ({id}) => `/extension/environments/slot/${id}`

/** Which tab of the slot page a link opens - the slot page is addressed by `?tab=`. */
export const SLOT_TAB_PARAM = 'tab'

/**
 * The slot page, opened straight on its **Setup** tab.
 *
 * Where the Setup page's Slots tab sends a reader: the rules and workflows of one slot are
 * configured on the slot's own page, and landing on its Deployments tab and having to find the
 * third tab is a step with nothing in it.
 */
export const slotSetupUri = (slot) => `${slotUri(slot)}?${SLOT_TAB_PARAM}=setup`

export const environmentsUri = `/extension/environments/environments`

/**
 * Where configuring environments and slots lives, out of the operational screens (#1793).
 *
 * The only place in the UI where the word *slot* is allowed to appear.
 */
export const environmentsSetupUri = `/extension/environments/setup`

export const projectEnvironmentsUri = ({id}) => `/extension/environments/projects/${id}`

export const restEnvironmentImageUri = ({id}) => `/api/protected/images/environments/${id}`

export const environmentsBreadcrumbs = () => [
    ...homeBreadcrumbs(),
    <Link key="environments" href={environmentsUri}>Environments</Link>,
]

export const slotBreadcrumbs = (slot) => [
    ...environmentsBreadcrumbs(),
    <Link key="slot" href={slotUri(slot)}>{slotTitle(slot)}</Link>,
]
