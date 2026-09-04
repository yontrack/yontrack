import React from "react";
import {render, screen, within} from "@testing-library/react";

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

// The badge fetches its count on mount; it is not what these tests are about
jest.mock("../../../../../components/extension/notifications/EntityNotificationsBadge", () => () => null)

import {EventsContext} from "@components/common/EventsContext";
import BuildInspectorPromotions from "@components/branches/views/pipeline/BuildInspectorPromotions";

// Several of the shared primitives subscribe to the page event bus on mount
const render_ = (element) => render(
    <EventsContext.Provider value={{fireEvent: jest.fn(), subscribeToEvent: jest.fn()}}>
        {element}
    </EventsContext.Provider>
)

const level = (id) => ({id: String(id), name: `LEVEL-${id}`, image: false})

const run = (id, levelId, {authorizations = []} = {}) => ({
    id: String(id),
    creation: {time: "2026-09-01T05:55:47Z", user: "admin"},
    promotionLevel: level(levelId),
    fieldValues: [],
    authorizations,
})

const canDelete = {authorizations: [{name: 'promotion_run', action: 'delete', authorized: true}]}

const build = ({runs = [], levels = [], canPromote = false} = {}) => ({
    id: "1",
    name: "20260901055547-36",
    displayName: "20260901055547-36",
    promotionRuns: runs,
    branch: {id: "1", promotionLevels: levels},
    authorizations: [{name: 'build', action: 'promote', authorized: canPromote}],
})

/**
 * The inspector's promotions panel, and the actions on each run.
 *
 * The actions are revealed on hover AND on keyboard focus - which is a CSS concern, so what is
 * pinned here is that they are RENDERED and correctly gated. A permission-gated action which is
 * merely transparent would be a security hole; one which is absent is not.
 */
describe('the promotions panel', () => {

    describe('per-run actions', () => {

        it('offers a re-promotion to the run\'s own level', () => {
            // The point of the action: a build can be promoted again to a level it already reached
            render_(<BuildInspectorPromotions
                build={build({runs: [run(10, 1)], levels: [level(1)], canPromote: true})}
            />)
            expect(screen.getByTestId('build-promote-1-1')).toBeInTheDocument()
        })

        it('offers no re-promotion when the user may not promote', () => {
            render_(<BuildInspectorPromotions
                build={build({runs: [run(10, 1)], levels: [level(1)], canPromote: false})}
            />)
            expect(screen.queryByTestId('build-promote-1-1')).not.toBeInTheDocument()
        })

        it('offers a deletion when the user may delete that run', () => {
            render_(<BuildInspectorPromotions
                build={build({runs: [run(10, 1, canDelete)], levels: [level(1)]})}
            />)
            expect(screen.getByTestId('build-promotion-delete-10')).toBeInTheDocument()
        })

        it('offers no deletion when the user may not delete that run', () => {
            // Gated per RUN, not per build: the query already carries each run's authorizations
            render_(<BuildInspectorPromotions
                build={build({runs: [run(10, 1)], levels: [level(1)]})}
            />)
            expect(screen.queryByTestId('build-promotion-delete-10')).not.toBeInTheDocument()
        })

        it('links to the promotion run from the timestamp it already renders', () => {
            // Unconditional, unlike the notifications badge, which only links when the run happens
            // to have notification records - navigation that reads as a notifications feature
            render_(<BuildInspectorPromotions
                build={build({runs: [run(10, 1)], levels: [level(1)]})}
            />)
            const link = screen.getByTestId('inspector-promotion-run-link-10')
            expect(link).toHaveAttribute('href', '/promotionRun/10')
        })

        it('gives every run its own actions when a build was promoted twice to one level', () => {
            // The panel already renders one row per run; the actions have to follow the run, not
            // the level, or the second promotion would be undeletable
            render_(<BuildInspectorPromotions
                build={build({
                    runs: [run(10, 1, canDelete), run(11, 1, canDelete)],
                    levels: [level(1)],
                })}
            />)
            expect(screen.getByTestId('build-promotion-delete-10')).toBeInTheDocument()
            expect(screen.getByTestId('build-promotion-delete-11')).toBeInTheDocument()
        })

    })

    describe('the promote button', () => {

        it('names no level, because the dialog is not restricted to one', () => {
            // The dialog has always carried an editable level picker, so a label naming the next
            // level was describing a restriction that was never there
            render_(<BuildInspectorPromotions
                build={build({runs: [], levels: [level(1), level(2)], canPromote: true})}
            />)
            expect(screen.getByText("Promote...")).toBeVisible()
            expect(screen.queryByText(/Promote to/)).not.toBeInTheDocument()
        })

        it('is offered even once every level is reached', () => {
            // There is no "next" level left, but re-promoting is still a thing you can do
            render_(<BuildInspectorPromotions
                build={build({runs: [run(10, 1)], levels: [level(1)], canPromote: true})}
            />)
            expect(screen.getByText("Promote...")).toBeVisible()
        })

        it('is hidden outright when the user may not promote', () => {
            render_(<BuildInspectorPromotions
                build={build({runs: [], levels: [level(1)], canPromote: false})}
            />)
            expect(screen.queryByText("Promote...")).not.toBeInTheDocument()
        })

    })

})
