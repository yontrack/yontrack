import {activeMobileNavKey, MOBILE_NAV_ITEMS} from "@components/mobile/layout/mobileNav"

describe('MOBILE_NAV_ITEMS', () => {

    it('offers home and projects, and nothing that leads nowhere', () => {
        // Search was a third tab over a placeholder screen. Global search stays
        // desktop-only for 5.4, so one of three thumb-level destinations was
        // spent on a dead end - worse than two that work.
        expect(MOBILE_NAV_ITEMS.map(item => item.key)).toEqual(['home', 'projects'])
    })

    it('keeps every destination inside the mobile UI', () => {
        // A bottom-bar tab leaving `/mobile` would drop the user into the
        // desktop UI - and, once installed as a PWA, out of the app itself.
        MOBILE_NAV_ITEMS.forEach(item => {
            expect(item.href.startsWith('/mobile')).toBe(true)
        })
    })
})

describe('activeMobileNavKey', () => {

    it('highlights home on the mobile home', () => {
        expect(activeMobileNavKey('/mobile')).toEqual('home')
    })

    it('highlights the tab a screen belongs to, not just its own path', () => {
        // A project screen reached from the Projects tab keeps that tab lit.
        expect(activeMobileNavKey('/mobile/projects')).toEqual('projects')
        expect(activeMobileNavKey('/mobile/projects/12')).toEqual('projects')
    })

    it('lights nothing on a screen no tab owns any more', () => {
        // `/mobile/search` is gone with its tab; a stale bookmark to it now
        // 404s rather than lighting a tab that is not there.
        expect(activeMobileNavKey('/mobile/search')).toBeNull()
    })

    it('does not light home for every mobile screen', () => {
        // `/mobile` is a prefix of everything under it, so a naive prefix match
        // would keep Home lit on every screen.
        expect(activeMobileNavKey('/mobile/projects')).not.toEqual('home')
    })

    it('lights nothing on a screen that belongs to no tab', () => {
        expect(activeMobileNavKey('/mobile/desktop-only')).toBeNull()
    })

    it('lights nothing on the account screen', () => {
        // It belongs to neither destination, exactly as the interstitial does -
        // and it is deliberately not a fourth tab: two thumb-level destinations
        // are what the bar carries, and a settings page does not earn one.
        expect(activeMobileNavKey('/mobile/account')).toBeNull()
    })
})
