import React from "react";
import {render, screen, waitFor} from "@testing-library/react";

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
import PromotionRunSummary from "@components/promotionRuns/PromotionRunSummary";

// `ProxyImage` fetches the medal from the REST API and renders nothing until it answers.
global.fetch = jest.fn(() => Promise.resolve({
    ok: true,
    json: () => Promise.resolve({dataURL: 'data:image/png;base64,AAAA'}),
}))

const run = {
    id: 100,
    creation: {user: 'admin', time: '2024-01-01T10:00:00Z'},
    promotionLevel: {
        id: 1,
        name: 'BRONZE',
        image: true,
        fields: [],
        branch: {id: 10, name: 'main', project: {id: 20, name: 'project'}},
    },
    build: {
        id: 30,
        name: '1.0.0',
        branch: {id: 10, name: 'main', displayName: 'main', project: {id: 20, name: 'project'}},
    },
    fieldValues: [],
}

describe('PromotionRunSummary', () => {

    it('renders the promotion level icon exactly once', async () => {
        render(<PromotionRunSummary run={run}/>)
        await waitFor(() => {
            expect(screen.getAllByTestId('promotion-level-image-1').length).toBeGreaterThan(0)
        })
        expect(screen.getAllByTestId('promotion-level-image-1')).toHaveLength(1)
    })

})
