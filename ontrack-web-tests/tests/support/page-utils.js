import {expect} from "@playwright/test";

export const expectOnPage = async (page, pageId) => {
    await expect(page.locator(`[data-page-id="page-${pageId}"]`)).toBeVisible()
}

/** One reading of how wide the document is against how wide its viewport is. */
const readWidths = page => page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
}))

const describeWidths = ({scrollWidth, clientWidth}) =>
    `the page scrolls sideways: scrollWidth ${scrollWidth} exceeds clientWidth ${clientWidth} by ${scrollWidth - clientWidth}px`

/**
 * The page does not scroll sideways - the acceptance criterion every mobile
 * surface has to meet, and the one the desktop header has to meet at phone
 * width.
 *
 * Why this samples twice rather than once, which is the whole point of the
 * helper. `page.evaluate` does not auto-wait, unlike an `expect` assertion, so a
 * single read answers for the instant it runs. On a page whose content lays out
 * asynchronously - the dashboard's `react-grid-layout` widgets, a card waiting
 * on its own deployments query - the document is transiently too wide while a
 * child is mid-layout, or in the moment a scrollbar appears and squeezes
 * `clientWidth`. The read comes back `true`, the assertion fails, and the retry
 * passes because the page is warm by then. That is #1735, and it is the same
 * hazard as #1730's unwaited `boundingBox()` wearing a different costume.
 *
 * The obvious fix - retry until the read is `false` - trades that flake for a
 * worse one in the opposite direction. A page starts narrow and empty, so the
 * *first* sample is already `false` and the assertion passes before the content
 * whose width it is supposed to be measuring exists at all. An overflow that
 * arrives with the widgets would never be seen, and silence would read as
 * success - the failure mode ADR-0005 records this repo as refusing. So the
 * helper requires the answer to be `false` on two reads separated by
 * `settleInterval`, and only then passes: a transient overflow during layout no
 * longer fails, a settled one still does, and a page that is merely not painted
 * yet has to stay non-overflowing across the interval to satisfy it.
 *
 * The timeout is the helper's own, and much shorter than the suite's 30s
 * `expect` default, so a genuine overflow fails in seconds rather than at the
 * end of a long wait. Both it and the interval are overridable for a screen
 * known to settle more slowly.
 *
 * On failure the message names both widths, so the reader learns by how much the
 * page overflowed without re-running locally with a `console.log`.
 */
export const expectNoSidewaysScroll = async (page, {timeout = 5000, settleInterval = 250} = {}) => {
    await expect(async () => {
        const first = await readWidths(page)
        expect(first.scrollWidth, describeWidths(first)).toBeLessThanOrEqual(first.clientWidth)
        await page.waitForTimeout(settleInterval)
        const second = await readWidths(page)
        expect(second.scrollWidth, describeWidths(second)).toBeLessThanOrEqual(second.clientWidth)
    }).toPass({timeout})
}
