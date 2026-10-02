import "@testing-library/jest-dom"
import {fireEvent, render, screen, waitFor} from "@testing-library/react"
import {Form} from "antd"

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

import CHMLDisplay from "@components/framework/validation-data-type/general.validation.CHMLValidationDataType"
import CHMLForm from "@components/framework/validation-data-type-form/general.validation.CHMLValidationDataType"

const warningLevel = {level: 'HIGH', value: 1}
const failedLevel = {level: 'CRITICAL', value: 1}

describe('the CHML validation data type display', () => {

    it('shows that a warning passes the auto promotion when the stamp opts in', () => {
        render(<CHMLDisplay warningLevel={warningLevel} failedLevel={failedLevel} warningPassesAutoPromotion={true}/>)
        expect(screen.getByTestId('chml-warning-passes-auto-promotion'))
            .toHaveTextContent('Warning passes auto-promotion')
    })

    it('says nothing about the auto promotion by default', () => {
        render(<CHMLDisplay warningLevel={warningLevel} failedLevel={failedLevel}/>)
        expect(screen.queryByTestId('chml-warning-passes-auto-promotion')).toBeNull()
    })
})

describe('the CHML validation data type form', () => {

    const renderForm = (config, onValues) => {
        const Wrapper = () => {
            const [form] = Form.useForm()
            return <Form form={form} onFinish={onValues}>
                <CHMLForm prefix="config" {...config}/>
                <button type="submit">Submit</button>
            </Form>
        }
        render(<Wrapper/>)
    }

    const formConfig = {
        failedLevel: 'CRITICAL',
        failedValue: 1,
        warningLevel: 'HIGH',
        warningValue: 1,
    }

    it('leaves the warning tolerance off by default', () => {
        renderForm(formConfig, () => {})
        expect(screen.getByRole('checkbox', {name: 'A warning counts as passed for auto-promotion'})).not.toBeChecked()
    })

    it('shows the stored warning tolerance', () => {
        renderForm({...formConfig, warningPassesAutoPromotion: true}, () => {})
        expect(screen.getByRole('checkbox', {name: 'A warning counts as passed for auto-promotion'})).toBeChecked()
    })

    it('submits the warning tolerance', async () => {
        const onValues = jest.fn()
        renderForm(formConfig, onValues)
        fireEvent.click(screen.getByRole('checkbox', {name: 'A warning counts as passed for auto-promotion'}))
        fireEvent.click(screen.getByText('Submit'))
        await waitFor(() => expect(onValues).toHaveBeenCalledWith(expect.objectContaining({
            config: expect.objectContaining({warningPassesAutoPromotion: true}),
        })))
    })
})
