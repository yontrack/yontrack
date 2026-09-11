import "@testing-library/jest-dom"
import {act, fireEvent, render, screen, within} from "@testing-library/react"

let queryResult = {data: null, loading: false, error: null, finished: true}
/** The options the screen handed `useQuery` on its last render. */
let queryOptions
/*
 * The screen runs two queries. Deployments are asked for separately so that an
 * instance without the environments licence - where `currentDeployments` is
 * absent from the schema - loses the deployment badges alone rather than the
 * whole screen. This lets a test fail one without the other.
 */
let deploymentsResult = null
/** The options the deployments query was handed on its last render. */
let deploymentsOptions

const isDeployments = (query) => String(query).includes('MobileBranchDeployments')

jest.mock("../../../components/services/GraphQL", () => ({
    useQuery: (query, options) => {
        if (isDeployments(query)) {
            deploymentsOptions = options
            return deploymentsResult ?? queryResult
        }
        queryOptions = options
        return queryResult
    },
    callGraphQL: jest.fn(),
}))

import MobileBranchScreen, {MOBILE_BUILD_PAGE_SIZE} from "@/app/mobile/branch/[id]/BranchScreen"

const setResult = (result) => {
    queryResult = {data: null, loading: false, error: null, finished: true, ...result}
}

const build = (id, name, {displayName, time, promotions = [], deployments = []} = {}) => ({
    id,
    name,
    displayName: displayName ?? name,
    creation: {time: time ?? '2024-03-01T10:00:00Z'},
    promotionRuns: promotions.map(([runId, levelId, levelName]) => ({
        id: runId,
        promotionLevel: {id: levelId, name: levelName, image: false},
    })),
    currentDeployments: deployments.map(([pipelineId, environmentName, qualifier = '']) => ({
        id: pipelineId,
        slot: {
            id: `slot-${pipelineId}`,
            qualifier,
            environment: {id: environmentName, name: environmentName},
        },
    })),
})

const branch = (
    builds = [],
    {nextPage = null, disabled = false, favourite = false, promotionLevels = []} = {},
) => setResult({
    data: {
        branch: {
            id: 10,
            name: 'main',
            displayName: 'main',
            disabled,
            favourite,
            project: {id: 1, name: 'petclinic'},
            promotionLevels: promotionLevels.map(([id, name]) => ({id, name, image: false})),
            buildsPaginated: {
                pageInfo: {nextPage},
                pageItems: builds,
            },
        },
    },
})

/** A branch offering the two promotion levels the search tests filter on. */
const promotable = (builds = [], options = {}) =>
    branch(builds, {promotionLevels: [[500, 'BRONZE'], [501, 'SILVER']], ...options})

describe('the mobile branch screen', () => {

    beforeEach(() => {
        queryOptions = undefined
        deploymentsOptions = undefined
        deploymentsResult = null
    })

    it('is the branch, named, and says which project it belongs to', () => {
        // A bare branch name says too little - two projects can both have a
        // `main`, and this screen is reachable from a favourites list mixing
        // branches from every project.
        branch([build(100, '1')])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-screen-title')).toHaveTextContent('main')
        expect(screen.getByTestId('mobile-screen-subtitle')).toHaveTextContent('petclinic')
    })

    it('goes back up to the project it belongs to', () => {
        branch([build(100, '1')])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-screen-subtitle').querySelector('a'))
            .toHaveAttribute('href', '/mobile/project/1')
    })

    it('offers the favourite toggle on the branch itself', () => {
        branch([build(100, '1')], {favourite: true})
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-favourite-branch-10')).toHaveAttribute('aria-pressed', 'true')
    })

    it('says when the branch is disabled', () => {
        branch([build(100, '1')], {disabled: true})
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-branch-disabled')).toBeInTheDocument()
    })

    it('shows the latest builds as cards, in the order the server gave them', () => {
        // The desktop UI renders builds as a matrix with a column per validation
        // stamp, which has no phone form at all. A card per build is what
        // replaces it - and the server already answers most-recent-first, which
        // the screen must not undo.
        branch([build(100, '3'), build(101, '2'), build(102, '1')])
        render(<MobileBranchScreen id="10"/>)
        const cards = screen.queryAllByTestId(/^mobile-build-\d+$/)
        expect(cards.map(card => card.getAttribute('data-testid')))
            .toEqual(['mobile-build-100', 'mobile-build-101', 'mobile-build-102'])
    })

    it('calls a build by its display name', () => {
        // A build name is a timestamp-run pair; the version people talk about is
        // the release property, which is exactly what `displayName` answers with
        // when it is set.
        branch([build(100, '20260901055547-36', {displayName: '1.4.0'})])
        render(<MobileBranchScreen id="10"/>)
        const card = screen.getByTestId('mobile-build-100')
        expect(card).toHaveTextContent('1.4.0')
        expect(card).not.toHaveTextContent('20260901055547-36')
    })

    it('says when each build happened', () => {
        branch([build(100, '1', {time: '2024-03-01T10:00:00Z'})])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-build-100-time')).not.toBeEmptyDOMElement()
    })

    it('shows the promotions of a build, named rather than only drawn', () => {
        // "Legible without zooming" is the acceptance criterion: a 16px medal on
        // a phone is a coloured dot, so the name goes beside it.
        branch([build(100, '1', {promotions: [[900, 500, 'BRONZE'], [901, 501, 'SILVER']]})])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-promotion-900')).toHaveTextContent('BRONZE')
        expect(screen.getByTestId('mobile-promotion-901')).toHaveTextContent('SILVER')
    })

    it('shows where a build is deployed', () => {
        branch([build(100, '1', {deployments: [[800, 'staging']]})])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-deployment-800')).toHaveTextContent('staging')
    })

    it('tells two slots of one environment apart by their qualifier', () => {
        // The whole point of #1731: `currentDeployments` now answers with
        // qualified slots too, and two deployments of one build into one
        // environment would otherwise be the same badge twice.
        branch([build(100, '1', {deployments: [[800, 'staging'], [801, 'staging', 'demo']]})])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-deployment-800')).toHaveTextContent('staging')
        expect(screen.getByTestId('mobile-deployment-800')).not.toHaveTextContent('[')
        expect(screen.getByTestId('mobile-deployment-801')).toHaveTextContent('staging [demo]')
    })

    it('keeps the builds when the instance has no environments feature', () => {
        // `currentDeployments` is contributed by the environments extension and
        // only registered when the licence enables it, so a query naming it
        // fails validation outright on an instance without one. Asking for the
        // deployments separately is what stops that taking the build list down.
        branch([build(100, '1', {deployments: [[800, 'staging']]})])
        deploymentsResult = {data: null, loading: false, error: "Validation error", finished: true}
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-build-100')).toHaveTextContent('1')
        expect(screen.queryByTestId('mobile-deployment-800')).not.toBeInTheDocument()
    })

    it('shows nothing about promotions or deployments when a build has neither', () => {
        branch([build(100, '1')])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.queryByTestId('mobile-build-100-promotions')).not.toBeInTheDocument()
        expect(screen.queryByTestId('mobile-build-100-deployments')).not.toBeInTheDocument()
    })

    it('sends a build card to that build on the phone', () => {
        branch([build(100, '1')])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-build-100').querySelector('a'))
            .toHaveAttribute('href', '/mobile/build/100')
    })

    describe('reaching older builds', () => {

        it('starts with one page of builds', () => {
            branch([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            expect(queryOptions.variables.size).toEqual(MOBILE_BUILD_PAGE_SIZE)
        })

        it('asks for one more page when the user wants more', () => {
            branch([build(100, '1')], {nextPage: {offset: MOBILE_BUILD_PAGE_SIZE}})
            render(<MobileBranchScreen id="10"/>)
            fireEvent.click(screen.getByTestId('mobile-builds-more'))
            expect(queryOptions.variables.size).toEqual(MOBILE_BUILD_PAGE_SIZE * 2)
        })

        it('offers nothing more to load once the branch is exhausted', () => {
            branch([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            expect(screen.queryByTestId('mobile-builds-more')).not.toBeInTheDocument()
        })
    })

    describe('searching the builds', () => {

        /*
         * The name box is debounced, so these tests have to let the timer fire -
         * only inside this block, because relative timestamps elsewhere in the
         * file read the real clock.
         */
        beforeEach(() => jest.useFakeTimers())
        afterEach(() => jest.useRealTimers())

        /** Types into the name box, then lets the debounce fire. */
        const searchFor = async (text) => {
            fireEvent.change(screen.getByTestId('mobile-builds-filter'), {target: {value: text}})
            await act(async () => {
                jest.advanceTimersByTime(1000)
            })
        }

        /**
         * Picks a promotion level from the dropdown.
         *
         * The click goes to the option antd actually listens on. The dropdown
         * also renders a parallel, visually hidden `role="option"` tree for
         * screen readers, which carries the same text and no handler - clicking
         * that one would select nothing and the test would pass for the wrong
         * reason.
         */
        const promotedTo = async (name) => {
            await act(async () => {
                fireEvent.mouseDown(
                    within(screen.getByTestId('mobile-builds-promotion')).getByRole('combobox')
                )
            })
            const option = Array.from(document.querySelectorAll('.ant-select-item-option'))
                .find(item => item.textContent.includes(name))
            await act(async () => {
                fireEvent.click(option)
            })
        }

        /**
         * Clears it again.
         *
         * The clear affordance acts on `mousedown`, not on `click` - it has to,
         * or the selector would take focus and reopen the dropdown under the
         * user's finger.
         */
        const anyPromotion = async () => {
            await act(async () => {
                fireEvent.mouseDown(document.querySelector('.ant-select-clear'))
            })
        }

        it('asks for no filter at all until something is searched for', () => {
            // The unfiltered screen must ask exactly the query it asked before
            // this search existed: `buildsPaginated` builds its own default
            // filter when it is given none.
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            expect(queryOptions.variables.filter).toBeNull()
        })

        it('searches on the server, not in the browser', async () => {
            // The browser holds one page of a branch that can have thousands of
            // builds, so a client-side filter could never reach the one the page
            // size left out - which is the only case the search exists for.
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            await searchFor('1.4')
            expect(queryOptions.variables.filter).toEqual({
                withDisplayName: '1\\.4',
                withPromotionLevel: null,
            })
        })

        it('takes what was typed literally', async () => {
            // `withDisplayName` is a regular expression and a version is mostly
            // dots - see `namePatterns`. Asserted there in full; here only that
            // the screen goes through it at all.
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            await searchFor('1.4.0')
            expect(queryOptions.variables.filter.withDisplayName).toEqual('1\\.4\\.0')
        })

        it('offers the branch own promotion levels to search on', async () => {
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            await act(async () => {
                fireEvent.mouseDown(
                    within(screen.getByTestId('mobile-builds-promotion')).getByRole('combobox')
                )
            })
            const offered = Array.from(document.querySelectorAll('.ant-select-item-option'))
                .map(item => item.textContent)
            expect(offered.some(text => text.includes('BRONZE'))).toBe(true)
            expect(offered.some(text => text.includes('SILVER'))).toBe(true)
        })

        it('searches by promotion level, by name rather than by id', async () => {
            // `withPromotionLevel` is matched as `PL.NAME = ?`; an id would
            // simply find nothing, quietly.
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            await promotedTo('BRONZE')
            expect(queryOptions.variables.filter).toEqual({
                withDisplayName: null,
                withPromotionLevel: 'BRONZE',
            })
        })

        it('combines the two', async () => {
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            await searchFor('1.4')
            await promotedTo('BRONZE')
            expect(queryOptions.variables.filter).toEqual({
                withDisplayName: '1\\.4',
                withPromotionLevel: 'BRONZE',
            })
        })

        it('goes back to the default list when both are cleared', async () => {
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            await searchFor('1.4')
            await promotedTo('BRONZE')
            await searchFor('')
            await anyPromotion()
            expect(queryOptions.variables.filter).toBeNull()
        })

        it('names both controls for a screen reader', () => {
            /*
             * Pinned because it is not obvious and could break silently on an
             * antd upgrade: `aria-label` is an unknown prop to rc-select, which
             * spreads those onto its outer wrapper. antd forwards this one to
             * the inner `role="combobox"` input as well - which is the element
             * a screen reader actually announces. Asserted by role and name
             * together, so the day it stops being forwarded this fails.
             */
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            expect(screen.getByRole('textbox', {name: 'Filter the builds by name'}))
                .toBeInTheDocument()
            expect(screen.getByRole('combobox', {name: 'Filter the builds by promotion'}))
                .toBeInTheDocument()
        })

        it('offers no promotion control on a branch that has none', () => {
            // `withPromotionLevel` is an exact name match, so a branch with no
            // level offers no choice - and an empty dropdown reads as broken
            // rather than as inapplicable.
            branch([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            expect(screen.queryByTestId('mobile-builds-promotion')).not.toBeInTheDocument()
        })

        it('starts again at the first page when the search changes', async () => {
            // Otherwise someone who pressed "Load more" five times and then typed
            // would ask a phone network for fifty filtered builds in one go.
            promotable([build(100, '1')], {nextPage: {offset: MOBILE_BUILD_PAGE_SIZE}})
            render(<MobileBranchScreen id="10"/>)
            fireEvent.click(screen.getByTestId('mobile-builds-more'))
            expect(queryOptions.variables.size).toEqual(MOBILE_BUILD_PAGE_SIZE * 2)
            await searchFor('1.4')
            expect(queryOptions.variables.size).toEqual(MOBILE_BUILD_PAGE_SIZE)
        })

        it('asks for the deployments of the same filtered page', async () => {
            // The two queries are a page of one list, keyed by build id. Asked
            // for under different terms they are pages of two different lists,
            // and every badge on the screen would silently disappear.
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            await searchFor('1.4')
            expect(deploymentsOptions.variables.filter).toEqual(queryOptions.variables.filter)
            expect(deploymentsOptions.variables.size).toEqual(queryOptions.variables.size)
        })

        it('says a name matched nothing, rather than that the branch is empty', async () => {
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            promotable([])
            await searchFor('9.9.9')
            expect(screen.getByTestId('mobile-builds-empty'))
                .toHaveTextContent('No build matches "9.9.9".')
        })

        it('says a promotion matched nothing', async () => {
            // A different sentence from the one above, because it asks for a
            // different next move: nothing to retype, just nothing that far.
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            promotable([])
            await promotedTo('SILVER')
            expect(screen.getByTestId('mobile-builds-empty'))
                .toHaveTextContent('No build has been promoted to SILVER.')
        })

        it('says so when the two together matched nothing', async () => {
            promotable([build(100, '1')])
            render(<MobileBranchScreen id="10"/>)
            promotable([])
            await searchFor('9.9.9')
            await promotedTo('SILVER')
            expect(screen.getByTestId('mobile-builds-empty'))
                .toHaveTextContent('No build named "9.9.9" has been promoted to SILVER.')
        })
    })

    it('says so when the branch has no build yet', () => {
        branch([])
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-builds-empty')).toHaveTextContent(/no build/i)
    })

    it('keeps the builds on screen while another page is fetched', () => {
        setResult({
            data: {
                branch: {
                    id: 10, name: 'main', displayName: 'main', favourite: false,
                    project: {id: 1, name: 'petclinic'},
                    buildsPaginated: {pageInfo: {nextPage: null}, pageItems: [build(100, '1')]},
                },
            },
            loading: true,
            finished: true,
        })
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByTestId('mobile-build-100')).toBeInTheDocument()
    })

    it('does not flash the empty state before the first answer arrives', () => {
        setResult({data: null, loading: true, finished: false})
        render(<MobileBranchScreen id="10"/>)
        expect(screen.queryByTestId('mobile-builds-empty')).not.toBeInTheDocument()
    })

    it('says so when the branch could not be loaded', () => {
        // Also how "no such branch" arrives: the root `branch(id:)` field is
        // non-null, so a missing one is a GraphQL error rather than a null.
        setResult({data: null, error: "Boom"})
        render(<MobileBranchScreen id="10"/>)
        expect(screen.getByText(/Boom/)).toBeInTheDocument()
        expect(screen.queryByTestId('mobile-builds-empty')).not.toBeInTheDocument()
    })
})
