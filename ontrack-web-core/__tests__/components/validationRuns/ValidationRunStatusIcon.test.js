import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import ValidationRunStatusIcon from "@components/validationRuns/ValidationRunStatusIcon"
import {SIZE_VARIANTS, VALIDATION_RUN_STATUS_CONFIG} from "@components/validationRuns/ValidationRunStatusConfig"
import {ThemeContext} from "@components/providers/ThemeProvider"

const PASSED = {id: 'PASSED', name: 'Passed'}
const FIXED = {id: 'FIXED', name: 'Fixed'}

const mark = (name) => screen.getByRole('img', {name})

describe('ValidationRunStatusIcon', () => {

    describe('accessible name', () => {

        it('exposes the status name as the accessible name, with no interaction', () => {
            render(<ValidationRunStatusIcon statusID={PASSED}/>)
            // Present on first render - not conditional on hover or focus.
            expect(mark('Passed')).toHaveAttribute('aria-label', 'Passed')
        })

        it('keeps the accessible name even when the tooltip is suppressed', () => {
            // A tooltip must never be the ONLY carrier of the name.
            render(<ValidationRunStatusIcon statusID={PASSED} tooltip={false}/>)
            const el = mark('Passed')
            expect(el).toHaveAttribute('aria-label', 'Passed')
            expect(el).not.toHaveAttribute('title')
        })

        it('falls back to the raw id when the status has no name', () => {
            render(<ValidationRunStatusIcon statusID={{id: 'NONE'}}/>)
            expect(mark('NONE')).toBeInTheDocument()
        })
    })

    describe('glyph is decorative', () => {

        it('hides the glyph from assistive tech so it cannot compete with the label', () => {
            render(<ValidationRunStatusIcon statusID={PASSED}/>)
            const svg = mark('Passed').querySelector('svg')
            expect(svg).toHaveAttribute('aria-hidden', 'true')
        })

        it('renders no <title> inside the glyph (would be a second accessible name)', () => {
            render(<ValidationRunStatusIcon statusID={PASSED}/>)
            expect(mark('Passed').querySelector('svg title')).toBeNull()
        })
    })

    describe('tier is carried by shape, not colour alone', () => {

        it('renders a root status as a filled disc', () => {
            render(<ValidationRunStatusIcon statusID={PASSED}/>)
            const el = mark('Passed')
            expect(el).toHaveAttribute('data-tier', 'root')
            expect(el.style.borderRadius).toEqual('50%')
            // Filled, no outline.
            expect(el.style.backgroundColor).not.toEqual('')
            expect(el.style.backgroundColor).not.toEqual('transparent')
            expect(el.style.borderWidth).toEqual('')
        })

        it('renders a derived status as an outlined squircle', () => {
            render(<ValidationRunStatusIcon statusID={FIXED}/>)
            const el = mark('Fixed')
            expect(el).toHaveAttribute('data-tier', 'derived')
            // Squircle, clearly distinguishable from a disc at small sizes.
            expect(el.style.borderRadius).toEqual('9px')
            expect(el.style.borderWidth).toEqual('1.5px')
            expect(el.style.backgroundColor).toEqual('transparent')
        })

        it('distinguishes the tiers by shape even if colour is ignored', () => {
            const {container: root} = render(<ValidationRunStatusIcon statusID={PASSED}/>)
            const {container: derived} = render(<ValidationRunStatusIcon statusID={FIXED}/>)
            const radius = (c) => c.querySelector('[role="img"]').style.borderRadius
            expect(radius(root)).not.toEqual(radius(derived))
        })

        it('treats an unknown status as derived rather than as an outcome', () => {
            render(<ValidationRunStatusIcon statusID={{id: 'SOMETHING_NEW'}}/>)
            expect(mark('SOMETHING_NEW')).toHaveAttribute('data-tier', 'derived')
        })
    })

    describe('absence of a status is not a status', () => {

        it('renders NONE as an empty dashed slot with no glyph', () => {
            render(<ValidationRunStatusIcon statusID={{id: 'NONE', name: 'No status'}}/>)
            const el = mark('No status')
            expect(el).toHaveAttribute('data-tier', 'empty')
            expect(el.style.borderStyle).toEqual('dashed')
            // No glyph at all - the hollow shape is the mark.
            expect(el.querySelector('svg')).toBeNull()
        })

        it('does not let an empty slot look like any real status', () => {
            // Regression: NONE once drew a pause glyph, making an empty cell
            // pixel-identical to a real INTERRUPTED run.
            const {container: none} = render(<ValidationRunStatusIcon statusID={{id: 'NONE'}}/>)
            const {container: interrupted} =
                render(<ValidationRunStatusIcon statusID={{id: 'INTERRUPTED', name: 'Interrupted'}}/>)

            const el = (c) => c.querySelector('[role="img"]')
            // Differ in outline style...
            expect(el(none).style.borderStyle).not.toEqual(el(interrupted).style.borderStyle)
            // ...and one has a glyph while the other has none.
            expect(el(none).querySelector('svg')).toBeNull()
            expect(el(interrupted).querySelector('svg')).not.toBeNull()
        })

        it('renders an empty slot when no status id is supplied at all', () => {
            render(<ValidationRunStatusIcon statusID={{}}/>)
            expect(mark('Unknown status')).toHaveAttribute('data-tier', 'empty')
        })

        it('does not reuse a real status glyph for an unknown status', () => {
            const glyphOf = (statusID) => {
                const {container} = render(<ValidationRunStatusIcon statusID={statusID}/>)
                return container.querySelector('[role="img"] svg')?.innerHTML
            }
            const unknown = glyphOf({id: 'SOMETHING_NEW'})
            expect(unknown).toBeTruthy()
            // Must not impersonate any status in the table.
            Object.keys(VALIDATION_RUN_STATUS_CONFIG).forEach(key => {
                expect(unknown).not.toEqual(glyphOf({id: key}))
            })
        })
    })

    describe('size variants', () => {

        it('defaults to the compact mark with a tooltip as an extra convenience', () => {
            render(<ValidationRunStatusIcon statusID={PASSED}/>)
            const el = mark('Passed')
            expect(el.style.width).toEqual(`${SIZE_VARIANTS.compact.box}px`)
            expect(el).toHaveAttribute('title', 'Passed')
        })

        it('renders the full mark larger and without a tooltip, since a label sits beside it', () => {
            render(<ValidationRunStatusIcon statusID={PASSED} variant="full"/>)
            const el = mark('Passed')
            expect(el.style.width).toEqual(`${SIZE_VARIANTS.full.box}px`)
            expect(el).not.toHaveAttribute('title')
        })

        it('thickens the stroke at compact size to keep detail legible', () => {
            render(<ValidationRunStatusIcon statusID={FIXED}/>)
            const svg = mark('Fixed').querySelector('svg')
            expect(svg).toHaveAttribute('stroke-width', String(SIZE_VARIANTS.compact.strokeWidth))
            expect(SIZE_VARIANTS.compact.strokeWidth).toBeGreaterThan(SIZE_VARIANTS.full.strokeWidth)
        })
    })

    describe('colours come from the config table', () => {

        it('fills a root disc with the configured fill colour', () => {
            render(<ValidationRunStatusIcon statusID={PASSED}/>)
            const el = mark('Passed')
            // #3B6D11 -> jsdom normalises to rgb()
            expect(el.style.backgroundColor).toEqual('rgb(59, 109, 17)')
            expect(VALIDATION_RUN_STATUS_CONFIG.PASSED.fill).toEqual('#3B6D11')
        })

        it('uses the light colour by default and the dark colour on request', () => {
            const {container: light} = render(<ValidationRunStatusIcon statusID={FIXED}/>)
            const {container: dark} = render(<ValidationRunStatusIcon statusID={FIXED} mode="dark"/>)
            // Compared against the config rather than literals, so the test
            // proves the component reads the table instead of hardcoding.
            const expected = VALIDATION_RUN_STATUS_CONFIG.FIXED.color
            const borderColor = (c) => c.querySelector('[role="img"]').style.borderColor.toLowerCase()
            expect(borderColor(light)).toEqual(expected.light.toLowerCase())
            expect(borderColor(dark)).toEqual(expected.dark.toLowerCase())
            expect(expected.light).not.toEqual(expected.dark)
        })
    })

    describe('the palette side follows the theme', () => {

        const inTheme = (resolvedTheme, element) => render(
            <ThemeContext.Provider value={{resolvedTheme, isDark: resolvedTheme === 'dark'}}>
                {element}
            </ThemeContext.Provider>
        )

        const borderColor = (c) => c.querySelector('[role="img"]').style.borderColor.toLowerCase()
        const expected = VALIDATION_RUN_STATUS_CONFIG.FIXED.color

        it('picks the dark side of the palette under a dark theme', () => {
            // No `mode` prop: the call sites do not thread the theme through.
            const {container} = inTheme('dark', <ValidationRunStatusIcon statusID={FIXED}/>)
            expect(borderColor(container)).toEqual(expected.dark.toLowerCase())
        })

        it('picks the light side under a light theme', () => {
            const {container} = inTheme('light', <ValidationRunStatusIcon statusID={FIXED}/>)
            expect(borderColor(container)).toEqual(expected.light.toLowerCase())
        })

        it('lets an explicit mode pin the mark against the theme', () => {
            // For a mark that must stay readable on a fixed-light surface.
            const {container} = inTheme('dark', <ValidationRunStatusIcon statusID={FIXED} mode="light"/>)
            expect(borderColor(container)).toEqual(expected.light.toLowerCase())
        })

        it('falls back to light outside any provider, rather than throwing', () => {
            const {container} = render(<ValidationRunStatusIcon statusID={FIXED}/>)
            expect(borderColor(container)).toEqual(expected.light.toLowerCase())
        })
    })
})
