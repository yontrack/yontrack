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

import GeneratedIcon, {generateInitials} from "@components/common/icons/GeneratedIcon";

// WCAG 2.x relative luminance and contrast ratio, recomputed here rather than
// imported: the point is to check the pair the component actually renders, not
// to ask `getTextColorForBackground` whether it agrees with itself.
// Ref: https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
const luminance = (color) => {
    const linearize = (c) => {
        const v = c / 255
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)
    }
    // jsdom normalises both `#rrggbb` and a colour name to `rgb(r, g, b)`.
    const [r, g, b] = color.match(/\d+/g).map(Number)
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

const contrastRatio = (a, b) => {
    const la = luminance(a), lb = luminance(b)
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

describe('GeneratedIcon', () => {

    it('name initials', () => {
        expect(generateInitials("acceptance")).toEqual('AC')
        expect(generateInitials("staging-live")).toEqual('SL')
        expect(generateInitials("acceptance-pilot")).toEqual('AP')
        expect(generateInitials("production-live")).toEqual('PL')
        expect(generateInitials("production")).toEqual('PR')
    })

    it('renders the initials', () => {
        render(<GeneratedIcon id="icon" name="staging-live" colorIndex={4}/>)
        expect(screen.getByTestId('icon')).toHaveTextContent('SL')
    })

    it('renders nothing without a name', () => {
        render(<GeneratedIcon id="icon" colorIndex={4}/>)
        expect(screen.queryByTestId('icon')).not.toBeInTheDocument()
    })

    it('names itself for screen readers', () => {
        render(<GeneratedIcon id="icon" name="staging-live" colorIndex={4}/>)
        expect(screen.getByLabelText('staging-live')).toBeInTheDocument()
    })

    // The fallback is drawn at 16px in dense tables and at 22-32px on the medals
    // and chips. Two capitals have to fit at every one of those sizes, so the
    // glyph is sized as a fraction of the box rather than fixed.
    it.each([16, 22, 24, 28, 32])('fills a %spx box exactly', (size) => {
        render(<GeneratedIcon id="icon" name="staging-live" colorIndex={4} size={size}/>)
        const style = screen.getByTestId('icon').style
        expect(style.width).toBe(`${size}px`)
        expect(style.height).toBe(`${size}px`)
    })

    it.each([22, 24, 28, 32])('keeps two capitals inside a %spx box', (size) => {
        render(<GeneratedIcon id="icon" name="staging-live" colorIndex={4} size={size}/>)
        const fontSize = parseFloat(screen.getByTestId('icon').style.fontSize)
        // Two capitals at this weight run about 1.4x the font size wide; wider
        // than the box and they clip.
        expect(fontSize * 1.4).toBeLessThanOrEqual(size)
        // ... and small enough to clip is not the same as large enough to read.
        expect(fontSize).toBeGreaterThanOrEqual(size * 0.35)
    })

    it('never shrinks the glyph below a readable floor', () => {
        render(<GeneratedIcon id="icon" name="staging-live" colorIndex={4} size={12}/>)
        expect(parseFloat(screen.getByTestId('icon').style.fontSize)).toBeGreaterThanOrEqual(9)
    })

    // The initials sit on a generated hue, so the foreground is picked by WCAG
    // contrast rather than by a brightness threshold (#1815). Colour index 198
    // is the worst of the 720 distinct generated hues, and the one the previous
    // rule got wrong at 3.91:1.
    it.each([0, 4, 15, 20, 109, 198, 12345])(
        'draws initials that clear WCAG AA on generated colour %i',
        (colorIndex) => {
            render(<GeneratedIcon id="icon" name="staging-live" colorIndex={colorIndex}/>)
            const style = screen.getByTestId('icon').style
            expect(contrastRatio(style.color, style.backgroundColor)).toBeGreaterThanOrEqual(4.5)
        }
    )

    it('greys out a disabled icon', () => {
        render(<GeneratedIcon id="icon" name="staging-live" colorIndex={4} disabled={true}/>)
        expect(screen.getByTestId('icon').style.filter).toContain('grayscale')
    })
})
