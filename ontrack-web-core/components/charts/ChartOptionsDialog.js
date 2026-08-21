import {createContext, useContext, useEffect, useState} from "react";
import ChartOptionsCommand from "@components/charts/ChartOptionsCommand";
import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import SelectChartInterval from "@components/charts/SelectChartInterval";
import {Form} from "antd";
import SelectChartPeriod from "@components/charts/SelectChartPeriod";
import {DEFAULT_CHART_INTERVAL} from "@components/charts/ChartInterval";
import {DEFAULT_CHART_PERIOD} from "@components/charts/ChartPeriod";
import {getLocalChartOptions, setLocalChartOptions} from "@components/storage/local";

const ChartOptionsCommandContext = createContext({
    dialog: {},
    interval: DEFAULT_CHART_INTERVAL,
    period: DEFAULT_CHART_PERIOD,
})

export const useChartOptionsCommand = () => {

    const {dialog, interval, period} = useContext(ChartOptionsCommandContext)

    const onOpen = () => {
        dialog.start({interval, period})
    }

    const command = <ChartOptionsCommand
        interval={interval}
        period={period}
        onClick={onOpen}
    />

    return {
        command,
        interval,
        period,
    }
}

export const ChartOptionsDialog = ({chartOptionsDialog}) => {
    return (
        <>
            <FormDialog id="chart-options-dialog" dialog={chartOptionsDialog}>
                <Form.Item name="interval"
                           label="Interval"
                           rules={[
                               {
                                   required: true,
                                   message: 'Interval is required.',
                               },
                           ]}
                >
                    <SelectChartInterval/>
                </Form.Item>
                <Form.Item name="period"
                           label="Period"
                           rules={[
                               {
                                   required: true,
                                   message: 'Period is required.',
                               },
                           ]}
                >
                    <SelectChartPeriod/>
                </Form.Item>
            </FormDialog>
        </>
    )
}

export default function StoredChartOptionsCommandContextProvider({id, children}) {

    const [interval, setInterval] = useState(DEFAULT_CHART_INTERVAL)
    const [period, setPeriod] = useState(DEFAULT_CHART_PERIOD)

    useEffect(() => {
        const options = getLocalChartOptions(id)
        if (options) {
            setInterval(options.interval ?? DEFAULT_CHART_INTERVAL)
            setPeriod(options.period ?? DEFAULT_CHART_PERIOD)
        }
    }, []);

    const onSuccess = ({interval, period}) => {
        setInterval(interval)
        setPeriod(period)
        // Saves the values into the local storage
        setLocalChartOptions(id, {interval, period})
    }

    const dialog = useFormDialog({
        onSuccess: onSuccess,
        init: (form, {interval, period}) => {
            form.setFieldsValue({interval, period})
        }
    })

    const context = {
        dialog,
        interval,
        period,
    }

    return (
        <ChartOptionsCommandContext.Provider value={context}>
            {children}
            <ChartOptionsDialog chartOptionsDialog={dialog}/>
        </ChartOptionsCommandContext.Provider>
    )
}