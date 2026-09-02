import React from "react";
import {render, screen} from "@testing-library/react";

// Ant Design uses window.matchMedia for responsive features; jsdom doesn't provide it
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
import '@testing-library/jest-dom';

import StatePill, {PILL_STATES} from "@components/primitives/StatePill";

describe('StatePill', () => {

    it('renders its text', () => {
        render(<StatePill id="pill" state="success" text="3"/>)
        expect(screen.getByTestId('pill')).toHaveTextContent('3')
    })

    it('renders children when no text is given', () => {
        render(<StatePill id="pill" state="error"><span>Failed</span></StatePill>)
        expect(screen.getByTestId('pill')).toHaveTextContent('Failed')
    })

    it.each(PILL_STATES)('exposes the %s state as a data attribute', (state) => {
        render(<StatePill id="pill" state={state} text="x"/>)
        expect(screen.getByTestId('pill')).toHaveAttribute('data-state', state)
    })

    it.each(PILL_STATES)('paints the %s state with a colour of its own', (state) => {
        render(<StatePill id="pill" state={state} text="x"/>)
        const style = screen.getByTestId('pill').style
        expect(style.color).not.toBe('')
        expect(style.backgroundColor).not.toBe('')
        expect(style.borderColor).not.toBe('')
    })

    it('falls back to the neutral state for an unknown one', () => {
        render(<StatePill id="pill" state="not-a-state" text="x"/>)
        expect(screen.getByTestId('pill')).toHaveAttribute('data-state', 'neutral')
    })

    it('carries an accessible name when a title is given', () => {
        render(<StatePill id="pill" state="error" text="2" title="2 notification(s) have failed."/>)
        expect(screen.getByLabelText('2 notification(s) have failed.')).toBeInTheDocument()
    })

    it('does not animate by default', () => {
        render(<StatePill id="pill" state="processing" text="1"/>)
        expect(screen.getByTestId('pill')).not.toHaveClass('ot-pulse')
    })

    it('marks the pulsing pill with the motion-guarded class', () => {
        render(<StatePill id="pill" state="processing" text="1" pulse={true}/>)
        expect(screen.getByTestId('pill')).toHaveClass('ot-pulse')
    })

    it('links to the href when one is given', () => {
        render(<StatePill id="pill" state="success" text="3" href="/promotionRun/1"/>)
        expect(screen.getByRole('link')).toHaveAttribute('href', '/promotionRun/1')
    })

    it('renders no link when no href is given', () => {
        render(<StatePill id="pill" state="success" text="3"/>)
        expect(screen.queryByRole('link')).not.toBeInTheDocument()
    })
})
