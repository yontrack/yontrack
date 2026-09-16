/**
 * The security headers the UI sends (#1770).
 *
 * Asserted on `next.config.js` itself rather than only on the module that
 * builds the list: the config is what Next reads, and a list nobody wires into
 * `headers()` would pass every other test in this file.
 */
import {getPathMatch} from "next/dist/shared/lib/router/utils/path-match"
import nextConfig from "../../../next.config"
import {
    FRAME_ANCESTORS_ENV,
    contentSecurityPolicyReportOnly,
    frameAncestorsOverride,
    securityHeaders,
} from "@components/security/securityHeaders"

const headerMap = (headers) => Object.fromEntries(headers.map(({key, value}) => [key, value]))

/** Every header rule of the config which applies to this path, merged in order. */
const configHeadersFor = async (pathname) => {
    const rules = await nextConfig.headers()
    return rules
        .filter(rule => getPathMatch(rule.source)(pathname) !== false)
        .reduce((acc, rule) => ({...acc, ...headerMap(rule.headers)}), {})
}

/** Parses a CSP into {directive: [sources]}. */
const parseCsp = (policy) => Object.fromEntries(
    policy.split(';')
        .map(part => part.trim())
        .filter(part => part.length > 0)
        .map(part => {
            const [name, ...sources] = part.split(/\s+/)
            return [name, sources]
        })
)

describe('next.config.js headers()', () => {

    it.each([
        '/',
        '/mobile',
        '/mobile/build/12',
        '/project/1',
        '/auth/signin',
        '/api/health',
        '/api/protected/graphql',
        '/_next/static/chunks/main.js',
        '/favicon.ico',
    ])('sends the security headers on %s', async (pathname) => {
        const headers = await configHeadersFor(pathname)
        expect(headers['X-Content-Type-Options']).toEqual('nosniff')
        expect(headers['Referrer-Policy']).toEqual('strict-origin-when-cross-origin')
        expect(headers['X-Frame-Options']).toEqual('SAMEORIGIN')
        expect(headers['Content-Security-Policy']).toEqual("frame-ancestors 'self'")
        expect(headers['Permissions-Policy']).toContain('camera=()')
        expect(headers['Content-Security-Policy-Report-Only']).toContain("default-src 'self'")
    })

    it('does not send HSTS: that is the ingress\'s to send', async () => {
        const headers = await configHeadersFor('/')
        expect(Object.keys(headers).map(key => key.toLowerCase())).not.toContain('strict-transport-security')
    })
})

describe('securityHeaders', () => {

    it('denies the browser features the UI does not use', () => {
        const policy = headerMap(securityHeaders())['Permissions-Policy']
        for (const feature of ['camera', 'microphone', 'geolocation', 'payment', 'usb']) {
            expect(policy).toContain(`${feature}=()`)
        }
    })

    it('does not deny the features the UI does use', () => {
        const policy = headerMap(securityHeaders())['Permissions-Policy']
        // Copy buttons all over the UI, and the editors' full-screen mode.
        expect(policy).not.toContain('clipboard-write')
        expect(policy).not.toContain('fullscreen')
    })
})

describe('contentSecurityPolicyReportOnly', () => {

    it('lets Ant Design inject its styles', () => {
        const csp = parseCsp(contentSecurityPolicyReportOnly())
        expect(csp['style-src']).toEqual(expect.arrayContaining(["'self'", "'unsafe-inline'"]))
    })

    it('closes what the UI never needs', () => {
        const csp = parseCsp(contentSecurityPolicyReportOnly())
        expect(csp['default-src']).toEqual(["'self'"])
        expect(csp['object-src']).toEqual(["'none'"])
        expect(csp['base-uri']).toEqual(["'self'"])
    })

    it('carries no frame-ancestors: browsers ignore it in a report-only policy', () => {
        const csp = parseCsp(contentSecurityPolicyReportOnly())
        expect(csp['frame-ancestors']).toBeUndefined()
    })

    it('allows eval in development only, where the Next dev runtime needs it', () => {
        expect(parseCsp(contentSecurityPolicyReportOnly())['script-src']).not.toContain("'unsafe-eval'")
        expect(parseCsp(contentSecurityPolicyReportOnly({development: true}))['script-src']).toContain("'unsafe-eval'")
    })
})

describe('frameAncestorsOverride', () => {

    it('is named like the other UI environment variables', () => {
        expect(FRAME_ANCESTORS_ENV).toEqual('YONTRACK_UI_FRAME_ANCESTORS')
    })

    it.each([undefined, null, '', '   '])('keeps the default when the variable is %p', (raw) => {
        expect(frameAncestorsOverride(raw)).toBeNull()
    })

    it('replaces the frame-ancestors source list', () => {
        expect(frameAncestorsOverride("'self' https://portal.example.com")).toEqual({
            key: 'Content-Security-Policy',
            value: "frame-ancestors 'self' https://portal.example.com",
        })
    })

    it('trims the value', () => {
        expect(frameAncestorsOverride("  'none'  ").value).toEqual("frame-ancestors 'none'")
    })

    it.each([
        "'self'; script-src *",
        "'self', https://evil.example.com",
        "'self'\r\nSet-Cookie: x=y",
    ])('refuses a value which would be more than a source list: %p', (raw) => {
        const warn = jest.spyOn(console, 'warn').mockImplementation(() => {})
        try {
            expect(frameAncestorsOverride(raw)).toBeNull()
        } finally {
            warn.mockRestore()
        }
    })
})
