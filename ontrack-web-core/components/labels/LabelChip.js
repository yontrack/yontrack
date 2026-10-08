import {Tag, theme, Tooltip} from "antd";
import Link from "next/link";
import {projectLabelUri} from "@components/common/Links";

/**
 * Display string of a label: `category:name`, or `name` alone when the label
 * has no category.
 */
export function labelDisplay(label) {
    if (!label) return ''
    return label.category ? `${label.category}:${label.name}` : label.name
}

/**
 * The one chip used to display a label everywhere - the admin page, the project
 * page, the project lists.
 *
 * It comes in two variants:
 *
 * - `solid`, the default: the background is the label's own colour and the
 *   foreground is the one the model computes from it (`Label.foregroundColor`),
 *   so the text stays readable whichever colour was picked. It is the chip of
 *   the places where the label is the subject - the admin page, the project
 *   page header, the labels dialog, the label selector.
 * - `quiet`: neutral text and a neutral hairline border, with no fill, the
 *   label's colour being only a small decorative dot before the text. It is the
 *   chip of the project lists (`ProjectBox`), where the project name, not its
 *   labels, must draw the eye. The text still reads `category:name`, so the
 *   colour never carries meaning on its own. The neutral colours are antd's
 *   tokens, so that the chip follows the dark theme.
 *
 * The rendered chip carries its variant as `data-variant`.
 *
 * The description, when there is one, is the tooltip.
 *
 * `link` sends the chip to the label page; it is on by default because that is
 * what a chip does everywhere it is shown next to a project. A page which
 * already carries its own link to the label - the admin page, whose project
 * count goes there - passes `link={false}` rather than making the whole row
 * navigate.
 *
 * The queries feeding this component use `gqlLabelFragment`.
 */
export default function LabelChip({label, link = true, variant = "solid"}) {

    const {token} = theme.useToken()

    if (!label) return null

    const display = labelDisplay(label)
    const quiet = variant === "quiet"

    const chip = <Tag
        data-testid={`label-${display}`}
        data-variant={quiet ? "quiet" : "solid"}
        style={quiet ? {
            backgroundColor: 'transparent',
            color: token.colorTextSecondary,
            borderColor: token.colorBorder,
            margin: 0,
        } : {
            backgroundColor: label.color,
            color: label.foregroundColor,
            borderColor: label.color,
            margin: 0,
        }}
    >
        {
            quiet && <span
                data-testid="label-dot"
                aria-hidden="true"
                style={{
                    display: 'inline-block',
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    backgroundColor: label.color,
                    // A white label stays visible on a white box, a black one on a dark box
                    boxShadow: `inset 0 0 0 1px ${token.colorBorder}`,
                    marginRight: 6,
                    verticalAlign: 'middle',
                }}
            />
        }
        {display}
    </Tag>

    const withTooltip = label.description ?
        <Tooltip title={label.description}>{chip}</Tooltip> :
        chip

    return link ?
        <Link href={projectLabelUri(label)}>{withTooltip}</Link> :
        withTooltip
}
