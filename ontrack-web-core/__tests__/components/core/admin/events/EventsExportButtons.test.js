import "@testing-library/jest-dom"
import {render, screen, waitFor} from "@testing-library/react"
import EventsExportButtons from "@components/core/admin/events/EventsExportButtons"

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

/**
 * The buttons of the events page downloading the filtered events, and the warning shown when the
 * export would be truncated.
 */
describe('EventsExportButtons', () => {

    const answer = (eventsExport) => {
        global.fetch = jest.fn().mockImplementation(async () => ({
            ok: true,
            status: 200,
            json: async () => ({eventsExport}),
        }))
    }

    afterEach(() => {
        delete global.fetch
    })

    it('downloads the events of the applied filter', async () => {
        answer({maxRows: 100000, truncated: false})
        render(<EventsExportButtons filterFormData={{user: "adm", eventTypes: ["new_build"]}}/>)

        expect(screen.getByRole("link", {name: /Download CSV/})).toHaveAttribute(
            "href", "/api/protected/downloads/events/export?format=csv&user=adm&eventTypes=new_build"
        )
        expect(screen.getByRole("link", {name: /Download JSON/})).toHaveAttribute(
            "href", "/api/protected/downloads/events/export?format=json&user=adm&eventTypes=new_build"
        )
        expect(screen.getByRole("link", {name: /Download CSV/})).toHaveAttribute("download")

        await waitFor(() => expect(global.fetch).toHaveBeenCalled())
        const {variables} = JSON.parse(global.fetch.mock.calls[0][1].body)
        expect(variables).toEqual({user: "adm", eventTypes: ["new_build"]})
        expect(screen.queryByText(/most recent matching events/)).not.toBeInTheDocument()
    })

    it('downloads the events of the actor of the applied filter', async () => {
        answer({maxRows: 100000, truncated: false})
        render(<EventsExportButtons filterFormData={{actor: "agent"}}/>)

        expect(screen.getByRole("link", {name: /Download CSV/})).toHaveAttribute(
            "href", "/api/protected/downloads/events/export?format=csv&actor=agent"
        )
        await waitFor(() => expect(global.fetch).toHaveBeenCalled())
        const {variables} = JSON.parse(global.fetch.mock.calls[0][1].body)
        expect(variables).toEqual({actor: "agent"})
    })

    it('warns when the export would be truncated', async () => {
        answer({maxRows: 3, truncated: true})
        render(<EventsExportButtons filterFormData={{}}/>)

        await waitFor(() => expect(screen.getByText(
            "Only the 3 most recent matching events will be exported: narrow the filter to get them all."
        )).toBeInTheDocument())
    })

})
