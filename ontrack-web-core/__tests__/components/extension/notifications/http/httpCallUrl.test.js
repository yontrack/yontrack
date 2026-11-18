import {httpCallUrl} from "@components/extension/notifications/http/httpCallUrl"

/**
 * The URL displayed for an HTTP call is the one the backend calls: these cases are those of
 * `HttpCallUrlsTest`, for `resolveHttpCallUrl`.
 */
describe('httpCallUrl', () => {

    it.each([
        ['https://api.example.com/v1', '/deployments', 'https://api.example.com/v1/deployments'],
        ['https://api.example.com/v1', 'deployments', 'https://api.example.com/v1/deployments'],
        ['https://api.example.com/v1/', '/deployments', 'https://api.example.com/v1/deployments'],
        ['https://api.example.com/v1', '/deployments?env=prod&force=true', 'https://api.example.com/v1/deployments?env=prod&force=true'],
        ['https://api.example.com/v1', '?env=prod', 'https://api.example.com/v1?env=prod'],
        ['https://api.example.com/v1/', '?env=prod', 'https://api.example.com/v1?env=prod'],
        ['https://api.example.com', '/deployments', 'https://api.example.com/deployments'],
        ['https://api.example.com/', 'deployments', 'https://api.example.com/deployments'],
        ['http://localhost:8080/api', '/items:batchGet', 'http://localhost:8080/api/items:batchGet'],
    ])('appends to %s the path %s', (baseUrl, path, expected) => {
        expect(httpCallUrl(baseUrl, path)).toEqual(expected)
    })

    it.each([[''], ['  '], [null], [undefined]])('is the base URL itself for the path "%s"', (path) => {
        expect(httpCallUrl('https://api.example.com/v1', path)).toEqual('https://api.example.com/v1')
    })

    it('keeps the template expressions of the path', () => {
        expect(httpCallUrl('https://api.example.com/v1', '/deployments?project=${project}'))
            .toEqual('https://api.example.com/v1/deployments?project=${project}')
    })

    it.each([[null], [undefined], ['']])('falls back on the path when the base URL is "%s"', (baseUrl) => {
        expect(httpCallUrl(baseUrl, '/deployments')).toEqual('/deployments')
    })

    it('falls back on the root when there is neither a base URL nor a path', () => {
        expect(httpCallUrl(null, '')).toEqual('/')
    })

})
