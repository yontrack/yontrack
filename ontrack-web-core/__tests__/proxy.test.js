/**
 * @jest-environment node
 */
/*
 * The proxy as an adapter: the framing override of #1770 must reach both
 * kinds of answer it gives - a page served as asked, and a redirect to the
 * mobile UI. The redirect rules themselves are tested in mobileRedirect.test.js.
 */
import {NextRequest} from "next/server"
import {proxy} from "../proxy"

const PHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1'
const DESKTOP = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15'

const request = (path, userAgent) => new NextRequest(`http://localhost:3000${path}`, {
    headers: {'user-agent': userAgent},
})

describe('proxy framing override', () => {

    const original = process.env.YONTRACK_UI_FRAME_ANCESTORS

    afterEach(() => {
        if (original === undefined) {
            delete process.env.YONTRACK_UI_FRAME_ANCESTORS
        } else {
            process.env.YONTRACK_UI_FRAME_ANCESTORS = original
        }
    })

    it('adds nothing when the variable is not set, leaving the config default', () => {
        delete process.env.YONTRACK_UI_FRAME_ANCESTORS
        const response = proxy(request('/', DESKTOP))
        expect(response.headers.get('content-security-policy')).toBeNull()
    })

    it('overrides frame-ancestors on a page served as asked', () => {
        process.env.YONTRACK_UI_FRAME_ANCESTORS = "'self' https://portal.example.com"
        const response = proxy(request('/', DESKTOP))
        expect(response.headers.get('location')).toBeNull()
        expect(response.headers.get('content-security-policy')).toEqual("frame-ancestors 'self' https://portal.example.com")
    })

    it('overrides frame-ancestors on a redirect to the mobile UI', () => {
        process.env.YONTRACK_UI_FRAME_ANCESTORS = "'self' https://portal.example.com"
        const response = proxy(request('/', PHONE))
        expect(response.headers.get('location')).toMatch(/\/mobile$/)
        expect(response.headers.get('content-security-policy')).toEqual("frame-ancestors 'self' https://portal.example.com")
    })
})
