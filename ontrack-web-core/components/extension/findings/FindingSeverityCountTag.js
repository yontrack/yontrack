import {Tag, Tooltip, Typography} from "antd";
import Link from "next/link";
import {
    findingsCountText,
    findingSeverityColor,
    severityName,
} from "@components/extension/findings/findingsModel";

/**
 * Number of findings of a severity, as a tag filled with the colour of the severity.
 *
 * The colour only doubles the text, it never replaces it: the tag reads `Critical 3`, or `C 3` in
 * its short form, for where space is short — the short form names the severity in a tooltip. Its
 * label spells it out for a screen reader: on the link when there is one, else on the tag itself,
 * exposed as an image so that the label is read rather than `C 3`. A zero count is muted — outlined, not coloured — and links nowhere,
 * so that a row of counts keeps its layout and only draws the eye where there is something.
 *
 * @param severity Severity
 * @param count Number of findings
 * @param href Findings page the count leads to, for a count which is not zero
 * @param short Initial of the severity instead of its name
 * @param testId Test ID
 */
export default function FindingSeverityCountTag({severity, count, href, short = false, testId}) {
    const name = severityName(severity)
    const text = `${short ? name.charAt(0) : name} ${count}`
    const label = findingsCountText(count, severity?.toLowerCase())

    // Without a link to carry the label, the tag carries it
    const labelling = href && count ? {} : {role: 'img', 'aria-label': label}
    const withTooltip = (tag) => short ? <Tooltip title={label}>{tag}</Tooltip> : tag

    if (!count) {
        return withTooltip(
            <Tag variant="outlined" data-testid={testId} {...labelling}>
                <Typography.Text type="secondary">{text}</Typography.Text>
            </Tag>
        )
    }

    const {background, text: color} = findingSeverityColor(severity)
    const tag = withTooltip(
        <Tag
            variant="solid"
            color={background}
            style={{backgroundColor: background, color, borderColor: background}}
            data-testid={testId}
            {...labelling}
        >
            {text}
        </Tag>
    )
    return href ?
        <Link href={href} aria-label={label} title={label}>{tag}</Link> :
        tag
}
