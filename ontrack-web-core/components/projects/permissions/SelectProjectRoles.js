import {Select, Typography} from "antd";

/**
 * Picks one or more project roles among the given ones.
 */
export default function SelectProjectRoles({id, value, onChange, roles}) {
    const options = roles.map(role => ({
        value: role.id,
        label: <Typography.Text title={role.description}>{role.name}</Typography.Text>,
        name: role.name,
    }))
    return (
        <Select
            id={id}
            data-testid={id}
            mode="multiple"
            value={value}
            onChange={onChange}
            options={options}
            showSearch={true}
            optionFilterProp="name"
            allowClear={true}
            placeholder="Roles"
            style={{
                minWidth: '16em',
            }}
        />
    )
}
