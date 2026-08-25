import {render, screen, waitFor} from "@testing-library/react";
import SelectValidationStamp from "@components/validationStamps/SelectValidationStamp";

const branch = {id: "42"}

describe('SelectValidationStamp', () => {

    afterEach(() => {
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

        render(<SelectValidationStamp branch={branch}/>)

        await waitFor(() => expect(screen.queryByText("Error")).not.toBeNull())
        expect(document.querySelector('.ant-select')).toBeNull()
    })

    it('renders the dropdown when the validation stamps load', async () => {
        global.fetch = jest.fn().mockResolvedValue({
            ok: true,
            status: 200,
            json: async () => ({
                branches: [{validationStamps: []}],
            }),
        })

        render(<SelectValidationStamp branch={branch}/>)

        await waitFor(() => expect(document.querySelector('.ant-select')).not.toBeNull())
        expect(screen.queryByText("Error")).toBeNull()
    })

})
