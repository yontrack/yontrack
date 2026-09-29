import {expect} from "@playwright/test";

/**
 * Checks on sticky table headers (#1932).
 *
 * A table header sticks to its nearest scroll container: the body of a grid section, or the
 * window for a plain page. The check below finds that container from the table itself, scrolls it,
 * and measures where the header ended up - so a spec says which table, never which container.
 */

/**
 * Runs in the browser: scrolls the nearest ancestor of the header which scrolls vertically - the
 * document's scrolling element when none does - to its bottom, and measures the header and the
 * first body row against the visible area of that container, in viewport coordinates.
 */
const scrollAndMeasure = (container) => {
    const header = container.querySelector('thead')
    // Past antd's hidden measure row
    const firstRow = container.querySelector('tbody tr:not([aria-hidden])')
    let scroller = document.scrollingElement
    for (let p = header.parentElement; p && p !== document.body; p = p.parentElement) {
        const {overflowY} = getComputedStyle(p)
        if ((overflowY === 'auto' || overflowY === 'scroll') && p.scrollHeight > p.clientHeight) {
            scroller = p
            break
        }
    }
    scroller.scrollTop = scroller.scrollHeight
    const inWindow = scroller === document.scrollingElement
    let view
    if (inWindow) {
        view = {top: 0, bottom: window.innerHeight}
    } else {
        const top = scroller.getBoundingClientRect().top + scroller.clientTop
        view = {top, bottom: top + scroller.clientHeight}
    }
    const headerRect = header.getBoundingClientRect()
    return {
        inWindow,
        scrollTop: scroller.scrollTop,
        view,
        header: {top: headerRect.top, bottom: headerRect.bottom},
        firstRowTop: firstRow.getBoundingClientRect().top,
    }
}

/**
 * Scrolls the scroll container of the table in `container` to its bottom, and checks that its
 * header is still inside the visible area of that container.
 *
 * The first body row having scrolled under the header is checked as well: without it, a table
 * too short to scroll would pass without its header ever having had to stick.
 *
 * @param container Locator holding exactly one table: a section, a widget, or the table itself
 * @param inWindow `true` when the table is expected to scroll with the window, `false` when inside
 * a scrolling section
 */
export const expectStickyHeader = async (container, {inWindow}) => {
    await expect(container.locator('thead')).toBeVisible()
    // A section below the fold would measure outside the window whatever its header does
    await container.scrollIntoViewIfNeeded()
    await expect.poll(async () => {
        // Scrolling again on every attempt: rows still arriving make the container taller
        const m = await container.evaluate(scrollAndMeasure)
        return {
            inWindow: m.inWindow,
            scrolled: m.scrollTop > 0,
            rowsUnderHeader: m.firstRowTop < m.header.bottom - 1,
            headerVisible: m.header.top >= m.view.top - 1 && m.header.bottom <= m.view.bottom + 1,
        }
    }, {message: 'The table header must stay visible when its container scrolls'}).toEqual({
        inWindow,
        scrolled: true,
        rowsUnderHeader: true,
        headerVisible: true,
    })
}
