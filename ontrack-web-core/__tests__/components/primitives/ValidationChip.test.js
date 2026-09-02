import React from "react";
import {fireEvent, render, screen, waitFor} from "@testing-library/react";

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
import ValidationChip from "@components/primitives/ValidationChip";
import {
    DEFAULT_VALIDATION_DATA_TYPE_GLYPH,
    getValidationDataTypeGlyph,
    VALIDATION_DATA_TYPE_GLYPHS,
} from "@components/primitives/ValidationDataTypeGlyphs";

const DATA_URL = 'data:image/png;base64,AAAA'

beforeEach(() => {
    global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({dataURL: DATA_URL}),
    })
})

const stamp = (overrides = {}) => ({
    id: 7,
    name: 'SMOKE',
    image: false,
    ...overrides,
})

const renderChip = (props) => render(
    <EventsContext.Provider value={{fireEvent: jest.fn(), subscribeToEvent: jest.fn()}}>
        <ValidationChip id="chip" {...props}/>
    </EventsContext.Provider>
)

describe('ValidationChip', () => {

    describe('the stamp icon', () => {

        it('uses the uploaded image when there is one', async () => {
            renderChip({validationStamp: stamp({image: true})})
            await waitFor(() => {
                expect(screen.getByTestId('validation-stamp-image-7')).toHaveAttribute('src', DATA_URL)
            })
        })

        it('falls back to a glyph for the data type when there is none', () => {
            renderChip({
                validationStamp: stamp({
                    dataType: {descriptor: {id: 'net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType'}},
                }),
            })
            expect(screen.getByTestId('validation-stamp-glyph-7')).toBeInTheDocument()
        })

        // A stamp with no data type is the common case, so this path has to be
        // as good as the others rather than an apology.
        it('falls back to a glyph for a stamp with no data type at all', () => {
            renderChip({validationStamp: stamp()})
            expect(screen.getByTestId('validation-stamp-glyph-7')).toBeInTheDocument()
        })

        it('never uses the initials tile on a chip', () => {
            renderChip({validationStamp: stamp()})
            expect(screen.queryByTestId('validation-stamp-icon-7')).not.toBeInTheDocument()
        })
    })

    describe('the state', () => {

        it('names the state so colour is never the only carrier', () => {
            renderChip({validationStamp: stamp(), statusID: {id: 'PASSED', name: 'Passed'}})
            expect(screen.getByText('Passed')).toBeInTheDocument()
        })

        it('exposes the state as a data attribute', () => {
            renderChip({validationStamp: stamp(), statusID: {id: 'FAILED', name: 'Failed'}})
            expect(screen.getByTestId('chip')).toHaveAttribute('data-status', 'FAILED')
        })

        it.each([
            ['PASSED', 'Passed'],
            ['FAILED', 'Failed'],
            ['WARNING', 'Warning'],
            ['FIXED', 'Fixed'],
            ['EXPLAINED', 'Explained'],
            ['INVESTIGATING', 'Investigating'],
            ['DEFECTIVE', 'Defective'],
            ['INTERRUPTED', 'Interrupted'],
        ])('carries %s in colour', (id, name) => {
            renderChip({validationStamp: stamp(), statusID: {id, name}})
            expect(screen.getByTestId('chip').style.borderColor).not.toBe('')
        })

        it('gives different states different colours', () => {
            const {unmount} = renderChip({validationStamp: stamp(), statusID: {id: 'PASSED', name: 'Passed'}})
            const passed = screen.getByTestId('chip').style.borderColor
            unmount()
            renderChip({validationStamp: stamp(), statusID: {id: 'FAILED', name: 'Failed'}})
            expect(screen.getByTestId('chip').style.borderColor).not.toBe(passed)
        })

        it('stays neutral, and does not invent a state, when there is no run', () => {
            renderChip({validationStamp: stamp()})
            expect(screen.getByTestId('chip')).toHaveAttribute('data-status', 'NONE')
        })

        it('names an unknown state by its id rather than leaving it unnamed', () => {
            renderChip({validationStamp: stamp(), statusID: {id: 'BRAND_NEW'}})
            expect(screen.getByText('BRAND_NEW')).toBeInTheDocument()
        })

        it('can be asked for the mark alone', () => {
            renderChip({
                validationStamp: stamp(),
                statusID: {id: 'PASSED', name: 'Passed'},
                displayStatus: false,
            })
            expect(screen.queryByText('Passed')).not.toBeInTheDocument()
            // ... but the state must still be announced.
            expect(screen.getByTestId('chip')).toHaveAccessibleName('SMOKE — Passed')
        })

        // The pill is not a coloured rectangle with a word in it: it repeats the
        // status's own glyph, so the state survives greyscale.
        it('repeats the status glyph inside the pill', () => {
            const {container} = renderChip({
                validationStamp: stamp(),
                statusID: {id: 'PASSED', name: 'Passed'},
            })
            expect(container.querySelector('[data-testid="chip-status"] svg')).toBeInTheDocument()
        })

        it('draws no glyph for a state that has none', () => {
            // 'no run' is a glyphless mark on purpose - the absence must never
            // borrow a real status's glyph.
            const {container} = renderChip({validationStamp: stamp()})
            expect(container.querySelector('[data-testid="chip-status"] svg')).not.toBeInTheDocument()
        })
    })

    describe('the stamp name', () => {

        it('is shown by default', () => {
            renderChip({validationStamp: stamp()})
            expect(screen.getByText('SMOKE')).toBeInTheDocument()
        })

        it('can be suppressed for dense rows', () => {
            renderChip({validationStamp: stamp(), displayText: false})
            expect(screen.queryByText('SMOKE')).not.toBeInTheDocument()
        })
    })

    // A label here would replace the very text that already says it: `role="img"`
    // prunes its own subtree, so the name and the status would be hidden behind
    // a label repeating them.
    it('leaves itself unlabelled while it says everything in text', () => {
        renderChip({validationStamp: stamp(), statusID: {id: 'FAILED', name: 'Failed'}})
        const chip = screen.getByTestId('chip')
        expect(chip).not.toHaveAttribute('aria-label')
        expect(chip).not.toHaveAttribute('role')
        expect(chip).toHaveTextContent('SMOKE')
        expect(chip).toHaveTextContent('Failed')
    })

    it('says so when there is no run yet', () => {
        renderChip({validationStamp: stamp()})
        expect(screen.getByTestId('chip')).toHaveTextContent('No run')
    })

    it('names itself once it stops saying everything in text', () => {
        renderChip({validationStamp: stamp(), statusID: {id: 'FAILED', name: 'Failed'}, displayText: false})
        expect(screen.getByTestId('chip')).toHaveAccessibleName('SMOKE — Failed')
    })

    describe('when it is clickable', () => {

        it('is a button, not an image', () => {
            renderChip({validationStamp: stamp(), statusID: {id: 'PASSED', name: 'Passed'}, onClick: jest.fn()})
            expect(screen.getByRole('button')).toHaveAccessibleName('SMOKE — Passed')
        })

        it('is reachable from the keyboard', () => {
            renderChip({validationStamp: stamp(), onClick: jest.fn()})
            expect(screen.getByRole('button')).toHaveAttribute('tabindex', '0')
        })

        it.each(['Enter', ' '])('activates on %s', (key) => {
            const onClick = jest.fn()
            renderChip({validationStamp: stamp(), onClick})
            fireEvent.keyDown(screen.getByRole('button'), {key})
            expect(onClick).toHaveBeenCalledTimes(1)
        })

        it('activates on a click', () => {
            const onClick = jest.fn()
            renderChip({validationStamp: stamp(), onClick})
            fireEvent.click(screen.getByRole('button'))
            expect(onClick).toHaveBeenCalledTimes(1)
        })
    })

    it('links when given an href', () => {
        renderChip({validationStamp: stamp(), href: '/validationRun/3'})
        expect(screen.getByRole('link')).toHaveAttribute('href', '/validationRun/3')
    })

    it('renders nothing without a stamp', () => {
        const {container} = renderChip({validationStamp: null})
        expect(container).toBeEmptyDOMElement()
    })
})

describe('validation data type glyphs', () => {

    it('resolves each known data type to its own glyph', () => {
        const glyphs = Object.keys(VALIDATION_DATA_TYPE_GLYPHS).map(shortId =>
            getValidationDataTypeGlyph(`net.nemerosa.ontrack.extension.${shortId}`)
        )
        expect(new Set(glyphs).size).toBe(glyphs.length)
        glyphs.forEach(glyph => expect(glyph).not.toBe(DEFAULT_VALIDATION_DATA_TYPE_GLYPH))
    })

    it('accepts an already-shortened id', () => {
        expect(getValidationDataTypeGlyph('general.validation.CHMLValidationDataType'))
            .toBe(getValidationDataTypeGlyph('net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType'))
    })

    it('falls back for a data type it has never heard of', () => {
        expect(getValidationDataTypeGlyph('some.new.DataType')).toBe(DEFAULT_VALIDATION_DATA_TYPE_GLYPH)
    })

    it('falls back when there is no data type', () => {
        expect(getValidationDataTypeGlyph(undefined)).toBe(DEFAULT_VALIDATION_DATA_TYPE_GLYPH)
    })
})
