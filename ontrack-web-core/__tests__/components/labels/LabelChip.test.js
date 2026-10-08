import "@testing-library/jest-dom"
import {fireEvent, render, screen} from "@testing-library/react"

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

import LabelChip from "@components/labels/LabelChip"

const label = {
    id: 12,
    category: 'team',
    name: 'platform',
    description: 'Projects of the platform team',
    color: '#ff0000',
    foregroundColor: '#ffffff',
}

describe('the label chip', () => {

    it('is solid by default: filled with the label colour, in the computed foreground', () => {
        render(<LabelChip label={label}/>)
        const chip = screen.getByTestId('label-team:platform')
        expect(chip).toHaveAttribute('data-variant', 'solid')
        expect(chip).toHaveStyle({backgroundColor: '#ff0000', color: '#ffffff'})
        expect(chip).toHaveTextContent('team:platform')
        expect(chip.querySelector('[data-testid="label-dot"]')).toBeNull()
    })

    it('is neutral in the quiet variant, the colour only on a decorative dot', () => {
        render(<LabelChip label={label} variant="quiet"/>)
        const chip = screen.getByTestId('label-team:platform')
        expect(chip).toHaveAttribute('data-variant', 'quiet')
        expect(chip).not.toHaveStyle({backgroundColor: '#ff0000'})
        expect(chip).not.toHaveStyle({color: '#ffffff'})
        expect(chip).toHaveTextContent('team:platform')

        const dot = chip.querySelector('[data-testid="label-dot"]')
        expect(dot).not.toBeNull()
        expect(dot).toHaveAttribute('aria-hidden', 'true')
        expect(dot).toHaveStyle({backgroundColor: '#ff0000'})
    })

    it.each(['solid', 'quiet'])('links to the label page in the %s variant', (variant) => {
        render(<LabelChip label={label} variant={variant}/>)
        expect(screen.getByRole('link')).toHaveAttribute('href', '/project-labels/12')
    })

    it.each(['solid', 'quiet'])('shows the description as a tooltip in the %s variant', async (variant) => {
        render(<LabelChip label={label} variant={variant}/>)
        fireEvent.mouseEnter(screen.getByTestId('label-team:platform'))
        expect(await screen.findByText('Projects of the platform team')).toBeInTheDocument()
    })

    it('does not link when told not to', () => {
        render(<LabelChip label={label} variant="quiet" link={false}/>)
        expect(screen.queryByRole('link')).toBeNull()
    })
})
