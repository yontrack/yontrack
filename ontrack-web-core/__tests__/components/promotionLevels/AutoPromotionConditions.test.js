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

import AutoPromotionConditions from "@components/promotionLevels/AutoPromotionConditions"

const vs = (id, name) => ({id: String(id), name, image: false})
const pl = (id, name) => ({id: String(id), name, image: false})
const status = (id, name) => ({statusID: {id, name}})

const buildConditions = {
    include: '.*TESTS',
    exclude: '',
    autoRevoke: false,
    validationStamps: [
        {validationStamp: vs(1, 'BUILD'), passed: true, lastRun: {id: '10', lastStatus: status('PASSED', 'Passed')}},
        {validationStamp: vs(2, 'UNIT.TESTS'), passed: false, lastRun: {id: '11', lastStatus: status('FAILED', 'Failed')}},
        {validationStamp: vs(3, 'INTEGRATION.TESTS'), passed: false, lastRun: null},
    ],
    promotionLevels: [
        {promotionLevel: pl(1, 'BRONZE'), promotionRun: {id: '20'}},
        {promotionLevel: pl(2, 'IRON'), promotionRun: null},
    ],
}

describe('the auto promotion conditions, with a build', () => {

    it('summarises what passed and what was granted', () => {
        render(<AutoPromotionConditions conditions={buildConditions} withBuild={true}/>)
        expect(screen.getByTestId('auto-promotion-summary'))
            .toHaveTextContent('1/3 validations passed · 1/2 promotions granted')
    })

    it('counts a FIXED run as passed when the server says so', () => {
        const conditions = {
            ...buildConditions,
            validationStamps: [
                {validationStamp: vs(1, 'BUILD'), passed: true, lastRun: {id: '10', lastStatus: status('FIXED', 'Fixed')}},
            ],
            promotionLevels: [],
        }
        render(<AutoPromotionConditions conditions={conditions} withBuild={true}/>)
        expect(screen.getByTestId('auto-promotion-summary')).toHaveTextContent('1/1 validations passed')
        expect(screen.getByTestId('auto-promotion-summary')).not.toHaveTextContent('promotions')
    })

    it('links a stamp which ran to its latest run, with its status', () => {
        render(<AutoPromotionConditions conditions={buildConditions} withBuild={true}/>)
        expect(screen.getByTestId('auto-promotion-vs-link-UNIT.TESTS')).toHaveAttribute('href', '/validationRun/11')
        expect(screen.getByTestId('auto-promotion-vs-UNIT.TESTS')).toHaveTextContent('Failed')
    })

    it('says "Not run" for a stamp which never ran, without a link', () => {
        render(<AutoPromotionConditions conditions={buildConditions} withBuild={true}/>)
        const row = screen.getByTestId('auto-promotion-vs-INTEGRATION.TESTS')
        expect(row).toHaveTextContent('Not run')
        expect(within(row).queryByRole('link')).toBeNull()
    })

    it('links a granted promotion to its run, and says "Not granted" otherwise', () => {
        render(<AutoPromotionConditions conditions={buildConditions} withBuild={true}/>)
        expect(screen.getByTestId('auto-promotion-pl-link-BRONZE')).toHaveAttribute('href', '/promotionRun/20')
        const iron = screen.getByTestId('auto-promotion-pl-IRON')
        expect(iron).toHaveTextContent('Not granted')
        expect(within(iron).queryByRole('link')).toBeNull()
    })
})

describe('the auto promotion conditions, plain', () => {

    const plainConditions = {
        include: '.*TESTS',
        exclude: 'SLOW.*',
        autoRevoke: true,
        validationStamps: [vs(1, 'BUILD'), vs(2, 'UNIT.TESTS')],
        promotionLevels: [pl(1, 'BRONZE')],
    }

    it('lists the conditions without any state', () => {
        render(<AutoPromotionConditions conditions={plainConditions}/>)
        expect(screen.getByTestId('auto-promotion-vs-BUILD')).toBeInTheDocument()
        expect(screen.getByTestId('auto-promotion-vs-UNIT.TESTS')).toBeInTheDocument()
        expect(screen.getByTestId('auto-promotion-pl-BRONZE')).toBeInTheDocument()
        expect(screen.queryByTestId('auto-promotion-summary')).toBeNull()
        expect(screen.queryByText('Not run')).toBeNull()
    })

    it('shows the regular expressions and the auto revocation', () => {
        render(<AutoPromotionConditions conditions={plainConditions}/>)
        const patterns = screen.getByTestId('auto-promotion-patterns')
        expect(patterns).toHaveTextContent('Include .*TESTS')
        expect(patterns).toHaveTextContent('Exclude SLOW.*')
        expect(screen.getByText('Revoked when a prerequisite is no longer valid')).toBeInTheDocument()
    })

    it('hides the regular expressions when none is set', () => {
        render(<AutoPromotionConditions conditions={{...plainConditions, include: '', exclude: ''}}/>)
        expect(screen.queryByTestId('auto-promotion-patterns')).toBeNull()
    })
})
