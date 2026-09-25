import {getLocalRecentlyVisited, setLocalRecentlyVisited} from "@components/storage/local";
import {renderHook} from "@testing-library/react";
import {
    branchVisit,
    buildVisit,
    projectVisit,
    recordRecentlyVisited,
    useRecordVisit,
} from "@components/search/palette/recentlyVisited";

const project = (id, name) => ({type: 'project', id, name, href: `/project/${id}`})
const branch = (id, name, context) => ({type: 'branch', id, name, context, href: `/branch/${id}`})

describe('recently visited entities', () => {

    beforeEach(() => {
        localStorage.clear()
    })

    it('starts empty', () => {
        expect(getLocalRecentlyVisited()).toEqual([])
    })

    it('stores what it is given', () => {
        setLocalRecentlyVisited([project(1, 'ontrack')])
        expect(getLocalRecentlyVisited()).toEqual([project(1, 'ontrack')])
    })

    it('falls back to nothing for an entry it cannot read', () => {
        localStorage.setItem('recently-visited', 'not json at all')
        expect(getLocalRecentlyVisited()).toEqual([])
    })

    it('falls back to nothing for an entry which is not a list', () => {
        localStorage.setItem('recently-visited', '{"type":"project"}')
        expect(getLocalRecentlyVisited()).toEqual([])
    })

    it('drops the entries it cannot open', () => {
        localStorage.setItem('recently-visited', JSON.stringify([
            project(1, 'ontrack'),
            {type: 'project', id: 2, name: 'no link'},
            null,
            'a string',
        ]))
        expect(getLocalRecentlyVisited()).toEqual([project(1, 'ontrack')])
    })

    it('puts the last visit first', () => {
        recordRecentlyVisited(project(1, 'ontrack'))
        recordRecentlyVisited(branch(10, 'main', 'ontrack'))
        expect(getLocalRecentlyVisited().map(it => it.name)).toEqual(['main', 'ontrack'])
    })

    it('lists an entity once, at the place of its last visit', () => {
        recordRecentlyVisited(project(1, 'ontrack'))
        recordRecentlyVisited(branch(10, 'main', 'ontrack'))
        recordRecentlyVisited(project(1, 'ontrack'))
        expect(getLocalRecentlyVisited().map(it => it.name)).toEqual(['ontrack', 'main'])
    })

    it('tells entities of different types apart', () => {
        recordRecentlyVisited(project(1, 'ontrack'))
        recordRecentlyVisited(branch(1, 'main', 'ontrack'))
        expect(getLocalRecentlyVisited()).toHaveLength(2)
    })

    it('keeps the name the entity has now', () => {
        recordRecentlyVisited(project(1, 'ontrack'))
        recordRecentlyVisited(project(1, 'yontrack'))
        expect(getLocalRecentlyVisited()).toEqual([project(1, 'yontrack')])
    })

    it('keeps the last 10 visits only', () => {
        for (let id = 1; id <= 12; id++) {
            recordRecentlyVisited(project(id, `p${id}`))
        }
        const names = getLocalRecentlyVisited().map(it => it.name)
        expect(names).toEqual(['p12', 'p11', 'p10', 'p9', 'p8', 'p7', 'p6', 'p5', 'p4', 'p3'])
    })

})

describe('what an entity page records', () => {

    beforeEach(() => {
        localStorage.clear()
    })

    it('records a project by its name', () => {
        expect(projectVisit({id: 1, name: 'ontrack'})).toEqual({
            type: 'project', id: 1, name: 'ontrack', context: null, href: '/project/1',
        })
    })

    it('records a branch by its display name, in its project', () => {
        expect(branchVisit({id: 10, name: 'release-1.0', displayName: 'release/1.0', project: {id: 1, name: 'ontrack'}}))
            .toEqual({type: 'branch', id: 10, name: 'release/1.0', context: 'ontrack', href: '/branch/10'})
    })

    it('records a build by its release when it has one, in its branch', () => {
        const build = {id: 100, name: '42', branch: {id: 10, name: 'main', project: {id: 1, name: 'ontrack'}}}
        expect(buildVisit(build))
            .toEqual({type: 'build', id: 100, name: '42', context: 'ontrack / main', href: '/build/100'})
        expect(buildVisit({...build, releaseProperty: {value: {name: '1.0.0'}}}).name).toBe('1.0.0')
    })

    it('records nothing for an entity not loaded yet', () => {
        expect(projectVisit({})).toBeNull()
        expect(branchVisit(null)).toBeNull()
        expect(buildVisit({branch: {project: {}}})).toBeNull()
    })

    it('records the visit once the entity is loaded', () => {
        const {rerender} = renderHook(({project}) => useRecordVisit(projectVisit(project)), {
            initialProps: {project: {}},
        })
        expect(getLocalRecentlyVisited()).toEqual([])
        rerender({project: {id: 1, name: 'ontrack'}})
        expect(getLocalRecentlyVisited().map(it => it.name)).toEqual(['ontrack'])
    })

    it('records the visit once, not on every rendering of the page', () => {
        const {rerender} = renderHook(({project}) => useRecordVisit(projectVisit(project)), {
            initialProps: {project: {id: 1, name: 'ontrack'}},
        })
        recordRecentlyVisited(branchVisit({id: 10, name: 'main', project: {id: 1, name: 'ontrack'}}))
        rerender({project: {id: 1, name: 'ontrack'}})
        expect(getLocalRecentlyVisited().map(it => it.name)).toEqual(['main', 'ontrack'])
    })

})
