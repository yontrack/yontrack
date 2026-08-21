import {genericGraphQLErrorMessage, graphQLErrorMessage} from "@components/services/GraphQL";

describe('graphQLErrorMessage', () => {

    it('returns the message of the first GraphQL error', () => {
        const body = {
            error: {
                response: {
                    errors: [
                        {message: "Project name not found: my-project"},
                        {message: "Some other error"},
                    ],
                },
            },
        }
        expect(graphQLErrorMessage(body)).toBe("Project name not found: my-project")
    })

    it('falls back to a generic message when there is no GraphQL error', () => {
        expect(graphQLErrorMessage(undefined)).toBe(genericGraphQLErrorMessage)
        expect(graphQLErrorMessage({})).toBe(genericGraphQLErrorMessage)
        expect(graphQLErrorMessage({error: {}})).toBe(genericGraphQLErrorMessage)
        expect(graphQLErrorMessage({error: {response: {errors: []}}})).toBe(genericGraphQLErrorMessage)
    })

})
