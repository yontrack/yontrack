import {Tag, Tooltip} from "antd";
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
 * The background is the label's own colour and the foreground is the one the
 * model computes from it (`Label.foregroundColor`), so the text stays readable
 * whichever colour was picked. The description, when there is one, is the
 * tooltip.
 *
 * `link` sends the chip to the label page; it is on by default because that is
 * what a chip does everywhere it is shown next to a project. A page which
 * already carries its own link to the label - the admin page, whose project
 * count goes there - passes `link={false}` rather than making the whole row
 * navigate.
 *
 * The queries feeding this component use `gqlLabelFragment`.
 */
export default function LabelChip({label, link = true}) {

    if (!label) return null

    const display = labelDisplay(label)

    const chip = <Tag
        data-testid={`label-${display}`}
        style={{
            backgroundColor: label.color,
            color: label.foregroundColor,
            borderColor: label.color,
            margin: 0,
        }}
    >
        {display}
    </Tag>

    const withTooltip = label.description ?
        <Tooltip title={label.description}>{chip}</Tooltip> :
        chip

    return link ?
        <Link href={projectLabelUri(label)}>{withTooltip}</Link> :
        withTooltip
}
