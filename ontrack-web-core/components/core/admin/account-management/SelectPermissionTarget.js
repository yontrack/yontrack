import {usePermissionTargets} from "@components/core/admin/account-management/GlobalPermissionsService";
import {useEffect, useState} from "react";
import {Select} from "antd";
import PermissionTarget from "@components/core/admin/account-management/PermissionTarget";

/**
 * Picks an account or a group. With a `projectId`, the ones to grant a role to on this project.
 */
export default function SelectPermissionTarget({id, value, onChange, projectId}) {
    const [token, setToken] = useState('')
    const {permissionTargets, loading} = usePermissionTargets({token, projectId})

    const [options, setOptions] = useState([])
    useEffect(() => {
        setOptions(permissionTargets.map(permissionTarget => ({
            value: `${permissionTarget.type}-${permissionTarget.id}`,
            label: <PermissionTarget target={permissionTarget} displayDescription={false}/>,
        })))
    }, [permissionTargets])

    const handleChange = (newValue) => {
        if (onChange) onChange(newValue)
    }

    const handleSearch = async (token) => {
        if (token && token.length > 0) {
            setToken(token)
        } else {
            setOptions([])
        }
    }

    const handleClear = () => {
        setOptions([])
        if (onChange) onChange(null)
    }

    return (
        <>
            <Select
                id={id}
                data-testid={id}
                showSearch={true}
                value={value}
                defaultActiveFirstOption={true}
                suffixIcon={null}
                filterOption={false}
                onSearch={handleSearch}
                onChange={handleChange}
                loading={loading}
                allowClear={true}
                onClear={handleClear}
                notFoundContent={null}
                options={options}
                style={{
                    width: '16em',
                }}
            />
        </>
    )
}
