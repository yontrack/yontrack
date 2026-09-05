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
import PromotionChartTitle from "@components/widgets/home/PromotionChartTitle";
import ValidationChartTitle from "@components/widgets/home/ValidationChartTitle";

beforeEach(() => {
    // The entity icon fetches its image on mount; it is not what these tests are about
    global.fetch = jest.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({dataURL: 'data:image/png;base64,AAAA'}),
    })
})

// The entity icons subscribe to the page event bus on mount
const render_ = (element) => render(
    <EventsContext.Provider value={{fireEvent: jest.fn(), subscribeToEvent: jest.fn()}}>
        {element}
    </EventsContext.Provider>
)

const branch = {
    id: "3",
    name: "main",
    displayName: "main",
    project: {id: "1", name: "petclinic"},
}

const hrefs = () => screen.getAllByRole('link').map(link => link.getAttribute('href'))

/**
 * The titles of the promotion and validation chart widgets.
 *
 * These two components are the whole of the linking logic: the four promotion chart widgets and
 * the two validation chart widgets are pure call sites, so pinning the titles here covers all six.
 *
 * The fallback cases matter as much as the linked ones. `usePromotionLevel` starts on `{}`, which is
 * truthy, so the promotion widgets really do render this title once with nothing resolved - a title
 * that assumed `branch` were present would crash the whole dashboard cell rather than merely look
 * unfinished. The validation half cannot reach that state today (its hook starts `undefined` and the
 * widgets skip the title entirely), so its fallback case is a contract test, pinning the two titles
 * as symmetrical against #1694 changing either hook.
 */
describe('PromotionChartTitle', () => {

    const promotionLevel = {id: "12", name: "GOLD", image: false, branch}

    it('links the promotion level, the branch and the project', () => {
        render_(<PromotionChartTitle
            prefix="Lead time to"
            project="petclinic"
            branch="main"
            promotionLevel={promotionLevel}
            interval="3m"
            period="1w"
        />)
        expect(hrefs()).toEqual(['/promotionLevel/12', '/branch/3', '/project/1'])
    })

    it('keeps the wording and the chart options around the links', () => {
        render_(<PromotionChartTitle
            prefix="Lead time to"
            project="petclinic"
            branch="main"
            promotionLevel={promotionLevel}
            interval="3m"
            period="1w"
        />)
        expect(screen.getByText('Lead time to')).toBeVisible()
        expect(screen.getByRole('link', {name: /GOLD/})).toBeVisible()
        expect(screen.getByRole('link', {name: 'main'})).toBeVisible()
        expect(screen.getByRole('link', {name: 'petclinic'})).toBeVisible()
        expect(screen.getByText(/3m/)).toBeVisible()
    })

    it('falls back to the configured names when the promotion level has no branch yet', () => {
        // The widget's query starts on an empty object, so this render happens on every dashboard
        render_(<PromotionChartTitle
            prefix="Lead time to"
            project="petclinic"
            branch="main"
            promotionLevel={{}}
            interval="3m"
            period="1w"
        />)
        expect(screen.queryAllByRole('link')).toHaveLength(0)
        expect(screen.getByText(/main/)).toBeVisible()
        expect(screen.getByText(/petclinic/)).toBeVisible()
    })
})

describe('ValidationChartTitle', () => {

    const validationStamp = {id: "42", name: "UNIT.TESTS", image: false, branch}

    it('links the validation stamp, the branch and the project', () => {
        render_(<ValidationChartTitle
            prefix="Stability of"
            project="petclinic"
            branch="main"
            validationStamp={validationStamp}
            interval="3m"
            period="1w"
        />)
        expect(hrefs()).toEqual(['/validationStamp/42', '/branch/3', '/project/1'])
    })

    it('keeps the wording and the chart options around the links', () => {
        render_(<ValidationChartTitle
            prefix="Stability of"
            project="petclinic"
            branch="main"
            validationStamp={validationStamp}
            interval="3m"
            period="1w"
        />)
        expect(screen.getByText('Stability of')).toBeVisible()
        expect(screen.getByRole('link', {name: /UNIT\.TESTS/})).toBeVisible()
        expect(screen.getByRole('link', {name: 'main'})).toBeVisible()
        expect(screen.getByRole('link', {name: 'petclinic'})).toBeVisible()
        expect(screen.getByText(/3m/)).toBeVisible()
    })

    it('falls back to the configured names when the validation stamp has no branch yet', () => {
        render_(<ValidationChartTitle
            prefix="Stability of"
            project="petclinic"
            branch="main"
            validationStamp={{}}
            interval="3m"
            period="1w"
        />)
        expect(screen.queryAllByRole('link')).toHaveLength(0)
        expect(screen.getByText(/main/)).toBeVisible()
        expect(screen.getByText(/petclinic/)).toBeVisible()
    })
})
