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

import {EventsContext} from "@components/common/EventsContext";
import PipelineStages from "@components/branches/views/pipeline/PipelineStages";
import PipelineStats from "@components/branches/views/pipeline/PipelineStats";
import BuildTimeline from "@components/branches/views/pipeline/BuildTimeline";
import BuildTimelineCard from "@components/branches/views/pipeline/BuildTimelineCard";
import BuildInspectorPromotions from "@components/branches/views/pipeline/BuildInspectorPromotions";
import BuildInspectorValidations from "@components/branches/views/pipeline/BuildInspectorValidations";

/**
 * The degradation matrix of the pipeline view.
 *
 * Every row here is a branch some real user has: one promotion level, twelve of them, none at all,
 * no validations, no builds. These are the states a view is judged on and the states nobody clicks
 * through by hand, so they are pinned here instead.
 */

// The notification badges fetch on mount; the inspector's panels are not what is under test
jest.mock("../../../../../components/extension/notifications/EntityNotificationsBadge", () => () => null)

const withEvents = (element) => render(
    <EventsContext.Provider value={{fireEvent: jest.fn(), subscribeToEvent: jest.fn()}}>
        {element}
    </EventsContext.Provider>
)

const level = (id, overrides = {}) => ({
    id: String(id),
    name: `LEVEL-${id}`,
    image: false,
    promotedBuildCount: 0,
    ...overrides,
})

const build = (overrides = {}) => ({
    id: "1",
    name: "20260901055547-36",
    displayName: "20260901055547-36",
    creation: {time: "2026-09-01T05:55:47Z"},
    promotionRuns: [],
    validations: [],
    ...overrides,
})

describe('a branch with no promotion levels', () => {

    it('hides the pipeline band entirely, rather than showing an empty one', () => {
        // There is no pipeline to be empty of: the branch simply does not work that way
        const {container} = withEvents(<PipelineStages promotionLevels={[]}/>)
        expect(container).toBeEmptyDOMElement()
    })

})

describe('a branch with one promotion level', () => {

    it('renders the one stage, with no special casing and no connectors', () => {
        withEvents(<PipelineStages promotionLevels={[level(1, {promotedBuildCount: 3})]}/>)
        expect(screen.getByTestId('pipeline-stage-1')).toBeVisible()
        expect(screen.getByText("3 builds")).toBeVisible()
    })

})

describe('a branch with twelve promotion levels', () => {

    const levels = Array.from({length: 12}, (_, index) => level(index + 1))

    it('renders every stage and lets the band scroll rather than shrinking the cards', () => {
        withEvents(<PipelineStages promotionLevels={levels}/>)
        levels.forEach(it => expect(screen.getByTestId(`pipeline-stage-${it.id}`)).toBeVisible())
        // `ot-scroll-x` scrolls AND keeps the scrollbar visible: macOS hides the overlay one until
        // something scrolls, which would make a twelve-stage band look like a cropped three-stage one
        expect(screen.getByTestId('pipeline-stages')).toHaveClass('ot-scroll-x')
    })

})

describe('a promotion level nothing has ever reached', () => {

    it('still renders, dimmed, so the shape of the pipeline is visible', () => {
        withEvents(<PipelineStages promotionLevels={[level(1, {promotedBuildCount: 0})]}/>)
        const stage = screen.getByTestId('pipeline-stage-1')
        expect(stage).toHaveAttribute('data-reached', 'false')
        expect(screen.getByText("Never reached")).toBeVisible()
    })

})

describe('a build with no validations', () => {

    it('omits the strip rather than drawing empty bars', () => {
        // Empty bars would claim stamps this build never ran
        withEvents(<BuildTimelineCard build={build({validations: []})} promotionLevels={[]}/>)
        expect(screen.queryByTestId('validation-strip')).not.toBeInTheDocument()
    })

    it('shows the inspector empty state', () => {
        withEvents(<BuildInspectorValidations build={build({validations: []})}/>)
        expect(screen.getByText("This build has no validation to show.")).toBeVisible()
    })

})

describe('a build with no promotions', () => {

    it('shows the promotions empty state', () => {
        withEvents(<BuildInspectorPromotions build={build({branch: {promotionLevels: []}})}/>)
        expect(screen.getByText("This build has not been promoted.")).toBeVisible()
    })

    it('still offers the first level when the user may promote', () => {
        withEvents(<BuildInspectorPromotions
            build={build({
                branch: {promotionLevels: [level(1), level(2)]},
                authorizations: [{name: 'build', action: 'promote', authorized: true}],
            })}
        />)
        expect(screen.getByText("Promote to LEVEL-1")).toBeVisible()
    })

    it('hides the promote affordance from a user who cannot promote', () => {
        // An affordance which can only ever produce a permission error is worse than none
        withEvents(<BuildInspectorPromotions
            build={build({
                branch: {promotionLevels: [level(1)]},
                authorizations: [{name: 'build', action: 'promote', authorized: false}],
            })}
        />)
        expect(screen.queryByText("Promote to LEVEL-1")).not.toBeInTheDocument()
    })

    it('offers no promotion once every level is reached', () => {
        withEvents(<BuildInspectorPromotions
            build={build({
                branch: {promotionLevels: [level(1)]},
                promotionRuns: [{id: "r1", promotionLevel: level(1), creation: {time: "2026-09-01T06:00:00Z"}}],
                authorizations: [{name: 'build', action: 'promote', authorized: true}],
            })}
        />)
        expect(screen.queryByText(/^Promote to/)).not.toBeInTheDocument()
    })

})

describe('a branch with no builds at all', () => {

    it('replaces the timeline with a single empty state', () => {
        withEvents(<BuildTimeline builds={[]} loading={false} promotionLevels={[]}/>)
        expect(screen.getByText("No build to show on this branch.")).toBeVisible()
        expect(screen.queryByTestId('build-timeline')).not.toBeInTheDocument()
    })

    it('shows no empty state while the first page is still in flight', () => {
        // An empty state flashed over a branch which has builds reads as "this branch is empty"
        withEvents(<BuildTimeline builds={[]} loading={true} promotionLevels={[]}/>)
        expect(screen.queryByText("No build to show on this branch.")).not.toBeInTheDocument()
    })

})

describe('a project which does not use releases', () => {

    it('shows no latest version, because it would only repeat the build name', () => {
        const name = "20260901055547-36"
        withEvents(<PipelineStats
            totalBuilds={12}
            latestBuild={{id: "1", name, displayName: name, creation: {time: "2026-09-01T05:55:47Z"}}}
            loading={false}
        />)
        expect(screen.getByTestId('pipeline-stat-total-builds')).toHaveTextContent("12")
        expect(screen.queryByTestId('pipeline-stat-latest-version')).not.toBeInTheDocument()
    })

    it('shows the version when the project does use releases', () => {
        withEvents(<PipelineStats
            totalBuilds={12}
            latestBuild={{
                id: "1",
                name: "20260901055547-36",
                displayName: "5.3.0",
                creation: {time: "2026-09-01T05:55:47Z"},
            }}
            loading={false}
        />)
        expect(screen.getByTestId('pipeline-stat-latest-version')).toHaveTextContent("5.3.0")
    })

})
