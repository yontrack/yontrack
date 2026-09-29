import {Button, Popover} from "antd";
import {FaInfoCircle} from "react-icons/fa";

/**
 * An ⓘ explaining a part of the scorecard in a popover.
 *
 * The trigger is a button, so that the popover opens on focus for the keyboard as well as on hover
 * — a bare icon cannot be reached — and a popover rather than a tooltip, which holds structured
 * content such as label chips. A click does nothing else: in a column header, it must not reach the
 * header.
 */
export default function ScorecardInfo({label, title, content, testId}) {
    return (
        <Popover
            title={title}
            content={<div style={{maxWidth: 360}} data-testid={testId}>{content}</div>}
            trigger={['hover', 'focus']}
        >
            <Button
                type="text"
                size="small"
                icon={<FaInfoCircle/>}
                aria-label={label}
                onClick={e => e.stopPropagation()}
            />
        </Popover>
    )
}
