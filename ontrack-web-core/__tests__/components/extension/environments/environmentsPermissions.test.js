import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"

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

jest.mock("../../../../components/services/GraphQL", () => ({
    callGraphQL: jest.fn(),
    useQuery: () => ({data: null, loading: false, error: null, finished: true}),
}))

jest.mock("../../../../components/providers/ConnectionContextProvider", () => ({
    useGraphQLClient: () => ({request: jest.fn()}),
}))

import {UserContext} from "@components/providers/UserProvider"
import EnvironmentsSetupCommand from "@components/extension/environments/EnvironmentsSetupCommand"
import DeleteEnvironmentButton from "@components/extension/environments/DeleteEnvironmentButton"
import SlotAdmissionRuleActions from "@components/extension/environments/SlotAdmissionRuleActions"

/**
 * The three permission defects #1793 fixed on the way, each one a control offered to somebody the
 * backend would then refuse - or hidden from somebody allowed to use it.
 *
 * Tested here rather than in Playwright because the acceptance stack signs in through Keycloak as
 * one administrator: a spec cannot become a user without a right, and a gate that can only be
 * exercised from one side is not being tested at all.
 */

const asUser = (authorizations, children) => render(
    <UserContext.Provider value={{authorizations}}>{children}</UserContext.Provider>
)

const slotWith = (edit) => ({
    id: 'slot-1',
    authorizations: [{name: 'slot', action: 'edit', authorized: edit}],
})

describe('the Setup command on the Environments home', () => {

    it('is hidden from a user with no configuration right at all', () => {
        // A Setup page whose every control is missing is worse than no Setup button.
        asUser({environment: {view: true}}, <EnvironmentsSetupCommand/>)
        expect(screen.queryByTestId('environments-setup')).not.toBeInTheDocument()
    })

    it.each([
        ['creating an environment', {environment: {create: true}}],
        ['editing an environment', {environment: {edit: true}}],
        ['deleting an environment', {environment: {delete: true}}],
        ['creating a slot', {slot: {create: true}}],
    ])('is there for a user who can do one thing: %s', (_, authorizations) => {
        asUser(authorizations, <EnvironmentsSetupCommand/>)
        expect(screen.getByTestId('environments-setup')).toBeInTheDocument()
    })
})

describe('deleting an environment', () => {

    const environment = {id: 'env-1', name: 'production'}

    it('is offered to a user who may delete one', () => {
        asUser({environment: {delete: true}}, <DeleteEnvironmentButton environment={environment}/>)
        expect(screen.getByTitle('Environment deletion')).toBeInTheDocument()
    })

    it('is not offered to a user who may only create one', () => {
        // The gate used to be `environment.create`, which is a different global function
        // (`EnvironmentSave` rather than `EnvironmentDelete`) - so the button was shown to somebody
        // the backend would refuse.
        asUser({environment: {create: true, edit: true}}, <DeleteEnvironmentButton environment={environment}/>)
        expect(screen.queryByTitle('Environment deletion')).not.toBeInTheDocument()
    })
})

describe("an admission rule's actions", () => {

    const rule = {id: 'rule-1', name: 'promotion', ruleId: 'promotion', ruleConfig: {}}
    const dialog = {start: jest.fn()}

    it('offers editing and deleting to a user who may configure the slot', () => {
        render(<SlotAdmissionRuleActions slot={slotWith(true)} rule={rule} dialog={dialog}/>)
        expect(screen.getByTestId('slot-rule-edit-rule-1')).toBeInTheDocument()
        expect(screen.getByTitle('Delete this rule')).toBeInTheDocument()
    })

    it('offers nothing to a user who may not', () => {
        // Deleting a rule had *no* check in front of it: the backend has always refused it without
        // `SlotUpdate`, so this was a button that produced an error rather than a button that was
        // not there.
        render(<SlotAdmissionRuleActions slot={slotWith(false)} rule={rule} dialog={dialog}/>)
        expect(screen.queryByTestId('slot-rule-edit-rule-1')).not.toBeInTheDocument()
        expect(screen.queryByTitle('Delete this rule')).not.toBeInTheDocument()
    })
})
