/**
 * @jest-environment node
 */
/*
 * Next 16 hands a route handler its dynamic segments as a promise (#1787):
 * `params.id` read synchronously is `undefined`, and every image would be
 * answered "Missing image ID".
 */
jest.mock("../../../../app/api/protected/backend", () => ({
    backend: {url: "http://backend"},
    getAccessToken: jest.fn(async () => "token"),
}))

import {getImage, putImage} from "../../../../app/api/protected/images/images"

const uri = (params) => `rest/structure/promotionLevels/${params.id}/image`

const props = (params) => ({params: Promise.resolve(params)})

describe("image route handlers", () => {

    const originalFetch = global.fetch

    afterEach(() => {
        global.fetch = originalFetch
    })

    it("GET reads the id from the awaited params", async () => {
        global.fetch = jest.fn(async () => ({
            ok: true,
            arrayBuffer: async () => new Uint8Array([1, 2, 3]).buffer,
        }))
        const response = await getImage({uri})(new Request("http://localhost/"), props({id: "42"}))
        expect(response.status).toBe(200)
        expect(global.fetch).toHaveBeenCalledWith(
            "http://backend/rest/structure/promotionLevels/42/image",
            expect.anything(),
        )
        expect((await response.json()).dataURL).toBe("data:image/png;base64,AQID")
    })

    it("GET answers 400 when there is no id", async () => {
        const response = await getImage({uri})(new Request("http://localhost/"), props({}))
        expect(response.status).toBe(400)
    })

    it("PUT reads the id from the awaited params", async () => {
        global.fetch = jest.fn(async () => new Response(null, {status: 200}))
        await putImage({uri})(new Request("http://localhost/", {method: "PUT", body: "data"}), props({id: "42"}))
        expect(global.fetch).toHaveBeenCalledWith(
            "http://backend/rest/structure/promotionLevels/42/image",
            expect.objectContaining({method: "PUT", body: "data"}),
        )
    })

    it("PUT answers 400 when there is no id", async () => {
        const response = await putImage({uri})(new Request("http://localhost/", {method: "PUT", body: "data"}), props({}))
        expect(response.status).toBe(400)
    })
})
