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
import PromotionRunFieldValues from "@components/promotionRuns/PromotionRunFieldValues";

const fields = [
    {name: 'version', displayName: 'Version', type: 'TEXT'},
    {name: 'released', displayName: 'Released', type: 'BOOLEAN'},
    {name: 'url', displayName: 'URL', type: 'LINK'},
    {name: 'count', displayName: 'Count', type: 'INTEGER'},
]

describe('PromotionRunFieldValues', () => {

    it('renders "—" for null value', () => {
        render(
            <PromotionRunFieldValues
                fields={fields}
                fieldValues={[{name: 'version', value: null}]}
            />
        )
        expect(screen.getByText('—')).toBeInTheDocument()
    })

    it('renders "—" for undefined value', () => {
        render(
            <PromotionRunFieldValues
                fields={fields}
                fieldValues={[{name: 'version', value: undefined}]}
            />
        )
        expect(screen.getByText('—')).toBeInTheDocument()
    })

    it('renders BOOLEAN field type as plain string', () => {
        render(
            <PromotionRunFieldValues
                fields={fields}
                fieldValues={[{name: 'released', value: 'true'}]}
            />
        )
        expect(screen.getByText('true')).toBeInTheDocument()
        // Should not be inside an <a> element
        expect(screen.queryByRole('link')).not.toBeInTheDocument()
    })

    it('renders JS boolean value as plain string', () => {
        render(
            <PromotionRunFieldValues
                fields={[{name: 'flag', displayName: 'Flag', type: 'TEXT'}]}
                fieldValues={[{name: 'flag', value: false}]}
            />
        )
        expect(screen.getByText('false')).toBeInTheDocument()
    })

    it('renders LINK field type as an anchor with correct href', () => {
        render(
            <PromotionRunFieldValues
                fields={fields}
                fieldValues={[{name: 'url', value: 'https://example.com'}]}
            />
        )
        const link = screen.getByRole('link', {name: 'https://example.com'})
        expect(link).toBeInTheDocument()
        expect(link).toHaveAttribute('href', 'https://example.com')
        expect(link).toHaveAttribute('target', '_blank')
    })

    it('renders default text field inside a code element', () => {
        render(
            <PromotionRunFieldValues
                fields={fields}
                fieldValues={[{name: 'version', value: '1.2.3'}]}
            />
        )
        const codeEl = screen.getByText('1.2.3')
        expect(codeEl.tagName).toBe('CODE')
    })

    it('uses displayName as label when available', () => {
        render(
            <PromotionRunFieldValues
                fields={fields}
                fieldValues={[{name: 'count', value: '42'}]}
            />
        )
        expect(screen.getByText('Count')).toBeInTheDocument()
    })

    it('falls back to field name when no matching field definition exists', () => {
        render(
            <PromotionRunFieldValues
                fields={[]}
                fieldValues={[{name: 'unknownField', value: 'someValue'}]}
            />
        )
        expect(screen.getByText('unknownField')).toBeInTheDocument()
    })

    it('renders multiple field values', () => {
        render(
            <PromotionRunFieldValues
                fields={fields}
                fieldValues={[
                    {name: 'version', value: '2.0.0'},
                    {name: 'released', value: 'true'},
                    {name: 'url', value: 'https://example.com'},
                ]}
            />
        )
        expect(screen.getByText('2.0.0')).toBeInTheDocument()
        expect(screen.getByText('true')).toBeInTheDocument()
        expect(screen.getByRole('link', {name: 'https://example.com'})).toBeInTheDocument()
    })

    it('renders the "Field values" heading', () => {
        render(
            <PromotionRunFieldValues
                fields={fields}
                fieldValues={[{name: 'version', value: '1.0.0'}]}
            />
        )
        expect(screen.getByText('Field values')).toBeInTheDocument()
    })
})
