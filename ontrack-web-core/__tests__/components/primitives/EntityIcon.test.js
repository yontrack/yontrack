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

import {EventsContext} from "@components/common/EventsContext";
import EntityIcon from "@components/primitives/EntityIcon";

const DATA_URL = 'data:image/png;base64,AAAA'

beforeEach(() => {
    global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({dataURL: DATA_URL}),
    })
})

const renderIcon = (props) => render(
    <EventsContext.Provider value={{fireEvent: jest.fn(), subscribeToEvent: jest.fn()}}>
        <EntityIcon {...props}/>
    </EventsContext.Provider>
)

describe('EntityIcon', () => {

    describe('promotion level', () => {

        const withImage = {id: 12, name: 'BRONZE', image: true}
        const withoutImage = {id: 12, name: 'BRONZE', image: false}

        it('renders the uploaded image when there is one', async () => {
            renderIcon({kind: 'promotionLevel', entity: withImage})
            await waitFor(() => {
                expect(screen.getByTestId('promotion-level-image-12')).toHaveAttribute('src', DATA_URL)
            })
        })

        it('renders the generated fallback when there is none', () => {
            renderIcon({kind: 'promotionLevel', entity: withoutImage})
            expect(screen.getByTestId('promotion-level-icon-12')).toHaveTextContent('BR')
            expect(screen.queryByTestId('promotion-level-image-12')).not.toBeInTheDocument()
        })

        it('does not fetch an image the entity does not have', () => {
            renderIcon({kind: 'promotionLevel', entity: withoutImage})
            expect(global.fetch).not.toHaveBeenCalled()
        })
    })

    describe('validation stamp', () => {

        const withImage = {id: 7, name: 'SMOKE', image: true}
        const withoutImage = {id: 7, name: 'SMOKE', image: false}

        it('renders the uploaded image when there is one', async () => {
            renderIcon({kind: 'validationStamp', entity: withImage})
            await waitFor(() => {
                expect(screen.getByTestId('validation-stamp-image-7')).toHaveAttribute('src', DATA_URL)
            })
        })

        it('renders the generated fallback when there is none', () => {
            renderIcon({kind: 'validationStamp', entity: withoutImage})
            expect(screen.getByTestId('validation-stamp-icon-7')).toHaveTextContent('SM')
        })
    })

    it('sizes the uploaded image', async () => {
        renderIcon({kind: 'promotionLevel', entity: {id: 1, name: 'GOLD', image: true}, size: 32})
        await waitFor(() => {
            const style = screen.getByTestId('promotion-level-image-1').style
            expect(style.width).toBe('32px')
            expect(style.height).toBe('32px')
        })
    })

    // The fallback ignoring `size` is the bug this primitive exists to stop
    // repeating: a 32px medal must not collapse to a 24px tile when its owner
    // never uploaded an image.
    it('sizes the fallback the same as the image it replaces', () => {
        renderIcon({kind: 'validationStamp', entity: {id: 1, name: 'SMOKE', image: false}, size: 32})
        expect(screen.getByTestId('validation-stamp-icon-1').style.width).toBe('32px')
    })

    it('uses a caller-supplied fallback instead of the initials', () => {
        renderIcon({
            kind: 'validationStamp',
            entity: {id: 1, name: 'SMOKE', image: false},
            fallback: <span data-testid="custom-fallback">custom</span>,
        })
        expect(screen.getByTestId('custom-fallback')).toBeInTheDocument()
        expect(screen.queryByTestId('validation-stamp-icon-1')).not.toBeInTheDocument()
    })

    it('ignores a caller-supplied fallback when there is an image', async () => {
        renderIcon({
            kind: 'validationStamp',
            entity: {id: 1, name: 'SMOKE', image: true},
            fallback: <span data-testid="custom-fallback">custom</span>,
        })
        await waitFor(() => {
            expect(screen.getByTestId('validation-stamp-image-1')).toBeInTheDocument()
        })
        expect(screen.queryByTestId('custom-fallback')).not.toBeInTheDocument()
    })

    it('renders nothing at all without an entity', () => {
        const {container} = renderIcon({kind: 'promotionLevel', entity: null})
        expect(container).toBeEmptyDOMElement()
    })

    it('falls back rather than crashing on an unknown kind', () => {
        renderIcon({kind: 'nonesuch', entity: {id: 1, name: 'SMOKE', image: true}})
        expect(screen.getByLabelText('SMOKE')).toBeInTheDocument()
        expect(global.fetch).not.toHaveBeenCalled()
    })
})
