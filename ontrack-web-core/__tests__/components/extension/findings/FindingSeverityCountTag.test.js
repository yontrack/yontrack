import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import FindingSeverityCountTag from "@components/extension/findings/FindingSeverityCountTag"

describe('Count of findings of a severity', () => {

    it('names the severity and gives the count, colour never being the only carrier', () => {
        render(<FindingSeverityCountTag severity="CRITICAL" count={3} testId="count"/>)
        expect(screen.getByTestId('count')).toHaveTextContent('Critical 3')
    })

    it('is filled with the colour of the severity', () => {
        render(<FindingSeverityCountTag severity="CRITICAL" count={3} testId="count"/>)
        const tag = screen.getByTestId('count').closest('.ant-tag') ?? screen.getByTestId('count')
        expect(tag).toHaveStyle({backgroundColor: '#cf1322', color: '#ffffff'})
    })

    it('links to the findings it counts', () => {
        render(<FindingSeverityCountTag severity="HIGH" count={2} href="/findings?severity=HIGH" testId="count"/>)
        expect(screen.getByRole('link', {name: '2 high findings'})).toHaveAttribute('href', '/findings?severity=HIGH')
    })

    it('shows a zero count muted, not coloured, and linking nowhere', () => {
        render(<FindingSeverityCountTag severity="HIGH" count={0} href="/findings?severity=HIGH" testId="count"/>)
        const tag = screen.getByTestId('count')
        expect(tag).toHaveTextContent('High 0')
        expect(tag).not.toHaveStyle({backgroundColor: '#d4380d'})
        expect(screen.queryByRole('link')).not.toBeInTheDocument()
    })

    it('has a short form, the initial of the severity and the count, the full name in its label', () => {
        render(<FindingSeverityCountTag severity="MEDIUM" count={4} short={true} testId="count"/>)
        const tag = screen.getByTestId('count')
        expect(tag).toHaveTextContent('M 4')
        expect(tag).toHaveAttribute('aria-label', '4 medium findings')
    })

    it('names the severity in a tooltip in its short form', async () => {
        const {fireEvent} = require("@testing-library/react")
        render(<FindingSeverityCountTag severity="CRITICAL" count={2} short={true} testId="count"/>)
        fireEvent.mouseEnter(screen.getByTestId('count'))
        expect(await screen.findByRole('tooltip')).toHaveTextContent('2 critical findings')
    })

    it('exposes its label to a screen reader when it links nowhere', () => {
        render(<FindingSeverityCountTag severity="HIGH" count={2} testId="count"/>)
        expect(screen.getByRole('img', {name: '2 high findings'})).toBeInTheDocument()
    })

    it('uses the singular for one finding', () => {
        render(<FindingSeverityCountTag severity="LOW" count={1} testId="count"/>)
        expect(screen.getByTestId('count')).toHaveAttribute('aria-label', '1 low finding')
    })
})
