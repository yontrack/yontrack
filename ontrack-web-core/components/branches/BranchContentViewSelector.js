import {FaWindowRestore} from "react-icons/fa";
import {Button, Dropdown, Space, Typography} from "antd";

/**
 * Selector for the branch content view, rendered in the command bar of the branch view.
 *
 * @param views List of available content views ({key, name, icon})
 * @param selectedViewKey Key of the currently displayed content view
 * @param onSelect Called with the key of the content view the user picks
 */
export default function BranchContentViewSelector({views, selectedViewKey, onSelect}) {

    const items = views.map(view => ({
        key: view.key,
        label: view.name,
        icon: view.icon,
        onClick: () => onSelect(view.key),
    }))

    return (
        <>
            {
                items.length > 0 &&
                <Dropdown
                    menu={{
                        selectedKeys: [selectedViewKey],
                        items,
                        'data-testid': 'branch-content-views',
                    }}
                    trigger={['click']}
                >
                    <Button type="text" title="Selection of the way to read this branch">
                        <Space size={8}>
                            <FaWindowRestore/>
                            <Typography.Text>View</Typography.Text>
                        </Space>
                    </Button>
                </Dropdown>
            }
        </>
    )
}
