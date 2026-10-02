import {Popover, Space} from "antd";
import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import ValidationGroupDialog, {useValidationGroupDialog} from "@components/validationRuns/ValidationGroupDialog";
import ValidationRunQuickTransition from "@components/validationRuns/ValidationRunQuickTransition";
import ValidationChip from "@components/primitives/ValidationChip";

/**
 * One status bucket in the builds table's grouped validation column.
 *
 * A bucket holding a single run is drawn as the `ValidationChip` that run would
 * be anywhere else - stamp icon, stamp name, run state - rather than as a
 * hand-assembled copy of one, which is what this used to do.
 *
 * A bucket holding several keeps `ValidationRunStatus`: there is no stamp
 * identity to show, only a status and a count, and the status mark's shape is
 * worth more there than a chip's outline would be.
 *
 * Clicking the single run's chip opens its quick-transition popover; clicking a
 * bucket of several opens the list of its runs.
 *
 * @param onChange Called when the status of a run has changed
 */
export default function ValidationGroup({group, onChange}) {

    const dialog = useValidationGroupDialog()
    const onClick = () => {
        dialog.start(group)
    }

    const single = group.count === 1 ? group.validations[0] : undefined

    return (
        <>
            <Space className="ot-validation-group">
                {
                    group.count > 1 &&
                    <ValidationRunStatus
                        status={group}
                        text={`${group.count} ${group.statusID.name}`}
                        tooltipContent={`${group.description}. Click to get more details.`}
                        onClick={onClick}
                    />
                }
                {
                    single &&
                    <ValidationRunQuickTransition run={single.validationRuns[0]} onChange={onChange}>
                        {({open}) => <Popover
                            title={group.statusID.name}
                            content={group.description}
                            placement="bottom"
                            // The hover summary steps aside while the quick-transition popover is open
                            open={open ? false : undefined}
                        >
                            <span>
                                <ValidationChip
                                    id={`validation-group-${group.statusID.id}`}
                                    validationStamp={single.validationStamp}
                                    statusID={group.statusID}
                                />
                            </span>
                        </Popover>}
                    </ValidationRunQuickTransition>
                }
            </Space>
            <ValidationGroupDialog dialog={dialog} onChange={onChange}/>
        </>
    )
}
