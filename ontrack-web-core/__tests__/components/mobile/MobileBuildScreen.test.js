import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"

// antd's Drawer - the promote sheet - reads the responsive breakpoints, and
// jsdom ships no `matchMedia`. Same stand-in as the other antd component tests.
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

let queryResult = {data: null, loading: false, error: null, finished: true}
/*
 * The screen runs two queries, and they are not interchangeable: deployments are
 * asked for separately so that an instance without the environments licence -
 * where `currentDeployments` is absent from the schema - loses that section
 * alone instead of the whole document. The mock has to be able to fail one and
 * not the other, or that isolation is untested.
 */
let deploymentsResult = null
/*
 * The promote sheet asks for the branch's promotion levels, which is a third
 * query with a shape of its own - handed back here so the promotion can actually
 * be driven from the screen, as a user drives it.
 */
let promotionLevelsResult = null
/** The deploy sheet's eligible slots, when a test drives the deployment. */
let eligibleSlotsResult = null
/** The `deps` of the build's own query, which is what a refresh changes. */
let buildQueryDeps = null

const callGraphQL = jest.fn()

jest.mock("../../../components/services/GraphQL", () => ({
    useQuery: (query, {deps = [], dataFn = data => data, initialData = null} = {}) => {
        const text = String(query)
        if (text.includes('MobileBuildDeployments') && deploymentsResult) return deploymentsResult
        if (text.includes('MobileEligibleSlots')) {
            const result = eligibleSlotsResult ??
                {data: {eligibleSlotsForBuild: []}, loading: false, error: null, finished: true}
            return {...result, data: result.data ? dataFn(result.data) : initialData}
        }
        if (text.includes('MobilePromotionLevels')) {
            const result = promotionLevelsResult ?? {data: null, loading: false, error: null, finished: true}
            return {...result, data: result.data ? dataFn(result.data) : initialData}
        }
        if (text.includes('query MobileBuild(')) buildQueryDeps = deps
        return queryResult
    },
    callGraphQL: (...args) => callGraphQL(...args),
}))

/*
 * Starting a deployment takes the user to the deployment screen, which is a
 * navigation and not a render - so the router is the only way to see that it
 * happened.
 */
const push = jest.fn()
jest.mock("next/navigation", () => ({
    useRouter: () => ({push: (...args) => push(...args)}),
}))

/*
 * The deploy sheet's own slot list, and the rule summaries inside it. Both are
 * covered in `MobileDeploySheet.test.js`; here the sheet only has to open.
 */
jest.mock("../../../components/extension/environments/SlotAdmissionRuleSummary", () => ({
    __esModule: true,
    default: ({ruleId}) => <span>{ruleId}</span>,
}))

import MobileBuildScreen from "@/app/mobile/build/[id]/BuildScreen"

const setResult = (result) => {
    queryResult = {data: null, loading: false, error: null, finished: true, ...result}
}

const promotion = (id, levelId, name, {time = '2024-03-01T10:00:00Z', user = 'admin'} = {}) => ({
    id,
    creation: {time, user},
    promotionLevel: {id: levelId, name, image: false},
})

const deployment = (id, environmentName, qualifier = '') => ({
    id,
    end: '2024-03-02T10:00:00Z',
    slot: {id: `slot-${id}`, qualifier, environment: {id: environmentName, name: environmentName}},
})

const validation = (stampId, stampName, statusId, {time = '2024-03-01T11:00:00Z'} = {}) => ({
    validationStamp: {id: stampId, name: stampName, image: false, dataType: null},
    validationRuns: statusId ? [{
        id: stampId * 10,
        lastStatus: {creation: {time, user: 'admin'}, statusID: {id: statusId, name: statusId}},
    }] : [],
})

const build = ({
                   displayName = '1.4.0',
                   description,
                   promotions = [],
                   deployments = [],
                   candidates = [],
                   running = [],
                   validations = [],
                   authorizations = [],
               } = {}) => setResult({
    data: {
        build: {
            id: 100,
            name: '20260901055547-36',
            displayName,
            description,
            creation: {time: '2024-03-01T09:00:00Z', user: 'ci'},
            branch: {id: 10, name: 'main', displayName: 'main', project: {id: 1, name: 'petclinic'}},
            authorizations,
            promotionRuns: promotions,
            currentDeployments: deployments,
            candidatePipelines: candidates,
            runningPipelines: running,
            validations,
        },
    },
})

/** The shape `isAuthorized` reads. */
const granted = (name, action) => ({name, action, authorized: true})
const refused = (name, action) => ({name, action, authorized: false})

beforeEach(() => {
    push.mockClear()
    deploymentsResult = null
    promotionLevelsResult = null
    eligibleSlotsResult = null
    buildQueryDeps = null
    callGraphQL.mockReset()
    callGraphQL.mockResolvedValue({createPromotionRunById: {errors: null}})
})

describe('the mobile build screen', () => {

    describe('identity', () => {

        it('calls the build by its display name, not its own name', () => {
            // A build name is a timestamp-run pair; the version people talk
            // about is the release property, which `displayName` already is.
            build()
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-screen-title')).toHaveTextContent('1.4.0')
            expect(screen.getByTestId('mobile-screen-title')).not.toHaveTextContent('20260901055547-36')
        })

        it('places the build in its branch and its project', () => {
            build()
            render(<MobileBuildScreen id="100"/>)
            const subtitle = screen.getByTestId('mobile-screen-subtitle')
            expect(subtitle).toHaveTextContent('petclinic')
            expect(subtitle).toHaveTextContent('main')
        })

        it('goes back up to both of them, inside the mobile UI', () => {
            build()
            render(<MobileBuildScreen id="100"/>)
            const hrefs = Array.from(screen.getByTestId('mobile-screen-subtitle').querySelectorAll('a'))
                .map(link => link.getAttribute('href'))
            expect(hrefs).toEqual(['/mobile/project/1', '/mobile/branch/10'])
        })

        it('says when the build was created and by whom', () => {
            build()
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-created')).toHaveTextContent('ci')
        })

        it('shows the description when there is one', () => {
            build({description: "Owner search by phone number."})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-description'))
                .toHaveTextContent('Owner search by phone number.')
        })

        it('leaves the description out when there is none', () => {
            build()
            render(<MobileBuildScreen id="100"/>)
            expect(screen.queryByTestId('mobile-build-description')).not.toBeInTheDocument()
        })
    })

    describe('promotions', () => {

        it('lists them, named as well as drawn', () => {
            build({promotions: [promotion(900, 500, 'BRONZE'), promotion(901, 501, 'SILVER')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-promotion-900')).toHaveTextContent('BRONZE')
            expect(screen.getByTestId('mobile-build-promotion-901')).toHaveTextContent('SILVER')
        })

        it('says when each promotion happened and who made it', () => {
            // The decision this screen exists for is "should I promote or deploy
            // this?", and who promoted it last, and when, is an input to it.
            build({promotions: [promotion(900, 500, 'BRONZE', {user: 'alice'})]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-promotion-900')).toHaveTextContent('alice')
        })

        it('says so when the build has none', () => {
            build()
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-promotions')).toHaveTextContent(/not been promoted/i)
        })
    })

    describe('deployments', () => {

        it('says where the build is deployed', () => {
            build({deployments: [deployment(800, 'production')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-deployment-800')).toHaveTextContent('production')
        })

        it('names the qualifier beside the environment', () => {
            // A project can have two slots in one environment, told apart by
            // nothing but the qualifier - so an environment name on its own
            // would draw the same row twice and say nothing about either.
            build({deployments: [deployment(800, 'production'), deployment(801, 'production', 'demo')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-deployment-801')).toHaveTextContent('production [demo]')
        })

        it('leaves the unqualified deployment unadorned', () => {
            build({deployments: [deployment(800, 'production')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-deployment-800')).not.toHaveTextContent('[')
        })

        it('says so when it is deployed nowhere', () => {
            build()
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-deployments')).toHaveTextContent(/not deployed/i)
        })

        it('loses only this section when the instance has no environments feature', () => {
            // `currentDeployments` is contributed by the environments extension
            // and only registered when the licence enables it, so without it the
            // field is absent from the *schema* and a query naming it fails
            // validation - taking the whole document with it. Asking separately
            // is what keeps the rest of the screen alive.
            build({promotions: [promotion(900, 500, 'BRONZE')]})
            deploymentsResult = {data: null, loading: false, error: "Validation error", finished: true}
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-deployments')).toHaveTextContent(/not available on this instance/i)
            // Everything else still there.
            expect(screen.getByTestId('mobile-build-promotion-900')).toBeInTheDocument()
            expect(screen.getByTestId('mobile-screen-title')).toHaveTextContent('1.4.0')
        })

        it('does not call an unavailable section empty', () => {
            // "Deployed nowhere" is a fact about the build; "unavailable" is a
            // fact about the instance, and saying the first for the second
            // would be a lie on the screen whose job is a decision.
            build()
            deploymentsResult = {data: null, loading: false, error: "Validation error", finished: true}
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-deployments')).not.toHaveTextContent(/not deployed anywhere/i)
        })
    })

    describe('validations', () => {

        it('lists the validations with their status', () => {
            // Read-only, and deliberately included even though validations are
            // otherwise out of scope: whether the build is green is the input to
            // the promote-or-deploy decision this screen serves.
            build({validations: [validation(700, 'BUILD', 'PASSED'), validation(701, 'SMOKE', 'FAILED')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-validation-700')).toHaveTextContent('BUILD')
            expect(screen.getByTestId('mobile-build-validation-700')).toHaveTextContent('PASSED')
            expect(screen.getByTestId('mobile-build-validation-701')).toHaveTextContent('FAILED')
        })

        it('shows a stamp with no run at all rather than hiding it', () => {
            // "Not run yet" is a different answer from "passed", and on a
            // decision screen the difference matters.
            build({validations: [validation(700, 'BUILD', null)]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-validation-700')).toBeInTheDocument()
        })

        it('does not drill into a run', () => {
            // Run detail pages are explicitly out of scope.
            build({validations: [validation(700, 'BUILD', 'PASSED')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-validation-700').querySelector('a')).toBeNull()
        })

        it('says when there were more validations than it shows', () => {
            // Silently showing 50 of 80 would let a reader conclude the build is
            // green on evidence the screen never displayed.
            const many = Array.from({length: 51}, (_, index) => validation(700 + index, `STAMP-${index}`, 'PASSED'))
            build({validations: many})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-validations-truncated')).toBeInTheDocument()
        })

        it('says nothing about more validations when the list is whole', () => {
            build({validations: [validation(700, 'BUILD', 'PASSED')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.queryByTestId('mobile-build-validations-truncated')).not.toBeInTheDocument()
        })

        it('says so when the build has no validation', () => {
            build()
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-validations')).toHaveTextContent(/no validation/i)
        })
    })

    describe('the action entry points', () => {

        it('offers to promote a build the user may promote', () => {
            build({authorizations: [granted('build', 'promote')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-promote')).toBeInTheDocument()
        })

        it('hides the promote entry point from a user who may not', () => {
            build({authorizations: [refused('build', 'promote')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.queryByTestId('mobile-build-promote')).not.toBeInTheDocument()
        })

        it('offers to deploy a build the user may deploy', () => {
            build({authorizations: [granted('slotPipeline', 'create')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-deploy')).toBeInTheDocument()
        })

        it('hides the deploy entry point from a user who may not', () => {
            // Also how an instance without the environments licence looks: the
            // server answers `slotPipeline/create` false when the feature is off.
            build({authorizations: [refused('slotPipeline', 'create')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.queryByTestId('mobile-build-deploy')).not.toBeInTheDocument()
        })

        it('shows nothing at all to a user who may do neither', () => {
            build({authorizations: []})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.queryByTestId('mobile-build-actions')).not.toBeInTheDocument()
        })

        it('promotes on the phone rather than sending the user to the desktop', async () => {
            // #1724: the promote entry point used to switch this device to the
            // desktop UI, because there was nowhere here to promote from.
            build({authorizations: [granted('build', 'promote')]})
            render(<MobileBuildScreen id="100"/>)
            fireEvent.click(screen.getByTestId('mobile-build-promote'))
            expect(await screen.findByTestId('mobile-promote-level')).toBeInTheDocument()
        })

        it('deploys on the phone rather than sending the user to the desktop', async () => {
            // #1725: the deploy entry point used to switch this device to the
            // desktop UI, for the same reason the promote one did.
            build({authorizations: [granted('slotPipeline', 'create')]})
            render(<MobileBuildScreen id="100"/>)
            fireEvent.click(screen.getByTestId('mobile-build-deploy'))
            expect(await screen.findByTestId('mobile-deploy-none')).toBeInTheDocument()
        })

        it('says nothing about the desktop at all any more', () => {
            // The caption under the buttons covered the deploy entry point while
            // it was the one thing here that still happened somewhere else.
            build({authorizations: [granted('build', 'promote'), granted('slotPipeline', 'create')]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-actions')).not.toHaveTextContent(/desktop/i)
        })
    })

    describe('deploying from the build screen', () => {

        const deployable = (slots = []) => {
            build({authorizations: [granted('slotPipeline', 'create')]})
            eligibleSlotsResult = {
                data: {eligibleSlotsForBuild: slots},
                loading: false,
                error: null,
                finished: true,
            }
        }

        const slot = (id, name) => ({
            eligible: true,
            nonEligibleRules: [],
            slot: {id, qualifier: '', environment: {id: `env-${id}`, name, order: 100}},
        })

        it('takes the user to the new deployment, which is where the rest happens', async () => {
            // A deployment starts as a CANDIDATE: nothing has happened to the
            // environment yet, and the input, the override and the run are all
            // on its own screen.
            callGraphQL.mockResolvedValue({
                startSlotPipeline: {pipeline: {id: 'pipeline-1'}, errors: null},
            })
            deployable([slot('slot-1', 'staging')])
            render(<MobileBuildScreen id="100"/>)

            fireEvent.click(screen.getByTestId('mobile-build-deploy'))
            fireEvent.click(await screen.findByTestId('mobile-deploy-start-slot-1'))

            await waitFor(() => expect(push).toHaveBeenCalledWith('/mobile/deployment/pipeline-1'))
        })
    })

    describe('deployments in progress', () => {

        const unsettled = (id, environmentName, {status = 'CANDIDATE', order = 100} = {}) => ({
            id,
            end: null,
            start: '2024-03-02T09:00:00Z',
            status,
            slot: {
                id: `slot-${id}`,
                qualifier: '',
                environment: {id: environmentName, name: environmentName, order},
            },
        })

        it('lists them, and taps through to the deployment', () => {
            // The only way into a deployment somebody else started - CI, usually
            // - which is half of what the mobile deployment flow is for.
            build({candidates: [unsettled('pipeline-1', 'production')]})
            render(<MobileBuildScreen id="100"/>)
            const row = screen.getByTestId('mobile-build-unsettled-pipeline-1')
            expect(row).toHaveTextContent('production')
            expect(row.querySelector('a').getAttribute('href')).toEqual('/mobile/deployment/pipeline-1')
        })

        it('lists a running deployment too, which is the only way back to one', () => {
            // `currentDeployments` names pipelines which reached DONE, so a
            // RUNNING one used to be reachable only while the user stayed on the
            // screen that started it (#1736).
            build({running: [unsettled('pipeline-2', 'staging', {status: 'RUNNING'})]})
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-unsettled-pipeline-2')).toHaveTextContent('staging')
        })

        it('carries each row\'s own status, because "waiting" is wrong for a running one', () => {
            build({
                candidates: [unsettled('pipeline-1', 'production', {order: 200})],
                running: [unsettled('pipeline-2', 'staging', {status: 'RUNNING', order: 100})],
            })
            render(<MobileBuildScreen id="100"/>)
            expect(screen.getByTestId('mobile-build-unsettled-pipeline-1')).toHaveTextContent('Candidate')
            expect(screen.getByTestId('mobile-build-unsettled-pipeline-2')).toHaveTextContent('Running')
        })

        it('puts the furthest environment first, whatever the status', () => {
            build({
                candidates: [unsettled('pipeline-1', 'staging', {order: 100})],
                running: [unsettled('pipeline-2', 'production', {status: 'RUNNING', order: 200})],
            })
            render(<MobileBuildScreen id="100"/>)
            const rows = Array.from(
                screen.getByTestId('mobile-build-unsettled')
                    .querySelectorAll('[data-testid^="mobile-build-unsettled-"]')
            ).map(row => row.getAttribute('data-testid'))
            expect(rows).toEqual(['mobile-build-unsettled-pipeline-2', 'mobile-build-unsettled-pipeline-1'])
        })

        it('is absent rather than empty when nothing is in progress', () => {
            // Unlike the three sections below it, this is not a facet of the
            // build: "no deployment is waiting" on every build screen would be a
            // line nobody reads.
            build()
            render(<MobileBuildScreen id="100"/>)
            expect(screen.queryByTestId('mobile-build-unsettled')).not.toBeInTheDocument()
        })
    })

    describe('promoting from the build screen', () => {

        const promotable = () => {
            build({authorizations: [granted('build', 'promote')]})
            promotionLevelsResult = {
                data: {branches: [{promotionLevels: [{id: 500, name: 'BRONZE', image: false, fields: []}]}]},
                loading: false,
                error: null,
                finished: true,
            }
        }

        it('shows the new promotion without the user reloading anything', async () => {
            // The acceptance criterion. The screen refetches rather than
            // patching its copy: the run's id and its signature are the
            // server's answer, not something the phone can invent.
            promotable()
            render(<MobileBuildScreen id="100"/>)
            const before = buildQueryDeps

            fireEvent.click(screen.getByTestId('mobile-build-promote'))
            const level = await screen.findByTestId('mobile-promote-level')
            fireEvent.mouseDown(level.querySelector('.ant-select-selector'))
            fireEvent.click(await screen.findByTitle('BRONZE'))
            fireEvent.click(screen.getByTestId('mobile-promote-submit'))

            await waitFor(() => expect(callGraphQL).toHaveBeenCalled())
            await waitFor(() => expect(buildQueryDeps).not.toEqual(before))
        })
    })

    it('does not flash an empty build before the first answer arrives', () => {
        setResult({data: null, loading: true, finished: false})
        render(<MobileBuildScreen id="100"/>)
        expect(screen.queryByTestId('mobile-build-promotions')).not.toBeInTheDocument()
    })

    it('says so when the build could not be loaded', () => {
        // Also how "no such build" arrives: `build(id:)` is a non-null field, so
        // an id nobody can see is a GraphQL error rather than a null.
        setResult({data: null, error: "Build ID not found: 100"})
        render(<MobileBuildScreen id="100"/>)
        expect(screen.getByText(/not found/)).toBeInTheDocument()
    })
})
