import {act, renderHook, waitFor} from "@testing-library/react";
import {useQuery} from "@components/services/useQuery";

const mockRequest = jest.fn()

// the client identity is one of the hook's effect dependencies, so it has to be stable across renders
const mockClient = {
    request: (...args) => mockRequest(...args),
}

// the `@components` alias is rewritten by SWC at transform time, so `jest.mock` needs a real path
jest.mock("../../../components/providers/ConnectionContextProvider", () => ({
    useGraphQLClient: () => mockClient,
}))

const QUERY = `query { whatever }`

describe('deprecated useQuery', () => {

    beforeEach(() => {
        mockRequest.mockReset()
    })

    it('exposes the data when the request succeeds', async () => {
        mockRequest.mockResolvedValue({project: {name: "my-project"}})

        const {result} = renderHook(() => useQuery(QUERY))

        await waitFor(() => expect(result.current.loading).toBe(false))
        expect(result.current.data).toEqual({project: {name: "my-project"}})
        expect(result.current.error).toBeFalsy()
    })

    it('sets the error when the request is rejected', async () => {
        mockRequest.mockRejectedValue(new Error("Network is down"))

        const {result} = renderHook(() => useQuery(QUERY))

        await waitFor(() => expect(result.current.loading).toBe(false))
        expect(result.current.error).toBe("Network is down")
    })

    it('stops loading when the request is rejected', async () => {
        mockRequest.mockRejectedValue(new Error("Network is down"))

        const {result} = renderHook(() => useQuery(QUERY))

        await waitFor(() => expect(result.current.loading).toBe(false))
    })

    it('sets the error to the message of the first GraphQL error in the body', async () => {
        mockRequest.mockResolvedValue({
            errors: [
                {message: "Variable 'id' has an invalid value"},
                {message: "Some other error"},
            ],
        })

        const {result} = renderHook(() => useQuery(QUERY))

        await waitFor(() => expect(result.current.loading).toBe(false))
        expect(result.current.error).toBe("Variable 'id' has an invalid value")
    })

    it('does not leave a stale error after a successful refetch', async () => {
        mockRequest.mockRejectedValueOnce(new Error("Transient failure"))
        mockRequest.mockResolvedValue({ok: true})

        const {result} = renderHook(() => useQuery(QUERY))
        await waitFor(() => expect(result.current.error).toBe("Transient failure"))

        act(() => result.current.refetch())

        await waitFor(() => expect(result.current.data).toEqual({ok: true}))
        expect(result.current.error).toBeFalsy()
    })

})
