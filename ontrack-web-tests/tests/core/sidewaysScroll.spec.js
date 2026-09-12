const {expect} = require('@playwright/test');
const {login} = require("./login");
const {test} = require("../fixtures/connection");
const {expectNoSidewaysScroll} = require("../support/page-utils");

/**
 * `expectNoSidewaysScroll` itself.
 *
 * The helper is the suite's one answer to "does this page scroll sideways?",
 * and seven call sites lean on it - one in `navBar.spec.js`, six in
 * `mobile.spec.js`. Every one of those exercises the green direction only: they
 * pass when the helper is right, and they pass just as quietly when the helper
 * has stopped asserting anything at all. That is the hole #1735's fix could have
 * opened - a check made to stop flaking by making it stop checking - so this
 * file supplies the other direction, and the helper is never merely assumed to
 * hold.
 *
 * The second test is the one that earns the helper's shape. Sampling twice is
 * the decision the whole issue turns on, and no call site can tell a two-sample
 * helper from a one-sample one: on a settled page both answer the same. Only a
 * page whose width changes *between* the samples separates them, so this file
 * makes one, in both directions - an overflow that clears must be tolerated,
 * and one that arrives late must not be.
 *
 * The fixture is the home page at phone width, which is what the helper was
 * written for; the overflow is injected rather than caused, because what is
 * under test here is the measurement and not any layout.
 */

/** A 1px-tall block on <body>, outside the layout's own containers. */
const PROBE = 'sideways-scroll-probe'

/**
 * Puts the probe on the page at `width`, and optionally resizes it to
 * `then.width` after `then.afterMs` - the page changing width on its own, which
 * is the whole hazard.
 */
const probe = (page, width, then = null) => page.evaluate(({id, width, then}) => {
    const el = document.createElement('div')
    el.id = id
    el.style.width = `${width}px`
    el.style.height = '1px'
    document.body.appendChild(el)
    if (then) setTimeout(() => el.style.width = `${then.width}px`, then.afterMs)
}, {id: PROBE, width, then})

const removeProbe = page => page.evaluate(id => document.getElementById(id)?.remove(), PROBE)

const onTheHomePageAtPhoneWidth = async (page, ontrack) => {
    await page.setViewportSize({width: 375, height: 812})
    await login(page, ontrack)
    await page.goto(ontrack.connection.ui)
    await expect(page.getByTestId('user-menu-trigger')).toBeVisible()
    // Green before anything is injected, so a red below is known to come from
    // the probe rather than from the page it is injected into.
    await expectNoSidewaysScroll(page)
}

test('a page that scrolls sideways is rejected, and the message says by how much', async ({page, ontrack}) => {
    await onTheHomePageAtPhoneWidth(page, ontrack)

    await probe(page, 3000)
    // A shorter timeout than the helper's own default: the point being made is
    // that a settled overflow is reported at all, and the helper's bound is what
    // keeps a real one from costing the suite's 30s `expect` timeout.
    await expect(expectNoSidewaysScroll(page, {timeout: 1500})).rejects
        .toThrow(/scrolls sideways: scrollWidth \d+ exceeds clientWidth \d+ by \d+px/)

    // And green again once the cause is gone, so the failure above was the probe
    // rather than the helper having become unsatisfiable.
    await removeProbe(page)
    await expectNoSidewaysScroll(page)
})

test('an overflow that clears is tolerated, one that arrives late is not', async ({page, ontrack}) => {
    await onTheHomePageAtPhoneWidth(page, ontrack)

    // #1735 itself: the page is too wide when first read and settles a moment
    // later, the way a widget mid-layout is. The helper has to ride this out -
    // failing here is the flake it exists to remove.
    await probe(page, 3000, {width: 1, afterMs: 400})
    await expectNoSidewaysScroll(page)
    await removeProbe(page)

    // And the opposite, which is what the second sample buys. The page is narrow
    // when first read and overflows a moment later, the way a dashboard is
    // before its widgets land. A helper that stopped at one sample would pass
    // this - and would be exactly the "fix" that turns the check off.
    await probe(page, 1, {width: 3000, afterMs: 100})
    await expect(expectNoSidewaysScroll(page, {timeout: 1500})).rejects
        .toThrow(/scrolls sideways/)
})
