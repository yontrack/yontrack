import {Button, Dropdown, Form, InputNumber, Popover, Select, Space, Typography} from "antd";
import {useEffect, useState} from "react";
import dayjs from "dayjs";
import duration from "dayjs/plugin/duration";
import {FaHourglassHalf} from "react-icons/fa";

dayjs.extend(duration);

export default function DurationPicker({
                                           value, onChange, disabled,
                                           inMilliseconds = false,
                                           maxUnit = undefined,
                                       }) {

    const [years, setYears] = useState(0)
    const [months, setMonths] = useState(0)
    const [days, setDays] = useState(0)
    const [hours, setHours] = useState(0)
    const [minutes, setMinutes] = useState(0)
    const [seconds, setSeconds] = useState(0)
    const [milliseconds, setMilliseconds] = useState(0)

    const [ready, setReady] = useState(false)

    useEffect(() => {
        const duration = dayjs.duration(value, inMilliseconds ? "milliseconds" : "seconds")
        setYears(duration.get('year'))
        setMonths(duration.get('month'))
        setDays(duration.get('day'))
        setHours(duration.get('hour'))
        setMinutes(duration.get('minute'))
        setSeconds(duration.get('second'))
        setMilliseconds(duration.get('millisecond'))
        setReady(true)
    }, [value]);

    useEffect(() => {
        if (ready) {
            const duration = dayjs.duration({
                years,
                months,
                days,
                hours,
                minutes,
                seconds,
                milliseconds: inMilliseconds ? milliseconds : 0,
            })
            if (onChange) {
                onChange(inMilliseconds ? duration.asMilliseconds() : duration.asSeconds())
            }
        }
    }, [ready, years, months, days, hours, minutes, seconds, milliseconds]);

    const formatUnit = (count, unit) => {
        if (count > 0) {
            const suffix = count > 1 ? `${unit}s` : unit
            return `${count} ${suffix} `
        } else {
            return ''
        }
    }

    const formatDuration = () => {
        let result = ''
        result += formatUnit(years, 'year')
        result += formatUnit(months, 'month')
        result += formatUnit(days, 'day')
        result += formatUnit(hours, 'hour')
        result += formatUnit(minutes, 'minute')
        result += formatUnit(seconds, 'second')
        if (inMilliseconds) {
            result += formatUnit(milliseconds, 'second')
        }
        return result
    }

    const formFields = [
        {
            id: 'year',
            label: "Years",
            value: years,
            setValue: setYears,
        },
        {
            id: 'month',
            label: "Months",
            value: months,
            setValue: setMonths,
        },
        {
            id: 'day',
            label: "Days",
            value: days,
            setValue: setDays,
        },
        {
            id: 'hour',
            label: "Hours",
            value: hours,
            setValue: setHours,
        },
        {
            id: 'minute',
            label: "Minutes",
            value: minutes,
            setValue: setMinutes,
        },
        {
            id: 'second',
            label: "Seconds",
            value: seconds,
            setValue: setSeconds,
        },
    ]

    if (inMilliseconds) {
        formFields.push({
            id: 'millisecond',
            label: "Millis",
            value: milliseconds,
            setValue: setMilliseconds,
        })
    }

    const fieldsToUse = []
    if (maxUnit) {
        const maxEligible = formFields.findIndex(it => it.id === maxUnit)
        fieldsToUse.push(...formFields.slice(maxEligible))
    } else {
        fieldsToUse.push(...formFields)
    }

    const formContent = (
        <Form>
            {
                fieldsToUse.map(field => (
                    <Form.Item
                        key={field.id}
                        label={field.label}
                    >
                        <InputNumber
                            min={0}
                            value={field.value}
                            onChange={value => field.setValue(value)}
                            style={{width: '5em'}}
                        />
                    </Form.Item>
                ))
            }
        </Form>
    )

    return (
        <>
            <Space>
                <Popover content={formContent} trigger="click">
                    <Button
                        icon={<FaHourglassHalf/>}
                        title="Edit the duration"
                        disabled={disabled}
                    />
                </Popover>
                <Typography.Text>
                    {
                        formatDuration()
                    }
                </Typography.Text>
            </Space>
        </>
    )
}