import {Fragment, useContext, useEffect, useId, useRef, useState} from "react";
import {Input, Modal, Space, Spin, theme, Typography} from "antd";
import {FaBars, FaSearch} from "react-icons/fa";
import {gql} from "graphql-request";
import {useRouter} from "next/router";
import {useQuery} from "@components/services/GraphQL";
import {UserContext} from "@components/providers/UserProvider";
import {Dynamic} from "@components/common/Dynamic";
import {getLocalRecentlyVisited} from "@components/storage/local";
import {
    countLabel,
    MIN_SEARCH_LENGTH,
    paletteSections,
    RESULTS_PER_TYPE
} from "@components/search/palette/paletteSections";
import {useIsMacPlatform} from "@components/search/palette/platform";
import {resultContext} from "@components/search/searchResultContext";

/**
 * Delay between the last key typed and the search.
 */
const SEARCH_DEBOUNCE_MS = 250

const SEARCH_QUERY = gql`
    query PaletteSearch($query: String!, $perType: Int!) {
        search(query: $query, perType: $perType) {
            total
            capped
            message
            facets {
                type {
                    id
                    name
                    description
                }
                count
                capped
            }
            items {
                type {
                    id
                    name
                    description
                }
                title
                description
                data
            }
        }
    }
`

/**
 * The value, once it has stopped changing for the given delay.
 */
function useDebouncedValue(value, delay) {
    const [debounced, setDebounced] = useState(value)
    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delay)
        return () => clearTimeout(timer)
    }, [value, delay])
    return debounced
}

const accessibleNameOf = (option) => {
    switch (option.kind) {
        case 'recent':
            return [option.entry.name, option.entry.context].filter(Boolean).join(', ')
        case 'menu':
            return `${option.item.name}, ${option.group.name}`
        case 'result':
            return [
                option.result.title,
                option.result.type?.name,
                resultContext(option.result),
            ].filter(Boolean).join(', ')
        case 'all':
            return `See all results for "${option.text}"`
        default:
            return ''
    }
}

function OptionContent({option}) {
    switch (option.kind) {
        case 'recent':
            return (
                <Space size="small">
                    <Dynamic path={`framework/search/${option.entry.type}/Icon`}/>
                    <Typography.Text>{option.entry.name}</Typography.Text>
                    {
                        option.entry.context &&
                        <Typography.Text type="secondary">{option.entry.context}</Typography.Text>
                    }
                </Space>
            )
        case 'menu':
            return (
                <Space size="small">
                    <FaBars aria-hidden="true"/>
                    <Typography.Text>{option.item.name}</Typography.Text>
                    <Typography.Text type="secondary">{option.group.name}</Typography.Text>
                </Space>
            )
        case 'result':
            // The per-type component draws the result with links of its own, to the project of a
            // branch for example. Inside an option they would be interactive content nested in an
            // interactive element: `inert` makes them a picture of the result, the option itself
            // being what opens it - by click as by keyboard - and carrying its accessible name.
            return (
                <div inert className="ot-palette-result">
                    <Dynamic path={`framework/search/${option.result.type.id}/Result`} props={option.result}/>
                </div>
            )
        case 'all':
            return (
                <Space size="small">
                    <FaSearch aria-hidden="true"/>
                    <Typography.Text>See all results for &quot;{option.text}&quot;</Typography.Text>
                </Space>
            )
        default:
            return null
    }
}

function SectionHeader({section}) {
    if (section.kind === 'type') {
        return (
            <Space size="small">
                <Dynamic path={`framework/search/${section.type.id}/Icon`}/>
                <span>{section.type.name}</span>
                <Typography.Text type="secondary">({countLabel(section.count, section.capped)})</Typography.Text>
            </Space>
        )
    }
    return section.title
}

function Kbd({children}) {
    return <kbd className="ot-kbd">{children}</kbd>
}

/**
 * The ⌘K command palette (#1884).
 *
 * A dialog holding a combobox - the text field - which drives a listbox of options, following the
 * WAI-ARIA combobox pattern: the focus stays in the text field, ↑ and ↓ move the active option,
 * which the field points at with `aria-activedescendant`.
 */
export default function CommandPalette({onClose}) {

    const router = useRouter()
    const user = useContext(UserContext)
    const isMac = useIsMacPlatform()
    const {token} = theme.useToken()

    const id = useId()
    const listboxId = `${id}-listbox`
    const optionId = (index) => `${id}-option-${index}`

    const inputRef = useRef(null)
    useEffect(() => {
        inputRef.current?.focus()
    }, [])

    const [text, setText] = useState('')
    const [activeIndex, setActiveIndex] = useState(0)

    // Read once per opening: the list does not change while the palette is open
    const [recent] = useState(() => getLocalRecentlyVisited())

    const query = useDebouncedValue(text.trim(), SEARCH_DEBOUNCE_MS)
    // The results of the last search stay while the next one is typed, rather than blinking out
    const searching = query.length >= MIN_SEARCH_LENGTH && text.trim().length >= MIN_SEARCH_LENGTH
    const {data: search, loading} = useQuery(SEARCH_QUERY, {
        variables: {query, perType: RESULTS_PER_TYPE},
        deps: [query],
        condition: query.length >= MIN_SEARCH_LENGTH,
        dataFn: data => data.search,
    })

    const sections = paletteSections({
        text,
        recent,
        menuGroups: user?.userMenuGroups ?? [],
        search: searching ? search : null,
    })
    const options = sections.flatMap(section => section.options)
    const indexOf = new Map(options.map((option, index) => [option.key, index]))
    const active = options.length > 0 ? Math.min(activeIndex, options.length - 1) : -1

    useEffect(() => {
        if (active >= 0) {
            document.getElementById(optionId(active))?.scrollIntoView?.({block: 'nearest'})
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [active])

    const openOption = (option, newTab) => {
        if (!option?.href) return
        if (newTab) {
            window.open(option.href, '_blank', 'noopener')
        } else {
            onClose()
            if (option.href.startsWith('/')) {
                router.push(option.href)
            } else {
                window.location.assign(option.href)
            }
        }
    }

    const onKeyDown = (event) => {
        if (event.nativeEvent?.isComposing) return
        switch (event.key) {
            case 'ArrowDown':
                event.preventDefault()
                if (options.length > 0) setActiveIndex((active + 1) % options.length)
                break
            case 'ArrowUp':
                event.preventDefault()
                if (options.length > 0) setActiveIndex((active - 1 + options.length) % options.length)
                break
            case 'Home':
                if (options.length > 0 && event.ctrlKey) {
                    event.preventDefault()
                    setActiveIndex(0)
                }
                break
            case 'End':
                if (options.length > 0 && event.ctrlKey) {
                    event.preventDefault()
                    setActiveIndex(options.length - 1)
                }
                break
            case 'Enter':
                event.preventDefault()
                if (active >= 0) openOption(options[active], event.metaKey || event.ctrlKey)
                break
            case 'Escape':
                event.preventDefault()
                event.stopPropagation()
                onClose()
                break
            default:
                break
        }
    }

    const onChange = (event) => {
        setText(event.target.value)
        setActiveIndex(0)
    }

    const pending = text.trim().length >= MIN_SEARCH_LENGTH && (loading || query !== text.trim())
    const resultCount = searching && search ? search.items?.length ?? 0 : null
    const status = pending ?
        'Searching…' :
        resultCount !== null ?
            (resultCount === 0 ? 'No results' : `${resultCount} results`) :
            ''

    return (
        <Modal
            open={true}
            onCancel={onClose}
            title={<span className="ot-visually-hidden">Search</span>}
            closable={false}
            footer={null}
            width={640}
            style={{top: 80}}
            styles={{
                header: {margin: 0, padding: 0},
                body: {padding: 0},
                container: {padding: 0, overflow: 'hidden'},
            }}
            data-testid="command-palette"
        >
            <div className="ot-palette">
                <div style={{padding: '8px 12px', borderBottom: `1px solid ${token.colorSplit}`}}>
                    <Input
                        ref={inputRef}
                        role="combobox"
                        aria-label="Search"
                        aria-expanded={options.length > 0}
                        aria-controls={listboxId}
                        aria-autocomplete="list"
                        aria-activedescendant={active >= 0 ? optionId(active) : undefined}
                        aria-keyshortcuts={isMac ? 'Meta+K' : 'Control+K'}
                        autoComplete="off"
                        spellCheck={false}
                        placeholder="Search projects, branches, builds, commits, issues… or a page"
                        variant="borderless"
                        size="large"
                        prefix={<FaSearch aria-hidden="true"/>}
                        suffix={pending ? <Spin size="small"/> : <span/>}
                        value={text}
                        onChange={onChange}
                        onKeyDown={onKeyDown}
                        data-testid="command-palette-input"
                    />
                </div>
                {
                    searching && search?.message &&
                    <div style={{padding: '8px 16px'}}>
                        <Typography.Text type="warning">{search.message}</Typography.Text>
                    </div>
                }
                <div
                    role="listbox"
                    id={listboxId}
                    aria-label="Search results"
                    className="ot-palette-listbox"
                    style={{maxHeight: '60vh', overflowY: 'auto'}}
                >
                    {
                        sections.map(section => (
                            <div key={section.key} role="group" aria-label={section.title}>
                                <div
                                    className="ot-palette-group"
                                    aria-hidden="true"
                                    style={{
                                        padding: '8px 16px 4px',
                                        color: token.colorTextSecondary,
                                        fontSize: token.fontSizeSM,
                                        fontWeight: 600,
                                    }}
                                >
                                    <SectionHeader section={section}/>
                                </div>
                                {
                                    section.options.map(option => {
                                        const optionIndex = indexOf.get(option.key)
                                        const selected = optionIndex === active
                                        return (
                                            <div
                                                key={option.key}
                                                id={optionId(optionIndex)}
                                                role="option"
                                                aria-selected={selected}
                                                aria-label={accessibleNameOf(option)}
                                                className="ot-palette-option"
                                                data-testid={`command-palette-option-${option.kind}`}
                                                onMouseMove={() => {
                                                    if (!selected) setActiveIndex(optionIndex)
                                                }}
                                                onMouseDown={(event) => {
                                                    // Keeps the focus in the text field
                                                    event.preventDefault()
                                                }}
                                                onClick={(event) => openOption(option, event.metaKey || event.ctrlKey)}
                                                style={{
                                                    padding: '6px 16px 6px 13px',
                                                    cursor: 'pointer',
                                                    borderLeft: `3px solid ${selected ? token.colorPrimary : 'transparent'}`,
                                                    background: selected ? token.controlItemBgActive : undefined,
                                                }}
                                            >
                                                <OptionContent option={option}/>
                                            </div>
                                        )
                                    })
                                }
                            </div>
                        ))
                    }
                    {
                        !text.trim() && options.length === 0 &&
                        <div style={{padding: '12px 16px'}}>
                            <Typography.Text type="secondary">
                                Pages you visit are listed here. Type to search.
                            </Typography.Text>
                        </div>
                    }
                </div>
                <div role="status" className="ot-visually-hidden">{status}</div>
                <div
                    style={{
                        padding: '6px 16px',
                        borderTop: `1px solid ${token.colorSplit}`,
                        color: token.colorTextSecondary,
                        fontSize: token.fontSizeSM,
                    }}
                >
                    <Space size="middle" wrap>
                        <span><Kbd>↑</Kbd><Kbd>↓</Kbd> to move</span>
                        <span><Kbd>Enter</Kbd> to open</span>
                        <span><Kbd>{isMac ? '⌘' : 'Ctrl'}</Kbd><Kbd>Enter</Kbd> in a new tab</span>
                        <span><Kbd>Esc</Kbd> to close</span>
                    </Space>
                </div>
            </div>
        </Modal>
    )
}
