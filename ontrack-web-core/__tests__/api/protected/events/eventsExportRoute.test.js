/**
 * @jest-environment node
 */
/*
 * The route of the UI through which the browser downloads the export of the events: it carries
 * the token of the session and the filter, as a query string, to the REST end point.
 */
jest.mock("../../../../app/api/protected/backend", () => ({
    backend: {url: "http://backend"},
    getAccessToken: jest.fn(async () => "token"),
}))

import {GET} from "../../../../app/api/protected/downloads/events/export/route"
import {GET as GET_TRAIL_EXPORT} from "../../../../app/api/protected/downloads/audit-trail/builds/[id]/export/route"

describe("events export route", () => {

    const originalFetch = global.fetch

    afterEach(() => {
        global.fetch = originalFetch
    })

    it("forwards the query string with the token of the session", async () => {
        global.fetch = jest.fn(async () => new Response("id,time\n", {
            status: 200,
            headers: {
                'Content-Type': 'text/csv;charset=UTF-8',
                'Content-Disposition': 'attachment; filename="yontrack-events-20261007-101112.csv"',
                'X-Yontrack-Export-Format-Version': '1',
                'X-Yontrack-Export-Truncated': 'true',
            },
        }))
        const response = await GET(new Request(
            "http://localhost/api/protected/downloads/events/export?format=csv&user=adm&eventTypes=new_build&eventTypes=new_branch&from=2026-10-01T08%3A30%3A00.000Z"
        ))
        expect(global.fetch).toHaveBeenCalledWith(
            "http://backend/rest/admin/events/export?format=csv&user=adm&eventTypes=new_build&eventTypes=new_branch&from=2026-10-01T08%3A30%3A00.000Z",
            expect.objectContaining({headers: {Authorization: "Bearer token"}}),
        )
        expect(response.status).toBe(200)
        expect(await response.text()).toBe("id,time\n")
        expect(response.headers.get('content-type')).toBe('text/csv;charset=UTF-8')
        expect(response.headers.get('content-disposition')).toBe('attachment; filename="yontrack-events-20261007-101112.csv"')
        expect(response.headers.get('x-yontrack-export-format-version')).toBe('1')
        expect(response.headers.get('x-yontrack-export-truncated')).toBe('true')
    })

    it("passes a refusal of the backend on", async () => {
        global.fetch = jest.fn(async () => new Response(null, {status: 403, statusText: "Forbidden"}))
        const response = await GET(new Request("http://localhost/api/protected/downloads/events/export?format=csv"))
        expect(response.status).toBe(403)
    })

    it("leaves the query string out of the other downloads", async () => {
        global.fetch = jest.fn(async () => new Response("{}", {status: 200}))
        await GET_TRAIL_EXPORT(
            new Request("http://localhost/api/protected/downloads/audit-trail/builds/42/export?format=csv"),
            {params: Promise.resolve({id: "42"})},
        )
        expect(global.fetch).toHaveBeenCalledWith(
            "http://backend/rest/extension/audit-trail/builds/42/export",
            expect.anything(),
        )
    })
})
