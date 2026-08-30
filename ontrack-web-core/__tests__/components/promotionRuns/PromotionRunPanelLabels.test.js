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
import {PromotionRunPanelLabel} from "@components/promotionRuns/PromotionRunPanelLabels";

describe('PromotionRunPanelLabel', () => {

    it('shows the label and its count', () => {
        render(<PromotionRunPanelLabel label="Notifications" count={5}/>)
        expect(screen.getByText('Notifications')).toBeInTheDocument()
        expect(screen.getByText('(5)')).toBeInTheDocument()
    })

    it('shows a zero count', () => {
        render(<PromotionRunPanelLabel label="Notifications" count={0}/>)
        expect(screen.getByText('(0)')).toBeInTheDocument()
    })

    it('shows no count when it is not known', () => {
        render(<PromotionRunPanelLabel label="Notifications" count={null}/>)
        expect(screen.getByText('Notifications')).toBeInTheDocument()
        expect(screen.queryByTestId('panel-count-Notifications')).not.toBeInTheDocument()
    })

    it('shows no count when it is undefined', () => {
        render(<PromotionRunPanelLabel label="Auto-versioning trail"/>)
        expect(screen.getByText('Auto-versioning trail')).toBeInTheDocument()
        expect(screen.queryByTestId('panel-count-Auto-versioning trail')).not.toBeInTheDocument()
    })
})
