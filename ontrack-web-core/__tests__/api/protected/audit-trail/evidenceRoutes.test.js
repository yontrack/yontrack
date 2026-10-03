/**
 * @jest-environment node
 */
/*
 * The routes of the UI through which the browser uploads and downloads evidence: they carry the
 * token of the session to the REST end points of the audit trail, and pass the answer back with
 * the headers which make a download safe.
 */
jest.mock("../../../../app/api/protected/backend", () => ({
    backend: {url: "http://backend"},
    getAccessToken: jest.fn(async () => "token"),
}))

import {getAccessToken} from "../../../../app/api/protected/backend"
import {GET} from "../../../../app/api/protected/downloads/audit-trail/evidence/[id]/route"
import {POST} from "../../../../app/api/protected/uploads/audit-trail/validation-runs/[id]/evidence/route"

const props = (params) => ({params: Promise.resolve(params)})

describe("evidence download route", () => {

    const originalFetch = global.fetch

    afterEach(() => {
        global.fetch = originalFetch
    })

    it("downloads the evidence with the token of the session", async () => {
        global.fetch = jest.fn(async () => new Response("%PDF-1.7", {
            status: 200,
            headers: {
                'Content-Type': 'application/pdf',
                'Content-Disposition': 'inline; filename="report.pdf"',
                'X-Content-Type-Options': 'nosniff',
                'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'",
            },
        }))
        const response = await GET(new Request("http://localhost/"), props({id: "42"}))
        expect(global.fetch).toHaveBeenCalledWith(
            "http://backend/rest/extension/audit-trail/evidence/42/download",
            expect.objectContaining({headers: {Authorization: "Bearer token"}}),
        )
        expect(response.status).toBe(200)
        expect(await response.text()).toBe("%PDF-1.7")
        expect(response.headers.get('content-type')).toBe('application/pdf')
        expect(response.headers.get('content-disposition')).toBe('inline; filename="report.pdf"')
    })

    it("keeps the headers which make the download safe, and lets only the UI frame it", async () => {
        global.fetch = jest.fn(async () => new Response("<html></html>", {
            status: 200,
            headers: {
                'Content-Type': 'application/octet-stream',
                'Content-Disposition': 'attachment; filename="report.html"',
                'X-Content-Type-Options': 'nosniff',
                'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; sandbox",
            },
        }))
        const response = await GET(new Request("http://localhost/"), props({id: "42"}))
        expect(response.headers.get('content-type')).toBe('application/octet-stream')
        expect(response.headers.get('content-disposition')).toBe('attachment; filename="report.html"')
        expect(response.headers.get('x-content-type-options')).toBe('nosniff')
        expect(response.headers.get('content-security-policy'))
            .toBe("default-src 'none'; style-src 'unsafe-inline'; sandbox; frame-ancestors 'self'")
    })

    it("passes a refusal of the backend on", async () => {
        global.fetch = jest.fn(async () => new Response(null, {status: 404, statusText: "Not Found"}))
        const response = await GET(new Request("http://localhost/"), props({id: "42"}))
        expect(response.status).toBe(404)
    })

    it("refuses anything but an evidence ID", async () => {
        global.fetch = jest.fn()
        const response = await GET(new Request("http://localhost/"), props({id: "../../admin"}))
        expect(response.status).toBe(400)
        expect(global.fetch).not.toHaveBeenCalled()
    })
})

describe("evidence upload route", () => {

    const originalFetch = global.fetch

    afterEach(() => {
        global.fetch = originalFetch
    })

    const uploadRequest = () => {
        const form = new FormData()
        form.append('file', new Blob(['{"ok":true}'], {type: 'application/json'}), 'report.json')
        form.append('sourceTool', 'trivy')
        return new Request("http://localhost/", {method: 'POST', body: form})
    }

    it("sends the multipart request on with the token of the session", async () => {
        global.fetch = jest.fn(async () => new Response(JSON.stringify({id: 7, fileName: 'report.json'}), {
            status: 201,
            headers: {'Content-Type': 'application/json'},
        }))
        const request = uploadRequest()
        const contentType = request.headers.get('content-type')
        const response = await POST(request, props({id: "42"}))

        expect(global.fetch).toHaveBeenCalledTimes(1)
        const [url, init] = global.fetch.mock.calls[0]
        expect(url).toBe("http://backend/rest/extension/audit-trail/validation-runs/42/evidence")
        expect(init.method).toBe('POST')
        expect(init.headers).toEqual({Authorization: "Bearer token", 'Content-Type': contentType})
        expect(contentType).toMatch(/^multipart\/form-data; boundary=/)
        // The body is streamed, not read
        expect(init.body).toBeInstanceOf(ReadableStream)
        expect(init.duplex).toBe('half')

        expect(response.status).toBe(201)
        expect(await response.json()).toEqual({id: 7, fileName: 'report.json'})
    })

    it("passes the refusal of an evidence on, with its code and message", async () => {
        const refusal = {
            status: 413,
            code: 'audit-trail.evidence.too-large',
            message: 'The evidence is bigger than the instance allows.',
        }
        global.fetch = jest.fn(async () => new Response(JSON.stringify(refusal), {
            status: 413,
            headers: {'Content-Type': 'application/json'},
        }))
        const response = await POST(uploadRequest(), props({id: "42"}))
        expect(response.status).toBe(413)
        expect(await response.json()).toEqual(refusal)
    })

    it("refuses anything but a validation run ID", async () => {
        global.fetch = jest.fn()
        const response = await POST(uploadRequest(), props({id: "1/../../x"}))
        expect(response.status).toBe(400)
        expect(global.fetch).not.toHaveBeenCalled()
    })

    it("refuses a request without a session", async () => {
        getAccessToken.mockResolvedValueOnce(null)
        global.fetch = jest.fn()
        const response = await POST(uploadRequest(), props({id: "42"}))
        expect(response.status).toBe(401)
        expect(global.fetch).not.toHaveBeenCalled()
    })
})
