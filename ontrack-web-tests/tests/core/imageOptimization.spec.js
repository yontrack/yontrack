const {devices, expect} = require('@playwright/test');
const {login, signInButton} = require("./login");
const {test} = require("../fixtures/connection");

/**
 * The Next.js Image Optimization API is off (#1789), on the image the
 * acceptance stack runs - a production build.
 *
 * GHSA-2xp9-vwfh-vxw4 is an unauthenticated RCE in `/_next/image`. With
 * `images.unoptimized` in `next.config.js` (pinned by a unit test in
 * `ontrack-web-core`), Next answers that endpoint with a 404, and every
 * `next/image` renders a plain `<img>` pointing at its source. This checks both
 * on the wire, and that the logos still load on the desktop and mobile UIs.
 */

const {defaultBrowserType: _ignored, ...PHONE} = devices['Pixel 5']

/** The image is served from its own URL - not through `/_next/image` - and has actually loaded. */
const expectPlainImage = async (image, src) => {
    await expect(image).toBeVisible()
    await expect(image).toHaveAttribute('src', src)
    await expect.poll(() => image.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true)
}

test.describe('image optimization', () => {

    test('the /_next/image endpoint is off', async ({request, ontrack}) => {
        // A raster source: SVGs are never optimized anyway, so this is the
        // request the optimizer would otherwise have handled.
        const response = await request.get(
            `${ontrack.connection.ui}/_next/image?url=%2Ffavicon.ico&w=64&q=75`,
            {maxRedirects: 0},
        )
        expect(response.status()).toEqual(404)
    })

    test('the sign-in page logo loads as a plain image', async ({page, ontrack}) => {
        // Straight to the page: signing in from `/` shows the buttons without it.
        await page.goto(`${ontrack.connection.ui}/auth/signin`)
        await signInButton(page)
        await expectPlainImage(page.getByAltText('Yontrack logo'), '/yontrack-logo.svg')
    })

    test('the nav bar logo loads as a plain image', async ({page, ontrack}) => {
        await login(page, ontrack)
        await expectPlainImage(page.getByAltText('Yontrack Logo'), '/yontrack-logo.svg')
    })

    test.describe('on a phone', () => {

        test.use(PHONE)

        test('the mobile header logos load as plain images', async ({page, ontrack}) => {
            await login(page, ontrack, undefined, undefined, {
                ready: page => page.getByTestId('mobile-screen-title'),
            })
            await expectPlainImage(page.getByTestId('mobile-logo'), '/yontrack-logo.svg')
            await expectPlainImage(page.getByTestId('mobile-wordmark'), '/yontrack-text.svg')
        })
    })
})
