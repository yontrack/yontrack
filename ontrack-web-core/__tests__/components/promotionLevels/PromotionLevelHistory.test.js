import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"
import PromotionLevelHistory from "@components/promotionLevels/PromotionLevelHistory"

jest.mock("next/router", () => ({useRouter: () => ({push: jest.fn()})}))
jest.mock("../../../components/builds/BuildLink", () => function BuildLink({build}) {
    return <span>{build.name}</span>
})
jest.mock("../../../components/framework/decorations/Decorations", () => function Decorations() {
    return null
})
jest.mock("../../../components/promotionRuns/PromotionRunLink", () => function PromotionRunLink() {
    return null
})
jest.mock("../../../components/common/AnnotatedDescription", () => function AnnotatedDescription() {
    return null
})
jest.mock("../../../components/common/TimestampText", () => function TimestampText() {
    return null
})

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
        dispatchEvent: jest.fn(),
    })),
})

/**
 * `PromotionLevelHistory` runs its query through `useQuery` (#1929). A page beyond the first one
 * is appended to the promotion runs already loaded.
 */

const run = (id, name) => ({
    id,
    description: null,
    annotatedDescription: null,
    build: {id: `b${id}`, name, displayName: name, releaseProperty: null, decorations: []},
    creation: {user: "admin", time: "2026-09-30T10:00:00Z"},
})

const pages = {
    0: {
        pageItems: [run("1", "build-one"), run("2", "build-two")],
        pageInfo: {nextPage: {offset: 2, size: 2}},
    },
    2: {
        pageItems: [run("3", "build-three")],
        pageInfo: {nextPage: null},
    },
}

const promotionLevel = {id: "10"}

const requestedOffsets = () => global.fetch.mock.calls.map(([, init]) => JSON.parse(init.body).variables.offset)

describe('PromotionLevelHistory', () => {

    beforeEach(() => {
        global.fetch = jest.fn().mockImplementation(async (_, init) => {
            const {variables} = JSON.parse(init.body)
            return {
                ok: true,
                status: 200,
                json: async () => ({
                    promotionLevel: {
                        branch: {scmBranchInfo: null},
                        promotionRuns: pages[variables.offset],
                    },
                }),
            }
        })
    })

    afterEach(() => {
        delete global.fetch
    })

    it('appends the next page to the promotion runs already loaded', async () => {
        render(<PromotionLevelHistory promotionLevel={promotionLevel}/>)

        await waitFor(() => expect(screen.getByText("build-two")).toBeInTheDocument())

        fireEvent.click(screen.getByText("Load more..."))

        await waitFor(() => expect(screen.getByText("build-three")).toBeInTheDocument())
        expect(screen.getByText("build-one")).toBeInTheDocument()
        expect(screen.getByText("build-two")).toBeInTheDocument()
        expect(requestedOffsets()).toEqual([0, 2])
    })

    it('does not query anything without a promotion level', async () => {
        render(<PromotionLevelHistory promotionLevel={undefined}/>)

        await waitFor(() => expect(screen.getByText("Load more...")).toBeInTheDocument())
        expect(global.fetch).not.toHaveBeenCalled()
    })

})
