import {
    describeDesktopRoute,
    isRedirectExempt,
    mobileBranchUri,
    mobileBuildUri,
    MOBILE_ACCOUNT,
    MOBILE_HOME,
    mobileEquivalent,
    mobileProjectUri,
} from "@components/mobile/mobileRoutes"

describe('mobileEquivalent', () => {

    it('maps the desktop home to the mobile home', () => {
        expect(mobileEquivalent('/')).toEqual(MOBILE_HOME)
    })

    /*
     * The map is deliberately small: a route only earns an entry once the mobile
     * screen behind it actually exists. Sending a phone to a mobile route that
     * is still a placeholder is worse than the interstitial, which at least
     * offers the desktop page that does work.
     */
    it('maps a project to its mobile screen, keeping the id', () => {
        // The point of following a link to a project from a phone is to land on
        // *that* project, so the id has to survive the redirect.
        expect(mobileEquivalent('/project/12')).toEqual(mobileProjectUri('12'))
        expect(mobileEquivalent('/project/12')).toEqual('/mobile/project/12')
    })

    it('maps a branch to its mobile screen, keeping the id', () => {
        expect(mobileEquivalent('/branch/34')).toEqual(mobileBranchUri('34'))
        expect(mobileEquivalent('/branch/34')).toEqual('/mobile/branch/34')
    })

    it('maps a build to its mobile screen, keeping the id', () => {
        expect(mobileEquivalent('/build/56')).toEqual(mobileBuildUri('56'))
        expect(mobileEquivalent('/build/56')).toEqual('/mobile/build/56')
    })

    it.each([
        '/project/12/something',
        '/branch/',
        '/project/not-a-number',
    ])('does not mistake %s for an entity screen', (pathname) => {
        // The desktop routes are `/project/[id]` and `/branch/[id]` and nothing
        // else. A looser match would send a phone to a mobile screen that then
        // asked the server for a project whose id is a word.
        expect(mobileEquivalent(pathname)).toBeNull()
    })

    it.each([
        '/search',
        '/graphiql',
        '/core/admin/settings',
        '/extension/scm/my-project/changelog',
    ])('has no mobile equivalent for %s yet', (pathname) => {
        expect(mobileEquivalent(pathname)).toBeNull()
    })

    it('does not answer the desktop user profile with the account screen', () => {
        // `/core/admin/userProfile` is API tokens and groups; `/mobile/account`
        // is who you are and a sign-out button. The two share a name and nothing
        // else, and redirecting a phone there would answer a link about tokens
        // with a sign-out button. It keeps falling through to the interstitial
        // as "an administration page".
        expect(mobileEquivalent('/core/admin/userProfile')).toBeNull()
        expect(describeDesktopRoute('/core/admin/userProfile')).toEqual('an administration page')
    })

    it.each([
        '/',
        '/project/12',
        '/branch/34',
        '/build/56',
        '/core/admin/userProfile',
    ])('never answers %s with the account screen', (pathname) => {
        // The account screen is the mobile UI's own and stands in for no desktop
        // route: nothing on the desktop UI is "who you are plus a sign-out
        // button", and the redirect must never land someone on it.
        expect(mobileEquivalent(pathname)).not.toEqual(MOBILE_ACCOUNT)
    })

    it('is reached only from inside the mobile UI', () => {
        // Under `/mobile`, so the redirect leaves it alone entirely.
        expect(MOBILE_ACCOUNT).toEqual('/mobile/account')
        expect(isRedirectExempt(MOBILE_ACCOUNT)).toBe(true)
    })
})

describe('isRedirectExempt', () => {

    it.each([
        // Already there.
        ['/mobile', 'the mobile home'],
        ['/mobile/projects', 'a mobile screen'],
        ['/mobile/desktop-only', 'the interstitial itself'],
        // Redirecting the sign-in page would strand a phone user outside the
        // login flow - the interstitial it landed on is itself behind the login.
        ['/auth/signin', 'the sign-in page'],
        ['/api/protected/graphql', 'the API'],
        // `/display/...` answers with a redirect to the real page, which the
        // middleware then sees on its own terms.
        ['/display/build/my-project/main/1', 'a display redirect'],
        ['/_next/static/chunks/main.js', 'a build asset'],
        ['/favicon.ico', 'a static file'],
        ['/yontrack-logo.svg', 'a static file'],
    ])('leaves %s alone (%s)', (pathname) => {
        expect(isRedirectExempt(pathname)).toBe(true)
    })

    it.each([
        '/',
        '/project/12',
        '/extension/environments/slot/7',
    ])('does not exempt %s', (pathname) => {
        expect(isRedirectExempt(pathname)).toBe(false)
    })

    it('does not exempt a path that merely starts with the same letters', () => {
        // `/mobiles` is not under `/mobile`.
        expect(isRedirectExempt('/mobiles')).toBe(false)
        expect(isRedirectExempt('/authoring')).toBe(false)
    })
})

describe('describeDesktopRoute', () => {

    it.each([
        ['/project/12', 'a project'],
        ['/branch/34', 'a branch'],
        ['/build/56', 'a build'],
        ['/extension/scm/my-project/changelog', 'a change log'],
        ['/extension/scm/changelog', 'a change log'],
        ['/extension/environments/slot/7', 'an environment'],
        ['/core/admin/settings', 'an administration page'],
        ['/graphiql', 'GraphiQL'],
        ['/', 'the Yontrack home page'],
        ['/search', 'the search page'],
    ])('describes %s as "%s"', (pathname, description) => {
        expect(describeDesktopRoute(pathname)).toEqual(description)
    })

    it('has no description for a route it does not know', () => {
        // The interstitial falls back to naming the raw path, which is still
        // more use than a made-up label.
        expect(describeDesktopRoute('/something/entirely/new')).toBeNull()
    })
})
