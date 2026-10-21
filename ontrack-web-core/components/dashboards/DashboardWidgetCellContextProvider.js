import {createContext, useCallback, useContext, useEffect, useRef, useState} from "react";
import {DashboardContext} from "@components/dashboards/DashboardContextProvider";
import {Form} from "antd";

export const DashboardWidgetCellContext = createContext({
    // UUID of the widget instance, for what a widget keeps per instance (like a format kept in the browser)
    uuid: undefined,
    // Widget title
    title: "",
    setTitle: (value) => {
    },
    // Extra commands for the widget
    extra: undefined,
    setExtra: (value) => {
    },
    // Dashboard edition
    dashboardEdition: false,
    widgetEdition: false,
    widgetSaving: false,
    widgetEditionForm: undefined,
    startWidgetEdition: () => {
    },
    saveWidgetEdition: () => {
    },
    cancelWidgetEdition: () => {
    },
    deleteWidget: () => {
    },
    // Human-readable name of the type of the widget
    widgetTypeName: undefined,
    // Adjusting the values received from the form
    onReceivingValues: undefined,
    onReceivingValuesHandler: (fn) => {
    },
})

export default function DashboardWidgetCellContextProvider({widget, children}) {

    const [title, setTitle] = useState("")
    const [extra, setExtra] = useState()

    const {registerWidget, saveWidget, ...dashboardContext} = useContext(DashboardContext)

    const [widgetEdition, setWidgetEdition] = useState(false)
    const [widgetSaving, setWidgetSaving] = useState(false)
    const [widgetEditionForm] = Form.useForm()

    const startWidgetEdition = () => {
        setWidgetEdition(true)
    }

    const onReceivingValues = useRef(undefined);

    const onReceivingValuesHandler = (func) => {
        onReceivingValues.current = func
    }

    const saveWidgetEdition = () => {
        if (widgetEdition) {
            setWidgetSaving(true)
            // See https://ant.design/components/form#forminstance
            widgetEditionForm.validateFields()
                .then(values => {
                    // console.log("DashboardWidgetCellContext@saveWidgetEdition@values", values)
                    // console.log("DashboardWidgetCellContext@saveWidgetEdition@onReceivingValues", !!onReceivingValues.current)
                    const actualValues = onReceivingValues.current ? onReceivingValues.current(values) : values
                    // console.log("DashboardWidgetCellContext@saveWidgetEdition@actualValues", actualValues)
                    return saveWidget(widget.uuid, actualValues)
                })
                .then(() => {
                    cancelWidgetEdition()
                })
                .finally(() => {
                    setWidgetSaving(false)
                })
        }
    }

    const cancelWidgetEdition = () => {
        if (widgetEdition) {
            widgetEditionForm.resetFields()
            setWidgetEdition(false)
        }
    }

    const deleteWidget = () => {
        dashboardContext.deleteWidget(widget.uuid)
    }

    const widgetEditionCallback = useCallback(() => widgetEdition, [widgetEdition])

    useEffect(() => {
        const unregister = registerWidget({
            uuid: widget.uuid,
            widgetEdition: widgetEditionCallback,
            validate: async () => {
                try {
                    const values = await widgetEditionForm.validateFields()
                    return onReceivingValues.current ? onReceivingValues.current(values) : values
                } catch (error) {
                    return false
                }
            },
        })
        return () => {
            unregister()
        }
    }, [widgetEditionCallback, widget.uuid, widgetEditionForm, registerWidget]);

    const context = {
        uuid: widget.uuid,
        title,
        setTitle,
        extra,
        setExtra,
        dashboardEdition: dashboardContext?.edition,
        widgetTypeName: dashboardContext?.widgetTypeName?.(widget.key),
        widgetEdition,
        widgetSaving,
        widgetEditionForm,
        startWidgetEdition,
        saveWidgetEdition,
        cancelWidgetEdition,
        deleteWidget,
        onReceivingValuesHandler,
    }

    return (
        <>
            <DashboardWidgetCellContext.Provider value={context}>
                {children}
            </DashboardWidgetCellContext.Provider>
        </>
    )
}