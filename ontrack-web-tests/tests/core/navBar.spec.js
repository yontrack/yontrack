const {expect} = require('@playwright/test');
const {login} = require("./login");
const {test} = require("../fixtures/connection");

/**
 * The desktop UI's shared header, at phone width.
 *
 * The desktop UI is not responsive and is not becoming responsive - that
 * retrofit is exactly what the mobile UI exists to avoid. This file pins one
 * thing and nothing more: the **user-menu trigger stays tappable at phone
 * width**, because the "Mobile version" entry behind it is the only way back
 * from the desktop UI to the mobile one (#1719). A user who cannot hit it is
 * stranded on the desktop UI on a phone - and once the mobile UI is installed
 * as a PWA there is no address bar to escape with.
 *
 * What used to break it: the antd `Header` is a fixed 64px box with a 64px
 * `line-height`, so the user's name wrapped into 64px-tall line boxes and grew
 * the nav row to 192px. The row overflowed its own header and painted over the
 * page bar below, where the busiest bar - the home page's - put "New project"
 * on top of the avatar. `document.elementFromPoint` at the centre of
 * `#user-menu` resolved to that command, and a tap opened the wrong dialog.
 *
 * These tests run under the suite's default desktop user agent and only narrow
 * the viewport: a phone user agent would be redirected to `/mobile` and never
 * reach the desktop header at all. `mobile.spec.js` covers the phone-agent
 * round trip.
 */

/** 393px is the Pixel 5 the mobile suite uses; 375px is where a layout breaks first. */
const PHONE_WIDTHS = [393, 375]

/**
 * Where `#user-menu` is, and what is actually painted at its centre.
 */
const probeUserMenu = page => page.evaluate(() => {
    const trigger = document.querySelector('#user-menu')
    const rect = trigger.getBoundingClientRect()
    const x = rect.x + rect.width / 2
    const y = rect.y + rect.height / 2
    const hit = document.elementFromPoint(x, y)
    return {
        rect: {x: rect.x, y: rect.y, width: rect.width, height: rect.height},
        // The trigger is an <svg>; a point inside it can resolve to one of its
        // own <path> children, which is still the trigger being hit.
        hitIsTrigger: hit === trigger || trigger.contains(hit),
        hitDescription: hit ? `${hit.tagName}.${hit.getAttribute('class') ?? ''} "${(hit.textContent ?? '').trim().slice(0, 40)}"` : 'nothing',
        viewport: {width: innerWidth, height: innerHeight},
    }
})

for (const width of PHONE_WIDTHS) {

    test(`the user-menu trigger is tappable at ${width}px`, async ({page, ontrack}) => {
        await page.setViewportSize({width, height: 812})
        await login(page, ontrack)
        // The home page: the busiest page bar in the UI, and the one the issue
        // was reported against.
        await page.goto(ontrack.connection.ui)
        await expect(page.getByTestId('user-menu-trigger')).toBeVisible()

        const probe = await probeUserMenu(page)
        expect(probe.viewport.width).toEqual(width)
        // Inside the viewport, not pushed off the side of it.
        expect(probe.rect.x).toBeGreaterThanOrEqual(0)
        expect(probe.rect.x + probe.rect.width).toBeLessThanOrEqual(width)
        // And nothing painted on top of it.
        expect(probe.hitIsTrigger,
            `centre of #user-menu resolves to ${probe.hitDescription}`).toBe(true)

        // The whole point: a tap opens the menu, and the escape hatch is in it.
        // Playwright's own actionability check would fail here too if anything
        // covered the trigger.
        await page.locator('#user-menu').click()
        await expect(page.getByText("Mobile version", {exact: true})).toBeVisible()
    })

    test(`the header controls stay inside the header at ${width}px`, async ({page, ontrack}) => {
        await page.setViewportSize({width, height: 812})
        await login(page, ontrack)
        await page.goto(ontrack.connection.ui)
        await expect(page.getByTestId('user-menu-trigger')).toBeVisible()

        const geometry = await page.evaluate(() => {
            const box = selector => {
                const el = document.querySelector(selector)
                if (!el) return null
                const {x, y, width, height} = el.getBoundingClientRect()
                return {x, y, width, height}
            }
            return {
                header: box('.ant-layout-header'),
                navBar: box('[data-testid="nav-bar"]'),
                pageBar: box('[data-testid="main-page-bar"]'),
            }
        })

        // The nav row no longer outgrows the 64px box it lives in, so it cannot
        // reach the page bar below - which is what put "New project" on top of
        // the avatar.
        expect(geometry.navBar.height).toBeLessThanOrEqual(geometry.header.height)
        expect(geometry.navBar.y + geometry.navBar.height)
            .toBeLessThanOrEqual(geometry.pageBar.y + 1)

        // And the page never scrolls sideways at phone width.
        const overflows = await page.evaluate(() =>
            document.documentElement.scrollWidth > document.documentElement.clientWidth)
        expect(overflows).toBe(false)
    })
}
