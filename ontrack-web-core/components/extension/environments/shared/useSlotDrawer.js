import {useRouter} from "next/router"

/** The query parameter the slot drawer lives in. */
export const SLOT_DRAWER_PARAM = 'slot'

/**
 * Opening and closing the slot drawer, through the URL.
 *
 * **The drawer's open state is the URL, not a `useState`.** `?slot=<id>` is what makes it survive a
 * reload and makes it shareable - "look at this" is a link, not a description of which cell to
 * click - and it puts the back button in charge of closing it, which is what a reader expects of
 * something that covers the page. A local boolean would give none of that, and the two would then
 * have to be kept in step.
 *
 * The navigation is `shallow`, so opening the drawer does not re-run the page's data fetching: the
 * matrix behind it is unchanged by which slot is being looked at.
 *
 * @return {{slotId: ?string, open: boolean, openSlot: function, close: function}}
 */
export const useSlotDrawer = () => {
    const router = useRouter()

    const raw = router?.query?.[SLOT_DRAWER_PARAM]
    // A query parameter repeated in the URL arrives as an array. One drawer, so the first wins.
    const slotId = Array.isArray(raw) ? raw[0] : raw

    const openSlot = (slot) => {
        const id = typeof slot === 'string' ? slot : slot?.id
        if (!id) return
        router.push(
            {pathname: router.pathname, query: {...router.query, [SLOT_DRAWER_PARAM]: id}},
            undefined,
            {shallow: true},
        )
    }

    const close = () => {
        const query = {...router.query}
        delete query[SLOT_DRAWER_PARAM]
        router.push({pathname: router.pathname, query}, undefined, {shallow: true})
    }

    return {
        slotId: slotId ?? null,
        open: !!slotId,
        openSlot,
        close,
    }
}
