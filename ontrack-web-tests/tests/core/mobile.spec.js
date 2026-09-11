const {devices, expect} = require('@playwright/test');
const {login, signInButton} = require("./login");
const {selectUserMenu} = require("./userMenu");
const {test} = require("../fixtures/connection");

/**
 * The mobile UI.
 *
 * The user agent is the whole input to the redirect, so most of this file runs
 * under a phone device rather than the suite's default `Desktop Chrome` - which
 * would exercise none of it. The one test that must *not* look like a phone is
 * in its own block.
 *
 * The theme assertions read `data-theme` off <html>: it is what every colour
 * token keys off, and it is set before the first paint. They assume the shared
 * account is in the default `system` theme mode, exactly as `theme.spec.js`
 * does - which is also what leaves that mode behind.
 */

/*
 * A phone, minus `defaultBrowserType`: Playwright refuses that one inside a
 * describe group because it would force a new worker, and the suite runs on
 * chromium anyway.
 */
const {defaultBrowserType: _ignored, ...PHONE} = devices['Pixel 5']

/** Signs in from a phone - which already goes through the redirect. */
const signInOnPhone = (page, ontrack) => login(page, ontrack, undefined, undefined, {
    // The sign-in page has to be exempt from the redirect, or this never
    // completes. Landing on the mobile home is the proof that it is. Matched on
    // the screen's own test id rather than on its text: "Home" is also the label
    // of a bottom-bar tab, and Next's route announcer repeats a page title on
    // top of that.
    ready: page => page.getByTestId('mobile-screen-title'),
})

/**
 * Signs back in from wherever a sign-out landed, without navigating first.
 *
 * `login` starts with a `goto`, which would decide the callback the test is
 * there to assert: where the sign-out put the user is exactly the question.
 *
 * The provider's own form is not guaranteed to appear. Yontrack's sign-out is
 * local - it ends Yontrack's session and leaves the identity provider's alone,
 * on the desktop UI as much as here, and making it a real sign-out is #1734 -
 * so the provider can answer silently and hand the session straight back.
 */
const signBackInOnPhone = async (page, ontrack) => {
    await (await signInButton(page)).click()

    const usernameField = page.getByRole("textbox", {exact: false, name: "Username"})
    // Either the provider asks, or it has already answered and the screen is up.
    await expect(usernameField.or(page.getByTestId('mobile-screen-title')).first()).toBeVisible()

    if (await usernameField.isVisible()) {
        const {username, password} = ontrack.connection.credentials
        await usernameField.fill(username)
        await page.getByRole("textbox", {exact: false, name: "Password"}).fill(password)
        await page.getByRole("button", {name: "Sign In", exact: true}).click()
    }
}

test.describe('the mobile UI on a phone', () => {

    test.use(PHONE)

    test('a phone lands on the mobile shell', async ({page, ontrack}) => {
        await signInOnPhone(page, ontrack)
        await page.goto(ontrack.connection.ui)
        await expect(page).toHaveURL(/\/mobile$/)
        await expect(page.getByTestId('mobile-header')).toBeVisible()
        await expect(page.getByTestId('mobile-nav')).toBeVisible()
        // The tab the user is on, for the eye and for a screen reader alike.
        await expect(page.getByTestId('mobile-nav-home')).toHaveAttribute('aria-current', 'page')
    })

    test('the header carries the brand, not the word set in the UI font', async ({page, ontrack}) => {
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile`)

        // Both marks actually decode - a `next/image` pointing at nothing still
        // leaves an <img> in the DOM, so being visible proves too little.
        for (const testId of ['mobile-logo', 'mobile-wordmark']) {
            const mark = page.getByTestId(testId)
            await expect(mark).toBeVisible()
            expect(await mark.evaluate(img => img.naturalWidth)).toBeGreaterThan(0)
        }

        // And at their own aspect ratios. Squashing a drawn wordmark is the
        // failure this guards - the desktop `NavBar` puts the 8.08:1 mark in a
        // 120x24 box, and Next says so.
        for (const [testId, width, height] of [['mobile-logo', 27, 24], ['mobile-wordmark', 129, 16]]) {
            const box = await page.getByTestId(testId).boundingBox()
            expect(box.width).toBeCloseTo(width, 0)
            expect(box.height).toBeCloseTo(height, 0)
        }
    })

    test('the shell renders in both themes', async ({page, ontrack}) => {
        await signInOnPhone(page, ontrack)

        await page.emulateMedia({colorScheme: 'light'})
        await page.goto(`${ontrack.connection.ui}/mobile`)
        await expect(page.getByTestId('mobile-header')).toBeVisible()
        await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')

        await page.emulateMedia({colorScheme: 'dark'})
        await page.reload()
        await expect(page.getByTestId('mobile-header')).toBeVisible()
        await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    })

    test('the home screen is the favourites, and the project list is one tap away', async ({page, ontrack}) => {
        // A project nothing has starred yet, so the loop below starts where a
        // first-time user starts.
        const project = await ontrack.createProject()

        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile`)

        // One tap, from the bottom bar.
        await page.getByTestId('mobile-nav-projects').click()
        await expect(page).toHaveURL(/\/mobile\/projects$/)

        // Through the filter rather than by scrolling: an instance holds far more
        // projects than a phone screen, which is what the filter is for.
        await page.getByTestId('mobile-projects-filter').fill(project.name)
        await expect(page.getByTestId('mobile-projects')).toContainText(project.name)

        const star = page.getByTestId(`mobile-favourite-project-${project.id}`)
        await expect(star).toHaveAttribute('aria-pressed', 'false')
        await star.click()
        await expect(star).toHaveAttribute('aria-pressed', 'true')

        // Home is the favourites, so what was just starred is on it.
        await page.getByTestId('mobile-nav-home').click()
        await expect(page.getByTestId(`mobile-project-${project.id}`)).toContainText(project.name)

        // And unstarring from the home screen itself takes it away again.
        await page.getByTestId(`mobile-favourite-project-${project.id}`).click()
        await expect(page.getByTestId(`mobile-project-${project.id}`)).toHaveCount(0)
    })

    test('a favourite branch is on the home screen, under the project it belongs to', async ({page, ontrack}) => {
        // Favourite branches come from every project at once, so the home screen
        // has to say which project each one belongs to.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        await branch.favourite()

        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile`)

        const row = page.getByTestId(`mobile-branch-${branch.id}`)
        await expect(row).toContainText(branch.name)
        await expect(row).toContainText(project.name)
    })

    test('a user gets from the home screen to a build, on a phone', async ({page, ontrack}) => {
        // The whole path the project and branch screens exist for. A build
        // carrying a release, a promotion and nothing else - the card has to
        // show the version people talk about rather than the timestamp-run pair
        // Yontrack calls a build.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        const promotionLevel = await branch.createPromotionLevel()
        const build = await branch.createBuild()
        await build.setRelease('1.4.0')
        await build.promote(promotionLevel)

        // The narrowest phone the acceptance names. The device profile above is
        // 393px wide, and 375 is where a layout breaks first.
        await page.setViewportSize({width: 375, height: 812})

        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile`)

        // Home -> the project list, one tap from the bottom bar.
        await page.getByTestId('mobile-nav-projects').click()
        await page.getByTestId('mobile-projects-filter').fill(project.name)
        await expect(page.getByTestId(`mobile-project-${project.id}`)).toBeVisible()

        // -> the project.
        await page.getByTestId(`mobile-project-${project.id}`).getByRole('link').click()
        await expect(page).toHaveURL(new RegExp(`/mobile/project/${project.id}$`))
        await expect(page.getByTestId('mobile-screen-title')).toContainText(project.name)

        // The branch list is limited, so the filter is how a branch beyond the
        // limit is reached - and it has to find this one whatever else the
        // project holds.
        await page.getByTestId('mobile-branches-filter').fill(branch.name)
        await expect(page.getByTestId(`mobile-branch-${branch.id}`)).toBeVisible()

        // -> the branch.
        await page.getByTestId(`mobile-branch-${branch.id}`).getByRole('link').click()
        await expect(page).toHaveURL(new RegExp(`/mobile/branch/${branch.id}$`))
        await expect(page.getByTestId('mobile-screen-title')).toContainText(branch.name)
        // Which project this branch belongs to, and the way back up to it.
        await expect(page.getByTestId('mobile-screen-subtitle')).toContainText(project.name)

        // -> the build, as a card rather than as a row of a matrix.
        const card = page.getByTestId(`mobile-build-${build.id}`)
        await expect(card).toContainText('1.4.0')
        // Legible without zooming: the promotion is named, not only drawn.
        await expect(card).toContainText(promotionLevel.name)

        // And none of it scrolls sideways, which is the acceptance criterion the
        // desktop branch matrix cannot meet at any width.
        const overflows = await page.evaluate(() =>
            document.documentElement.scrollWidth > document.documentElement.clientWidth)
        expect(overflows).toBe(false)
    })

    test('a build is found on a branch by name, by promotion, and by both', async ({page, ontrack}) => {
        // The whole of #1723: two controls on the branch screen, scoped to that
        // branch. Everything else `StandardBuildFilter` offers stays on the
        // desktop - reproducing `BuildFilterDialog` on a phone is the trap.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        const promotionLevel = await branch.createPromotionLevel()

        const promoted = await branch.createBuild()
        await promoted.setRelease('1.4.0')
        await promoted.promote(promotionLevel)

        const plain = await branch.createBuild()
        await plain.setRelease('9.9.9')

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/branch/${branch.id}`)

        const promotedCard = page.getByTestId(`mobile-build-${promoted.id}`)
        const plainCard = page.getByTestId(`mobile-build-${plain.id}`)

        // Both builds to start with: the search narrows a list that is already
        // there rather than being the way to see one.
        await expect(promotedCard).toBeVisible()
        await expect(plainCard).toBeVisible()

        // By name - matched against the display name, which is the release
        // label here rather than the timestamp-run pair the build is called.
        await page.getByTestId('mobile-builds-filter').fill('1.4.0')
        await expect(promotedCard).toBeVisible()
        await expect(plainCard).toBeHidden()

        // Clearing gives the whole list back.
        await page.getByTestId('mobile-builds-filter').fill('')
        await expect(plainCard).toBeVisible()

        // By promotion level, on its own.
        await page.getByTestId('mobile-builds-promotion').click()
        await page.locator('.ant-select-item-option').filter({hasText: promotionLevel.name}).click()
        await expect(promotedCard).toBeVisible()
        await expect(plainCard).toBeHidden()

        // And the two together, which here can match nothing - the screen says
        // that, rather than claiming the branch has no build.
        await page.getByTestId('mobile-builds-filter').fill('9.9.9')
        await expect(page.getByTestId('mobile-builds-empty'))
            .toContainText(`No build named "9.9.9" has been promoted to ${promotionLevel.name}.`)

        // Clearing both gives the default latest-builds list back - the last of
        // the four acceptance criteria, and the one a search is useless without.
        await page.getByTestId('mobile-builds-filter').fill('')
        await page.getByTestId('mobile-builds-promotion').hover()
        await page.locator('.ant-select-clear').click()
        await expect(promotedCard).toBeVisible()
        await expect(plainCard).toBeVisible()

        // Two more controls, and still nothing scrolls sideways at 375px.
        const overflows = await page.evaluate(() =>
            document.documentElement.scrollWidth > document.documentElement.clientWidth)
        expect(overflows).toBe(false)
    })

    test('the build screen carries the promotions, deployments and validations', async ({page, ontrack}) => {
        // The decision surface: everything needed to answer "should I promote or
        // deploy this?", on one phone screen.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        const promotionLevel = await branch.createPromotionLevel()
        const validationStamp = await branch.createValidationStamp()
        const build = await branch.createBuild()
        await build.setRelease('1.4.0')
        await build.promote(promotionLevel)
        await build.validate(validationStamp, {status: 'PASSED'})

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)

        // Reached from the branch screen, by tapping the build's card.
        await page.goto(`${ontrack.connection.ui}/mobile/branch/${branch.id}`)
        await page.getByTestId(`mobile-build-${build.id}`).getByRole('link').click()
        await expect(page).toHaveURL(new RegExp(`/mobile/build/${build.id}$`))

        // Identity, and the way back up to both the branch and the project.
        await expect(page.getByTestId('mobile-screen-title')).toContainText('1.4.0')
        await expect(page.getByTestId('mobile-screen-subtitle')).toContainText(project.name)
        await expect(page.getByTestId('mobile-screen-subtitle')).toContainText(branch.name)

        await expect(page.getByTestId('mobile-build-promotions')).toContainText(promotionLevel.name)
        // Validations are read-only here, and deliberately present: whether the
        // build is green is the input to the decision this screen serves.
        await expect(page.getByTestId('mobile-build-validations')).toContainText(validationStamp.name)

        // Nothing deployed, and the screen says so rather than showing a gap.
        await expect(page.getByTestId('mobile-build-deployments')).toContainText(/not deployed/i)

        // And none of it scrolls sideways at 375px.
        const overflows = await page.evaluate(() =>
            document.documentElement.scrollWidth > document.documentElement.clientWidth)
        expect(overflows).toBe(false)
    })

    test('the build actions are gated by what the user may do', async ({page, ontrack}) => {
        // The admin account the suite runs as may promote, so the entry point is
        // there. The negative half is covered by the component tests, which can
        // hand the screen an unauthorized build without needing a second account.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        const build = await branch.createBuild()

        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/build/${build.id}`)
        await expect(page.getByTestId('mobile-build-promote')).toBeVisible()
    })

    test('a favourite branch on the home screen taps through to itself', async ({page, ontrack}) => {
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        await branch.favourite()

        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile`)

        await page.getByTestId(`mobile-branch-${branch.id}`).getByRole('link').click()
        await expect(page).toHaveURL(new RegExp(`/mobile/branch/${branch.id}$`))
    })

    test('the favourite toggles work on the project and branch screens', async ({page, ontrack}) => {
        const project = await ontrack.createProject()
        const branch = await project.createBranch()

        await signInOnPhone(page, ontrack)

        // On the project screen: the project itself, and each of its branches.
        await page.goto(`${ontrack.connection.ui}/mobile/project/${project.id}`)
        const projectStar = page.getByTestId(`mobile-favourite-project-${project.id}`)
        await expect(projectStar).toHaveAttribute('aria-pressed', 'false')
        await projectStar.click()
        await expect(projectStar).toHaveAttribute('aria-pressed', 'true')

        const branchStar = page.getByTestId(`mobile-favourite-branch-${branch.id}`)
        await branchStar.click()
        await expect(branchStar).toHaveAttribute('aria-pressed', 'true')

        // And on the branch screen, where the star acts on the branch being
        // looked at. It is already starred, so this one unstars it.
        await page.goto(`${ontrack.connection.ui}/mobile/branch/${branch.id}`)
        const ownStar = page.getByTestId(`mobile-favourite-branch-${branch.id}`)
        await expect(ownStar).toHaveAttribute('aria-pressed', 'true')
        await ownStar.click()
        await expect(ownStar).toHaveAttribute('aria-pressed', 'false')
    })

    test('a link to a desktop project or branch lands on its mobile screen', async ({page, ontrack}) => {
        // The point of the route map: a link shared from a desktop session has
        // to keep its entity, not drop the phone on the home screen or on the
        // interstitial.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()

        await signInOnPhone(page, ontrack)

        await page.goto(`${ontrack.connection.ui}/project/${project.id}`)
        await expect(page).toHaveURL(new RegExp(`/mobile/project/${project.id}$`))

        await page.goto(`${ontrack.connection.ui}/branch/${branch.id}`)
        await expect(page).toHaveURL(new RegExp(`/mobile/branch/${branch.id}$`))

        const build = await branch.createBuild()
        await page.goto(`${ontrack.connection.ui}/build/${build.id}`)
        await expect(page).toHaveURL(new RegExp(`/mobile/build/${build.id}$`))
    })

    test('a route with no mobile equivalent gets the interstitial', async ({page, ontrack}) => {
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/search`)
        // Named, not silently swallowed: neither the desktop page nor the mobile
        // home would tell the user what became of their link.
        await expect(page).toHaveURL(/\/mobile\/desktop-only\?target=%2Fsearch$/)
        await expect(page.getByTestId('desktop-only-destination')).toContainText('the search page')
    })

    test('the signed-in name in the header is the door to the account screen', async ({page, ontrack}) => {
        // The narrowest phone the acceptance names, where a tap target is
        // tightest.
        await page.setViewportSize({width: 375, height: 812})

        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile`)

        // It used to be 13px of text at 0.85 opacity with no affordance at all.
        // The whole header row is the target now, not the glyph.
        const name = page.getByTestId('mobile-user')
        const box = await name.boundingBox()
        expect(box.height).toBeGreaterThanOrEqual(44)

        await name.click()
        await expect(page).toHaveURL(/\/mobile\/account$/)

        // Who is signed in, and what they would read out on a support thread -
        // the PWA has no address bar and no user menu to find it in.
        await expect(page.getByTestId('mobile-screen-title')).not.toBeEmpty()
        await expect(page.getByTestId('mobile-account-version')).not.toBeEmpty()

        // It belongs to neither bottom-bar destination, exactly as the
        // interstitial does - and is deliberately not a fourth tab.
        for (const tab of ['mobile-nav-home', 'mobile-nav-projects']) {
            await expect(page.getByTestId(tab)).not.toHaveAttribute('aria-current', 'page')
        }
    })

    test('signing out comes back to the mobile home, not to where they were', async ({page, ontrack}) => {
        // The assertion that actually pins the `callbackUrl` decision. With
        // `signOut()` and no argument the callback defaults to the current URL,
        // so signing out of a branch screen would bring the *next* person to
        // pick up the phone straight back to it.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()

        await signInOnPhone(page, ontrack)

        await page.goto(`${ontrack.connection.ui}/mobile/branch/${branch.id}`)
        await expect(page.getByTestId('mobile-screen-title')).toContainText(branch.name)

        // Two taps, and no confirmation to get past on the second.
        await page.getByTestId('mobile-user').click()
        await page.getByTestId('mobile-account-sign-out').click()

        // Out of the app, and on somewhere they can sign back in from - which
        // the helper asserts for us.
        await signInButton(page)

        await signBackInOnPhone(page, ontrack)
        await expect(page).toHaveURL(/\/mobile$/)
    })

    test('a phone can switch to the desktop UI and back again', async ({page, ontrack}) => {
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/search`)
        await page.getByTestId('open-desktop-version').click()

        // It actually gets there, rather than being bounced straight back by the
        // redirect - which is what the cookie is for.
        await expect(page).toHaveURL(/\/search$/)

        // And that cookie dies with the browser session - a second line behind
        // the way back below, for everything else that could go wrong with a
        // desktop UI that is not responsive. Playwright reports -1 for a
        // session cookie.
        const [optOut] = (await page.context().cookies())
            .filter(cookie => cookie.name === 'yontrack-ui')
        expect(optOut.value).toEqual('desktop')
        expect(optOut.expires).toEqual(-1)

        // And it stays there.
        await page.goto(ontrack.connection.ui)
        await expect(page.getByText("Dashboard", {exact: true})).toBeVisible()

        // The way back. Without it, a phone that once chose the desktop UI would
        // be stranded on it - and once the mobile UI is installed as a PWA there
        // is no address bar to escape with.
        //
        // Driven at the phone's own viewport, which is the point: this used to
        // need widening to 1280px first, because the desktop header row grew
        // past its own 64px box and put the home page's "New project" command
        // on top of the avatar (#1729). The hit target itself is pinned by
        // `navBar.spec.js`; what this step proves is the whole round trip, from
        // a phone, at phone width.
        await selectUserMenu(page, "Mobile version")
        await expect(page).toHaveURL(/\/mobile$/)
        await expect(page.getByTestId('mobile-header')).toBeVisible()
    })
})

test('a desktop browser is left alone', async ({page, ontrack}) => {
    await login(page, ontrack)
    await expect(page).not.toHaveURL(/\/mobile/)
})
