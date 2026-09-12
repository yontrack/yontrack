const {devices, expect} = require('@playwright/test');
const {login, signInButton} = require("./login");
const {selectUserMenu} = require("./userMenu");
const {expectTheme, resetThemeMode} = require("./theme");
const {test} = require("../fixtures/connection");
const {expectNoSidewaysScroll} = require("../support/page-utils");
const {generate} = require("@ontrack/utils");
const {subscribeToWorkflow, waitForPromotionRunWorkflow} = require("../support/workflows");
const {
    overridePipelineWorkflow,
    waitForPipelineWorkflowToBeFinished,
    withSlotWorkflow,
} = require("../extensions/environments/workflows/slotWorkflowsFixtures");
const {addSlotWorkflow} = require("@ontrack/extensions/environments/workflows");
const {createPipeline} = require("../extensions/environments/pipelineFixtures");

/**
 * The mobile UI.
 *
 * This file is the acceptance of the whole mobile UI initiative, and is meant to
 * be readable as such. The journeys the mobile UI exists for, and where each one
 * is pinned:
 *
 * | Journey | Tests |
 * |---|---|
 * | Locate a project and a branch | "the home screen is the favourites…", "a user gets from the home screen to a build…" |
 * | See the latest builds, with their promotions and deployments | "a user gets from the home screen to a build…" (promotions), "a deployed build says where it is…" (deployments), "the build screen carries the promotions, deployments and validations" |
 * | Search for a build | "a build is found on a branch by name, by promotion, and by both" |
 * | Promote a build | "a build is promoted from a phone, required fields and all" |
 * | Deploy a build, with approval and override | "a build is deployed from a phone…", "a blocked deployment is overridden from a phone…" |
 * | See a deployment through, or kill it | "a deployment is completed from a phone…", "a deployment is cancelled from a phone…" |
 * | See what a workflow did, and where it got stuck | "a promotion carries the workflows it set off…" (promotions, the run itself), "a deployment draws its slot's workflows…" (deployments) |
 *
 * And the shell they all run inside, which has five behaviours of its own: the
 * redirect ("a phone lands on the mobile shell", and its negative "a desktop
 * browser is left alone" at the foot of the file), the entity route map ("a link
 * to a desktop project or branch lands on its mobile screen"), the interstitial
 * ("a route with no mobile equivalent gets the interstitial"), the desktop
 * opt-out with its way back ("a phone can switch to the desktop UI and back
 * again"), and the deliberate door to the desktop UI ("a phone reaches the
 * desktop version from the account screen"), which is the one a user takes when
 * they *want* the desktop UI rather than having been sent to a page the mobile
 * UI does not have.
 *
 * A journey gaining a screen gains a row here rather than a spec file of its
 * own: the shell, the theme and the account are shared by all of them, and so
 * are the fixtures and the hooks below.
 *
 * The user agent is the whole input to the redirect, so most of this file runs
 * under a phone device rather than the suite's default `Desktop Chrome` - which
 * would exercise none of it. The one test that must *not* look like a phone is
 * in its own block.
 *
 * The theme assertions read `data-theme` off <html>: it is what every colour
 * token keys off, and it is set before the first paint.
 *
 * This file both reads *and writes* the theme mode - the account screen carries
 * the control, and one of the tests below drives it. The mode is a server-side
 * preference on the shared account and the runner is single-worker and
 * non-parallel, so a test leaving `DARK` behind would drive every spec scheduled
 * after this one in the dark theme. The hooks below reset it on both sides of
 * every test, out of `./theme`, which `theme.spec.js` shares: there is one
 * `themeMode` for both UIs, so neither file can rely on the other cleaning up.
 */

/*
 * A phone, minus `defaultBrowserType`: Playwright refuses that one inside a
 * describe group because it would force a new worker, and the suite runs on
 * chromium anyway.
 */
const {defaultBrowserType: _ignored, ...PHONE} = devices['Pixel 5']

/*
 * A desktop browser, for the one test that has to look at the *other* UI.
 *
 * Spelled out rather than left to the default: inside a `test.use(PHONE)` group
 * the runner merges those options into `browser.newContext()` too, so a context
 * asking for nothing in particular is still a phone and is still redirected to
 * `/mobile`. Every field of the phone that decides the redirect has to be
 * overridden by name, which is what the device descriptor does.
 */
const {defaultBrowserType: _alsoIgnored, ...DESKTOP} = devices['Desktop Chrome']

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

/**
 * Picks a mode on the account screen's theme row.
 *
 * Scoped to the control's own test id: an unscoped `getByText` would also match
 * Next's route announcer, which repeats a page title on top of everything else
 * on the screen.
 */
const selectMobileTheme = async (page, label) => {
    const control = page.getByTestId('mobile-theme-switch')
    await expect(control).toBeVisible()
    await control.getByText(label, {exact: true}).click()
}

/**
 * Asserts this browser now carries the desktop opt-out, and that it dies with
 * the browser session.
 *
 * Shared by the two doors below - the deliberate one on the account screen and
 * the accidental one on the interstitial - because the cookie is the *same*
 * decision whichever door wrote it, and two copies of the assertion would let
 * one door's scoping change without the other's test noticing. Playwright
 * reports -1 for a session cookie.
 */
const expectSessionOptOut = async (page) => {
    const [optOut] = (await page.context().cookies())
        .filter(cookie => cookie.name === 'yontrack-ui')
    expect(optOut.value).toEqual('desktop')
    expect(optOut.expires).toEqual(-1)
}

test.beforeEach(async ({ontrack}) => resetThemeMode(ontrack))
test.afterEach(async ({ontrack}) => resetThemeMode(ontrack))

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
        await expectNoSidewaysScroll(page)
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
        await expectNoSidewaysScroll(page)
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
        await expectNoSidewaysScroll(page)
    })

    test('a deployed build says where it is, on the card and on its own screen', async ({page, ontrack}) => {
        // The other half of "the latest builds with their promotions *and
        // deployments*". Everything else in this file arranges deployments that
        // are still waiting on somebody; this one arranges a build that is
        // actually deployed, which is a different field
        // (`Build.currentDeployments`) and a different strip on the card.
        //
        // A pipeline has to reach DONE for it: `findCurrentDeployments` is the
        // last *DEPLOYED* pipeline of each of the project's slots, so one
        // stopping at RUNNING leaves the build deployed nowhere.
        const project = await ontrack.createProject()

        const staging = await ontrack.environments.createEnvironment({order: 100})
        const stagingSlot = await staging.createSlot({project})

        // A second slot in another environment, qualified. A project can hold
        // two slots told apart by nothing but the qualifier, and a badge naming
        // only the environment would draw the same word twice - which is what
        // `deploymentName` exists to prevent. It is also the end-to-end half of
        // #1731: `currentDeployments` could not answer with a qualified slot at
        // all until the `qualifier` argument lost its empty-string default, so a
        // build deployed into one showed no badge whatsoever.
        const production = await ontrack.environments.createEnvironment({order: 200})
        const productionSlot = await ontrack.environments.createSlot({
            project,
            environment: production,
            qualifier: 'eu',
        })

        const branch = await project.createBranch()
        const build = await branch.createBuild()
        await build.setRelease('1.4.0')

        for (const slot of [stagingSlot, productionSlot]) {
            const pipeline = await slot.createPipeline({build})
            await ontrack.environments.startPipeline({pipeline})
            await ontrack.environments.finishPipeline({pipeline})
        }

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)

        // On the branch screen, where a user scanning the latest builds sees it
        // without opening anything. The strip is its own test id, so this is not
        // satisfied by the environment's name turning up somewhere else on the
        // card.
        await page.goto(`${ontrack.connection.ui}/mobile/branch/${branch.id}`)
        const badges = page.getByTestId(`mobile-build-${build.id}-deployments`)
        await expect(badges).toContainText(staging.name)
        await expect(badges).toContainText(`${production.name} [eu]`)

        // Two badges plus a release name still do not push the card sideways at
        // 375px. Asserted after they are up: the deployments arrive with a query
        // of their own, and an empty card overflows nothing.
        await expectNoSidewaysScroll(page)

        // And on the build screen, which spells the same thing out as rows - the
        // positive case of the "not deployed anywhere" the test above pins.
        await page.getByTestId(`mobile-build-${build.id}`).getByRole('link').click()
        await expect(page).toHaveURL(new RegExp(`/mobile/build/${build.id}$`))

        const deployments = page.getByTestId('mobile-build-deployments')
        await expect(deployments).toContainText(staging.name)
        await expect(deployments).toContainText(`${production.name} [eu]`)
        await expect(deployments).not.toContainText(/not deployed/i)

        // Highest environment first, which is the order `findCurrentDeployments`
        // answers in and the one a reader wants: how far the build got.
        const rows = deployments.locator('[data-testid^="mobile-build-deployment-"]')
        await expect(rows).toHaveCount(2)
        await expect(rows.first()).toContainText(`${production.name} [eu]`)
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

    test('a build is promoted from a phone, required fields and all', async ({page, ontrack}) => {
        // The whole of #1724. A level that declares a required field is the
        // interesting case: a required field the mobile UI cannot fill makes
        // that promotion level impossible to use from a phone, which is why the
        // field mapping is shared with the desktop dialog rather than copied.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        const promotionLevel = await branch.createPromotionLevel('GOLD')
        // Every type the server can declare, so that one of them failing to
        // render is a failure here rather than a promotion level nobody can use
        // from a phone.
        await promotionLevel.setFields([
            {name: 'ticket', displayName: 'Ticket', type: 'TEXT', required: true},
            {name: 'count', displayName: 'Count', type: 'NUMBER', required: false},
            {name: 'approved', displayName: 'Approved', type: 'BOOLEAN', required: false},
            {name: 'env', displayName: 'Environment', type: 'CHOICE', required: false, options: ['prod', 'staging']},
            {name: 'ref', displayName: 'Reference', type: 'LINK', required: false},
        ])
        const build = await branch.createBuild()

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/build/${build.id}`)

        // Where it starts: nothing has promoted this build.
        await expect(page.getByTestId('mobile-build-promotions')).toContainText(/not been promoted/i)

        await page.getByTestId('mobile-build-promote').click()

        // The time is collapsed - someone promoting from their phone is
        // promoting now - and the picker is one tap away for the rare
        // correction.
        await expect(page.getByTestId('mobile-promote-time-toggle')).toBeVisible()
        await expect(page.getByTestId('mobile-promote-time')).toHaveCount(0)

        // The level, picked from the branch's own.
        const level = page.getByTestId('mobile-promote-level')
        await expect(level).toBeVisible()
        await level.click()
        await page.locator('.ant-select-item-option').filter({hasText: 'GOLD'}).click()

        // Every field type got an input a thumb can actually use.
        await expect(page.getByRole('textbox', {name: 'Ticket'})).toBeVisible()
        await expect(page.getByRole('spinbutton', {name: 'Count'})).toBeVisible()
        await expect(page.getByRole('checkbox', {name: 'Approved'})).toBeVisible()
        await expect(page.getByRole('textbox', {name: 'Reference'})).toHaveAttribute('placeholder', 'https://...')

        // And the sheet stays inside the phone. A level declaring this many
        // fields makes a form taller than an 812px screen, and a sheet that
        // grows past the window puts its own Promote button somewhere nothing
        // can scroll it back from - the drawer is fixed, and the page behind it
        // does not move. Waited for first: `boundingBox` answers for where an
        // element is *now*, and the fields arrive with the level's query.
        const submit = page.getByTestId('mobile-promote-submit')
        await expect(submit).toBeVisible()
        await submit.scrollIntoViewIfNeeded()
        const box = await submit.boundingBox()
        expect(box.y + box.height).toBeLessThanOrEqual(page.viewportSize().height)

        // Nothing scrolls sideways at 375px either, which is the criterion every
        // mobile surface has to meet.
        await expectNoSidewaysScroll(page)

        // The required field is enforced before anything leaves the phone.
        await submit.click()
        await expect(page.getByText('Ticket is required.')).toBeVisible()

        await page.getByRole('textbox', {name: 'Ticket'}).fill('PROJ-42')
        await page.getByTestId('mobile-promote-submit').click()

        // The screen behind reflects it, with nobody reloading anything.
        await expect(page.getByTestId('mobile-build-promotions')).toContainText('GOLD')
    })

    test('a build is deployed from a phone, ineligible environments and all', async ({page, ontrack}) => {
        // The first half of #1725: the list of where this build can go, why it
        // cannot go somewhere, and the deployment that comes out of tapping one.
        //
        // Environments are a licensed feature. The Playwright stack runs the
        // backend under the `dev` profile (`compose/docker-compose-kdsl.yml`),
        // and `DevLicenseService` enables every licensed feature - which is why
        // the whole of `tests/extensions/environments` runs in CI, and why this
        // journey can too.
        const project = await ontrack.createProject()

        // Where the build can go: a slot whose only rule needs a person.
        const staging = await ontrack.environments.createEnvironment({order: 100})
        const stagingSlot = await staging.createSlot({project})
        const approvalId = await ontrack.environments.addManualApproval({slot: stagingSlot})

        // And where it cannot: a slot wanting a promotion the branch does not even
        // declare, which is the demo's own broken-slot case and the one a user is
        // most likely to be baffled by.
        const production = await ontrack.environments.createEnvironment({order: 200})
        const productionSlot = await production.createSlot({project})
        await ontrack.environments.addPromotionRule({slot: productionSlot, promotion: 'GOLD'})

        const branch = await project.createBranch()
        const build = await branch.createBuild()
        await build.setRelease('1.4.0')

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/build/${build.id}`)

        await page.getByTestId('mobile-build-deploy').click()

        // Both environments are on offer to *read*, which is the point: hiding
        // the one that refuses leaves a user wondering where it went.
        const stagingCard = page.getByTestId(`mobile-deploy-slot-${stagingSlot.id}`)
        const productionCard = page.getByTestId(`mobile-deploy-slot-${productionSlot.id}`)
        await expect(stagingCard).toContainText(staging.name)
        await expect(productionCard).toContainText(production.name)

        // And the one that refuses says why, in the words the desktop UI uses -
        // the rule summaries are the one thing the two UIs share here.
        await expect(productionCard).toContainText(/not eligible/i)
        await expect(productionCard).toContainText(/GOLD/)
        await expect(page.getByTestId(`mobile-deploy-start-${productionSlot.id}`)).toHaveCount(0)

        // Nothing scrolls sideways at 375px, which every mobile surface has to
        // meet. Asserted after the cards are up: the sheet is empty until the
        // slots arrive, and an empty sheet overflows nothing.
        await expectNoSidewaysScroll(page)

        // Tapping the eligible one lands on the deployment it just created.
        await page.getByTestId(`mobile-deploy-start-${stagingSlot.id}`).click()
        await expect(page).toHaveURL(/\/mobile\/deployment\/[0-9a-f-]{36}$/)

        // Which is waiting on the approval, and says so rather than offering a
        // button that would fail.
        const rule = page.getByTestId(`mobile-deployment-rule-${approvalId}`)
        await expect(rule).toContainText('Blocking')
        await expect(page.getByTestId('mobile-deployment-run')).toBeDisabled()
        await expect(page.getByTestId('mobile-deployment-run-blocked')).toContainText('0 of 1')

        // The approval itself, through the rule's own form - the same mapping
        // the desktop input dialog draws.
        await page.getByTestId(`mobile-deployment-input-open-${approvalId}`).click()
        await page.getByTestId('manual-approval').click()
        await page.getByLabel('Approval message').fill('Checked with the release manager.')
        await page.getByTestId('mobile-deployment-input-submit').click()

        // The screen refetches: whether the rule now passes is the server's
        // answer and not something the phone works out.
        await expect(rule).toContainText('Passed')
        await expect(page.getByTestId('mobile-deployment-run')).toBeEnabled()

        await page.getByTestId('mobile-deployment-run').click()
        await expect(page.getByTestId('mobile-deployment-status')).toContainText('Running')

        // And a running deployment offers the other end of the lifecycle rather
        // than nothing: starting it again is not a thing, completing it is
        // (#1736). The completion itself is its own journey, below.
        await expect(page.getByTestId('mobile-deployment-run')).toHaveCount(0)
        await expect(page.getByTestId('mobile-deployment-finish')).toBeEnabled()
    })

    test('a blocked deployment is overridden from a phone, with a reason', async ({page, ontrack}) => {
        // The second half of #1725, and the case the whole flow exists for: a
        // deployment somebody else started - CI, usually - sitting on a manual
        // approval nobody is going to give.
        const project = await ontrack.createProject()
        const environment = await ontrack.environments.createEnvironment({})
        const slot = await environment.createSlot({project})
        const approvalId = await ontrack.environments.addManualApproval({slot})

        const branch = await project.createBranch()
        const build = await branch.createBuild()
        await build.setRelease('1.4.0')
        const pipeline = await slot.createPipeline({build})

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/build/${build.id}`)

        // Reached from the build screen, which is the only way in: nothing else
        // on a phone names a deployment somebody else started.
        const row = page.getByTestId(`mobile-build-unsettled-${pipeline.id}`)
        await expect(row).toContainText(environment.name)
        await row.getByRole('link').click()
        await expect(page).toHaveURL(new RegExp(`/mobile/deployment/${pipeline.id}$`))

        const rule = page.getByTestId(`mobile-deployment-rule-${approvalId}`)
        await expect(rule).toContainText('Blocking')

        // The override, which is refused without a reason.
        await page.getByTestId(`mobile-deployment-override-open-${approvalId}`).click()
        await expect(page.getByTestId('mobile-deployment-override-warning')).toBeVisible()
        await page.getByTestId('mobile-deployment-override-submit').click()
        await expect(page.getByText('Reason is required.')).toBeVisible()

        await page.getByTestId('mobile-deployment-override-message').fill('Hotfix, agreed with ops.')
        await page.getByTestId('mobile-deployment-override-submit').click()

        // Recorded against a name and readable on the row, which is the whole
        // point of making the reason mandatory.
        await expect(rule).toContainText(/overridden/i)
        await expect(page.getByTestId(`mobile-deployment-override-message-${approvalId}`))
            .toContainText('Hotfix, agreed with ops.')

        // There is no second override to offer, and the deployment can now run.
        await expect(page.getByTestId(`mobile-deployment-override-open-${approvalId}`)).toHaveCount(0)
        await expect(page.getByTestId('mobile-deployment-run')).toBeEnabled()
    })

    test('a deployment is completed from a phone, and the build says where it is', async ({page, ontrack}) => {
        // The path a phone is actually for (#1736): CI died, or nobody is
        // coming, and the person holding a phone is the one who has to finish
        // the deployment. The journey ends where CI's would - the deployment
        // DONE, and the build carrying the deployment badge that only a pipeline
        // which reached DONE puts there.
        const project = await ontrack.createProject()
        const environment = await ontrack.environments.createEnvironment({})
        const slot = await environment.createSlot({project})

        const branch = await project.createBranch()
        const build = await branch.createBuild()
        await build.setRelease('1.4.0')

        // Started by somebody else and left running, which is the state the
        // phone has to be able to reach at all: `currentDeployments` names only
        // pipelines which reached DONE, so before #1736 a RUNNING deployment was
        // reachable only from the screen that had just started it.
        const pipeline = await slot.createPipeline({build})
        await ontrack.environments.startPipeline({pipeline})

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/build/${build.id}`)

        // Reached from the build screen's *Deployments in progress*, which lists
        // running deployments beside candidates and says which is which.
        const row = page.getByTestId(`mobile-build-unsettled-${pipeline.id}`)
        await expect(row).toContainText(environment.name)
        await expect(row).toContainText('Running')
        await row.getByRole('link').click()
        await expect(page).toHaveURL(new RegExp(`/mobile/deployment/${pipeline.id}$`))

        // Nothing scrolls sideways at 375px with two full-width buttons stacked,
        // which every mobile surface has to meet.
        await expect(page.getByTestId('mobile-deployment-finish')).toBeEnabled()
        await expectNoSidewaysScroll(page)

        // The confirm sheet names the environment and the build, and asks for
        // nothing else: completion bypasses nobody's control, so it earns a
        // confirmation and not a justification.
        await page.getByTestId('mobile-deployment-finish').click()
        const confirm = page.getByTestId('mobile-deployment-finish-confirm')
        await expect(confirm).toContainText(environment.name)
        await expect(confirm).toContainText('1.4.0')

        await page.getByTestId('mobile-deployment-finish-submit').click()

        // The screen refetches, and a settled deployment says what happened
        // without mentioning the desktop version - there is nothing left to go
        // there for.
        await expect(page.getByTestId('mobile-deployment-status')).toContainText('Deployed')
        await expect(page.getByTestId('mobile-deployment-finish')).toHaveCount(0)
        await expect(page.getByTestId('mobile-deployment-cancel')).toHaveCount(0)
        await expect(page.getByTestId('mobile-deployment-settled')).toContainText(/finished/i)

        // And the build now says where it is, which is the half of the outcome
        // that lives outside this screen.
        await page.goto(`${ontrack.connection.ui}/mobile/build/${build.id}`)
        await expect(page.getByTestId('mobile-build-deployments')).toContainText(environment.name)
        await expect(page.getByTestId(`mobile-build-unsettled-${pipeline.id}`)).toHaveCount(0)
    })

    test('a deployment is cancelled from a phone, with a reason', async ({page, ontrack}) => {
        // The other end of #1736, and a different journey with a different end:
        // a candidate nobody is ever going to approve. The reason is required,
        // as on the desktop - it is the only record of why an environment's
        // deployment was killed, and the desktop reads it back.
        const project = await ontrack.createProject()
        const environment = await ontrack.environments.createEnvironment({})
        const slot = await environment.createSlot({project})
        await ontrack.environments.addManualApproval({slot})

        const branch = await project.createBranch()
        const build = await branch.createBuild()
        await build.setRelease('1.4.0')
        const pipeline = await slot.createPipeline({build})

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/deployment/${pipeline.id}`)

        // The destructive action is not on the lifecycle button's own row, which
        // is the acceptance criterion rather than a styling note: side by side
        // puts a destructive tap a thumb-width from the constructive one.
        const cancelRow = page.getByTestId('mobile-deployment-cancel-row')
        await expect(cancelRow.getByTestId('mobile-deployment-cancel')).toBeVisible()
        await expect(cancelRow.getByTestId('mobile-deployment-run')).toHaveCount(0)

        await page.getByTestId('mobile-deployment-cancel').click()
        await expect(page.getByTestId('mobile-deployment-cancel-warning')).toBeVisible()

        // Refused without a reason, before anything leaves the phone.
        await page.getByTestId('mobile-deployment-cancel-submit').click()
        await expect(page.getByText('Reason is required.')).toBeVisible()

        await page.getByTestId('mobile-deployment-cancel-reason').fill('CI died, nobody is coming.')
        await page.getByTestId('mobile-deployment-cancel-submit').click()

        // Cancelled, with the reason read back beside it as the desktop shows it.
        await expect(page.getByTestId('mobile-deployment-status')).toContainText('Cancelled')
        const settled = page.getByTestId('mobile-deployment-settled')
        await expect(settled).toContainText(/cancelled/i)
        await expect(settled).toContainText('CI died, nobody is coming.')

        // And it is gone from the build screen's in-progress section, because it
        // is no longer in progress.
        await page.goto(`${ontrack.connection.ui}/mobile/build/${build.id}`)
        await expect(page.getByTestId(`mobile-build-unsettled-${pipeline.id}`)).toHaveCount(0)
    })

    test('a promotion carries the workflows it set off, and the run is readable on a phone', async ({page, ontrack}) => {
        // The whole of #1737's promotion half, end to end: the nested line on the
        // build screen, the run behind it, and the node that failed saying why.
        //
        // The workflow fails on purpose. A green run proves the list renders; a
        // failed one proves the thing somebody actually opens a phone for - which
        // node broke, and with what error.
        const project = await ontrack.createProject()
        const branch = await project.createBranch()
        const promotionLevel = await branch.createPromotionLevel()

        const workflowName = generate("wf-")
        await subscribeToWorkflow(promotionLevel, {name: workflowName, failing: true})

        const build = await branch.createBuild()
        await build.setRelease('1.4.0')
        const run = await build.promote(promotionLevel)

        // The link from a promotion to its workflows runs through the notification
        // record the promotion leaves behind, and both the notification and the
        // workflow are asynchronous. The mobile screens ask the server once and do
        // not poll, so the wait is here rather than in the browser.
        const instanceId = await waitForPromotionRunWorkflow(page, ontrack, run)

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/build/${build.id}`)

        // One nested line under the promotion's own row, rather than a count on it:
        // `MobileEntityRow` allows exactly one trailing action, and a promotion can
        // fire more than one workflow.
        const promotionRow = page.getByTestId(`mobile-build-promotion-${run.id}`)
        await expect(promotionRow).toContainText(promotionLevel.name)
        const workflowLine = page.getByTestId(`mobile-build-promotion-workflow-${instanceId}`)
        await expect(workflowLine).toContainText(workflowName)
        await expect(workflowLine).toContainText('Error')

        // A nested line inside a row still does not push the card sideways at 375px.
        await expectNoSidewaysScroll(page)

        await workflowLine.click()
        await expect(page).toHaveURL(/\/mobile\/workflow-instance\/.+/)

        // The run as a list, not a graph: the desktop draws the DAG with React Flow
        // in a fixed 600px box, which on a phone would be a pan-and-zoom canvas.
        await expect(page.getByTestId('mobile-screen-title')).toContainText(workflowName)
        await expect(page.getByTestId('mobile-workflow-instance-status')).toContainText('Error')
        await expect(page.getByTestId('mobile-workflow-node-build')).toContainText('Success')
        await expect(page.getByTestId('mobile-workflow-node-test-unit')).toContainText('Success')
        await expect(page.getByTestId('mobile-workflow-node-publish')).toContainText('Error')

        // The failing node's error, inline - there is no side panel on a phone to go
        // and find it in.
        await expect(page.getByTestId('mobile-workflow-error-publish')).toContainText('Error in publish node')

        // Read-only: no stop, no override, anywhere on the screen.
        await expect(page.getByRole('button', {name: /stop/i})).toHaveCount(0)

        await expectNoSidewaysScroll(page)

        // And the desktop link to the same run lands here, which is what the route
        // map is for - an instance id is neither a number nor a UUID, so it needed a
        // pattern of its own and the redirect had to stop reading its fractional
        // seconds as a file extension.
        await page.goto(`${ontrack.connection.ui}/extension/workflows/instances/${instanceId}`)
        await expect(page).toHaveURL(new RegExp(`/mobile/workflow-instance/`))
        await expect(page.getByTestId('mobile-screen-title')).toContainText(workflowName)

        // The rest of the workflows pages keep reaching the interstitial: the audit
        // page and the definitions have no mobile equivalent and should not pretend.
        await page.goto(`${ontrack.connection.ui}/extension/workflows/audit`)
        await expect(page).toHaveURL(/\/mobile\/desktop-only\?target=/)
        await expect(page.getByTestId('desktop-only-destination')).toContainText('a workflow')
    })

    test('a deployment draws its slot workflows, including one nobody let run', async ({page, ontrack}) => {
        // #1736 left a phone showing a deployment blocked by a workflow, naming the
        // reason in that workflow's own words and offering no way to see which
        // workflow it was. This is that way, and it stops at the section: the run
        // itself is pinned once, by the journey above, and this suite runs
        // single-worker and non-parallel so every second here is serial wall clock.
        const {slot, project, slotWorkflow} = await withSlotWorkflow(ontrack, {trigger: 'CANDIDATE'})

        // A second CANDIDATE workflow which fails, so there is something to override.
        const blocking = await addSlotWorkflow({
            slot,
            trigger: 'CANDIDATE',
            workflowYaml: `
                name: Blocking
                nodes:
                  - id: check
                    executorId: mock
                    data:
                        text: Error
                        error: true
            `,
        })

        // And one on a trigger this deployment has not reached, which is the point of
        // drawing all three: a workflow which never ran is configuration, and
        // frequently the reason nothing ever deployed here.
        const announce = await addSlotWorkflow({
            slot,
            trigger: 'DONE',
            workflowYaml: `
                name: Announce
                nodes:
                  - id: announce
                    executorId: mock
                    data:
                        text: Announcing
            `,
        })

        const {pipeline} = await createPipeline({project, slot})
        await waitForPipelineWorkflowToBeFinished(page, ontrack, pipeline.id, slotWorkflow)
        await waitForPipelineWorkflowToBeFinished(page, ontrack, pipeline.id, blocking)

        await overridePipelineWorkflow(ontrack, {
            pipelineId: pipeline.id,
            slotWorkflowId: blocking.id,
            message: 'Known flake, agreed with ops.',
        })

        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/deployment/${pipeline.id}`)

        const section = page.getByTestId('mobile-deployment-workflows')
        await expect(section).toBeVisible()

        // Each row carries its trigger, which is what says *when* the workflow has
        // its turn - and is the only thing telling the first two apart from the third.
        const ran = page.getByTestId(`mobile-deployment-workflow-${slotWorkflow.id}`)
        await expect(ran).toContainText('On candidate')
        await expect(ran).toContainText('Success')

        const overridden = page.getByTestId(`mobile-deployment-workflow-${blocking.id}`)
        await expect(overridden).toContainText('On candidate')
        await expect(overridden).toContainText('Error')

        // Showing that a workflow *was* overridden is not the opposite of read-only:
        // a row reading Error with no sign that a human deliberately waved it through
        // would be actively misleading.
        await expect(page.getByTestId(`mobile-deployment-workflow-overridden-${blocking.id}`))
            .toContainText('Known flake, agreed with ops.')

        // The one whose turn has not come, present and honest about it.
        const notStarted = page.getByTestId(`mobile-deployment-workflow-${announce.id}`)
        await expect(notStarted).toContainText('On deployment done')
        await expect(page.getByTestId(`mobile-deployment-workflow-status-${announce.id}`))
            .toContainText('Not started')

        // No action on any of them. Overriding a blocking workflow needs `SlotUpdate`
        // *and* `SlotPipelineOverride`, a pair the role this screen is built around
        // does not hold, so the button would be absent for the very user it is for.
        await expect(section.getByRole('button')).toHaveCount(0)

        await expectNoSidewaysScroll(page)
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
        //
        // Waited for first: the name arrives with `UserContext`, a query and a
        // fetch after the header itself paints, and `boundingBox` answers `null`
        // for an element that is not there yet rather than waiting for one.
        const name = page.getByTestId('mobile-user')
        await expect(name).toBeVisible()
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

    test('a phone user can choose dark, and the choice survives a reload', async ({page, ontrack}) => {
        // Until this row the mobile UI had the whole theme machinery and no way
        // to reach it: a phone user got whatever the device decided.
        await page.emulateMedia({colorScheme: 'light'})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile`)

        // Tapped through to, as a user reaches it - the header name is the door.
        // Waited for first: the name arrives with `UserContext`, a query after
        // the header itself paints.
        const name = page.getByTestId('mobile-user')
        await expect(name).toBeVisible()
        await name.click()
        await expect(page).toHaveURL(/\/mobile\/account$/)
        await expectTheme(page, 'light')

        await selectMobileTheme(page, "Dark")
        // Immediately - no reload.
        await expectTheme(page, 'dark')
        // And a phone has no hover, so the mode says what it means in the open.
        await expect(page.getByTestId('mobile-theme-caption')).toHaveText("Always dark")

        await page.reload()
        await expect(page.getByTestId('mobile-theme-switch')).toBeVisible()
        await expectTheme(page, 'dark')
    })

    test('Auto says which theme it is currently resolving to', async ({page, ontrack}) => {
        // The caption is the whole of Auto's legibility on a touch screen: the
        // desktop defines the mode in a tooltip, which a phone cannot reach.
        await page.emulateMedia({colorScheme: 'dark'})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/account`)

        await selectMobileTheme(page, "Auto")
        await expectTheme(page, 'dark')
        await expect(page.getByTestId('mobile-theme-caption')).toHaveText("Auto — currently dark")

        // And it tracks the operating system live, without a reload - the
        // provider subscribes to the media query, and the caption reads the
        // resolved theme rather than the mode.
        await page.emulateMedia({colorScheme: 'light'})
        await expectTheme(page, 'light')
        await expect(page.getByTestId('mobile-theme-caption')).toHaveText("Auto — currently light")
    })

    test('a theme chosen on the phone is the theme the desktop UI is in', async ({page, ontrack, browser}) => {
        // The assertion that pins the shared-preference decision. The theme is a
        // preference of the *user*, not of the device - one `themeMode` on the
        // account - and without this, "one preference" is a sentence in a spec
        // that nothing enforces: the day someone adds a mobile-only cookie, no
        // test notices.
        await page.emulateMedia({colorScheme: 'light'})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/account`)
        await selectMobileTheme(page, "Dark")
        await expectTheme(page, 'dark')

        // A brand new context, and a desktop user agent so the redirect leaves
        // it alone: no cookies, no local storage. Only the server-side
        // preference can carry the choice across.
        const context = await browser.newContext({...DESKTOP, colorScheme: 'light'})
        try {
            const desktop = await context.newPage()
            await login(desktop, ontrack)
            await expectTheme(desktop, 'dark')
        } finally {
            await context.close()
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

    test('a phone reaches the desktop version from the account screen', async ({page, ontrack}) => {
        // The deliberate door, as against the accidental one below: a user who
        // wants the desktop UI should not have to first navigate to a page they
        // did not want in order to find the exit - which is all the interstitial
        // ever offered, and all an installed PWA would offer.
        // The narrowest phone the acceptance names, where the screen is
        // tightest.
        await page.setViewportSize({width: 375, height: 812})
        await signInOnPhone(page, ontrack)
        await page.goto(`${ontrack.connection.ui}/mobile/account`)

        // It says what the choice costs and where the way back is, before the
        // user commits to it: a phone has no hover, and the installed app has no
        // address bar.
        await expect(page.getByTestId('mobile-account-device-caption'))
            .toContainText('Mobile version')

        // And the section it was inserted into - a heading, a button and a
        // three-line caption - did not push sign out off the screen. Sign out
        // stays last, so the answer to this failing is tightening the section
        // rather than moving it.
        const signOut = page.getByTestId('mobile-account-sign-out')
        await expect(signOut).toBeVisible()
        const box = await signOut.boundingBox()
        expect(box.y + box.height).toBeLessThanOrEqual(812)

        await page.getByTestId('open-desktop-version').click()

        // The desktop home, and it actually gets there rather than being bounced
        // straight back by the redirect - which is what writing the cookie
        // before navigating is for.
        await expect(page.getByText("Dashboard", {exact: true})).toBeVisible()
        await expect(page).not.toHaveURL(/\/mobile/)
        expect(new URL(page.url()).pathname).toEqual('/')

        // And the choice it wrote is the session-scoped one, not a lasting
        // preference.
        await expectSessionOptOut(page)
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
        // desktop UI that is not responsive.
        await expectSessionOptOut(page)

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
