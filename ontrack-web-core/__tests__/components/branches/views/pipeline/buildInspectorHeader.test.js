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

let queryResult = {data: null, loading: false, error: null, finished: true}

jest.mock("../../../../../components/services/GraphQL", () => ({
    useQuery: () => queryResult,
}))

// The panels fetch and act on their own; what is under test is the header above them
jest.mock("../../../../../components/branches/views/pipeline/BuildInspectorPromotions", () => () => null)
jest.mock("../../../../../components/branches/views/pipeline/BuildInspectorValidations", () => () => null)

import BuildInspector from "@components/branches/views/pipeline/BuildInspector";

const setBuild = (build, overrides = {}) => {
    queryResult = {data: build, loading: false, error: null, finished: true, ...overrides}
}

const build = (overrides = {}) => ({
    id: "1",
    name: "20260901055547-36",
    displayName: "20260901055547-36",
    promotionRuns: [],
    validations: [],
    branch: {id: "1", promotionLevels: []},
    ...overrides,
})

/**
 * The inspector's header.
 *
 * The panel is deliberately partial - promotions and validations only - so it owes the reader two
 * things the grid alone never gave: which build it is describing, and a way out to the build's own
 * page, which carries the properties, links, run info and change log this panel never will.
 */
describe('the build inspector header', () => {

    it('names the inspected build and links to its page', () => {
        setBuild(build({displayName: "1.4.4"}))
        render(<BuildInspector buildId="1" showValidations={true}/>)

        const link = screen.getByRole('link', {name: "1.4.4"})
        expect(link).toBeVisible()
        expect(link).toHaveAttribute('href', '/build/1')
    })

    it('shows the build name too, when the version is not already it', () => {
        // The version is what a human reads, the name is the identity they need for an API call,
        // so a released build is worth both lines
        setBuild(build({displayName: "1.4.4"}))
        render(<BuildInspector buildId="1" showValidations={true}/>)

        expect(screen.getByRole('link', {name: "1.4.4"})).toBeVisible()
        expect(screen.getByText("20260901055547-36")).toBeVisible()
    })

    it('offers a copy button for the version', () => {
        // The affordance the release decoration used to carry on the timeline card, which had to go
        // when that duplicate was dropped. It belongs here: the header is not inside a button, so
        // antd's copy control - which IS a button - is legal, and this is where you have stopped.
        setBuild(build({displayName: "1.4.4"}))
        render(<BuildInspector buildId="1" showValidations={true}/>)

        expect(screen.getByRole('button', {name: /copy/i})).toBeVisible()
    })

    it('says the name once for a project which sets no release property', () => {
        // `displayName` falls back to `name` on the backend, so the two are the same string and
        // printing it twice tells nobody anything - the same rule the timeline card follows
        setBuild(build())
        render(<BuildInspector buildId="1" showValidations={true}/>)

        expect(screen.getByRole('link', {name: "20260901055547-36"})).toBeVisible()
        expect(screen.getAllByText("20260901055547-36")).toHaveLength(1)
    })

    it('shows no header while the build is still being fetched', () => {
        // A header naming nothing, or naming the previously selected build, is worse than none
        setBuild(null, {loading: true, finished: false})
        render(<BuildInspector buildId="1" showValidations={true}/>)

        expect(screen.queryByTestId('inspector-build-title')).not.toBeInTheDocument()
    })

    it('names nothing while the build in hand is not the one selected', () => {
        // `useQuery` keeps the previous `data` across a selection change, and an aborted request
        // still flips `finished` true and `loading` false - after the new request set `loading`
        // true. So "loaded" is not the same as "loaded THIS build", and the difference matters more
        // here than for the panels below: a stale header is a link to the WRONG build's page.
        setBuild(build({id: "1", displayName: "1.4.4"}))
        render(<BuildInspector buildId="2" showValidations={true}/>)

        expect(screen.queryByTestId('inspector-build-title')).not.toBeInTheDocument()
        expect(screen.queryByRole('link', {name: "1.4.4"})).not.toBeInTheDocument()
    })

    it('shows nothing at all when no build is selected', () => {
        setBuild(null)
        render(<BuildInspector showValidations={true}/>)

        expect(screen.queryByTestId('build-inspector')).not.toBeInTheDocument()
    })

})
