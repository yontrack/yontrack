import React from "react";
import {render, screen, waitFor} from "@testing-library/react";
import '@testing-library/jest-dom';
import {Form} from "antd";

// Ant Design uses window.matchMedia for responsive features; jsdom doesn't provide it
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

// The real selector, on top of the data of an existing branch
jest.mock("../../../../components/services/GraphQL", () => ({
    callGraphQL: jest.fn(() => Promise.resolve({})),
    useQuery: (query, {condition, dataFn}) => {
        const response = {
            branches: [{
                id: 1,
                name: 'main',
                promotionLevels: [{id: 10, name: 'BRONZE'}, {id: 11, name: 'SILVER'}],
            }],
        }
        return {
            data: condition === false ? undefined : dataFn(response),
            loading: false,
            finished: true,
        }
    },
}))

import {DashboardWidgetCellContext} from "@components/dashboards/DashboardWidgetCellContextProvider";
import ValidationsLastPromotionBuildWidgetForm from "@components/widgets/home/ValidationsLastPromotionBuildWidgetForm";

const Wrapper = ({onForm, onHandler}) => {
    const [form] = Form.useForm()
    onForm(form)
    return (
        <DashboardWidgetCellContext.Provider value={{widgetEditionForm: form, onReceivingValuesHandler: onHandler}}>
            <ValidationsLastPromotionBuildWidgetForm
                title="My widget"
                project="my-project"
                branch="main"
                promotion="BRONZE"
                validations={['unit']}
            />
        </DashboardWidgetCellContext.Provider>
    )
}

describe('ValidationsLastPromotionBuildWidgetForm', () => {

    it('shows the current promotion level when editing the widget', async () => {
        render(<Wrapper onForm={() => {
        }} onHandler={() => {
        }}/>)
        await waitFor(() => expect(screen.getByText('BRONZE')).toBeInTheDocument())
    })

    it('keeps the promotion level when saving without changing it', () => {
        let form
        let handler
        render(<Wrapper onForm={it => form = it} onHandler={it => handler = it}/>)
        expect(handler(form.getFieldsValue())).toEqual({
            title: 'My widget',
            project: 'my-project',
            branch: 'main',
            promotion: 'BRONZE',
            validations: ['unit'],
        })
    })

})
