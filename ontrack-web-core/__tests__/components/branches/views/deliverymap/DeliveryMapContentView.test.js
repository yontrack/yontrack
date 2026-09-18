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

// What the view does with an answer is under test, not how it gets one
jest.mock("../../../../../components/services/GraphQL", () => ({
    useQuery: jest.fn(),
}))

// React Flow does not render in jsdom, and none of these cases draws a map anyway
jest.mock("../../../../../components/branches/views/deliverymap/DeliveryMapGraph", () => ({
    __esModule: true,
    default: () => <div data-testid="delivery-map-graph"/>,
}))

// The slot drawer the checkpoints open (#1796): it mounts a router and a deploy dialog, neither of
// which this view has anything to say about. A pass-through, so that the view is still shown to
// wrap its content in it.
jest.mock("../../../../../components/extension/environments/deliverymap/DeliveryMapSlotDrawer", () => ({
    __esModule: true,
    default: ({children}) => <div data-testid="delivery-map-slot-drawer-host">{children}</div>,
}))

import {useQuery} from "@components/services/GraphQL";
import DeliveryMapContentView from "@components/branches/views/deliverymap/DeliveryMapContentView";

describe('delivery map content view', () => {

    const branch = {id: 22, name: "main"}

    const answering = ({data = null, error = undefined, finished = true}) => {
        useQuery.mockReturnValue({data, error, finished, loading: false})
    }

    it('says a fetch failed rather than claiming the branch has no map', () => {
        // A refresh which fails nulls the data, and the empty state would then make a false claim
        // about the branch - the one thing a view which refreshes itself must not do once a minute
        answering({error: "Network error"})
        render(<DeliveryMapContentView branch={branch}/>)
        expect(screen.getByTestId('delivery-map-error')).toHaveTextContent("could not be loaded")
        expect(screen.queryByTestId('delivery-map-empty')).not.toBeInTheDocument()
    })

    it('keeps its toolbar when a fetch failed, so the refresh can be turned off', () => {
        answering({error: "Network error"})
        render(<DeliveryMapContentView branch={branch}/>)
        expect(screen.getByTestId('delivery-map-header')).toBeInTheDocument()
        expect(screen.getByRole('button', {name: /Auto refresh/})).toBeInTheDocument()
    })

    it('wraps the map in the slot drawer host, so a slot checkpoint has somewhere to open', () => {
        answering({data: {checkpoints: [], edges: [], head: null}})
        render(<DeliveryMapContentView branch={branch}/>)
        const host = screen.getByTestId('delivery-map-slot-drawer-host')
        expect(host).toContainElement(screen.getByTestId('delivery-map-header'))
    })

    it('says the branch has nothing on its map when that is what came back', () => {
        answering({data: {checkpoints: [], edges: [], head: null}})
        render(<DeliveryMapContentView branch={branch}/>)
        expect(screen.getByTestId('delivery-map-empty')).toBeInTheDocument()
        expect(screen.queryByTestId('delivery-map-error')).not.toBeInTheDocument()
    })

    it('offers its toolbar above the empty state too', () => {
        // A control which comes and goes with the data is a control the reader cannot count on
        answering({data: {checkpoints: [], edges: [], head: null}})
        render(<DeliveryMapContentView branch={branch}/>)
        expect(screen.getByTestId('delivery-map-header')).toBeInTheDocument()
    })

    it('draws the map when there is one', () => {
        answering({
            data: {
                checkpoints: [{id: 'promotion-level:12', type: 'promotion-level', name: "SILVER"}],
                edges: [],
                head: {id: 42, name: "20260901-1", displayName: "20260901-1"},
            },
        })
        render(<DeliveryMapContentView branch={branch}/>)
        expect(screen.getByTestId('delivery-map-graph')).toBeInTheDocument()
        expect(screen.getByTestId('delivery-map-header')).toHaveTextContent("20260901-1")
    })

    it('shows nothing but the loader before the first answer', () => {
        answering({finished: false})
        render(<DeliveryMapContentView branch={branch}/>)
        expect(screen.queryByTestId('delivery-map-header')).not.toBeInTheDocument()
        expect(screen.queryByTestId('delivery-map-empty')).not.toBeInTheDocument()
    })

})
