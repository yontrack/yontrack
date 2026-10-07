import "@testing-library/jest-dom"
import {render, screen, within} from "@testing-library/react"

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

import ReadinessContent from "@components/readiness/ReadinessContent"

const item = (kind, name, message) => ({kind, name, message})

const validationStamps = [
    {id: '10', name: 'UNIT.TESTS', image: false},
    {id: '11', name: 'INTEGRATION.TESTS', image: false},
]

const promotionLevels = [
    {id: '20', name: 'BRONZE', image: false},
    {id: '21', name: 'SILVER', image: false},
]

const notReady = (...missing) => ({ready: false, missing})

describe('ReadinessContent', () => {

    it('says "Ready" and shows the ready action when nothing is missing', () => {
        render(<ReadinessContent
            readiness={{ready: true, missing: []}}
            target="promotionLevel"
            readyAction={<button>Promote to SILVER</button>}
        />)
        expect(screen.getByTestId('readiness-ready')).toHaveTextContent('Ready')
        expect(screen.getByRole('button', {name: 'Promote to SILVER'})).toBeInTheDocument()
        expect(screen.queryByTestId(/^readiness-group-/)).not.toBeInTheDocument()
    })

    it('does not say "Ready" nor show the ready action when something is missing', () => {
        render(<ReadinessContent
            readiness={notReady(item('VALIDATION', 'UNIT.TESTS', 'Not validated'))}
            target="promotionLevel"
            validationStamps={validationStamps}
            readyAction={<button>Promote to SILVER</button>}
        />)
        expect(screen.queryByTestId('readiness-ready')).not.toBeInTheDocument()
        expect(screen.queryByRole('button', {name: 'Promote to SILVER'})).not.toBeInTheDocument()
    })

    it('groups the missing items by kind, under a heading each', () => {
        render(<ReadinessContent
            readiness={notReady(
                item('VALIDATION', 'UNIT.TESTS', 'Last status: FAILED'),
                item('VALIDATION', 'INTEGRATION.TESTS', 'Not validated'),
                item('PROMOTION', 'BRONZE', 'Not promoted to BRONZE'),
            )}
            target="promotionLevel"
            validationStamps={validationStamps}
            promotionLevels={promotionLevels}
        />)
        const validations = screen.getByTestId('readiness-group-VALIDATION')
        expect(within(validations).getByText('Validations')).toBeInTheDocument()
        expect(within(validations).getAllByRole('listitem')).toHaveLength(2)
        const promotions = screen.getByTestId('readiness-group-PROMOTION')
        expect(within(promotions).getByText('Promotions')).toBeInTheDocument()
        expect(within(promotions).getAllByRole('listitem')).toHaveLength(1)
    })

    it('links a missing validation to its stamp, with its message', () => {
        render(<ReadinessContent
            readiness={notReady(item('VALIDATION', 'UNIT.TESTS', 'Last status: FAILED'))}
            target="promotionLevel"
            validationStamps={validationStamps}
        />)
        const row = screen.getByTestId('readiness-item-VALIDATION-UNIT.TESTS')
        expect(within(row).getByRole('link')).toHaveAttribute('href', '/validationStamp/10')
        expect(row).toHaveTextContent('UNIT.TESTS')
        expect(row).toHaveTextContent('Last status: FAILED')
    })

    it('names a missing validation it cannot link to', () => {
        render(<ReadinessContent
            readiness={notReady(item('VALIDATION', 'GONE', 'Not validated'))}
            target="promotionLevel"
            validationStamps={validationStamps}
        />)
        const row = screen.getByTestId('readiness-item-VALIDATION-GONE')
        expect(within(row).queryByRole('link')).not.toBeInTheDocument()
        expect(row).toHaveTextContent('GONE')
        expect(row).toHaveTextContent('Not validated')
    })

    it('shows the required level of a missing promotion', () => {
        render(<ReadinessContent
            readiness={notReady(item('PROMOTION', 'BRONZE', 'Not promoted to BRONZE'))}
            target="promotionLevel"
            promotionLevels={promotionLevels}
        />)
        const row = screen.getByTestId('readiness-item-PROMOTION-BRONZE')
        expect(within(row).getByRole('link')).toHaveAttribute('href', '/promotionLevel/20')
        expect(row).toHaveTextContent('BRONZE')
    })

    it('gives the reason of a failing check', () => {
        render(<ReadinessContent
            readiness={notReady(item('CHECK', 'Previous promotion', 'The build must be promoted to BRONZE first.'))}
            target="promotionLevel"
        />)
        expect(screen.getByTestId('readiness-item-CHECK-Previous promotion'))
            .toHaveTextContent('The build must be promoted to BRONZE first.')
    })

    it('says a manual promotion level is promoted by a person', () => {
        render(<ReadinessContent
            readiness={notReady(item('MANUAL', 'GOLD', 'GOLD has no auto promotion and is granted by a person: ...'))}
            target="promotionLevel"
        />)
        const row = screen.getByTestId('readiness-item-MANUAL-GOLD')
        expect(row).toHaveTextContent('Promoted by a person')
        expect(row).not.toHaveTextContent('has no auto promotion')
    })

    it('gives the message of a manual approval of a slot, which is not a promotion', () => {
        render(<ReadinessContent
            readiness={notReady(item('MANUAL', 'Approval', 'A person must approve the deployment.'))}
            target="slot"
        />)
        const row = screen.getByTestId('readiness-item-MANUAL-Approval')
        expect(row).toHaveTextContent('A person must approve the deployment.')
        expect(row).not.toHaveTextContent('Promoted by a person')
    })

    it('names an admission rule of a slot, with its reason', () => {
        render(<ReadinessContent
            readiness={notReady(item('ADMISSION_RULE', 'Needs GOLD', 'The build is not eligible for this slot.'))}
            target="slot"
        />)
        const row = screen.getByTestId('readiness-item-ADMISSION_RULE-Needs GOLD')
        expect(row).toHaveTextContent('Needs GOLD')
        expect(row).toHaveTextContent('The build is not eligible for this slot.')
    })

    it('gives the message of an agent policy', () => {
        render(<ReadinessContent
            readiness={notReady(item('AGENT_POLICY', 'release-bot', 'The agent is not admitted on SILVER.'))}
            target="promotionLevel"
        />)
        expect(screen.getByTestId('readiness-item-AGENT_POLICY-release-bot'))
            .toHaveTextContent('The agent is not admitted on SILVER.')
    })

})
