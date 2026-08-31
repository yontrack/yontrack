import {
    branchContentViews,
    defaultBranchContentViewKey,
    getBranchContentView,
} from "@components/branches/views/branchContentViews"

// The builds content view drags the whole builds table in; only the registry is under test here
jest.mock("../../../../components/branches/views/BuildsContentView", () => () => <div/>)

describe('branch content view registry', () => {

    it('registers the builds view, and only that one for now', () => {
        expect(branchContentViews.map(it => it.key)).toEqual(['builds'])
    })

    it('names the legacy view "Builds", never "Table"', () => {
        expect(getBranchContentView('builds').name).toBe("Builds")
    })

    it('gives every entry the shape a server-driven list could later populate', () => {
        branchContentViews.forEach(view => {
            expect(typeof view.key).toBe('string')
            expect(typeof view.name).toBe('string')
            expect(view.icon).toBeTruthy()
            expect(view.component).toBeTruthy()
        })
    })

    it('defaults to a registered view', () => {
        expect(branchContentViews.map(it => it.key)).toContain(defaultBranchContentViewKey)
    })

    it('resolves a registered key', () => {
        expect(getBranchContentView('builds').key).toBe('builds')
    })

    it.each([
        ['an unknown key', 'no-such-view'],
        ['an undefined key', undefined],
        ['a null key', null],
        ['an empty key', ''],
    ])('falls back to the default view for %s', (_, key) => {
        expect(getBranchContentView(key).key).toBe(defaultBranchContentViewKey)
    })

    describe('against a caller-provided list of views', () => {

        const views = [
            {key: 'builds', name: "Builds"},
            {key: 'pipeline', name: "Pipeline"},
        ]

        it('resolves a registered key', () => {
            expect(getBranchContentView('pipeline', views)).toBe(views[1])
        })

        it('falls back to the default view for an unknown key', () => {
            expect(getBranchContentView('no-such-view', views)).toBe(views[0])
        })

        it('falls back to the first view when the default one is not in the list', () => {
            const others = [{key: 'pipeline', name: "Pipeline"}]
            expect(getBranchContentView('no-such-view', others)).toBe(others[0])
        })

    })

})
