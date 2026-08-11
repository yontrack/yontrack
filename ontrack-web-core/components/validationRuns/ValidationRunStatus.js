import {Popover, Space} from "antd";
import ValidationRunStatusIcon from "@components/validationRuns/ValidationRunStatusIcon";
import Link from "next/link";

const CoreValidationRunStatus = ({id, status, displayText = true, text, onClick, href, nativeTooltip}) => {
    // 'full' when a visible label sits beside the mark, 'compact' when the mark
    // stands alone - see SIZE_VARIANTS in ValidationRunStatusConfig.
    const icon = <ValidationRunStatusIcon
        statusID={status.statusID}
        variant={displayText ? 'full' : 'compact'}
        tooltip={nativeTooltip}
    />
    return (
        <>
            <Space data-testid={id} size={8} className={onClick ? "ot-action" : undefined} onClick={onClick}>
                {
                    href && <Link href={href}>
                        {icon}
                    </Link>
                }
                {
                    !href && icon
                }
                {displayText && (text || status.statusID.name)}
            </Space>
        </>
    )
}

export default function ValidationRunStatus({
                                                id,
                                                status,
                                                tooltip = true,
                                                tooltipContent,
                                                displayText = true, text,
                                                onClick, href,
                                            }) {
    const core = <CoreValidationRunStatus
        id={id}
        status={status}
        displayText={displayText}
        text={text}
        onClick={onClick}
        href={href}
        // When the Popover below is showing the status name, suppress the mark's
        // own native tooltip so the two do not stack on the same element. The
        // accessible name on the mark is unaffected either way.
        nativeTooltip={tooltip ? false : undefined}
    />
    return (
        <>
            {
                tooltip && <Popover
                    title={status.statusID.name}
                    content={tooltipContent}
                    placement="bottom"
                >
                    <div>
                        {core}
                    </div>
                </Popover>
            }
            {
                !tooltip && core
            }
        </>
    )
}