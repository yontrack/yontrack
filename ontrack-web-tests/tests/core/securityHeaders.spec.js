const {devices, expect} = require('@playwright/test');
const {login} = require("./login");
const {test} = require("../fixtures/connection");

/**
 * The security headers of the UI (#1770), on the image the acceptance stack
 * runs - a production build, where `headers()` comes out of the routes manifest
 * written at build time rather than out of `next.config.js`.
 *
 * The values themselves are pinned by the unit tests of
 * `components/security/securityHeaders.js`; this checks they reach the wire on
 * the three kinds of route the passive scan crawls, and that the mobile UI
 * still works under the report-only policy.
 */

const {defaultBrowserType: _ignored, ...PHONE} = devices['Pixel 5']

const expectSecurityHeaders = (headers) => {
    expect(headers['x-content-type-options']).toEqual('nosniff')
    expect(headers['referrer-policy']).toEqual('strict-origin-when-cross-origin')
    expect(headers['permissions-policy']).toContain('camera=()')
    expect(headers['content-security-policy']).toContain('frame-ancestors')
    expect(headers['x-frame-options']).toEqual('SAMEORIGIN')
    expect(headers['content-security-policy-report-only']).toContain("default-src 'self'")
    // HSTS is the ingress's to send, never the application's.
    expect(headers['strict-transport-security']).toBeUndefined()
}

test.describe('security headers', () => {

    for (const path of ['/', '/mobile', '/api/health']) {
        test(`are sent on ${path}`, async ({request, ontrack}) => {
            const response = await request.get(`${ontrack.connection.ui}${path}`, {maxRedirects: 0})
            expectSecurityHeaders(response.headers())
        })
    }

    test.describe('on a phone', () => {

        test.use(PHONE)

        test('the mobile UI loads under the report-only policy', async ({page, ontrack}) => {
            await login(page, ontrack, undefined, undefined, {
                ready: page => page.getByTestId('mobile-screen-title'),
            })
            const response = await page.goto(`${ontrack.connection.ui}/mobile`)
            expectSecurityHeaders(response.headers())
            await expect(page.getByTestId('mobile-header')).toBeVisible()
            await expect(page.getByTestId('mobile-nav')).toBeVisible()
        })
    })
})
