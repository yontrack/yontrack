import "@testing-library/jest-dom"
import {render, screen} from "@testing-library/react"
import HttpNotificationChannelConfig from "../../../../../components/framework/notification-channel/http/Config"
import {useQuery} from "../../../../../components/services/GraphQL"

jest.mock("../../../../../components/services/GraphQL", () => ({
    useQuery: jest.fn(),
}))

jest.mock("../../../../../components/common/JsonDisplay", () => ({
    __esModule: true,
    default: () => null,
}))

/**
 * The configuration of an HTTP call displays the URL which is called, and the HTTP configuration
 * it goes through.
 */
describe('HttpNotificationChannelConfig', () => {

    const baseUrl = (url) => useQuery.mockReturnValue({data: url, loading: false})

    beforeAll(() => {
        // antd's Descriptions observes the breakpoints, and jsdom has no matchMedia
        window.matchMedia = window.matchMedia || (() => ({
            matches: false,
            addEventListener: () => {},
            removeEventListener: () => {},
            addListener: () => {},
            removeListener: () => {},
        }))
    })

    beforeEach(() => useQuery.mockReset())

    it('displays the absolute URL and the name of the configuration', () => {
        baseUrl('https://api.example.com/v1')
        render(<HttpNotificationChannelConfig config="deployments" method="POST" path="/deployments"/>)
        expect(screen.getByText('POST')).toBeInTheDocument()
        expect(screen.getByText('https://api.example.com/v1/deployments')).toBeInTheDocument()
        expect(screen.getByText('deployments')).toBeInTheDocument()
    })

    it('reads the base URL of the named HTTP configuration', () => {
        baseUrl('https://api.example.com/v1')
        render(<HttpNotificationChannelConfig config="deployments" path="/deployments"/>)
        const [, options] = useQuery.mock.calls[0]
        expect(options.variables).toEqual({name: 'deployments'})
        expect(options.deps).toEqual(['deployments'])
        expect(options.dataFn({configurationByName: {data: {url: 'https://api.example.com/v1'}}}))
            .toEqual('https://api.example.com/v1')
    })

    it('displays the path when the configuration does not exist', () => {
        baseUrl(undefined)
        render(<HttpNotificationChannelConfig config="deployments" method="GET" path="/deployments"/>)
        expect(screen.getByText('/deployments')).toBeInTheDocument()
        expect(screen.getByText('deployments')).toBeInTheDocument()
    })

})
