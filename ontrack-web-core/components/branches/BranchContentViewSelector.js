import {FaFlask, FaWindowRestore} from "react-icons/fa";
import {Button, Dropdown, Space, Tooltip, Typography} from "antd";

/**
 * Selector for the branch content view, rendered in the command bar of the branch view.
 *
 * @param views List of available content views ({key, name, icon, experimental})
 * @param selectedViewKey Key of the currently displayed content view
 * @param onSelect Called with the key of the content view the user picks
 */
export default function BranchContentViewSelector({views, selectedViewKey, onSelect}) {

    // The quiet half of the experimental marking: seen before opting in, and never dismissible.
    // The loud half - the invitation to give feedback - is the alert inside the view itself.
    // No link here: this sits inside a clickable menu item which closes on click, so a nested
    // link would fight both the item's own handler and the menu closing.
    const viewLabel = view => view.experimental
        ? <Space size={6}>
            {view.name}
            <Tooltip title="This view is experimental. Feedback is welcome.">
                <span data-testid={`branch-content-view-experimental-${view.key}`}>
                    <FaFlask/>
                </span>
            </Tooltip>
        </Space>
        : view.name

    const items = views.map(view => ({
        key: view.key,
        label: viewLabel(view),
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
