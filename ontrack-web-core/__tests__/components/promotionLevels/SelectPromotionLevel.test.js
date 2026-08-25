import {render, screen, waitFor} from "@testing-library/react";
import SelectPromotionLevel from "@components/promotionLevels/SelectPromotionLevel";

const branch = {id: "42"}

describe('SelectPromotionLevel', () => {

    let consoleError

    beforeEach(() => {
        // `callGraphQL` logs the failed response; keep it out of the test output
        consoleError = jest.spyOn(console, 'error').mockImplementation(() => {})
    })

    afterEach(() => {
        consoleError.mockRestore()
        delete global.fetch
    })

    it('reports the failure instead of rendering an empty dropdown', async () => {
        global.fetch = jest.fn().mockResolvedValue({
            ok: false,
            status: 500,
            json: async () => ({
                error: {response: {errors: [{message: "Branch not found"}]}},
            }),
        })

        render(<SelectPromotionLevel branch={branch}/>)

        await waitFor(() => expect(screen.queryByText("Error")).not.toBeNull())
        expect(screen.queryByRole('combobox')).toBeNull()
    })

    it('renders the dropdown when the promotion levels load', async () => {
        global.fetch = jest.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({
                branches: [{promotionLevels: []}],
            }),
        })

        render(<SelectPromotionLevel branch={branch}/>)

        await waitFor(() => expect(screen.queryByRole('combobox')).not.toBeNull())
        expect(screen.queryByText("Error")).toBeNull()
    })

})
