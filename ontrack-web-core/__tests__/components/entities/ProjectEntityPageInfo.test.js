import {act, renderHook, waitFor} from "@testing-library/react";
import {useProjectEntityPageInfo} from "@components/entities/ProjectEntityPageInfo";

const project = {id: 1, name: "my-project", authorizations: []}
const branch = {id: 10, name: "main", project: {id: 1, name: "my-project"}, authorizations: []}

/**
 * Holds each GraphQL call pending until the test answers it with `pending[n](body)`.
 */
const mockGraphQL = () => {
    const pending = []
    global.fetch = jest.fn().mockImplementation(() => new Promise(resolve => {
        pending.push((body) => resolve({
            ok: true,
            status: 200,
            json: async () => body,
        }))
    }))
    return pending
}

describe('useProjectEntityPageInfo', () => {

    afterEach(() => {
        delete global.fetch
    })

    it('loads the page information of a branch', async () => {
        const pending = mockGraphQL()

        const {result} = renderHook(() => useProjectEntityPageInfo('BRANCH', '10', 'Subscriptions'))

        expect(result.current.title).toBe('')
        expect(result.current.entity).toEqual({})

        await waitFor(() => expect(pending).toHaveLength(1))
        const request = JSON.parse(global.fetch.mock.calls[0][1].body)
        expect(request.variables).toEqual({id: 10})
        expect(request.query).toContain('branch(id: $id)')
        pending[0]({branch})

        await waitFor(() => expect(result.current.title).toBe('main / my-project | Subscriptions'))
        expect(result.current.entityTypeName).toBe('Branch')
        expect(result.current.uri).toBe('/branch/10')
        expect(result.current.entity).toBe(branch)
        expect(result.current.breadcrumbs).toHaveLength(3)
    })

    it('does not query an entity type it does not support', async () => {
        const pending = mockGraphQL()

        const {result} = renderHook(() => useProjectEntityPageInfo('VALIDATION_RUN', '10'))

        expect(pending).toHaveLength(0)
        expect(global.fetch).not.toHaveBeenCalled()
        expect(result.current.title).toBe('')
    })

    it('keeps reading the previous entity with its own type until the new one is loaded', async () => {
        const pending = mockGraphQL()

        const {result, rerender} = renderHook(
            ({type, id}) => useProjectEntityPageInfo(type, id),
            {initialProps: {type: 'PROJECT', id: '1'}}
        )
        await waitFor(() => expect(pending).toHaveLength(1))
        pending[0]({project})
        await waitFor(() => expect(result.current.entityTypeName).toBe('Project'))

        // Switching to a branch: the project is still displayed while the branch loads
        rerender({type: 'BRANCH', id: '10'})
        await waitFor(() => expect(pending).toHaveLength(2))
        expect(result.current.entityTypeName).toBe('Project')
        expect(result.current.title).toBe('my-project')

        pending[1]({branch})
        await waitFor(() => expect(result.current.entityTypeName).toBe('Branch'))
        expect(result.current.title).toBe('main / my-project')
    })

    it('stays empty when the entity is not found', async () => {
        const pending = mockGraphQL()

        const {result} = renderHook(() => useProjectEntityPageInfo('PROJECT', '1'))
        await waitFor(() => expect(pending).toHaveLength(1))
        await act(async () => {
            pending[0]({project: null})
        })

        expect(result.current.title).toBe('')
        expect(result.current.entity).toEqual({})
    })

})
