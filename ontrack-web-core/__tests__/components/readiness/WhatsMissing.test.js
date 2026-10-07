import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"
import {useContext} from "react"

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

import EventsContextProvider, {EventsContext} from "@components/common/EventsContext"
import WhatsMissing from "@components/readiness/WhatsMissing"
import {BUILD_PROMOTED, BUILD_VALIDATED} from "@components/builds/buildEvents"

/**
 * The "What's missing" control (#2023): one icon button with an accessible name, opening a popover
 * which asks `Build.readiness` for its target - on open only, and again on every validation or
 * promotion while it is open.
 */

const build = {id: '42', name: '42'}
const silver = {id: '21', name: 'SILVER'}

let answers

const readinessCalls = () => global.fetch.mock.calls
    .map(([, init]) => JSON.parse(init.body))
    .filter(({query}) => query.includes('BuildReadiness'))

/**
 * Stands for the validation and promotion dialogs of the page, which fire these events.
 */
function Firing() {
    const events = useContext(EventsContext)
    return <>
        <button onClick={() => events.fireEvent(BUILD_VALIDATED, {buildId: '42'})}>Fire validated</button>
        <button onClick={() => events.fireEvent(BUILD_PROMOTED, {buildId: '42'})}>Fire promoted</button>
    </>
}

const fire = (name) => fireEvent.click(screen.getByRole('button', {name}))

const renderControl = (props) => render(
    <EventsContextProvider>
        <Firing/>
        <WhatsMissing build={build} {...props}/>
    </EventsContextProvider>
)

describe('WhatsMissing', () => {

    beforeEach(() => {
        answers = [
            {ready: false, missing: [{kind: 'VALIDATION', name: 'UNIT.TESTS', message: 'Not validated'}]},
        ]
        global.fetch = jest.fn().mockImplementation(async () => {
            const readiness = answers.length > 1 ? answers.shift() : answers[0]
            return {
                ok: true,
                status: 200,
                json: async () => ({
                    build: {
                        id: '42',
                        readiness,
                        branch: {
                            validationStamps: [{id: '10', name: 'UNIT.TESTS', image: false}],
                            promotionLevels: [{id: '21', name: 'SILVER', image: false}],
                        },
                    },
                }),
            }
        })
    })

    afterEach(() => {
        delete global.fetch
    })

    it('is an icon button named after its target', () => {
        renderControl({promotionLevel: silver, label: 'SILVER'})
        const button = screen.getByRole('button', {name: "What's missing for SILVER"})
        expect(button).toHaveAttribute('aria-expanded', 'false')
    })

    it('asks nothing before it is opened', () => {
        renderControl({promotionLevel: silver, label: 'SILVER'})
        expect(readinessCalls()).toHaveLength(0)
    })

    it('asks the readiness of the build for the promotion level when opened, and lists what is missing', async () => {
        renderControl({promotionLevel: silver, label: 'SILVER'})
        fireEvent.click(screen.getByRole('button', {name: "What's missing for SILVER"}))
        expect(await screen.findByTestId('readiness-item-VALIDATION-UNIT.TESTS')).toHaveTextContent('Not validated')
        expect(screen.getByRole('button', {name: "What's missing for SILVER"})).toHaveAttribute('aria-expanded', 'true')
        const calls = readinessCalls()
        expect(calls).toHaveLength(1)
        expect(calls[0].variables).toEqual({buildId: 42, promotionLevel: 'SILVER', slotId: null})
    })

    it('asks the readiness of the build for a slot', async () => {
        renderControl({slot: {id: 'slot-1'}, label: 'production'})
        fireEvent.click(screen.getByRole('button', {name: "What's missing for production"}))
        await screen.findByTestId('readiness-item-VALIDATION-UNIT.TESTS')
        expect(readinessCalls()[0].variables).toEqual({buildId: 42, promotionLevel: null, slotId: 'slot-1'})
    })

    it('says "Ready" with the ready action when nothing is missing', async () => {
        answers = [{ready: true, missing: []}]
        renderControl({
            promotionLevel: silver,
            label: 'SILVER',
            readyAction: <button>Promote to SILVER</button>,
        })
        fireEvent.click(screen.getByRole('button', {name: "What's missing for SILVER"}))
        expect(await screen.findByTestId('readiness-ready')).toHaveTextContent('Ready')
        expect(screen.getByRole('button', {name: 'Promote to SILVER'})).toBeInTheDocument()
    })

    it('asks again when a validation or a promotion happens while it is open', async () => {
        answers = [
            {ready: false, missing: [{kind: 'VALIDATION', name: 'UNIT.TESTS', message: 'Not validated'}]},
            {ready: false, missing: [{kind: 'PROMOTION', name: 'BRONZE', message: 'Not promoted to BRONZE'}]},
            {ready: true, missing: []},
        ]
        renderControl({promotionLevel: silver, label: 'SILVER'})
        fireEvent.click(screen.getByRole('button', {name: "What's missing for SILVER"}))
        await screen.findByTestId('readiness-item-VALIDATION-UNIT.TESTS')

        fire('Fire validated')
        await screen.findByTestId('readiness-item-PROMOTION-BRONZE')

        fire('Fire promoted')
        expect(await screen.findByTestId('readiness-ready')).toHaveTextContent('Ready')
        expect(readinessCalls()).toHaveLength(3)
    })

    it('does not ask on a validation while it is closed', async () => {
        renderControl({promotionLevel: silver, label: 'SILVER'})
        fire('Fire validated')
        await waitFor(() => expect(readinessCalls()).toHaveLength(0))
    })

})
