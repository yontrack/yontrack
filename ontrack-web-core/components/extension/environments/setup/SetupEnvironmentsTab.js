import {useContext} from "react"
import {Button, Space, Table, Tag, Typography} from "antd"
import {FaPlus} from "react-icons/fa"
import {UserContext} from "@components/providers/UserProvider"
import EnvironmentEditableIcon from "@components/extension/environments/EnvironmentEditableIcon"
import DeleteEnvironmentButton from "@components/extension/environments/DeleteEnvironmentButton"
import NewEnvironmentDialog, {
    useNewEnvironmentDialog,
} from "@components/extension/environments/NewEnvironmentDialog"

/**
 * The **Environments** tab of Setup: every environment, in `order`, with what can be done to it.
 *
 * A flat table rather than the cards the old Environments home used. Cards were right when this was
 * also the operational screen and each one had to show its slots' state; here nothing is
 * operational, the questions are "in what order do these run", "what are they tagged", "how many
 * slots hang off this one" - and a table answers all three by being read down a column.
 *
 * The icon is editable in place, which is the one edit that has nowhere else to live: it is an
 * upload, not a field of the environment's form.
 *
 * @param {Array} environments The environments, already sorted.
 * @param {function} onChange Called after any change, so the page asks the server again.
 */
export default function SetupEnvironmentsTab({environments, onChange}) {

    const user = useContext(UserContext)
    const canCreate = !!user.authorizations?.environment?.create

    const newEnvironmentDialog = useNewEnvironmentDialog()

    return (
        <>
            <Table
                dataSource={environments}
                rowKey={environment => environment.id}
                pagination={false}
                size="small"
                data-testid="setup-environments"
                onRow={environment => ({'data-testid': `setup-environment-${environment.id}`})}
                footer={() =>
                    canCreate &&
                    <Button
                        icon={<FaPlus/>}
                        data-testid="setup-new-environment"
                        onClick={() => newEnvironmentDialog.start({})}
                    >
                        New environment
                    </Button>
                }
                columns={[
                    {
                        key: 'icon',
                        title: 'Icon',
                        render: (_, environment) => <EnvironmentEditableIcon environment={environment}/>,
                    },
                    {
                        key: 'order',
                        title: 'Order',
                        dataIndex: 'order',
                    },
                    {
                        key: 'name',
                        title: 'Name',
                        render: (_, environment) => <Typography.Text strong>{environment.name}</Typography.Text>,
                    },
                    {
                        key: 'description',
                        title: 'Description',
                        dataIndex: 'description',
                    },
                    {
                        key: 'tags',
                        title: 'Tags',
                        render: (_, environment) => <Space size={4} wrap>
                            {(environment.tags ?? []).map(tag => <Tag key={tag}>{tag}</Tag>)}
                        </Space>,
                    },
                    {
                        key: 'slots',
                        title: 'Slots',
                        render: (_, environment) => <Typography.Text
                            data-testid={`setup-environment-slots-${environment.id}`}
                        >
                            {(environment.slots ?? []).length}
                        </Typography.Text>,
                    },
                    {
                        key: 'actions',
                        title: 'Actions',
                        render: (_, environment) => <DeleteEnvironmentButton environment={environment}/>,
                    },
                ]}
            />
            <NewEnvironmentDialog newEnvironmentDialog={newEnvironmentDialog}/>
        </>
    )
}
