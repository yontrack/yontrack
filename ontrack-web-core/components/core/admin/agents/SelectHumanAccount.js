import {Select} from "antd";
import {useHumanAccounts} from "@components/core/admin/agents/AgentsService";

/**
 * Selection of a person, by email, for an administrator: the owner of an agent.
 */
export default function SelectHumanAccount({id, value, onChange, allowClear = false, placeholder}) {
    const {accounts, loading} = useHumanAccounts({})
    return (
        <Select
            id={id}
            value={value}
            onChange={onChange}
            loading={loading}
            showSearch={{
                optionFilterProp: 'label',
            }}
            allowClear={allowClear}
            placeholder={placeholder}
            options={accounts.map(account => ({
                value: account.email,
                label: `${account.fullName} (${account.email})`,
            }))}
        />
    )
}
