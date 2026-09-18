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

// `SlotAdmissionRuleSummary` goes through `Dynamic`, whose webpack context does not exist under Jest.
jest.mock("../../../../components/extension/environments/SlotAdmissionRuleSummary", () => ({
    __esModule: true,
    default: ({ruleId}) => <span>{ruleId}</span>,
}))

import BuildEnvironmentsDecorations
    from "@components/framework/decorations/environments.ui.BuildEnvironmentsDecorations"

const stub = (name, order) => ({
    environmentId: `env-${name}`,
    environmentName: name,
    environmentOrder: order,
    environmentImage: false,
    slotId: `slot-${name}`,
    qualifier: '',
    pipelineId: `pipeline-${name}`,
})

describe('a build decoration', () => {

    it('draws a chip per environment, linking to the slot', () => {
        render(<BuildEnvironmentsDecorations decoration={{data: [stub('production', 20)]}}/>)
        const link = screen.getByTestId('build-decoration-slot-production')
        expect(link).toHaveAttribute('href', '/extension/environments/slot/slot-production')
        // Compact: the state and the icon, with the environment named in the chip's tooltip.
        expect(screen.getByTestId('journey-chip-slot-production')).toHaveTextContent('Deployed')
        expect(screen.getByTestId('journey-chip-slot-production')).not.toHaveTextContent('production —')
        expect(screen.getByRole('img', {name: 'production'})).toBeVisible()
    })

    it('draws nothing for a build deployed nowhere', () => {
        render(<BuildEnvironmentsDecorations decoration={{data: []}}/>)
        expect(screen.queryByTestId(/^journey-chip-/)).toBeNull()
    })
})
