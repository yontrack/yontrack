import React, {createContext, useContext, useEffect, useRef, useState} from "react";
import {Button, Dropdown, Space, Tooltip, Typography} from "antd";
import {EllipsisOutlined} from "@ant-design/icons";
import {FaSync} from "react-icons/fa";
import SelectableMenuItem from "@components/common/SelectableMenuItem";

export const AutoRefreshContext = createContext({
    autoRefreshEnabled: false,
    toggleEnabled: () => {
    },
    autoRefreshIntervalSeconds: 60,
    setAutoRefreshIntervalSeconds: () => {
    },
    autoRefreshCount: 0,
})

export function AutoRefreshContextProvider({children, onRefresh}) {

    const autoRefreshIdRef = useRef(0)
    const [autoRefreshCount, setAutoRefreshCount] = useState(0)
    const [autoRefreshEnabled, setAutoRefreshEnabled] = useState(false)
    const [autoRefreshIntervalSeconds, setAutoRefreshIntervalSeconds] = useState(60)
    const autoRefreshIntervalSecondsRef = useRef(autoRefreshIntervalSeconds)

    const toggleEnabled = () => {
        setAutoRefreshEnabled(state => !state)
    }

    const context = {
        autoRefreshEnabled,
        toggleEnabled,
        autoRefreshIntervalSeconds,
        setAutoRefreshIntervalSeconds,
        autoRefreshCount,
    }

    const refresh = () => {
        setAutoRefreshCount(count => count + 1)
        if (onRefresh) {
            onRefresh()
        }
    }

    const close = () => {
        if (autoRefreshIdRef.current) {
            clearInterval(autoRefreshIdRef.current)
            autoRefreshIdRef.current = 0
        }
    }

    const startRefresh = () => {
        if (autoRefreshIntervalSecondsRef.current > 0) {
            autoRefreshIdRef.current = setInterval(refresh, autoRefreshIntervalSecondsRef.current * 1000)
        }
    }

    useEffect(() => {
        if (autoRefreshEnabled) {
            if (!autoRefreshIdRef.current) {
                refresh()
                startRefresh()
            }
        } else {
            close()
        }
    }, [autoRefreshEnabled])

    useEffect(() => {
        if (autoRefreshIdRef.current && autoRefreshEnabled) {
            clearInterval(autoRefreshIdRef.current)
            autoRefreshIntervalSecondsRef.current = autoRefreshIntervalSeconds
            startRefresh()
        }
    }, [autoRefreshIntervalSeconds, autoRefreshEnabled]);

    useEffect(() => {
        return () => {
            close()
        }
    }, []);

    return (
        <>
            <AutoRefreshContext.Provider value={context}>
                {children}
            </AutoRefreshContext.Provider>
        </>
    )
}

export function AutoRefreshButton({size = undefined}) {

    const autoRefresh = useContext(AutoRefreshContext)

    const allowedValues = [
        [5, "Every 5 seconds"],
        [10, "Every 10 seconds"],
        [30, "Every 30 seconds"],
        [60, "Every minute"],
        [120, "Every 2 minutes"],
        [300, "Every 5 minutes"],
        [600, "Every 10 minutes"],
    ]

    const items = allowedValues.map(([seconds, text]) => ({
        key: String(seconds),
        label: <SelectableMenuItem
            value={autoRefresh.autoRefreshIntervalSeconds === seconds}
            text={text}
        />,
    }))

    const handleMenuClick = (e) => {
        const seconds = Number(e.key)
        autoRefresh.setAutoRefreshIntervalSeconds(seconds)
    }

    const menuProps = {
        items,
        onClick: handleMenuClick,
    }

    const onButtonClick = () => {
        autoRefresh.toggleEnabled()
    }

    return (
        <Space.Compact
            size={size}
            className={autoRefresh.autoRefreshEnabled ? "ot-auto-refresh ot-auto-refresh-enabled" : "ot-auto-refresh"}
        >
            <Tooltip title={
                autoRefresh.autoRefreshEnabled ? "Auto refresh is enabled. Click to disable." : "No auto refresh. Click to enable it."
            }>
                <Button onClick={onButtonClick}>
                    <Space>
                        <FaSync/>
                        <Typography.Text>Auto refresh</Typography.Text>
                    </Space>
                </Button>
            </Tooltip>
            <Dropdown menu={menuProps}>
                <Tooltip title="Intervals between each refresh.">
                    <Button icon={<EllipsisOutlined/>} aria-label="Refresh interval"/>
                </Tooltip>
            </Dropdown>
        </Space.Compact>
    )
}