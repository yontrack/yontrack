import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import FindingsValidationDataType
    from "@components/framework/validation-run-data/findings.validation.FindingsValidationDataType"

describe('FindingsValidationDataType run data', () => {

    it('shows the counts per severity as severity tags, short, their label spelled out', () => {
        render(<FindingsValidationDataType
            levels={{CRITICAL: 1, HIGH: 2, MEDIUM: 3, LOW: 4}}
            unknown={0}
            accepted={0}
        />)
        expect(screen.getByText('C 1')).toHaveAttribute('aria-label', '1 critical finding')
        expect(screen.getByText('H 2')).toHaveAttribute('aria-label', '2 high findings')
        expect(screen.getByText('M 3')).toBeInTheDocument()
        expect(screen.getByText('L 4')).toBeInTheDocument()
    })

    it('shows a severity with no finding muted', () => {
        render(<FindingsValidationDataType
            levels={{CRITICAL: 0, HIGH: 1, MEDIUM: 0, LOW: 0}}
            unknown={0}
            accepted={0}
        />)
        expect(screen.getByText('C 0')).toBeInTheDocument()
    })

    it('shows the unknown and accepted counts when there are some', () => {
        render(<FindingsValidationDataType
            levels={{CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0}}
            unknown={5}
            accepted={6}
        />)
        expect(screen.getByText('U 5')).toBeInTheDocument()
        expect(screen.getByText('6')).toBeInTheDocument()
    })

    it('hides the unknown and accepted counts when there are none', () => {
        render(<FindingsValidationDataType
            levels={{CRITICAL: 7, HIGH: 7, MEDIUM: 7, LOW: 7}}
            unknown={0}
            accepted={0}
        />)
        expect(screen.getAllByText(/^[CHML] 7$/)).toHaveLength(4)
        expect(screen.queryByText(/^U /)).not.toBeInTheDocument()
        expect(screen.queryByText('0')).not.toBeInTheDocument()
    })
})
