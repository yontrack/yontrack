import {useState} from "react";
import {gql} from "graphql-request";
import {Alert, Button, Empty, Select, Space, Switch, Tooltip, Typography} from "antd";
import {FaCheck, FaCopy} from "react-icons/fa";
import copy from "copy-to-clipboard";
import GridCell from "@components/grid/GridCell";
import {useQuery} from "@components/services/GraphQL";
import useTemplatingRenderers from "@components/extension/scm/views/useTemplatingRenderers";

/**
 * The semantic change log: the commits between the two builds, grouped into sections by their
 * conventional-commit type, exactly as the templating renderer produces them for a
 * notification.
 *
 * The rendered string is shown **raw**, in every format, including `html` — the point of
 * choosing `jira` or `markdown` is to paste the result somewhere else, so the text is the
 * product. See `docs/adr/0008-change-log-views.md`.
 *
 * Every option is a query dependency, because the rendering is done server-side and there is
 * nothing here to re-render it from: changing one re-runs the change log, and with `issues` on
 * that includes resolving the issues against the issue service, which can be slow. That is the
 * cost of the design in #1699 — the alternative, rendering client-side, would duplicate the
 * templating renderer the notifications already use.
 *
 * @param id Identifier of the cell in the grid
 * @param from Id of the build the change log starts at
 * @param to Id of the build the change log stops at
 * @param options Current options: `format`, `emojis`, `issues`, `commits`
 * @param onOptionChange Called with (name, value) when the user changes an option
 */
export default function ChangeLogSemantic({id, from, to, options, onOptionChange}) {

    const {format, emojis, issues, commits} = options

    // Loaded by this cell, in parallel of the rest of the change log, and reloaded whenever an
    // option changes - the rendering is done server-side and there is nothing to render from
    // the change log the page already holds.
    const {data: semantic, loading: fetching, error, finished} = useQuery(
        gql`
            query SemanticChangeLog(
                $from: Int!,
                $to: Int!,
                $format: String!,
                $emojis: Boolean!,
                $issues: Boolean!,
            ) {
                scmChangeLog(from: $from, to: $to) {
                    semantic(
                        renderer: $format,
                        config: {
                            emojis: $emojis,
                            issues: $issues,
                        }
                    )
                }
            }
        `,
        {
            variables: {from, to, format, emojis, issues},
            deps: [from, to, format, emojis, issues],
            condition: !!from && !!to,
            initialData: '',
            dataFn: data => data.scmChangeLog?.semantic ?? '',
        }
    )

    // `useQuery` starts with `fetching` false and only flips it inside its effect
    const loading = fetching || !finished

    const renderers = useTemplatingRenderers()

    // What was copied, not whether something was: the rendering changes under this cell every
    // time an option is touched, and a plain `copied` flag would leave the button disabled and
    // reading "Copied" over text nobody has copied yet.
    const [copiedText, setCopiedText] = useState(null)
    const copied = !!semantic && copiedText === semantic

    const onCopy = () => {
        if (semantic && copy(semantic)) {
            setCopiedText(semantic)
        }
    }

    const toggle = (name, value, label, title) => (
        <Tooltip title={title} key={name}>
            <Space size={4}>
                <Switch
                    size="small"
                    checked={value}
                    onChange={(checked) => onOptionChange(name, checked)}
                    data-testid={`semantic-option-${name}`}
                />
                <Typography.Text type="secondary">{label}</Typography.Text>
            </Space>
        </Tooltip>
    )

    return (
        <GridCell
            id={id}
            title="Semantic change log"
            loading={loading}
            // Text, not a table: the commits and issues cells beside this one let their rows
            // bleed to the edges, but a block of rendered prose against the border reads as
            // cramped. Same choice as the boundary cells.
            padding={true}
            extra={
                <Space size={16}>
                    <Tooltip title="Syntax the change log is rendered in">
                        <Select
                            size="small"
                            value={format}
                            // The change log renders in whatever renderer the server offers;
                            // until they are loaded, the current format is the only option, so
                            // the control shows what is in use rather than an empty box.
                            options={
                                renderers.length > 0
                                    ? renderers.map(it => ({value: it.id, label: it.name}))
                                    : [{value: format, label: format}]
                            }
                            onChange={(value) => onOptionChange('format', value)}
                            style={{minWidth: 120}}
                            data-testid="semantic-option-format"
                        />
                    </Tooltip>
                    {toggle('emojis', emojis, "Emojis", "Emojis in the section titles")}
                    {toggle('issues', issues, "Issues", "An issues section inside the rendered text")}
                    {toggle('commits', commits, "Commits", "Shows the commits beside this panel")}
                    <Button
                        size="small"
                        icon={copied ? <FaCheck/> : <FaCopy/>}
                        disabled={!semantic || copied}
                        onClick={onCopy}
                        data-testid="semantic-copy"
                    >
                        {copied ? "Copied" : "Copy"}
                    </Button>
                </Space>
            }
        >
            {
                error &&
                <Alert type="error" showIcon title="The semantic change log could not be rendered."/>
            }
            {
                !error && !loading && !semantic &&
                <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={
                        <Typography.Text data-testid="semantic-empty">
                            The commits between these builds carry no conventional-commit types,
                            so there is nothing to group. Turn on <b>Commits</b> to read them as
                            they are.
                        </Typography.Text>
                    }
                />
            }
            {
                !error && semantic &&
                <Typography.Text
                    className="ot-semantic-change-log"
                    data-testid="semantic-content"
                >
                    {semantic}
                </Typography.Text>
            }
        </GridCell>
    )
}
