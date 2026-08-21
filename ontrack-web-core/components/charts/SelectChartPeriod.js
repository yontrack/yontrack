import {Select, Typography} from "antd";
import {periods} from "@components/charts/ChartPeriod";

export default function SelectChartPeriod({value, onChange, ...props}) {

    const options = periods.map(it => ({
        value: it.id,
        label: <Typography.Text>
            {it.name}
            <Typography.Text disabled>&nbsp;({it.id})</Typography.Text>
        </Typography.Text>
    }))

    return (
        <>
            <Select
                {...props}
                options={options}
                value={value}
                onChange={onChange}
            />
        </>
    )
}