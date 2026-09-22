import {Children, createContext, useContext} from "react";
import {Empty, Flex, theme} from "antd";

/**
 * Plain list of items, replacing antd's deprecated `List` (#1853).
 *
 * Renders a semantic `ul` > `li`, with a divider between items. Any other prop, `data-testid`
 * included, is passed through to the `ul` - or, when the list is empty, to the element holding the
 * empty text, since a `ul` may only contain `li`s.
 *
 * @param emptyText What to display when there is no item (antd's "No data" when not set)
 * @param size `small` for a denser list, indented like antd's small `List`
 * @param component Component rendering the list instead of a plain `ul`, like a react-easy-sort
 * `SortableList` - it receives the `className` and `style` which draw the dividers, and must
 * render a `ul` itself (`as="ul"` for `SortableList`)
 */
export default function ItemList({emptyText, size, component: Component = 'ul', className, style, children, ...rest}) {
    const {token} = theme.useToken()

    if (Children.toArray(children).length === 0) {
        return (
            <div className={className} style={{padding: token.padding, ...style}} {...rest}>
                {emptyText ?? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}/>}
            </div>
        )
    }

    return (
        <ItemListContext.Provider value={{size}}>
            <Component
                className={['ot-item-list', className].filter(Boolean).join(' ')}
                style={{
                    // Read by the divider rule in `globals.css`: antd's own CSS variables are scoped to
                    // its components, so the token is handed over explicitly
                    '--ot-item-list-split': token.colorSplit,
                    ...style,
                }}
                {...rest}
            >
                {children}
            </Component>
        </ItemListContext.Provider>
    )
}

const ItemListContext = createContext({size: undefined})

/**
 * One item of an `ItemList`.
 *
 * The `avatar`, `title` and `description` form the item's heading, laid out like antd's
 * `List.Item.Meta`; the `children` come after it and the `actions` at the end of the line. Any other
 * prop - `data-testid`, `className`, the `ref` a react-easy-sort `SortableItem` sets - is passed
 * through to the `li`.
 */
function ItemListItem({avatar, title, description, actions, style, children, ...rest}) {
    const {token} = theme.useToken()
    const {size} = useContext(ItemListContext)

    const hasMeta = avatar || title || description

    return (
        <li
            style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: token.padding,
                padding: size === 'small'
                    ? `${token.paddingContentVerticalSM}px ${token.paddingContentHorizontal}px`
                    : `${token.paddingContentVertical}px 0`,
                color: token.colorText,
                ...style,
            }}
            {...rest}
        >
            {
                hasMeta &&
                // `width: 0`, like antd's `List.Item.Meta`: the heading takes the room the actions leave, and never
                // widens the item past its container when the list is laid out in a shrink-to-fit parent
                <Flex gap={token.padding} align="flex-start" flex="1 0 0" style={{width: 0}}>
                    {avatar && <div>{avatar}</div>}
                    <Flex vertical gap={token.marginXXS} flex={1} style={{minWidth: 0}}>
                        {title && <div>{title}</div>}
                        {description && <div style={{color: token.colorTextDescription}}>{description}</div>}
                    </Flex>
                </Flex>
            }
            {children}
            {
                actions && actions.length > 0 &&
                <Flex gap={token.padding} align="center" flex="none" style={{marginInlineStart: token.marginXXL}}>
                    {actions}
                </Flex>
            }
        </li>
    )
}

ItemList.Item = ItemListItem
